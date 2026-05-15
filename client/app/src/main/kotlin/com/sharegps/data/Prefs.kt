package com.sharegps.data

import android.content.Context

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var homeWifiSsid: String?
        get() = prefs.getString(KEY_HOME_SSID, null)
        set(value) {
            if (value != null) prefs.edit().putString(KEY_HOME_SSID, value).apply()
            else prefs.edit().remove(KEY_HOME_SSID).apply()
        }

    companion object {
        private const val KEY_HOME_SSID = "home_wifi_ssid"
    }
}
