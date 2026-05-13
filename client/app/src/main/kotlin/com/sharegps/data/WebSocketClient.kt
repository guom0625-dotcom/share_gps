package com.sharegps.data

import android.content.Context
import com.sharegps.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.TimeUnit

class WebSocketClient private constructor(serverUrl: String, private val apiKey: String) {

    companion object {
        @Volatile private var instance: WebSocketClient? = null

        fun get(context: Context): WebSocketClient? {
            instance?.let { return it }
            val key = KeyStore(context).getKey() ?: return null
            return synchronized(this) {
                instance ?: WebSocketClient(BuildConfig.SERVER_URL, key).also { instance = it }
            }
        }
    }

    private val wsUrl = serverUrl.replace("https://", "wss://").replace("http://", "ws://") + "/ws"

    private val okClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeViewers: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    var onActiveModeChanged: ((Boolean) -> Unit)? = null

    private val _locationUpdates = MutableSharedFlow<LocationUpdateMsg>(extraBufferCapacity = 50)
    val locationUpdates: SharedFlow<LocationUpdateMsg> = _locationUpdates.asSharedFlow()

    @Volatile private var ws: WebSocket? = null
    @Volatile private var reconnectJob: Job? = null
    val isConnected: Boolean get() = ws != null
    val isBeingWatched: Boolean get() = activeViewers.isNotEmpty()

    fun connect() {
        if (ws != null) return
        ws = okClient.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
    }

    fun sendRaw(json: String) {
        ws?.send(json)
    }

    private fun scheduleReconnect(delayMs: Long = 5_000L) {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(delayMs)
            if (ws == null) connect()
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectJob?.cancel()
            webSocket.send("""{"type":"auth","key":"$apiKey"}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val json = JSONObject(text)
                when (json.optString("type")) {
                    "watching" -> {
                        val viewerId = json.optString("viewerUserId")
                        if (viewerId.isNotEmpty() && activeViewers.add(viewerId)) {
                            onActiveModeChanged?.invoke(true)
                        }
                    }
                    "watching_stop" -> {
                        val viewerId = json.optString("viewerUserId")
                        activeViewers.remove(viewerId)
                        if (activeViewers.isEmpty()) onActiveModeChanged?.invoke(false)
                    }
                    "location_update" -> {
                        _locationUpdates.tryEmit(
                            LocationUpdateMsg(
                                userId = json.optString("userId"),
                                lat = json.optDouble("lat"),
                                lng = json.optDouble("lng"),
                                accuracy = json.optDouble("accuracy").takeIf { !it.isNaN() },
                                recordedAt = json.optLong("recordedAt"),
                            )
                        )
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            ws = null
            activeViewers.clear()
            onActiveModeChanged?.invoke(false)
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            ws = null
            activeViewers.clear()
            onActiveModeChanged?.invoke(false)
            if (code < 4000) scheduleReconnect()
        }
    }
}
