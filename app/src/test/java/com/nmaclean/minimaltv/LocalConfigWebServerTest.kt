package com.nmaclean.minimaltv

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocalConfigWebServerTest {

    @Test
    fun parseFormUrlEncoded_decodesKeysAndValues() {
        val body = "url=https%3A%2F%2Fha.example.com%2F&token=dummy-header.abc%2Bdef%3D"
        val parsed = LocalConfigWebServer.parseFormUrlEncoded(body)

        assertEquals("https://ha.example.com/", parsed["url"])
        assertEquals("dummy-header.abc+def=", parsed["token"])
    }

    @Test
    fun handleSavePost_savesNormalizedUrlAndTrimmedToken() {
        val prefs = HomeAssistantPreferences(TestSharedPreferences())
        val body = "url=+ha.example.com%2F+&token=+token-value-9876+"

        val result = LocalConfigWebServer.handleSavePost(body, prefs)

        assertTrue(result.success)
        assertEquals("https://ha.example.com", result.savedUrl)
        assertEquals("https://ha.example.com", prefs.getUrl())
        assertEquals("token-value-9876", prefs.getToken())
        assertTrue(prefs.isConfigured())
    }

    @Test
    fun handleSavePost_rejectsWhenBothUrlAndTokenBlank() {
        val prefs = HomeAssistantPreferences(TestSharedPreferences())

        val bothBlank = LocalConfigWebServer.handleSavePost("url=+++&token=+++", prefs)
        assertFalse(bothBlank.success)
        assertFalse(prefs.isConfigured())
    }

    @Test
    fun handleSavePost_allowsUpdatingOnlyUrlOrOnlyToken() {
        val prefs = HomeAssistantPreferences(TestSharedPreferences())
        prefs.save("https://initial.example.com", "initial-token-1234")

        val urlOnly = LocalConfigWebServer.handleSavePost("url=new.ha.example.com%2F&token=", prefs)
        assertTrue(urlOnly.success)
        assertEquals("https://new.ha.example.com", urlOnly.savedUrl)
        assertEquals("https://new.ha.example.com", prefs.getUrl())
        assertEquals("initial-token-1234", prefs.getToken())

        val tokenOnly = LocalConfigWebServer.handleSavePost("url=&token=+rotated-token-5678+", prefs)
        assertTrue(tokenOnly.success)
        assertEquals("https://new.ha.example.com", tokenOnly.savedUrl)
        assertEquals("https://new.ha.example.com", prefs.getUrl())
        assertEquals("rotated-token-5678", prefs.getToken())
    }

    @Test
    fun httpServer_servesFormAndPersistsPostSave() {
        val prefs = HomeAssistantPreferences(TestSharedPreferences())
        prefs.save("https://initial.example.com", "old-token")

        val callbackLatch = CountDownLatch(1)
        var callbackUrl = ""
        val server = LocalConfigWebServer(
            preferences = prefs,
            onConfigSaved = { savedUrl ->
                callbackUrl = savedUrl
                callbackLatch.countDown()
            },
            preferredPort = 0,
            mainThreadPoster = { action -> action() }
        )

        val port = server.start()
        try {
            // 1. Verify GET / pre-fills current URL
            val getConn = (URL("http://127.0.0.1:$port/").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
            }
            assertEquals(200, getConn.responseCode)
            val formHtml = getConn.inputStream.bufferedReader().readText()
            assertTrue(formHtml.contains("https://initial.example.com"))
            assertTrue(formHtml.contains("Save Configuration"))
            getConn.disconnect()

            // 2. Verify POST /save updates preferences and invokes callback
            val encodedUrl = URLEncoder.encode("ha.example.com/", StandardCharsets.UTF_8.name())
            val encodedToken = URLEncoder.encode("new-secret-token-4321", StandardCharsets.UTF_8.name())
            val payload = "url=$encodedUrl&token=$encodedToken".toByteArray(StandardCharsets.UTF_8)

            val postConn = (URL("http://127.0.0.1:$port/save").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                setRequestProperty("Content-Length", payload.size.toString())
            }
            postConn.outputStream.use { it.write(payload) }

            assertEquals(200, postConn.responseCode)
            val savedHtml = postConn.inputStream.bufferedReader().readText()
            assertTrue(savedHtml.contains("Saved! You can close this page."))
            postConn.disconnect()

            assertTrue(callbackLatch.await(2, TimeUnit.SECONDS))
            assertEquals("https://ha.example.com", callbackUrl)
            assertEquals("https://ha.example.com", prefs.getUrl())
            assertEquals("new-secret-token-4321", prefs.getToken())
        } finally {
            server.stop()
        }
    }

    private class TestSharedPreferences : SharedPreferences {
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
