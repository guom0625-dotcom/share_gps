package com.sharegps.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.sharegps.BuildConfig

private const val LOCAL_SERVER_URL = "http://192.168.1.101:3000"

fun resolveServerUrl(context: Context): String {
    val homeSsid = Prefs(context).homeWifiSsid ?: return BuildConfig.SERVER_URL
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return BuildConfig.SERVER_URL
    val network = cm.activeNetwork ?: return BuildConfig.SERVER_URL
    val caps = cm.getNetworkCapabilities(network) ?: return BuildConfig.SERVER_URL
    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return BuildConfig.SERVER_URL
    return if (getConnectedSsid(context) == homeSsid) LOCAL_SERVER_URL else BuildConfig.SERVER_URL
}

fun getConnectedSsid(context: Context): String? {
    @Suppress("DEPRECATION")
    val ssid = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .connectionInfo?.ssid ?: return null
    if (ssid == "<unknown ssid>" || ssid.isBlank()) return null
    return ssid.removeSurrounding("\"")
}
