package com.sharegps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.sharegps.data.ApiRepository
import com.sharegps.data.AuthEvent
import com.sharegps.data.KeyStore
import com.sharegps.data.resolveServerUrl
import com.sharegps.location.LocationService
import com.sharegps.location.LocationUploadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sharegps.ui.enroll.EnrollScreen
import com.sharegps.ui.home.HomeScreen
import com.sharegps.ui.permission.PermissionGate
import com.sharegps.update.AppUpdater
import com.sharegps.update.UpdateChecker

class MainActivity : ComponentActivity() {

    private val updateTag = mutableStateOf<String?>(null)
    private var lastUpdateCheckMs = 0L

    override fun onResume() {
        super.onResume()
        if (KeyStore(this).hasKey()) {
            LocationService.start(this)
            LocationUploadWorker.schedule(this)
            registerFcmToken()
        }
        checkForUpdate()
    }

    private fun checkForUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastUpdateCheckMs < 30 * 60_000L) return
        lastUpdateCheckMs = now
        CoroutineScope(Dispatchers.IO).launch {
            val latest = UpdateChecker.latestTag() ?: return@launch
            if (UpdateChecker.isNewer(latest, BuildConfig.VERSION_NAME)) {
                withContext(Dispatchers.Main) { updateTag.value = latest }
            }
        }
    }

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            CoroutineScope(Dispatchers.IO).launch {
                val key = KeyStore(applicationContext).getKey() ?: return@launch
                ApiRepository(resolveServerUrl(applicationContext), key).updateFcmToken(token)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        val keyStore = KeyStore(this)

        setContent {
            MaterialTheme {
                Surface {
                    val tag by updateTag
                    tag?.let { t ->
                        AlertDialog(
                            onDismissRequest = { updateTag.value = null },
                            title = { Text("업데이트 있음") },
                            text  = { Text("새 버전 $t 이 출시되었습니다.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    AppUpdater(applicationContext).download(t)
                                    updateTag.value = null
                                }) { Text("지금 업데이트") }
                            },
                            dismissButton = {
                                TextButton(onClick = { updateTag.value = null }) { Text("나중에") }
                            },
                        )
                    }

                    val nav   = rememberNavController()
                    val start = if (keyStore.hasKey()) "home" else "enroll"
                    LaunchedEffect(Unit) {
                        AuthEvent.needsReEnroll.collect {
                            keyStore.clearKey()
                            nav.navigate("enroll") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                    NavHost(navController = nav, startDestination = start) {
                        composable("enroll") {
                            EnrollScreen(keyStore) {
                                nav.navigate("home") {
                                    popUpTo("enroll") { inclusive = true }
                                }
                            }
                        }
                        composable("home") {
                            PermissionGate {
                                HomeScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
