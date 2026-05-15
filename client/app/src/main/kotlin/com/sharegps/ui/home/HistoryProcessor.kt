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
    ) : PathEvent()
}

fun processHistoryPath(points: List<HistoryPoint>): List<PathEvent> {
    if (points.isEmpty()) return emptyList()
    val stayRadiusM = 150.0
    val stayMinMs   = 10 * 60_000L
    val events = mutableListOf<PathEvent>()
    var i = 0
    while (i < points.size) {
        val anchor = points[i]
        var j = i + 1
        while (j < points.size &&
               haversineM(anchor.lat, anchor.lng, points[j].lat, points[j].lng) <= stayRadiusM) {
            j++
        }
        val endIdx   = j - 1
        val duration = points[endIdx].recordedAt - anchor.recordedAt
        if (duration >= stayMinMs && j > i + 2) {
            val sub = points.subList(i, j)
            events.add(PathEvent.Stay(
                lat    = sub.map { it.lat }.average(),
                lng    = sub.map { it.lng }.average(),
                fromMs = anchor.recordedAt,
                toMs   = points[endIdx].recordedAt,
            ))
            i = j
        } else {
            events.add(PathEvent.Transit(anchor.lat, anchor.lng, anchor.recordedAt))
            i++
        }
    }
    return events
}

fun filterByZoom(events: List<PathEvent>, zoom: Double): List<PathEvent> {
    if (events.size <= 1) return events
    val intervalMs = if (zoom >= 15.0) 30 * 60_000L else 60 * 60_000L
    var lastMs = Long.MIN_VALUE / 2
    val result = mutableListOf<PathEvent>()
    events.forEachIndexed { index, event ->
        val isEdge = index == 0 || index == events.lastIndex
        when (event) {
            is PathEvent.Stay -> {
                result.add(event)
                lastMs = event.toMs
            }
            is PathEvent.Transit -> {
                if (isEdge || event.timeMs - lastMs >= intervalMs) {
                    result.add(event)
                    lastMs = event.timeMs
                }
            }
        }
    }
    return result
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
