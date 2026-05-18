package com.sharegps.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sharegps.BuildConfig

private const val LOCAL_SERVER_URL = "http://192.168.1.101:3000"
private const val HOME_SUBNET_PREFIX = "192.168.1."

fun resolveServerUrl(context: Context): String {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return BuildConfig.SERVER_URL
    val network = cm.activeNetwork ?: return BuildConfig.SERVER_URL
    val caps = cm.getNetworkCapabilities(network) ?: return BuildConfig.SERVER_URL
    if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return BuildConfig.SERVER_URL
    val onHomeNet = cm.getLinkProperties(network)
        ?.linkAddresses
        ?.any { it.address.hostAddress?.startsWith(HOME_SUBNET_PREFIX) == true }
        ?: false
    return if (onHomeNet) LOCAL_SERVER_URL else BuildConfig.SERVER_URL
}
