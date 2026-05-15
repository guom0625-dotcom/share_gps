package com.sharegps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sharegps.data.KeyStore
import com.sharegps.location.LocationService
import com.sharegps.ui.enroll.EnrollScreen
import com.sharegps.ui.home.HomeScreen
import com.sharegps.ui.permission.PermissionGate
import com.sharegps.update.AppUpdater
import com.sharegps.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        if (KeyStore(this).hasKey()) LocationService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        val keyStore = KeyStore(this)

        setContent {
            MaterialTheme {
                Surface {
                    var updateTag by remember { mutableStateOf<String?>(null) }
                    LaunchedEffect(Unit) {
                        val latest = withContext(Dispatchers.IO) { UpdateChecker.latestTag() }
                        if (latest != null && UpdateChecker.isNewer(latest, BuildConfig.VERSION_NAME)) {
                            updateTag = latest
                        }
                    }
                    updateTag?.let { tag ->
                        AlertDialog(
                            onDismissRequest = { updateTag = null },
                            title = { Text("업데이트 있음") },
                            text  = { Text("새 버전 $tag 이 출시되었습니다.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    AppUpdater(this@MainActivity).download(tag)
                                    updateTag = null
                                }) { Text("지금 업데이트") }
                            },
                            dismissButton = {
                                TextButton(onClick = { updateTag = null }) { Text("나중에") }
                            },
                        )
                    }

                    val nav   = rememberNavController()
                    val start = if (keyStore.hasKey()) "home" else "enroll"
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
