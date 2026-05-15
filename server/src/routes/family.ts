import type { FastifyInstance } from 'fastify';
import type { Db } from '../db.ts';
import type { makeAuth } from '../auth.ts';
import { hasAvatar } from './avatar.ts';

interface FamilyRow {
    id: string;
    name: string;
    role: string;
    share_mode: string | null;
    lat: number | null;
    lng: number | null;
    accuracy: number | null;
    battery: number | null;
    recorded_at: number | null;
}

interface MeRow {
    id: string;
    name: string;
    role: string;
    mode: string | null;
    precision_mode: string | null;
    paused_until: number | null;
}

export function registerFamilyRoutes(
    app: FastifyInstance,
    db: Db,
    auth: ReturnType<typeof makeAuth>,
): void {
    const getMe = db.prepare(`
        SELECT u.id, u.name, u.role, s.mode, s.precision_mode, s.paused_until
        FROM users u
        LEFT JOIN share_state s ON s.user_id = u.id
        WHERE u.id = ?
    `);

    const getFamily = db.prepare(`
        SELECT
            u.id, u.name, u.role,
            COALESCE(s.mode, 'sharing') AS share_mode,
            cl.lat, cl.lng, cl.accuracy, cl.battery, cl.recorded_at
        FROM users u
        LEFT JOIN share_state s ON s.user_id = u.id
        LEFT JOIN current_location cl ON cl.user_id = u.id
        WHERE u.revoked_at IS NULL
        ORDER BY u.created_at
    `);

    app.get('/me', { preHandler: [auth] }, async (req) => {
        const row = getMe.get(req.user.id) as MeRow;
        return {
            id: row.id,
            name: row.name,
            role: row.role,
            shareState: {
                mode: row.mode ?? 'sharing',
                precisionMode: row.precision_mode ?? 'exact',
                pausedUntil: row.paused_until ?? null,
            },
        };
    });

    app.get('/family', { preHandler: [auth] }, async () => {
        const rows = getFamily.all() as FamilyRow[];
        return rows.map((r) => ({
            id: r.id,
            name: r.name,
            role: r.role,
            shareMode: r.share_mode ?? 'sharing',
            hasAvatar: hasAvatar(r.id),
            current:
                r.lat !== null && r.recorded_at !== null
                    ? { lat: r.lat, lng: r.lng, accuracy: r.accuracy, battery: r.battery, recordedAt: r.recorded_at }
                    : null,
        }));
    });
}
