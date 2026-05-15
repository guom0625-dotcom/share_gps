import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { props.load(it) }
}

fun localOrEnv(key: String): String =
    localProps.getProperty(key) ?: System.getenv(key) ?: ""

android {
    namespace   = "com.sharegps"
    compileSdk  = 35

    defaultConfig {
        applicationId = "com.sharegps"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName   = System.getenv("VERSION_NAME")?.removePrefix("v") ?: "0.1.0"

        buildConfigField(
            "String", "SERVER_URL",
            "\"${localOrEnv("SERVER_URL").ifEmpty { "https://guom0625.duckdns.org" }}\""
        )
        buildConfigField(
            "String", "NAVER_MAPS_CLIENT_ID",
            "\"${localOrEnv("NAVER_MAPS_CLIENT_ID")}\""
        )
        buildConfigField(
            "String", "LOCAL_SERVER_URL",
            "\"${localOrEnv("LOCAL_SERVER_URL")}\""
        )
    }

    signingConfigs {
        create("release") {
            val kPath = System.getenv("KEYSTORE_PATH")
            val kPass = System.getenv("KEYSTORE_PASSWORD")
            val kAlias = System.getenv("KEY_ALIAS")
            val kKeyPass = System.getenv("KEY_PASSWORD")
            if (kPath != null && kPass != null && kAlias != null && kKeyPass != null) {
                storeFile     = file(kPath)
                storePassword = kPass
                keyAlias      = kAlias
                keyPassword   = kKeyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Secure storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Ktor HTTP client (OkHttp engine — also provides OkHttp for WebSocket)
    implementation("io.ktor:ktor-client-okhttp:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // FusedLocationProvider
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Naver Maps
    implementation("com.naver.maps:map-sdk:3.23.2")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
}
