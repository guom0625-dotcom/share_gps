package com.sharegps.ui.enroll

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sharegps.data.KeyStore

@Composable
fun EnrollScreen(keyStore: KeyStore, onEnrolled: () -> Unit) {
    var key   by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = "Share GPS",
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "관리자에게 받은 인증 키를 입력하세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value         = key,
            onValueChange = { key = it; error = null },
            label         = { Text("인증 키 (K-xxxx-xxxx-…)") },
            isError       = error != null,
            supportingText = error?.let { msg -> { Text(msg) } },
            visualTransformation = PasswordVisualTransformation(),
            singleLine    = true,
            modifier      = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val trimmed = key.trim()
                if (!trimmed.startsWith("K-") || trimmed.length < 20) {
                    error = "키 형식이 올바르지 않습니다"
                } else {
                    keyStore.saveKey(trimmed)
                    onEnrolled()
                }
            },
            enabled  = key.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("저장")
        }
    }
}
