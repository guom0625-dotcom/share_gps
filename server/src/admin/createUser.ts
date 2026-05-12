import { randomBytes, createHash } from 'node:crypto';
import { ulid } from 'ulid';
import type { Db } from '../db.ts';

function genKey(): { key: string; hash: string } {
    const raw = randomBytes(16).toString('hex');
    const key = `K-${raw.slice(0, 4)}-${raw.slice(4, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}-${raw.slice(20, 24)}-${raw.slice(24, 28)}-${raw.slice(28, 32)}`;
    const hash = createHash('sha256').update(key).digest('hex');
    return { key, hash };
}

export function createUser(db: Db, flags: Record<string, string | undefined>): void {
    const name = flags['name'];
    const role = flags['role'];

    if (!name || !role) {
        console.error('Usage: npm run admin -- create-user --name=<name> --role=parent|child');
        process.exit(1);
    }
    if (role !== 'parent' && role !== 'child') {
        console.error('role은 parent 또는 child');
        process.exit(1);
    }

    const userId = ulid();
    const now = Date.now();
    const { key, hash } = genKey();

    db.transaction(() => {
        db.prepare('INSERT INTO users (id, name, role, created_at) VALUES (?, ?, ?, ?)').run(userId, name, role, now);
        db.prepare('INSERT INTO auth_keys (key_hash, user_id, created_at) VALUES (?, ?, ?)').run(hash, userId, now);
        db.prepare('INSERT INTO share_state (user_id, mode, precision_mode, updated_at) VALUES (?, ?, ?, ?)').run(userId, 'sharing', 'exact', now);
    })();

    console.log('사용자 생성 완료');
    console.log(`  ID  : ${userId}`);
    console.log(`  이름: ${name}`);
    console.log(`  역할: ${role}`);
    console.log(`  키  : ${key}`);
    console.log('(키는 재출력 불가 — 지금 안전한 곳에 보관하세요)');
}

export { genKey };
