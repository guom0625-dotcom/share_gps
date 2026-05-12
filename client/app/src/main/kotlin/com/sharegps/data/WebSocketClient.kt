package com.sharegps.data

import android.content.Context
import com.sharegps.BuildConfig
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val activeViewers: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    // LocationService registers this to flip active mode
    var onActiveModeChanged: ((Boolean) -> Unit)? = null

    // LiveMapViewModel collects this to update map position
    private val _locationUpdates = MutableSharedFlow<LocationUpdateMsg>(extraBufferCapacity = 50)
    val locationUpdates: SharedFlow<LocationUpdateMsg> = _locationUpdates.asSharedFlow()

    @Volatile private var ws: WebSocket? = null
    val isConnected: Boolean get() = ws != null
    val isBeingWatched: Boolean get() = activeViewers.isNotEmpty()

    fun connect() {
        if (ws != null) return
        ws = okClient.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
    }

    fun sendRaw(json: String) {
        ws?.send(json)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
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
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            ws = null
        }
    }
}
