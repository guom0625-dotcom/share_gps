package com.sharegps.ui.home

import android.os.Bundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import com.naver.maps.map.overlay.Marker
import com.sharegps.data.FamilyMember
import com.sharegps.data.LocationUpdateMsg

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val members   by vm.members.collectAsState()
    val positions by vm.positions.collectAsState()
    val selectedId by vm.selectedId.collectAsState()
    val loading   by vm.loading.collectAsState()
    val error     by vm.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 헤더
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

        // 가족 리스트 — 3명 분량 고정 높이, 스크롤 가능
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
                            onClick  = { vm.selectMember(member.id) },
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

        // 지도 — 남은 공간 전체
        FamilyMapView(
            members    = members,
            positions  = positions,
            selectedId = selectedId,
            modifier   = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MemberRow(
    member:   FamilyMember,
    position: LocationUpdateMsg?,
    selected: Boolean,
    onClick:  () -> Unit,
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
            val time = position?.let { relativeTime(it.recordedAt) } ?: "위치 없음"
            val batt = position?.battery?.let { " · 배터리 $it%" } ?: ""
            Text("$role · $time$batt")
        },
    )
}

@Composable
private fun FamilyMapView(
    members:    List<FamilyMember>,
    positions:  Map<String, LocationUpdateMsg>,
    selectedId: String?,
    modifier:   Modifier = Modifier,
) {
    val context   = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val mapView = remember { MapView(context) }
    var naverMap by remember { mutableStateOf<NaverMap?>(null) }
    val markers  = remember { mutableMapOf<String, Marker>() }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START   -> mapView.onStart()
                Lifecycle.Event.ON_RESUME  -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE   -> mapView.onPause()
                Lifecycle.Event.ON_STOP    -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> {
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

    LaunchedEffect(positions, naverMap) {
        val map = naverMap ?: return@LaunchedEffect
        for ((userId, pos) in positions) {
            val member = members.find { it.id == userId } ?: continue
            val marker = markers.getOrPut(userId) {
                Marker().apply { captionText = member.name }
            }
            marker.position = LatLng(pos.lat, pos.lng)
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

private fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L     -> "방금 전"
        diff < 3_600_000L  -> "${diff / 60_000}분 전"
        diff < 86_400_000L -> "${diff / 3_600_000}시간 전"
        else               -> "${diff / 86_400_000}일 전"
    }
}
