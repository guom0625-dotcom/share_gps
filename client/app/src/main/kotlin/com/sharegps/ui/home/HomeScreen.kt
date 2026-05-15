package com.sharegps.ui.home

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
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
import com.sharegps.data.FamilyMember
import com.sharegps.data.LocationUpdateMsg
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val members   by vm.members.collectAsState()
    val positions by vm.positions.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    val loading   by vm.loading.collectAsState()
    val error     by vm.error.collectAsState()
    val avatars   by vm.avatars.collectAsState()

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
            Text(
                text = "가족 위치",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::load, enabled = !loading) { Text("새로고침") }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp, max = 216.dp),
        ) {
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                members.isEmpty() -> Text(
                    "가족 구성원이 없습니다",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(members, key = { it.id }) { member ->
                        MemberRow(
                            member   = member,
                            position = positions[member.id],
                            selected = member.id == selectedId,
                            isMe     = member.id == vm.myId,
                            now      = now,
                            onClick  = { vm.selectMember(member.id) },
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
            members    = members,
            positions  = positions,
            selectedId = selectedId,
            avatars    = avatars,
            modifier   = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MemberRow(
    member:      FamilyMember,
    position:    LocationUpdateMsg?,
    selected:    Boolean,
    isMe:        Boolean,
    now:         Long,
    onClick:     () -> Unit,
    onPickPhoto: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                             else MaterialTheme.colorScheme.surface,
        ),
        headlineContent = { Text(member.name) },
        supportingContent = {
            val role = if (member.role == "parent") "부모" else "자녀"
            val time = position?.let { relativeTime(it.recordedAt, now) } ?: "위치 없음"
            val batt = position?.battery?.let { " · 배터리 $it%" } ?: ""
            Text("$role · $time$batt")
        },
        trailingContent = if (isMe) ({
            IconButton(
                onClick = onPickPhoto,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.AddAPhoto,
                    contentDescription = "프로필 사진 변경",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }) else null,
    )
}

@Composable
private fun FamilyMapView(
    members:    List<FamilyMember>,
    positions:  Map<String, LocationUpdateMsg>,
    selectedId: String?,
    avatars:    Map<String, Bitmap>,
    modifier:   Modifier = Modifier,
) {
    val context   = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember { MapView(context) }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    val markers  = remember { mutableMapOf<String, Marker>() }
    val circles  = remember { mutableMapOf<String, CircleOverlay>() }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
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

private fun relativeTime(ts: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - ts
    return when {
        diff < 60_000L     -> "방금 전"
        diff < 3_600_000L  -> "${diff / 60_000}분 전"
        diff < 86_400_000L -> "${diff / 3_600_000}시간 전"
        else               -> "${diff / 86_400_000}일 전"
    }
}
