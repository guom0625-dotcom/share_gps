import Database from 'better-sqlite3';
import { readFileSync, readdirSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const MIGRATIONS_DIR = join(__dirname, '..', 'migrations');

export type Db = Database.Database;

export function openDb(path: string): Db {
    mkdirSync(dirname(path), { recursive: true });
    const db = new Database(path);
    db.pragma('journal_mode = WAL');
    db.pragma('foreign_keys = ON');
    runMigrations(db);
    return db;
}

function runMigrations(db: Db): void {
    db.exec(`
        CREATE TABLE IF NOT EXISTS migrations (
            name        TEXT PRIMARY KEY,
            applied_at  INTEGER NOT NULL
        );
    `);

    const applied = new Set(
        db.prepare('SELECT name FROM migrations').all().map((r) => (r as { name: string }).name),
    );

    const files = readdirSync(MIGRATIONS_DIR)
        .filter((f) => f.endsWith('.sql'))
        .sort();

    const insert = db.prepare('INSERT INTO migrations (name, applied_at) VALUES (?, ?)');

    for (const file of files) {
        if (applied.has(file)) continue;
        const sql = readFileSync(join(MIGRATIONS_DIR, file), 'utf8');
        const tx = db.transaction(() => {
            db.exec(sql);
            insert.run(file, Date.now());
        });
        tx();
        console.log(`[migrations] applied ${file}`);
    }
}
