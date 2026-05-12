package com.sharegps.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class AppUpdater(private val context: Context) {

    fun download(tagName: String) {
        val fileName = "share-gps-$tagName.apk"
        val url = "https://github.com/guom0625-dotcom/share_gps/releases/download/$tagName/$fileName"

        val dest = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "share-gps.apk")
        if (dest.exists()) dest.delete()

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Share GPS 업데이트")
            .setDescription("$tagName 다운로드 중…")
            .setDestinationUri(Uri.fromFile(dest))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val dm = context.getSystemService(DownloadManager::class.java)
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(dest)
                    ctx.unregisterReceiver(this)
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
