package com.sharegps.ui.family

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sharegps.data.FamilyMember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyListScreen(
    onViewMap: (id: String, name: String) -> Unit,
    vm: FamilyViewModel = viewModel(),
) {
    val members by vm.members.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("가족 위치") })

        error?.let {
            Text(
                text = "오류: $it",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = vm::load,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (members.isEmpty() && !loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("가족 구성원이 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(members, key = { it.id }) { member ->
                        MemberRow(member = member, onViewMap = { onViewMap(member.id, member.name) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: FamilyMember, onViewMap: () -> Unit) {
    ListItem(
        headlineContent = { Text(member.name) },
        supportingContent = {
            val roleLabel = if (member.role == "parent") "부모" else "자녀"
            val timeLabel = member.current?.recordedAt?.let { relativeTime(it) } ?: "위치 없음"
            Text("$roleLabel · $timeLabel")
        },
        trailingContent = {
            if (member.current != null && member.shareMode == "sharing") {
                TextButton(onClick = onViewMap) { Text("지도") }
            }
        },
    )
    HorizontalDivider(modifier = Modifier.fillMaxWidth())
}

private fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L        -> "방금 전"
        diff < 3_600_000L     -> "${diff / 60_000}분 전"
        diff < 86_400_000L    -> "${diff / 3_600_000}시간 전"
        else                  -> "${diff / 86_400_000}일 전"
    }
}
