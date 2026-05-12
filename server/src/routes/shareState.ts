import { z } from 'zod';
import type { FastifyInstance } from 'fastify';
import type { Db } from '../db.ts';
import type { makeAuth } from '../auth.ts';

const ShareStateSchema = z.object({
    mode: z.enum(['sharing', 'paused', 'off']),
    precisionMode: z.enum(['exact', 'approximate']),
    pausedUntilMinutes: z.number().int().positive().optional(),
});

export function registerShareStateRoutes(
    app: FastifyInstance,
    db: Db,
    auth: ReturnType<typeof makeAuth>,
): void {
    const upsert = db.prepare(`
        INSERT INTO share_state (user_id, mode, precision_mode, paused_until, updated_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(user_id) DO UPDATE SET
            mode = excluded.mode,
            precision_mode = excluded.precision_mode,
            paused_until = excluded.paused_until,
            updated_at = excluded.updated_at
    `);

    app.post('/me/share-state', { preHandler: [auth] }, async (req, reply) => {
        const parsed = ShareStateSchema.safeParse(req.body);
        if (!parsed.success) {
            return reply.code(400).send({ error: 'invalid body', issues: parsed.error.issues });
        }

        const { mode, precisionMode, pausedUntilMinutes } = parsed.data;
        const now = Date.now();
        const pausedUntil =
            mode === 'paused' && pausedUntilMinutes
                ? now + pausedUntilMinutes * 60_000
                : null;

        upsert.run(req.user.id, mode, precisionMode, pausedUntil, now);
        return { ok: true };
    });
}
