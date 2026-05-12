package com.sharegps.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.MaterialTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareStateSheet(
    onDismiss: () -> Unit,
    vm: ShareStateViewModel = viewModel(),
) {
    val mode by vm.mode.collectAsState()
    val loading by vm.loading.collectAsState()
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "공유 상태: ${modeLabel(mode)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            if (mode == "paused" || mode == "off") {
                SheetButton("공유 재개", !loading) {
                    vm.setMode("sharing")
                    onDismiss()
                }
            }
            SheetButton("30분 일시정지", !loading && mode != "paused") {
                vm.setMode("paused", 30)
                onDismiss()
            }
            SheetButton("1시간 일시정지", !loading && mode != "paused") {
                vm.setMode("paused", 60)
                onDismiss()
            }
            SheetButton("2시간 일시정지", !loading && mode != "paused") {
                vm.setMode("paused", 120)
                onDismiss()
            }
            if (mode != "off") {
                SheetButton("공유 중단", !loading) {
                    vm.setMode("off")
                    onDismiss()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SheetButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

private fun modeLabel(mode: String) = when (mode) {
    "sharing" -> "공유 중"
    "paused"  -> "일시정지"
    "off"     -> "중단"
    else      -> mode
}
