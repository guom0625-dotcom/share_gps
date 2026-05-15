package com.sharegps.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import com.sharegps.BuildConfig

fun resolveServerUrl(context: Context): String {
    val local = BuildConfig.LOCAL_SERVER_URL
    if (local.isEmpty()) return BuildConfig.SERVER_URL
    val homeSsid = Prefs(context).homeWifiSsid ?: return BuildConfig.SERVER_URL
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return BuildConfig.SERVER_URL)
        ?: return BuildConfig.SERVER_URL
    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return BuildConfig.SERVER_URL
    return if (getConnectedSsid(context) == homeSsid) local else BuildConfig.SERVER_URL
}

fun getConnectedSsid(context: Context): String? {
    @Suppress("DEPRECATION")
    val ssid = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .connectionInfo?.ssid ?: return null
    if (ssid == "<unknown ssid>" || ssid.isBlank()) return null
    return ssid.removeSurrounding("\"")
}
