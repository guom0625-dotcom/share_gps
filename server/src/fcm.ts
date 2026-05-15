import { readFileSync, existsSync } from 'node:fs';
import { initializeApp, cert, type App } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';

let fcmApp: App | null = null;

export function initFcm(credPath: string): void {
    if (!existsSync(credPath)) {
        console.warn(`[fcm] credentials not found at ${credPath}, FCM disabled`);
        return;
    }
    try {
        const credentials = JSON.parse(readFileSync(credPath, 'utf8'));
        fcmApp = initializeApp({ credential: cert(credentials) });
        console.log('[fcm] initialized');
    } catch (e) {
        console.error('[fcm] init failed', e);
    }
}

export async function sendFcmToToken(token: string, data: Record<string, string>): Promise<void> {
    if (!fcmApp) return;
    try {
        await getMessaging(fcmApp).send({
            token,
            data,
            android: { priority: 'high' },
        });
        console.log(`[fcm] sent ${JSON.stringify(data)}`);
    } catch (e) {
        console.warn(`[fcm] send failed: ${e}`);
    }
}
