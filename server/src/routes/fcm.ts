import { z } from 'zod';
import type { FastifyInstance } from 'fastify';
import type { Db } from '../db.ts';
import type { makeAuth } from '../auth.ts';

const Body = z.object({ token: z.string().min(1) });

export function registerFcmRoutes(app: FastifyInstance, db: Db, auth: ReturnType<typeof makeAuth>): void {
    const upsertToken = db.prepare('UPDATE users SET fcm_token = ? WHERE id = ?');

    app.post('/me/fcm-token', { preHandler: [auth] }, async (req, reply) => {
        const parsed = Body.safeParse(req.body);
        if (!parsed.success) return reply.status(400).send({ error: 'invalid body' });
        upsertToken.run(parsed.data.token, req.user.id);
        return reply.status(204).send();
    });
}
