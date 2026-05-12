package com.sharegps.update

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object UpdateChecker {
    private const val API_URL =
        "https://api.github.com/repos/guom0625-dotcom/share_gps/releases/latest"

    private val http = OkHttpClient()

    fun latestTag(): String? = try {
        val response = http.newCall(
            Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()
        ).execute()
        if (!response.isSuccessful) return null
        val body = response.body?.string() ?: return null
        JSONObject(body).optString("tag_name").takeIf { it.isNotEmpty() }
    } catch (_: Exception) { null }

    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val b = current.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }
}
