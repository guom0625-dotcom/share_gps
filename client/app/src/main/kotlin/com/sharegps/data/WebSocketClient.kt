package com.sharegps.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.TimeUnit

class WebSocketClient(serverUrl: String, private val apiKey: String) {
    private val wsUrl = serverUrl
        .replace("https://", "wss://")
        .replace("http://", "ws://") + "/ws"

    private val okClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private val activeViewers: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    var onActiveModeChanged: ((Boolean) -> Unit)? = null

    @Volatile private var ws: WebSocket? = null

    fun connect() {
        ws = okClient.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
    }

    fun disconnect() {
        ws?.close(1000, null)
        ws = null
        activeViewers.clear()
    }

    val isBeingWatched: Boolean get() = activeViewers.isNotEmpty()

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
                }
            }
        }
    }
}
