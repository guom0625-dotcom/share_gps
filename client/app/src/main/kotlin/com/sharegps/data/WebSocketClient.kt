package com.sharegps.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.sharegps.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.TimeUnit

class WebSocketClient private constructor(
    context: Context,
    serverUrl: String,
    private val apiKey: String,
) {

    companion object {
        @Volatile private var instance: WebSocketClient? = null

        fun get(context: Context): WebSocketClient? {
            instance?.let { return it }
            val key = KeyStore(context).getKey() ?: return null
            return synchronized(this) {
                instance ?: WebSocketClient(context.applicationContext, resolveServerUrl(context), key).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val wsUrl = serverUrl.replace("https://", "wss://").replace("http://", "ws://") + "/ws"

    private val okClient = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeViewers: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private val watchingTargets: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private val _isBeingWatched = MutableStateFlow(false)
    val isBeingWatched: StateFlow<Boolean> = _isBeingWatched.asStateFlow()

    private val _locationUpdates = MutableSharedFlow<LocationUpdateMsg>(extraBufferCapacity = 50)
    val locationUpdates: SharedFlow<LocationUpdateMsg> = _locationUpdates.asSharedFlow()

    @Volatile private var ws: WebSocket? = null
    @Volatile private var reconnectJob: Job? = null
    @Volatile private var intentionalDisconnect = false
    @Volatile var myUserId: String? = null
        private set
    val isConnected: Boolean get() = ws != null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d("WS", "network onAvailable → forceReconnect")
            forceReconnect()
        }
    }

    init {
        appContext.getSystemService(ConnectivityManager::class.java)
            ?.registerDefaultNetworkCallback(networkCallback)
    }

    fun connect() {
        intentionalDisconnect = false
        if (ws != null) return
        ws = okClient.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
    }

    @Synchronized
    fun forceReconnect() {
        intentionalDisconnect = false
        val old = ws
        ws = okClient.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
        try { old?.close(4002, "force reconnect") } catch (_: Exception) {}
    }

    fun disconnect() {
        intentionalDisconnect = true
        reconnectJob?.cancel()
        ws?.close(4001, "background")
        ws = null
        applyWatcherChange { clear() }
    }

    fun sendRaw(json: String): Boolean = ws?.send(json) ?: false

    fun watchStart(targetUserId: String) {
        watchingTargets.add(targetUserId)
        sendRaw(JSONObject().put("type", "watch_start").put("targetUserId", targetUserId).toString())
    }

    fun watchStop(targetUserId: String) {
        watchingTargets.remove(targetUserId)
        sendRaw(JSONObject().put("type", "watch_stop").put("targetUserId", targetUserId).toString())
    }

    private fun applyWatcherChange(block: MutableSet<String>.() -> Unit) {
        synchronized(activeViewers) {
            activeViewers.block()
            _isBeingWatched.value = activeViewers.isNotEmpty()
        }
    }

    private fun scheduleReconnect(delayMs: Long = 5_000L) {
        if (intentionalDisconnect) return
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(delayMs)
            if (ws == null) connect()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectJob?.cancel()
            webSocket.send(JSONObject().put("type", "auth").put("key", apiKey).toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val json = JSONObject(text)
                when (json.optString("type")) {
                    "auth_ok" -> {
                        myUserId = json.optString("userId").takeIf { it.isNotEmpty() }
                        // Server replays watching/no_watchers next — reset to avoid stale phantom watchers.
                        applyWatcherChange { clear() }
                        for (targetId in watchingTargets) {
                            webSocket.send(JSONObject().put("type", "watch_start").put("targetUserId", targetId).toString())
                        }
                    }
                    "watching" -> {
                        val viewerId = json.optString("viewerUserId")
                        if (viewerId.isNotEmpty()) {
                            applyWatcherChange { add(viewerId) }
                            Log.d("WS", "watching from $viewerId, viewers=${activeViewers.size}")
                        }
                    }
                    "watching_stop" -> {
                        val viewerId = json.optString("viewerUserId")
                        if (viewerId.isNotEmpty()) {
                            applyWatcherChange { remove(viewerId) }
                            Log.d("WS", "watching_stop from $viewerId, remaining=${activeViewers.size}")
                        }
                    }
                    "no_watchers" -> {
                        applyWatcherChange { clear() }
                        Log.d("WS", "no_watchers")
                    }
                    "location_update" -> {
                        _locationUpdates.tryEmit(
                            LocationUpdateMsg(
                                userId = json.optString("userId"),
                                lat = json.optDouble("lat"),
                                lng = json.optDouble("lng"),
                                accuracy = json.optDouble("accuracy").takeIf { !it.isNaN() },
                                battery = if (json.has("battery") && !json.isNull("battery")) json.optInt("battery") else null,
                                recordedAt = json.optLong("recordedAt"),
                                speed = json.optDouble("speed").takeIf { !it.isNaN() && it > 0 },
                            )
                        )
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // Stale socket from forceReconnect — ignore, new socket already in place.
            if (ws !== webSocket) return
            // Keep activeViewers as-is: server replays watching/no_watchers on re-auth.
            ws = null
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            // Stale socket from forceReconnect — ignore.
            if (ws !== webSocket) return
            ws = null
            if (code < 4000) {
                // Transient: server replays state on re-auth, don't drop watchers.
                scheduleReconnect()
            } else {
                applyWatcherChange { clear() }
            }
        }
    }
}
