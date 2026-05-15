interface SocketLike {
    readonly readyState: number;
    send(data: string): void;
    close(code?: number, reason?: string): void;
}

const WS_OPEN = 1;

function safeSend(sock: SocketLike, msg: string): void {
    if (sock.readyState === WS_OPEN) {
        try { sock.send(msg); } catch { /* ignore closed socket */ }
    }
}

export class WatchingSession {
    private readonly connections = new Map<string, SocketLike>();
    // targetId → Set<viewerId>
    private readonly watchers = new Map<string, Set<string>>();
    // viewerId → Set<targetId>  (for cleanup on disconnect)
    private readonly viewerTargets = new Map<string, Set<string>>();

    addConnection(userId: string, socket: SocketLike): void {
        const prev = this.connections.get(userId);
        if (prev) {
            try { prev.close(4000, 'replaced by new connection'); } catch { /* ignore */ }
        }
        this.connections.set(userId, socket);
    }

    removeConnection(userId: string): void {
        this.connections.delete(userId);
        this._cleanupViewer(userId);
    }

    watchStart(viewerId: string, targetId: string): void {
        let viewerSet = this.watchers.get(targetId);
        if (!viewerSet) { viewerSet = new Set(); this.watchers.set(targetId, viewerSet); }
        viewerSet.add(viewerId);

        let targetSet = this.viewerTargets.get(viewerId);
        if (!targetSet) { targetSet = new Set(); this.viewerTargets.set(viewerId, targetSet); }
        targetSet.add(targetId);

        this.sendTo(targetId, { type: 'watching', viewerUserId: viewerId });
    }

    watchStop(viewerId: string, targetId: string): void {
        const viewerSet = this.watchers.get(targetId);
        if (viewerSet) {
            viewerSet.delete(viewerId);
            if (viewerSet.size === 0) this.watchers.delete(targetId);
        }
        const targetSet = this.viewerTargets.get(viewerId);
        if (targetSet) {
            targetSet.delete(targetId);
            if (targetSet.size === 0) this.viewerTargets.delete(viewerId);
        }
        this.sendTo(targetId, { type: 'watching_stop', viewerUserId: viewerId });
    }

    broadcastToWatchers(fromUserId: string, payload: object): void {
        const viewerSet = this.watchers.get(fromUserId);
        if (!viewerSet || viewerSet.size === 0) return;
        const msg = JSON.stringify(payload);
        for (const viewerId of viewerSet) {
            const sock = this.connections.get(viewerId);
            if (sock) safeSend(sock, msg);
        }
    }

    isConnected(userId: string): boolean {
        return this.connections.has(userId);
    }

    getWatchersOf(targetId: string): string[] {
        return Array.from(this.watchers.get(targetId) ?? []);
    }

    sendTo(userId: string, payload: object): void {
        const sock = this.connections.get(userId);
        if (sock) safeSend(sock, JSON.stringify(payload));
    }

    private _cleanupViewer(viewerId: string): void {
        const targetSet = this.viewerTargets.get(viewerId);
        if (!targetSet) return;
        for (const targetId of targetSet) {
            const viewerSet = this.watchers.get(targetId);
            if (viewerSet) {
                viewerSet.delete(viewerId);
                if (viewerSet.size === 0) this.watchers.delete(targetId);
            }
            this.sendTo(targetId, { type: 'watching_stop', viewerUserId: viewerId });
        }
        this.viewerTargets.delete(viewerId);
    }
}
