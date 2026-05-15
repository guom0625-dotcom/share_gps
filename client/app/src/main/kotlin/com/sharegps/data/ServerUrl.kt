package com.sharegps.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.sharegps.BuildConfig

fun resolveServerUrl(context: Context): String {
    val local = BuildConfig.LOCAL_SERVER_URL
    if (local.isEmpty()) return BuildConfig.SERVER_URL
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return BuildConfig.SERVER_URL)
        ?: return BuildConfig.SERVER_URL
    return if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) local
    else BuildConfig.SERVER_URL
}
