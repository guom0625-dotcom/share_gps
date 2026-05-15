package com.sharegps

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.sharegps.data.ApiRepository
import com.sharegps.data.KeyStore
import com.sharegps.data.resolveServerUrl
import com.sharegps.location.LocationService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ShareGpsFirebaseService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            val key = KeyStore(applicationContext).getKey() ?: return@launch
            ApiRepository(resolveServerUrl(applicationContext), key).updateFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        when (message.data["type"]) {
            "watch_start" -> LocationService.start(applicationContext)
        }
    }
}
