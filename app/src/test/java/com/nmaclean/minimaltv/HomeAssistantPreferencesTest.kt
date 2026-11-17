package com.nmaclean.minimaltv

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAssistantPreferencesTest {

    @Test
    fun normalizeUrl_trimsWhitespaceAndTrailingSlashesAndAddsHttps() {
        assertEquals("https://ha.example.com", HomeAssistantPreferences.normalizeUrl("  ha.example.com/  "))
        assertEquals("https://ha.example.com", HomeAssistantPreferences.normalizeUrl("https://ha.example.com///"))
        assertEquals("http://192.168.1.50:8123", HomeAssistantPreferences.normalizeUrl(" http://192.168.1.50:8123/ "))
        assertEquals("https://example.com", HomeAssistantPreferences.normalizeUrl("HTTPS://example.com/"))
        assertEquals("", HomeAssistantPreferences.normalizeUrl("   "))
    }

    @Test
    fun maskToken_masksPrefixAndShowsLastFourCharacters() {
        assertEquals("", HomeAssistantPreferences.maskToken(""))
        assertEquals("", HomeAssistantPreferences.maskToken("   "))
        assertEquals("••••••••", HomeAssistantPreferences.maskToken("abc"))
        assertEquals("••••••••", HomeAssistantPreferences.maskToken("abcd"))
        assertEquals("••••••••wxyz", HomeAssistantPreferences.maskToken("  secret-token-wxyz  "))
    }

    @Test
    fun saveAndLoad_persistsNormalizedUrlAndTrimmedToken() {
        val prefs = HomeAssistantPreferences(InMemorySharedPreferences())
        assertFalse(prefs.isConfigured())
        assertEquals("", prefs.getUrl())
        assertEquals("", prefs.getToken())

        prefs.save("  ha.example.com/ ", "  my-long-lived-token-1234  ")

        assertTrue(prefs.isConfigured())
        assertEquals("https://ha.example.com", prefs.getUrl())
        assertEquals("my-long-lived-token-1234", prefs.getToken())
    }

    @Test
    fun isConfigured_returnsFalseWhenUrlOrTokenBlank() {
        val prefs = HomeAssistantPreferences(InMemorySharedPreferences())
        prefs.save("ha.example.com", "   ")
        assertFalse(prefs.isConfigured())

        prefs.save("   ", "token-1234")
        assertFalse(prefs.isConfigured())
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (data[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = (data[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (data[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (data[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (data[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }
            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = null
            }
            override fun clear(): SharedPreferences.Editor = apply { clearAll = true }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearAll) data.clear()
                pending.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
