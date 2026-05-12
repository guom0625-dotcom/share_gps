import type { Db } from '../db.ts';
import { genKey } from './createUser.ts';

export function resetKey(db: Db, flags: Record<string, string | undefined>): void {
    const userId = flags['user-id'];
    if (!userId) {
        console.error('Usage: npm run admin -- reset-key --user-id=<id>');
        process.exit(1);
    }

    const user = db.prepare('SELECT id, name FROM users WHERE id = ? AND revoked_at IS NULL').get(userId) as
        | { id: string; name: string }
        | undefined;
    if (!user) {
        console.error(`사용자 없음 또는 폐기됨: ${userId}`);
        process.exit(1);
    }

    const now = Date.now();
    const { key, hash } = genKey();

    db.transaction(() => {
        db.prepare('UPDATE auth_keys SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL').run(now, userId);
        db.prepare('INSERT INTO auth_keys (key_hash, user_id, created_at) VALUES (?, ?, ?)').run(hash, userId, now);
    })();

    console.log(`키 재발급 완료: ${user.name} (${userId})`);
    console.log(`  새 키: ${key}`);
    console.log('(키는 재출력 불가 — 지금 안전한 곳에 보관하세요)');
}
