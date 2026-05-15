package com.sharegps.ui.home

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.CircleOverlay
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import com.naver.maps.map.overlay.Align
import com.naver.maps.map.overlay.PolylineOverlay
import com.sharegps.data.FamilyMember
import com.sharegps.data.HistoryPoint
import com.sharegps.data.LocationUpdateMsg
import java.time.YearMonth
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val members          by vm.members.collectAsState()
    val positions        by vm.positions.collectAsState()
    val selectedId       by vm.selectedId.collectAsState()
    val loading          by vm.loading.collectAsState()
    val error            by vm.error.collectAsState()
    val avatars          by vm.avatars.collectAsState()
    val historyMemberId   by vm.historyMemberId.collectAsState()
    val historyActiveDays by vm.historyActiveDays.collectAsState()
    val historyDaysLoading by vm.historyDaysLoading.collectAsState()
    val historyPath       by vm.historyPath.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    val context = LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { u ->
            val bytes = context.contentResolver.openInputStream(u)?.readBytes() ?: return@let
            vm.uploadAvatar(bytes)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (historyMemberId == null) {
                Text(
                    text = "가족 위치",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::load, enabled = !loading) { Text("새로고침") }
            } else {
                val memberName = members.find { it.id == historyMemberId }?.name ?: ""
                Text(
                    text = "$memberName 이동 이력",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::exitHistory) { Text("취소") }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (historyMemberId == null) Modifier.heightIn(min = 72.dp, max = 216.dp)
                    else Modifier
                ),
        ) {
            when {
                historyMemberId != null -> HistoryCalendar(
                    activeDays    = historyActiveDays,
                    daysLoading   = historyDaysLoading,
                    onDaySelect   = { y, m, d -> vm.loadHistoryDate(historyMemberId!!, y, m, d) },
                    onMonthChange = { y, m -> vm.loadActiveDays(historyMemberId!!, y, m) },
                    modifier      = Modifier.fillMaxWidth(),
                )
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                members.isEmpty() -> Text(
                    "가족 구성원이 없습니다",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(members, key = { it.id }) { member ->
                        MemberRow(
                            member      = member,
                            position    = positions[member.id],
                            selected    = member.id == selectedId,
                            isMe        = member.id == vm.myId,
                            avatar      = avatars[member.id],
                            now         = now,
                            onClick     = { vm.selectMember(member.id) },
                            onLongClick = { vm.enterHistory(member.id) },
                            onPickPhoto = {
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                        HorizontalDivider(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }

        error?.let {
            Text(
                text     = "오류: $it",
                color    = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        HorizontalDivider(thickness = 2.dp)

        FamilyMapView(
            members     = members,
            positions   = positions,
            selectedId  = selectedId,
            avatars     = avatars,
            historyPath = historyPath,
            modifier    = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberRow(
    member:      FamilyMember,
    position:    LocationUpdateMsg?,
    selected:    Boolean,
    isMe:        Boolean,
    avatar:      Bitmap?,
    now:         Long,
    onClick:     () -> Unit,
    onLongClick: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    val iconBmp = remember(avatar, member.name) {
        avatar ?: createInitialMarker(member.name, sizePx = 128)
    }
    ListItem(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface,
        ),
        leadingContent = {
            Image(
                bitmap = iconBmp.asImageBitmap(),
                contentDescription = if (isMe) "프로필 사진 변경" else member.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (isMe) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        else Modifier
                    )
                    .then(
                        if (isMe) Modifier.clickable(onClick = onPickPhoto)
                        else Modifier
                    ),
            )
        },
        headlineContent = { Text(member.name) },
        supportingContent = {
            val time = position?.let { relativeTime(it.recordedAt, now) } ?: "위치 없음"
            Text(time)
        },
        trailingContent = position?.battery?.let { batt ->
            {
                val tint = if (batt <= 20) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.onSurfaceVariant
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = when {
                            batt >= 95 -> Icons.Default.BatteryFull
                            batt >= 80 -> Icons.Default.Battery6Bar
                            batt >= 65 -> Icons.Default.Battery5Bar
                            batt >= 50 -> Icons.Default.Battery4Bar
                            batt >= 35 -> Icons.Default.Battery3Bar
                            batt >= 20 -> Icons.Default.Battery2Bar
                            batt >= 10 -> Icons.Default.Battery1Bar
                            else       -> Icons.Default.Battery0Bar
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = tint,
                    )
                    Text(
                        text = "$batt%",
                        style = MaterialTheme.typography.bodySmall,
                        color = tint,
                    )
                }
            }
        },
    )
}

@Composable
private fun FamilyMapView(
    members:     List<FamilyMember>,
    positions:   Map<String, LocationUpdateMsg>,
    selectedId:  String?,
    avatars:     Map<String, Bitmap>,
    historyPath: List<HistoryPoint> = emptyList(),
    modifier:    Modifier = Modifier,
) {
    val context   = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView     = remember { MapView(context) }
    var naverMap   by remember { mutableStateOf<NaverMap?>(null) }
    val markers     = remember { mutableMapOf<String, Marker>() }
    val circles     = remember { mutableMapOf<String, CircleOverlay>() }
    val polyline    = remember { PolylineOverlay() }
    val timeMarkers = remember { mutableMapOf<Int, Marker>() }
    var currentZoom by remember { mutableStateOf(14.0) }
    val transitDot  = remember { createTransitDot() }
    val stayDot     = remember { createStayDot() }
    val pathEvents  = remember(historyPath) { processHistoryPath(historyPath) }
    val visibleEvents = remember(pathEvents, currentZoom) { filterByZoom(pathEvents, currentZoom) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
                    polyline.map = null
                    timeMarkers.values.forEach { it.map = null }
                    timeMarkers.clear()
                    circles.values.forEach { it.map = null }
                    circles.clear()
                    markers.values.forEach { it.map = null }
                    markers.clear()
                    mapView.onDestroy()
                }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(historyPath, naverMap) {
        val map = naverMap ?: return@LaunchedEffect
        if (historyPath.isEmpty()) {
            polyline.map = null
        } else {
            val coords = historyPath.map { LatLng(it.lat, it.lng) }
            polyline.coords = coords
            polyline.color  = 0xCC1E88E5.toInt()
            polyline.width  = 10
            if (polyline.map == null) polyline.map = map
            val bounds = if (coords.size == 1)
                LatLngBounds(coords.first(), coords.first())
            else
                LatLngBounds.Builder().include(coords).build()
            map.moveCamera(CameraUpdate.fitBounds(bounds, 100))
        }
    }

    LaunchedEffect(naverMap) {
        val map = naverMap ?: return@LaunchedEffect
        map.addOnCameraIdleListener { currentZoom = map.cameraPosition.zoom }
    }

    LaunchedEffect(visibleEvents, naverMap) {
        val map = naverMap ?: return@LaunchedEffect
        timeMarkers.values.forEach { it.map = null }
        timeMarkers.clear()
        for ((idx, event) in visibleEvents.withIndex()) {
            val m = Marker()
            m.captionTextSize  = 11f
            m.captionColor     = 0xFF212121.toInt()
            m.captionHaloColor = 0xFFFFFFFF.toInt()
            m.setCaptionAligns(Align.Top)
            when (event) {
                is PathEvent.Stay -> {
                    m.position    = LatLng(event.lat, event.lng)
                    m.icon        = OverlayImage.fromBitmap(stayDot)
                    m.width       = stayDot.width
                    m.height      = stayDot.height
                    val from = formatTime(event.fromMs)
                    val to   = formatTime(event.toMs)
                    m.captionText = if (from == to) from else "$from~$to"
                }
                is PathEvent.Transit -> {
                    m.position    = LatLng(event.lat, event.lng)
                    m.icon        = OverlayImage.fromBitmap(transitDot)
                    m.width       = transitDot.width
                    m.height      = transitDot.height
                    m.captionText = formatTime(event.timeMs)
                }
            }
            m.map = map
            timeMarkers[idx] = m
        }
    }

    LaunchedEffect(positions, naverMap, avatars) {
        val map = naverMap ?: return@LaunchedEffect
        for ((userId, pos) in positions) {
            val member = members.find { it.id == userId } ?: continue
            val latlng = LatLng(pos.lat, pos.lng)

            // accuracy circle — shown only when accuracy > 0
            val acc = pos.accuracy
            if (acc != null && acc > 0.0) {
                val circle = circles.getOrPut(userId) {
                    CircleOverlay().apply {
                        color = 0x28448BFF.toInt()        // ~16% opacity blue fill
                        outlineColor = 0xA0448BFF.toInt() // ~63% opacity blue border
                        outlineWidth = 3
                    }
                }
                circle.center = latlng
                circle.radius = acc
                if (circle.map == null) circle.map = map
            } else {
                circles.remove(userId)?.map = null
            }

            val marker = markers.getOrPut(userId) { Marker() }
            marker.position = latlng
            marker.captionText = member.name

            val avatarBmp = avatars[userId]
            val iconBmp = if (avatarBmp != null) avatarBmp
                          else createInitialMarker(member.name)
            marker.icon = OverlayImage.fromBitmap(iconBmp)
            marker.width  = 96
            marker.height = 96

            if (marker.map == null) marker.map = map
        }
    }

    LaunchedEffect(positions.isNotEmpty(), naverMap) {
        val map = naverMap ?: return@LaunchedEffect
        if (positions.isEmpty()) return@LaunchedEffect
        if (selectedId != null) return@LaunchedEffect
        val latlngs = positions.values.map { LatLng(it.lat, it.lng) }
        if (latlngs.size == 1) {
            map.moveCamera(CameraUpdate.scrollAndZoomTo(latlngs.first(), 14.0))
        } else {
            val bounds = LatLngBounds.Builder().include(latlngs).build()
            map.moveCamera(CameraUpdate.fitBounds(bounds, 80))
        }
    }

    val selectedPos = selectedId?.let { positions[it] }
    LaunchedEffect(selectedPos, naverMap) {
        val map = naverMap ?: return@LaunchedEffect
        val pos = selectedPos ?: return@LaunchedEffect
        map.moveCamera(CameraUpdate.scrollAndZoomTo(LatLng(pos.lat, pos.lng), 15.0))
    }

    AndroidView(
        factory = {
            mapView.apply {
                onCreate(Bundle())
                getMapAsync { map ->
                    map.uiSettings.isZoomControlEnabled = true
                    naverMap = map
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun HistoryCalendar(
    activeDays:    Set<Int>,
    daysLoading:   Boolean = false,
    onDaySelect:   (year: Int, month: Int, day: Int) -> Unit,
    onMonthChange: (year: Int, month: Int) -> Unit,
    modifier:      Modifier = Modifier,
) {
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember(ym) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = {
                ym = ym.minusMonths(1)
                onMonthChange(ym.year, ym.monthValue)
            }) { Icon(Icons.Default.ChevronLeft, null, Modifier.size(20.dp)) }
            Text("${ym.year}년 ${ym.monthValue}월", style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = {
                ym = ym.plusMonths(1)
                onMonthChange(ym.year, ym.monthValue)
            }) { Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp)) }
        }

        if (daysLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        Row(Modifier.fillMaxWidth()) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val firstDow = ym.atDay(1).dayOfWeek.value % 7  // 0=Sun
        val lastDay  = ym.lengthOfMonth()
        val rows     = (firstDow + lastDay + 6) / 7

        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val day = row * 7 + col - firstDow + 1
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day in 1..lastDay) {
                            val hasData    = day in activeDays
                            val isSelected = day == selectedDay
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .then(
                                        if (hasData) Modifier.clickable {
                                            selectedDay = day
                                            onDaySelect(ym.year, ym.monthValue, day)
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$day",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (hasData) FontWeight.Bold else FontWeight.Normal,
                                    ),
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        hasData    -> MaterialTheme.colorScheme.onSurface
                                        else       -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun relativeTime(ts: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - ts
    return when {
        diff < 60_000L     -> "방금 전"
        diff < 3_600_000L  -> "${diff / 60_000}분 전"
        diff < 86_400_000L -> "${diff / 3_600_000}시간 전"
        else               -> "${diff / 86_400_000}일 전"
    }
}
