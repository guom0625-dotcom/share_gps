import 'dotenv/config';
import os from 'node:os';
// proot/Termux: uv_interface_addresses syscall is blocked; patch before Fastify logs server address
const _ni = os.networkInterfaces.bind(os);
os.networkInterfaces = () => { try { return _ni(); } catch { return {}; } };
import Fastify from 'fastify';
import websocket from '@fastify/websocket';
import { openDb } from './db.ts';
import { makeAuth } from './auth.ts';
import { registerFamilyRoutes } from './routes/family.ts';
import { registerLocationRoutes } from './routes/locations.ts';
import { registerShareStateRoutes } from './routes/shareState.ts';
import { registerWsServer } from './ws/server.ts';

const PORT = Number(process.env.PORT ?? 3000);
const HOST = process.env.HOST ?? '0.0.0.0';
const DB_PATH = process.env.DB_PATH ?? './data/share_gps.sqlite3';
const LOG_LEVEL = process.env.LOG_LEVEL ?? 'info';

const db = openDb(DB_PATH);

const app = Fastify({ logger: { level: LOG_LEVEL } });

app.decorateRequest('user', null);
await app.register(websocket);

const auth = makeAuth(db);
registerFamilyRoutes(app, db, auth);
registerLocationRoutes(app, db, auth);
registerShareStateRoutes(app, db, auth);
registerWsServer(app, db);

app.get('/health', async () => ({ ok: true, time: Date.now() }));
app.get('/version', async () => ({ server: '0.1.0', minClient: '0.1.0' }));

const shutdown = async (signal: string) => {
    app.log.info({ signal }, 'shutting down');
    await app.close();
    db.close();
    process.exit(0);
};
process.on('SIGINT', () => void shutdown('SIGINT'));
process.on('SIGTERM', () => void shutdown('SIGTERM'));

try {
    await app.listen({ port: PORT, host: HOST });
} catch (err) {
    app.log.error(err);
    process.exit(1);
}
