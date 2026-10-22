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

    // 켜면 앱이 열려 있는 동안 가족 전원을 실시간 추적, 끄면 선택한 사람만 추적
    var watchAllLive: Boolean
        get() = prefs.getBoolean(KEY_WATCH_ALL_LIVE, false)
        set(value) { prefs.edit().putBoolean(KEY_WATCH_ALL_LIVE, value).apply() }

    companion object {
        private const val KEY_HOME_SSID = "home_wifi_ssid"
        private const val KEY_WATCH_ALL_LIVE = "watch_all_live"
    }
}
