package com.sharegps.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sharegps.data.KeyStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (KeyStore(context).hasKey()) {
            LocationService.start(context)
            LocationUploadWorker.schedule(context)
        }
    }
}
