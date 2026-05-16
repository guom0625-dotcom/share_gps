package com.sharegps.location

import android.Manifest
import android.util.Log
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sharegps.R
import com.sharegps.data.AppDatabase
import com.sharegps.data.AuthEvent
import com.sharegps.data.KeyStore
import com.sharegps.data.LocationQueueDao
import com.sharegps.data.LocationQueueEntity
import com.sharegps.data.LocationUpdateMsg
import com.sharegps.data.OwnLocationBroadcast
import com.sharegps.data.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import org.json.JSONObject

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
    private var wsClient: WebSocketClient? = null
    private var transitionPendingIntent: PendingIntent? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var activeMode = false
    @Volatile private var isForeground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    private var locationCallback: LocationCallback? = null

    private val fgObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            Log.d("LocSvc", "fgObserver.onStart isFg=$isForeground→true activeMode=$activeMode")
            isForeground = true
            requestFreshFix()
            restartLocationUpdates()
            updateNotification()
        }
        override fun onStop(owner: LifecycleOwner) {
            Log.d("LocSvc", "fgObserver.onStop isFg=$isForeground→false activeMode=$activeMode")
            isForeground = false
            restartLocationUpdates()
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        val key = KeyStore(this).getKey() ?: run { stopSelf(); return }
        dao = AppDatabase.get(this).locationQueueDao()

        wsClient = WebSocketClient.get(this)?.also { ws ->
            scope.launch {
                ws.isBeingWatched.collect { watched ->
                    Log.d("LocSvc", "isBeingWatched=$watched prev=$activeMode isFg=$isForeground")
                    if (watched != activeMode) {
                        activeMode = watched
                        restartLocationUpdates()
                        updateNotification()
                    }
                }
            }
            if (!ws.isConnected) ws.connect()
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(fgObserver)
        registerActivityTransitions()

        ServiceCompat.startForeground(
            this, NOTIF_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        restartLocationUpdates()
        scope.launch {
            MotionState.isStill.drop(1).collect {
                restartLocationUpdates()
                updateNotification()
            }
        }
        scope.launch {
            AuthEvent.needsReEnroll.collect { stopSelf() }
        }
    }

    private fun registerActivityTransitions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) return

        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
        )
        val intent = Intent(this, ActivityTransitionReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(this, 0, intent, flags)
        transitionPendingIntent = pi
        ActivityRecognition.getClient(this)
            .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pi)
    }

    private fun getBattery(): Int? =
        (getSystemService(BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            .takeIf { it >= 0 }

    private fun onNewLocation(loc: android.location.Location) {
        scope.launch {
            val battery = getBattery()
            val speed = if (loc.hasSpeed() && loc.speed > 0.5f) loc.speed.toDouble() else null
            dao.insert(LocationQueueEntity(
                lat = loc.latitude, lng = loc.longitude,
                accuracy = loc.accuracy, battery = battery, timestamp = loc.time, speed = speed,
            ))
            val json = JSONObject().apply {
                put("type", "location")
                put("lat", loc.latitude)
                put("lng", loc.longitude)
                put("accuracy", loc.accuracy)
                put("recordedAt", loc.time)
                battery?.let { put("battery", it) }
                speed?.let   { put("speed", it) }
            }
            wsClient?.sendRaw(json.toString())
            val myId = wsClient?.myUserId ?: ""
            OwnLocationBroadcast.flow.tryEmit(
                LocationUpdateMsg(myId, loc.latitude, loc.longitude, loc.accuracy.toDouble(), battery, loc.time, speed)
            )
        }
    }

    private fun requestFreshFix() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc -> loc?.let { onNewLocation(it) } }
    }

    @Synchronized
    private fun restartLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null
        val highAccuracy = activeMode || isForeground
        if (!highAccuracy && MotionState.isStill.value) {
            Log.d("LocSvc", "restartLocationUpdates: idle+still, suspending GPS")
            return
        }
        Log.d("LocSvc", "restartLocationUpdates: highAccuracy=$highAccuracy (active=$activeMode fg=$isForeground)")
        requestLocationUpdates(highAccuracy)
    }

    private fun requestLocationUpdates(highAccuracy: Boolean) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        val req = LocationRequest.Builder(
            if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            if (highAccuracy) 5_000L else 600_000L,
        ).apply {
            if (!highAccuracy) setMinUpdateDistanceMeters(30f)
        }.build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                onNewLocation(loc)
            }
        }
        locationCallback = cb
        fusedClient.requestLocationUpdates(req, cb, Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wsClient?.takeIf { !it.isConnected }?.connect()
        return START_STICKY
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(fgObserver)
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        transitionPendingIntent?.let {
            ActivityRecognition.getClient(this).removeActivityTransitionUpdates(it)
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(NOTIF_CHANNEL, "위치 공유", NotificationManager.IMPORTANCE_LOW)
        )
        val highAccuracy = activeMode || isForeground
        val suspended = !highAccuracy && MotionState.isStill.value
        val status = when {
            activeMode -> "실시간 공유 중"
            suspended  -> "정지 감지 — GPS 일시 중단"
            else       -> "위치 공유 중"
        }
        return NotificationCompat.Builder(this, NOTIF_CHANNEL)
            .setContentTitle(status)
            .setContentText("share_gps")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }
}
