package com.sharegps.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import androidx.core.app.ActivityCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sharegps.data.ApiClient
import com.sharegps.data.AppDatabase
import com.sharegps.data.KeyStore
import com.sharegps.data.LocationQueueEntity
import com.sharegps.data.resolveServerUrl
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class LocationUploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val key = KeyStore(applicationContext).getKey() ?: return Result.success()
        val dao = AppDatabase.get(applicationContext).locationQueueDao()
        val pending = dao.getOldest()
        val loc = getCurrentLocation()
        val current = loc?.let {
            LocationQueueEntity(
                lat = it.latitude, lng = it.longitude,
                accuracy = it.accuracy, battery = getBattery(), timestamp = it.time,
            )
        }
        val batch = pending + listOfNotNull(current)
        if (batch.isEmpty()) return Result.success()
        val uploaded = ApiClient(resolveServerUrl(applicationContext), key).uploadBatch(batch)
        if (uploaded && pending.isNotEmpty()) {
            dao.deleteByIds(pending.map { it.id })
        }
        return Result.success()
    }

    private suspend fun getCurrentLocation(): android.location.Location? {
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return null
        return withTimeout(15_000L) {
            suspendCancellableCoroutine { cont ->
                val cts = CancellationTokenSource()
                LocationServices.getFusedLocationProviderClient(applicationContext)
                    .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                cont.invokeOnCancellation { cts.cancel() }
            }
        }
    }

    private fun getBattery(): Int? =
        (applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it >= 0 }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "location_upload",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LocationUploadWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    ).build()
            )
        }
    }
}
