package com.sharegps.ui.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sharegps.data.LocationUpdateMsg
import com.sharegps.data.WebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LiveMapViewModel(app: Application) : AndroidViewModel(app) {
    private val _position = MutableStateFlow<LocationUpdateMsg?>(null)
    val position: StateFlow<LocationUpdateMsg?> = _position

    fun startWatching(targetId: String) {
        val ws = WebSocketClient.get(getApplication()) ?: return
        ws.sendRaw("""{"type":"watch_start","targetUserId":"$targetId"}""")
        viewModelScope.launch {
            ws.locationUpdates.collect { msg ->
                if (msg.userId == targetId) _position.value = msg
            }
        }
    }

    fun stopWatching(targetId: String) {
        WebSocketClient.get(getApplication())
            ?.sendRaw("""{"type":"watch_stop","targetUserId":"$targetId"}""")
    }
}
