package com.sharegps

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sharegps.data.KeyStore
import com.sharegps.location.LocationService
import com.sharegps.ui.enroll.EnrollScreen
import com.sharegps.ui.family.FamilyListScreen
import com.sharegps.ui.map.LiveMapScreen

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        if (KeyStore(this).hasKey()) LocationService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val keyStore = KeyStore(this)

        setContent {
            MaterialTheme {
                Surface {
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
                            FamilyListScreen(onViewMap = { id, name ->
                                nav.navigate("map/$id/${Uri.encode(name)}")
                            })
                        }
                        composable("map/{userId}/{name}") { back ->
                            val userId = back.arguments?.getString("userId") ?: return@composable
                            val name   = Uri.decode(back.arguments?.getString("name") ?: "")
                            LiveMapScreen(
                                targetId   = userId,
                                targetName = name,
                                onBack     = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
