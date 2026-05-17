package com.sharegps.update

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sharegps.R
import java.io.File

class AppUpdater(private val context: Context) {

    fun download(tagName: String) {
        val dest = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "share-gps.apk")
        if (dest.exists()) dest.delete()

        val fileName = "share-gps-$tagName.apk"
        val url = "https://github.com/guom0625-dotcom/share_gps/releases/download/$tagName/$fileName"

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Share GPS 업데이트")
            .setDescription("$tagName 다운로드 중…")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "share-gps.apk")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val dm = context.getSystemService(DownloadManager::class.java)
        val downloadId = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                ctx.unregisterReceiver(this)

                // 다운로드 성공 여부 확인
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = dm.query(query)
                val success = cursor.use { c ->
                    c.moveToFirst() &&
                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL
                }
                if (success) installApk(dest)
            }
        }
        // ACTION_DOWNLOAD_COMPLETE는 시스템(DownloadManager)이 보내는 broadcast이므로 EXPORTED 필요
        ContextCompat.registerReceiver(
            context, receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val install = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        try {
            context.startActivity(install)
        } catch (_: Exception) {
            showInstallNotification(uri)
        }
    }

    private fun showInstallNotification(apkUri: Uri) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("update", "앱 업데이트", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getActivity(
            context, 0,
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        nm.notify(9001, NotificationCompat.Builder(context, "update")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("업데이트 준비 완료")
            .setContentText("탭하여 설치를 시작하세요")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        )
    }
}
