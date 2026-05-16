package com.sharegps.ui.home

import com.sharegps.data.HistoryPoint
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

sealed class PathEvent {
    data class Stay(
        val lat: Double, val lng: Double,
        val fromMs: Long, val toMs: Long,
    ) : PathEvent()
    data class Transit(
        val lat: Double, val lng: Double,
        val timeMs: Long,
        val speed: Double? = null,
    ) : PathEvent()
}

private fun cleanPoints(points: List<HistoryPoint>): List<HistoryPoint> {
    val filtered = points.filter { it.accuracy == null || it.accuracy <= 80.0 }
    if (filtered.isEmpty()) return emptyList()

    // Speed filter: drop teleport noise (>200 km/h)
    val speedOk = mutableListOf(filtered.first())
    for (k in 1 until filtered.size) {
        val prev = speedOk.last()
        val curr = filtered[k]
        val distM   = haversineM(prev.lat, prev.lng, curr.lat, curr.lng)
        val timeSec = (curr.recordedAt - prev.recordedAt) / 1000.0
        if (timeSec <= 0 || distM / timeSec <= 55.0) speedOk.add(curr)
    }

    // Distance dedup: collapse GPS drift — keep only points >= 50 m from the last kept point
    val result = mutableListOf(speedOk.first())
    for (point in speedOk.drop(1)) {
        val prev = result.last()
        if (haversineM(prev.lat, prev.lng, point.lat, point.lng) >= 50.0) result.add(point)
    }
    return result
}

fun filterHistoryPath(points: List<HistoryPoint>): List<HistoryPoint> = cleanPoints(points)

fun processHistoryPath(points: List<HistoryPoint>): List<PathEvent> {
    val cleaned = cleanPoints(points)

    val stayRadiusM = 150.0
    val stayMinMs   = 10 * 60_000L
    val events = mutableListOf<PathEvent>()
    var i = 0
    while (i < cleaned.size) {
        val anchor = cleaned[i]
        var j = i + 1
        while (j < cleaned.size &&
               haversineM(anchor.lat, anchor.lng, cleaned[j].lat, cleaned[j].lng) <= stayRadiusM) {
            j++
        }
        val endIdx   = j - 1
        val duration = cleaned[endIdx].recordedAt - anchor.recordedAt
        if (duration >= stayMinMs && j > i + 2) {
            val sub = cleaned.subList(i, j)
            events.add(PathEvent.Stay(
                lat    = sub.map { it.lat }.average(),
                lng    = sub.map { it.lng }.average(),
                fromMs = anchor.recordedAt,
                toMs   = cleaned[endIdx].recordedAt,
            ))
            i = j
        } else {
            events.add(PathEvent.Transit(anchor.lat, anchor.lng, anchor.recordedAt, anchor.speed))
            i++
        }
    }
    return events
}

fun formatTime(ms: Long): String {
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
    return "%02d:%02d".format(ldt.hour, ldt.minute)
}

private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r    = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return r * 2 * asin(sqrt(a))
}
