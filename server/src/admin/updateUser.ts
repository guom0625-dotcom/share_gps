import type { Db } from '../db.ts';

export function updateUser(db: Db, flags: Record<string, string | undefined>): void {
    const userId = flags['user-id'];
    const name   = flags['name'];
    const role   = flags['role'];

    if (!userId || (!name && !role)) {
        console.error('Usage: npm run admin -- update-user --user-id=<id> [--name=<이름>] [--role=parent|child]');
        process.exit(1);
    }
    if (role && role !== 'parent' && role !== 'child') {
        console.error('role은 parent 또는 child');
        process.exit(1);
    }

    const user = db.prepare<[string], { id: string; name: string; role: string }>(
        'SELECT id, name, role FROM users WHERE id = ? AND revoked_at IS NULL',
    ).get(userId);

    if (!user) {
        console.error(`사용자를 찾을 수 없습니다: ${userId}`);
        process.exit(1);
    }

    const newName = name ?? user.name;
    const newRole = role ?? user.role;

    db.prepare('UPDATE users SET name = ?, role = ? WHERE id = ?').run(newName, newRole, userId);

    console.log('사용자 정보 업데이트 완료');
    console.log(`  ID  : ${userId}`);
    console.log(`  이름: ${user.name}${user.name !== newName ? ` → ${newName}` : ''}`);
    console.log(`  역할: ${user.role}${user.role !== newRole ? ` → ${newRole}` : ''}`);
}
