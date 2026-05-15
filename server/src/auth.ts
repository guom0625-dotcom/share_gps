import { createHash } from 'node:crypto';
import type { FastifyRequest, FastifyReply } from 'fastify';
import type { Db } from './db.ts';

export interface AuthUser {
    id: string;
    name: string;
    role: 'parent' | 'child';
}

declare module 'fastify' {
    interface FastifyRequest {
        user: AuthUser;
    }
}

const ONE_HOUR_MS = 3_600_000;

export function makeAuth(db: Db) {
    const find = db.prepare(`
        SELECT u.id, u.name, u.role, k.last_used_at
        FROM auth_keys k
        JOIN users u ON u.id = k.user_id
        WHERE k.key_hash = ?
          AND k.revoked_at IS NULL
          AND u.revoked_at IS NULL
    `);
    const touch = db.prepare('UPDATE auth_keys SET last_used_at = ? WHERE key_hash = ?');

    return async function requireAuth(req: FastifyRequest, reply: FastifyReply): Promise<void> {
        const header = req.headers.authorization;
        if (!header?.startsWith('Bearer ')) {
            await reply.code(401).send({ error: 'unauthorized' });
            return;
        }
        const keyHash = createHash('sha256').update(header.slice(7)).digest('hex');
        const row = find.get(keyHash) as (AuthUser & { last_used_at: number | null }) | undefined;
        if (!row) {
            await reply.code(401).send({ error: 'unauthorized' });
            return;
        }
        const now = Date.now();
        if (!row.last_used_at || now - row.last_used_at > ONE_HOUR_MS) {
            touch.run(now, keyHash);
        }
        req.user = row;
    };
}
