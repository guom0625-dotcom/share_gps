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

enum class MoveMode { WALK, DRIVE, UNKNOWN }

sealed class PathEvent {
    data class Stay(
        val lat: Double, val lng: Double,
        val fromMs: Long, val toMs: Long,
    ) : PathEvent()
    data class Move(
        val fromMs: Long, val toMs: Long,
        val distanceM: Double,
        val mode: MoveMode,
    ) : PathEvent()
}

private fun cleanPoints(points: List<HistoryPoint>): List<HistoryPoint> {
    val filtered = points.filter { it.accuracy == null || it.accuracy <= 80.0 }
    if (filtered.isEmpty()) return emptyList()

    val speedOk = mutableListOf(filtered.first())
    for (k in 1 until filtered.size) {
        val prev = speedOk.last()
        val curr = filtered[k]
        val distM   = haversineM(prev.lat, prev.lng, curr.lat, curr.lng)
        val timeSec = (curr.recordedAt - prev.recordedAt) / 1000.0
        if (timeSec <= 0 || distM / timeSec <= 55.0) speedOk.add(curr)
    }

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
    if (cleaned.isEmpty()) return emptyList()

    val stayRadiusM = 150.0
    val stayMinMs   = 10 * 60_000L

    // Pass 1: detect stay index ranges
    val stayRanges = mutableListOf<IntRange>()
    var i = 0
    while (i < cleaned.size) {
        var j = i + 1
        while (j < cleaned.size &&
               haversineM(cleaned[i].lat, cleaned[i].lng, cleaned[j].lat, cleaned[j].lng) <= stayRadiusM) {
            j++
        }
        val endIdx   = j - 1
        val duration = cleaned[endIdx].recordedAt - cleaned[i].recordedAt
        if (duration >= stayMinMs && j > i + 1) {
            stayRanges.add(i until j)
            i = j
        } else {
            i++
        }
    }

    // Pass 2: emit Stays and Moves that include the boundary points of adjacent stays
    val events = mutableListOf<PathEvent>()
    var cursor = 0
    for (range in stayRanges) {
        if (cursor < range.first) {
            val moveStart = if (cursor > 0) cursor - 1 else cursor
            buildMove(cleaned, moveStart, range.first)?.let { events.add(it) }
        }
        val sub = cleaned.subList(range.first, range.last + 1)
        events.add(PathEvent.Stay(
            lat    = sub.map { it.lat }.average(),
            lng    = sub.map { it.lng }.average(),
            fromMs = cleaned[range.first].recordedAt,
            toMs   = cleaned[range.last].recordedAt,
        ))
        cursor = range.last + 1
    }
    if (cursor < cleaned.size) {
        val moveStart = if (cursor > 0) cursor - 1 else cursor
        buildMove(cleaned, moveStart, cleaned.size - 1)?.let { events.add(it) }
    }

    return events
}

private fun buildMove(cleaned: List<HistoryPoint>, fromIdx: Int, toIdx: Int): PathEvent.Move? {
    if (fromIdx >= toIdx) return null
    val from = cleaned[fromIdx]
    val to   = cleaned[toIdx]
    var distM = 0.0
    for (k in fromIdx + 1..toIdx) {
        distM += haversineM(
            cleaned[k - 1].lat, cleaned[k - 1].lng,
            cleaned[k].lat,     cleaned[k].lng,
        )
    }
    val durationMs = to.recordedAt - from.recordedAt
    if (distM == 0.0 && durationMs == 0L) return null
    val durationSec = durationMs / 1000.0
    val avgSpeedMs  = if (durationSec > 0) distM / durationSec else 0.0
    val mode = when {
        avgSpeedMs >= 8.0 -> MoveMode.DRIVE
        avgSpeedMs >= 0.5 -> MoveMode.WALK
        else              -> MoveMode.UNKNOWN
    }
    return PathEvent.Move(from.recordedAt, to.recordedAt, distM, mode)
}

fun formatTime(ms: Long): String {
    val ldt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
    return "%02d:%02d".format(ldt.hour, ldt.minute)
}

fun formatDuration(fromMs: Long, toMs: Long): String {
    val totalMin = ((toMs - fromMs) / 60_000).toInt().coerceAtLeast(0)
    return if (totalMin >= 60) "${totalMin / 60}시간 ${totalMin % 60}분"
    else "${totalMin}분"
}

private fun haversineM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r    = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return r * 2 * asin(sqrt(a))
}
