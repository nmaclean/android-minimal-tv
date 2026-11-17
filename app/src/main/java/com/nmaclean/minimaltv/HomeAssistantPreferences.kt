package com.nmaclean.minimaltv

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class HomeAssistantPreferences internal constructor(
    private val prefs: SharedPreferences
) {
    constructor(context: Context) : this(createPreferences(context.applicationContext))

    fun isConfigured(): Boolean = getUrl().isNotBlank() && getToken().isNotBlank()

    fun getUrl(): String = prefs.getString(KEY_URL, "").orEmpty()

    fun getToken(): String = prefs.getString(KEY_TOKEN, "").orEmpty()

    fun save(url: String, token: String) {
        val normalizedUrl = normalizeUrl(url)
        val trimmedToken = token.trim()
        prefs.edit()
            .putString(KEY_URL, normalizedUrl)
            .putString(KEY_TOKEN, trimmedToken)
            .commit()
    }

    companion object {
        const val KEY_URL = "ha_url"
        const val KEY_TOKEN = "ha_token"
        private const val ENCRYPTED_PREFS_NAME = "ha_secure_prefs"
        private const val FALLBACK_PREFS_NAME = "ha_prefs_fallback"

        fun normalizeUrl(rawUrl: String): String {
            val trimmed = rawUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            return when {
                trimmed.startsWith("https://", ignoreCase = true) ->
                    "https://" + trimmed.substring("https://".length)
                trimmed.startsWith("http://", ignoreCase = true) ->
                    "http://" + trimmed.substring("http://".length)
                else -> "https://$trimmed"
            }
        }

        fun maskToken(rawToken: String): String {
            val trimmed = rawToken.trim()
            if (trimmed.isEmpty()) return ""
            return if (trimmed.length <= 4) {
                "••••••••"
            } else {
                "••••••••${trimmed.takeLast(4)}"
            }
        }

        private fun createPreferences(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    ENCRYPTED_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Exception) {
                // Fall back to standard SharedPreferences if Android Keystore hardware fails on older TVs.
                context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }
}
