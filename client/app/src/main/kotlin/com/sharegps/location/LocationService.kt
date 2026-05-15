package com.sharegps.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sharegps.R
import com.sharegps.data.ApiClient
import com.sharegps.data.AppDatabase
import com.sharegps.data.KeyStore
import com.sharegps.data.LocationQueueDao
import com.sharegps.data.LocationQueueEntity
import com.sharegps.data.WebSocketClient
import com.sharegps.data.resolveServerUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LocationService : Service() {

    companion object {
        private const val NOTIF_CHANNEL = "location_sharing"
        private const val NOTIF_ID = 1

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LocationService::class.java),
            )
        }
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var dao: LocationQueueDao
    private lateinit var apiClient: ApiClient
    private var wsClient: WebSocketClient? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var activeMode = false
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        val key = KeyStore(this).getKey() ?: run { stopSelf(); return }
        dao = AppDatabase.get(this).locationQueueDao()
        apiClient = ApiClient(resolveServerUrl(this), key)

        wsClient = WebSocketClient.get(this)?.also { ws ->
            ws.onActiveModeChanged = { active ->
                if (active != activeMode) {
                    activeMode = active
                    restartLocationUpdates(active)
                }
            }
            if (!ws.isConnected) ws.connect()
        }

        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        requestLocationUpdates(false)
        startUploadLoop()
    }

    private fun startUploadLoop() {
        scope.launch {
            while (isActive) {
                runCatching {
                    val rows = dao.getOldest()
                    if (rows.isNotEmpty() && apiClient.uploadBatch(rows)) {
                        dao.deleteByIds(rows.map { it.id })
                    }
                }
                delay(2 * 60_000L)
            }
        }
    }

    @Synchronized
    private fun restartLocationUpdates(active: Boolean) {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        requestLocationUpdates(active)
    }

    private fun requestLocationUpdates(active: Boolean) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val req = LocationRequest.Builder(
            if (active) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            if (active) 5_000L else 600_000L,
        ).apply {
            if (!active) setMinUpdateDistanceMeters(30f)
        }.build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                scope.launch {
                    val battery = (getSystemService(BATTERY_SERVICE) as BatteryManager)
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                        .takeIf { it >= 0 }
                    dao.insert(
                        LocationQueueEntity(
                            lat = loc.latitude,
                            lng = loc.longitude,
                            accuracy = loc.accuracy,
                            battery = battery,
                            timestamp = loc.time,
                        )
                    )
                    val batteryField = if (battery != null) ""","battery":$battery""" else ""
                    wsClient?.sendRaw(
                        """{"type":"location","lat":${loc.latitude},"lng":${loc.longitude},"accuracy":${loc.accuracy},"recordedAt":${loc.time}$batteryField}"""
                    )
                }
            }
        }
        locationCallback = cb
        fusedClient.requestLocationUpdates(req, cb, Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        wsClient?.onActiveModeChanged = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(NOTIF_CHANNEL, "위치 공유", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle("위치 공유 중")
            .setContentText("백그라운드에서 위치를 공유하고 있습니다")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }
}
