import { z } from 'zod';
import type { FastifyInstance } from 'fastify';
import type { Db } from '../db.ts';
import type { makeAuth } from '../auth.ts';

const PointSchema = z.object({
    lat: z.number(),
    lng: z.number(),
    accuracy: z.number().optional(),
    activity: z.string().optional(),
    battery: z.number().int().optional(),
    recordedAt: z.number().int(),
});

const BatchSchema = z.array(PointSchema).min(1).max(500);

const THIRTY_DAYS_MS = 30 * 24 * 60 * 60 * 1000;

export function registerLocationRoutes(
    app: FastifyInstance,
    db: Db,
    auth: ReturnType<typeof makeAuth>,
): void {
    const getShareMode = db.prepare<[string], { mode: string }>(
        'SELECT mode FROM share_state WHERE user_id = ?',
    );
    const insertLoc = db.prepare(`
        INSERT INTO locations (user_id, lat, lng, accuracy, activity, battery, recorded_at, received_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `);
    const upsertCurrent = db.prepare(`
        INSERT INTO current_location (user_id, lat, lng, accuracy, activity, battery, recorded_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(user_id) DO UPDATE SET
            lat = excluded.lat, lng = excluded.lng,
            accuracy = excluded.accuracy, activity = excluded.activity,
            battery = excluded.battery, recorded_at = excluded.recorded_at
        WHERE excluded.recorded_at > current_location.recorded_at
    `);

    app.post('/locations/batch', { preHandler: [auth] }, async (req, reply) => {
        const parsed = BatchSchema.safeParse(req.body);
        if (!parsed.success) {
            return reply.code(400).send({ error: 'invalid body', issues: parsed.error.issues });
        }

        const stateRow = getShareMode.get(req.user.id);
        const mode = stateRow?.mode ?? 'sharing';

        if (mode === 'paused' || mode === 'off') {
            return { saved: 0, rejected: parsed.data.length };
        }

        const points = parsed.data;
        const receivedAt = Date.now();
        const userId = req.user.id;

        const save = db.transaction(() => {
            for (const p of points) {
                insertLoc.run(userId, p.lat, p.lng, p.accuracy ?? null, p.activity ?? null, p.battery ?? null, p.recordedAt, receivedAt);
                upsertCurrent.run(userId, p.lat, p.lng, p.accuracy ?? null, p.activity ?? null, p.battery ?? null, p.recordedAt);
            }
        });
        save();

        return { saved: points.length, rejected: 0 };
    });

    app.get<{ Params: { userId: string }; Querystring: { year?: string; month?: string } }>(
        '/locations/:userId/active-days',
        { preHandler: [auth] },
        async (req, reply) => {
            if (req.user.role !== 'parent' && req.params.userId !== req.user.id) {
                return reply.code(403).send({ error: 'not authorized' });
            }
            const y = parseInt(req.query.year  ?? String(new Date().getFullYear()), 10);
            const m = parseInt(req.query.month ?? String(new Date().getMonth() + 1),  10);
            const start = new Date(y, m - 1, 1).getTime();
            const end   = new Date(y, m,     1).getTime();
            const rows = db.prepare(`
                SELECT DISTINCT
                    CAST(strftime('%d', datetime(recorded_at / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS day
                FROM locations
                WHERE user_id = ? AND recorded_at >= ? AND recorded_at < ?
            `).all(req.params.userId, start, end) as { day: number }[];
            return { days: rows.map(r => r.day) };
        },
    );

    app.get<{ Params: { userId: string }; Querystring: { from?: string; to?: string } }>(
        '/locations/:userId/history',
        { preHandler: [auth] },
        async (req, reply) => {
            if (req.user.role !== 'parent' && req.params.userId !== req.user.id) {
                return reply.code(403).send({ error: 'not authorized' });
            }

            const now = Date.now();
            const to = req.query.to ? parseInt(req.query.to, 10) : now;
            const from = req.query.from
                ? parseInt(req.query.from, 10)
                : now - THIRTY_DAYS_MS;

            // Clamp to 30-day window
            const clampedFrom = Math.max(from, to - THIRTY_DAYS_MS);

            const BUCKET_MS = 5 * 60 * 1000; // 5분
            const rows = db.prepare(`
                SELECT lat, lng, accuracy, MIN(recorded_at) AS recordedAt
                FROM locations
                WHERE user_id = ? AND recorded_at BETWEEN ? AND ?
                GROUP BY recorded_at / ${BUCKET_MS}
                ORDER BY recordedAt ASC
            `).all(req.params.userId, clampedFrom, to);

            return rows;
        },
    );
}
