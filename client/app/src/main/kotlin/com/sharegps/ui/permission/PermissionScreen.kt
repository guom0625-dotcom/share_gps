package com.sharegps.ui.permission

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager.PERMISSION_GRANTED

@Composable
fun PermissionGate(content: @Composable () -> Unit) {
    val ctx = LocalContext.current

    fun check(perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PERMISSION_GRANTED

    var fineOk by remember { mutableStateOf(check(Manifest.permission.ACCESS_FINE_LOCATION)) }
    var bgOk by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                check(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            else true
        )
    }

    val step1Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        fineOk = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }

    val step2Launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> bgOk = granted }

    when {
        !fineOk -> PermissionStep(
            title = "위치 권한 필요",
            body = "가족 위치를 공유하려면 정밀 위치 권한이 필요합니다.",
            buttonLabel = "권한 허용",
        ) {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                perms.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            step1Launcher.launch(perms.toTypedArray())
        }
        !bgOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> PermissionStep(
            title = "백그라운드 위치 권한 필요",
            body = "앱을 닫아도 위치를 공유하려면\n다음 화면에서 '항상 허용'을 선택해주세요.",
            buttonLabel = "항상 허용 설정",
        ) {
            step2Launcher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        else -> content()
    }
}

@Composable
private fun PermissionStep(
    title: String,
    body: String,
    buttonLabel: String,
    onGrant: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(buttonLabel)
        }
    }
}
