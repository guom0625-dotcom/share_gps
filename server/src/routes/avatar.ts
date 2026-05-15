import { writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import type { FastifyInstance } from 'fastify';
import type { makeAuth } from '../auth.ts';

const __dirname = dirname(fileURLToPath(import.meta.url));
const AVATARS_DIR = join(__dirname, '..', '..', 'data', 'avatars');

mkdirSync(AVATARS_DIR, { recursive: true });

export function avatarPath(userId: string): string {
    return join(AVATARS_DIR, `${userId}.jpg`);
}

export function hasAvatar(userId: string): boolean {
    return existsSync(avatarPath(userId));
}

export function registerAvatarRoutes(
    app: FastifyInstance,
    auth: ReturnType<typeof makeAuth>,
): void {
    app.addContentTypeParser('image/jpeg', { parseAs: 'buffer' }, (_req, body, done) => done(null, body));
    app.addContentTypeParser('image/png', { parseAs: 'buffer' }, (_req, body, done) => done(null, body));

    app.post('/me/avatar', { preHandler: [auth] }, async (req, reply) => {
        const body = req.body as Buffer;
        if (!body || body.length === 0) return reply.code(400).send({ error: 'no body' });
        if (body.length > 5 * 1024 * 1024) return reply.code(413).send({ error: 'too large' });
        writeFileSync(avatarPath(req.user.id), body);
        return { ok: true };
    });

    app.get('/users/:userId/avatar', { preHandler: [auth] }, async (req, reply) => {
        const { userId } = req.params as { userId: string };
        const path = avatarPath(userId);
        if (!existsSync(path)) return reply.code(404).send();
        return reply.sendFile(`${userId}.jpg`, AVATARS_DIR);
    });
}
