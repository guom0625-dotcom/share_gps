import { createHash } from 'node:crypto';
import { z } from 'zod';
import type { FastifyInstance } from 'fastify';
import type { WebSocket } from '@fastify/websocket';
import type { Db } from '../db.ts';
import { WatchingSession } from './watching.ts';
import { sendFcmToToken } from '../fcm.ts';

const AUTH_TIMEOUT_MS = 10_000;

const AuthMsg = z.object({ type: z.literal('auth'), key: z.string() });

const ClientMsg = z.discriminatedUnion('type', [
    z.object({ type: z.literal('location'), lat: z.number(), lng: z.number(), accuracy: z.number().optional(), battery: z.number().int().min(0).max(100).optional(), recordedAt: z.number().int(), speed: z.number().optional() }),
    z.object({ type: z.literal('watch_start'), targetUserId: z.string() }),
    z.object({ type: z.literal('watch_stop'), targetUserId: z.string() }),
    z.object({ type: z.literal('ping') }),
]);

export const sessions = new WatchingSession();

export function registerWsServer(app: FastifyInstance, db: Db): void {
    const findUser = db.prepare(`
        SELECT u.id, u.name, u.role
        FROM auth_keys k JOIN users u ON u.id = k.user_id
        WHERE k.key_hash = ? AND k.revoked_at IS NULL AND u.revoked_at IS NULL
    `);
    const getFcmToken = db.prepare('SELECT fcm_token FROM users WHERE id = ?');
    const touchKey = db.prepare('UPDATE auth_keys SET last_used_at = ? WHERE key_hash = ?');
    const upsertCurrent = db.prepare(`
        INSERT INTO current_location (user_id, lat, lng, accuracy, activity, battery, recorded_at)
        VALUES (?, ?, ?, ?, NULL, ?, ?)
        ON CONFLICT(user_id) DO UPDATE SET
            lat = excluded.lat, lng = excluded.lng,
            accuracy = excluded.accuracy,
            battery = excluded.battery,
            recorded_at = excluded.recorded_at
        WHERE excluded.recorded_at > current_location.recorded_at
    `);
    const insertLoc = db.prepare(`
        INSERT OR IGNORE INTO locations (user_id, lat, lng, accuracy, activity, battery, speed, recorded_at, received_at)
        VALUES (?, ?, ?, ?, NULL, ?, ?, ?, ?)
    `);

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (app as any).get('/ws', { websocket: true }, (socket: WebSocket) => {
        let userId: string | null = null;
        let authed = false;

        const send = (payload: object) => {
            try { socket.send(JSON.stringify(payload)); } catch { /* ignore */ }
        };

        const authTimeout = setTimeout(() => {
            if (!authed) socket.close(4001, 'auth timeout');
        }, AUTH_TIMEOUT_MS);

        socket.on('message', (raw: Buffer | string) => {
            let data: unknown;
            try { data = JSON.parse(raw.toString()); } catch {
                send({ type: 'error', reason: 'invalid json' });
                return;
            }

            if (!authed) {
                const parsed = AuthMsg.safeParse(data);
                if (!parsed.success) {
                    send({ type: 'auth_fail', reason: 'expected auth message' });
                    socket.close(4002, 'auth required');
                    return;
                }
                const keyHash = createHash('sha256').update(parsed.data.key).digest('hex');
                const user = findUser.get(keyHash) as { id: string; name: string; role: string } | undefined;
                if (!user) {
                    send({ type: 'auth_fail', reason: 'invalid key' });
                    socket.close(4003, 'invalid key');
                    return;
                }
                clearTimeout(authTimeout);
                touchKey.run(Date.now(), keyHash);
                userId = user.id;
                authed = true;
                sessions.addConnection(userId, socket);
                send({ type: 'auth_ok', userId: user.id, role: user.role });
                // Re-send current watchers (for FCM-woken reconnects)
                const watchers = sessions.getWatchersOf(userId);
                if (watchers.length > 0) {
                    for (const viewerId of watchers) {
                        send({ type: 'watching', viewerUserId: viewerId });
                    }
                } else {
                    send({ type: 'no_watchers' });
                }
                return;
            }

            const msg = ClientMsg.safeParse(data);
            if (!msg.success || !userId) return;

            switch (msg.data.type) {
                case 'location': {
                    const { lat, lng, accuracy, battery, recordedAt, speed } = msg.data;
                    const receivedAt = Date.now();
                    db.transaction(() => {
                        upsertCurrent.run(userId, lat, lng, accuracy ?? null, battery ?? null, recordedAt);
                        insertLoc.run(userId, lat, lng, accuracy ?? null, battery ?? null, speed ?? null, recordedAt, receivedAt);
                    })();
                    sessions.broadcastToWatchers(userId, {
                        type: 'location_update',
                        userId, lat, lng,
                        accuracy: accuracy ?? null,
                        battery: battery ?? null,
                        recordedAt,
                    });
                    break;
                }
                case 'watch_start': {
                    const targetId = msg.data.targetUserId;
                    sessions.watchStart(userId, targetId);
                    // Always send FCM regardless of WS reachability:
                    // TCP silent drops can leave socket appearing open while messages are lost.
                    const row = getFcmToken.get(targetId) as { fcm_token: string | null } | undefined;
                    if (row?.fcm_token) {
                        console.log(`[fcm] sending watch_start to ${targetId}`);
                        void sendFcmToToken(row.fcm_token, { type: 'watch_start' });
                    } else {
                        console.log(`[fcm] no token for ${targetId}, cannot wake`);
                    }
                    break;
                }
                case 'watch_stop':
                    console.log(`[ws] watch_stop from ${userId} → ${msg.data.targetUserId}`);
                    sessions.watchStop(userId, msg.data.targetUserId);
                    break;
                case 'ping':
                    send({ type: 'pong' });
                    break;
            }
        });

        socket.on('close', () => {
            clearTimeout(authTimeout);
            if (userId) sessions.removeConnection(userId);
        });

        socket.on('error', () => {
            if (userId) sessions.removeConnection(userId);
        });
    });
}
