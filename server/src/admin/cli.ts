import 'dotenv/config';
import { openDb } from '../db.ts';
import { createUser } from './createUser.ts';
import { listUsers } from './listUsers.ts';
import { resetKey } from './resetKey.ts';
import { revokeUser } from './revokeUser.ts';
import { updateUser } from './updateUser.ts';

const DB_PATH = process.env.DB_PATH ?? './data/share_gps.sqlite3';
const db = openDb(DB_PATH);

const [, , cmd, ...rest] = process.argv;

const flags: Record<string, string | undefined> = {};
for (const arg of rest ?? []) {
    const m = arg.match(/^--([^=]+)=(.+)$/);
    if (m && m[1] && m[2]) flags[m[1]] = m[2];
}

switch (cmd) {
    case 'create-user':
        createUser(db, flags);
        break;
    case 'list-users':
        listUsers(db);
        break;
    case 'reset-key':
        resetKey(db, flags);
        break;
    case 'revoke-user':
        revokeUser(db, flags);
        break;
    case 'update-user':
        updateUser(db, flags);
        break;
    default:
        console.error('사용법:');
        console.error('  npm run admin -- create-user  --name=<이름> --role=parent|child');
        console.error('  npm run admin -- list-users');
        console.error('  npm run admin -- reset-key    --user-id=<id>');
        console.error('  npm run admin -- revoke-user  --user-id=<id>');
        console.error('  npm run admin -- update-user  --user-id=<id> [--name=<이름>] [--role=parent|child]');
        process.exit(1);
}

db.close();
