import type { Db } from '../db.ts';

interface UserRow {
    id: string;
    name: string;
    role: string;
    created_at: number;
    revoked_at: number | null;
    active_keys: number;
    last_used_at: number | null;
}

export function listUsers(db: Db): void {
    const rows = db.prepare(`
        SELECT
            u.id, u.name, u.role, u.created_at, u.revoked_at,
            COUNT(CASE WHEN k.revoked_at IS NULL THEN 1 END) AS active_keys,
            MAX(k.last_used_at) AS last_used_at
        FROM users u
        LEFT JOIN auth_keys k ON k.user_id = u.id
        GROUP BY u.id
        ORDER BY u.created_at
    `).all() as UserRow[];

    if (rows.length === 0) {
        console.log('등록된 사용자 없음');
        return;
    }

    for (const r of rows) {
        const status = r.revoked_at ? '[폐기]' : '[활성]';
        const lastUsed = r.last_used_at
            ? new Date(r.last_used_at).toLocaleString('ko-KR')
            : '없음';
        console.log(`${status} ${r.role.padEnd(6)} | ${r.name.padEnd(10)} | ${r.id}`);
        console.log(`         활성키: ${r.active_keys}개  마지막사용: ${lastUsed}`);
    }
}
