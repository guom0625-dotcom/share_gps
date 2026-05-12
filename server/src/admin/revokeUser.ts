import type { Db } from '../db.ts';

export function revokeUser(db: Db, flags: Record<string, string | undefined>): void {
    const userId = flags['user-id'];
    if (!userId) {
        console.error('Usage: npm run admin -- revoke-user --user-id=<id>');
        process.exit(1);
    }

    const user = db.prepare('SELECT id, name FROM users WHERE id = ? AND revoked_at IS NULL').get(userId) as
        | { id: string; name: string }
        | undefined;
    if (!user) {
        console.error(`사용자 없음 또는 이미 폐기됨: ${userId}`);
        process.exit(1);
    }

    const now = Date.now();

    db.transaction(() => {
        db.prepare('UPDATE users SET revoked_at = ? WHERE id = ?').run(now, userId);
        db.prepare('UPDATE auth_keys SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL').run(now, userId);
        db.prepare('DELETE FROM locations WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM current_location WHERE user_id = ?').run(userId);
        db.prepare('DELETE FROM share_state WHERE user_id = ?').run(userId);
    })();

    console.log(`사용자 폐기 완료: ${user.name} (${userId})`);
    console.log('위치 이력 및 현재 위치 삭제 완료');
}
