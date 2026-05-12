package com.sharegps.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class KeyStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun saveKey(key: String) = prefs.edit().putString(KEY_API, key).apply()
    fun getKey(): String?    = prefs.getString(KEY_API, null)
    fun clearKey()           = prefs.edit().remove(KEY_API).apply()
    fun hasKey(): Boolean    = getKey() != null

    private companion object {
        const val KEY_API = "api_key"
    }
}
