package com.nmaclean.minimaltv

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.Locale

class HomeAssistantManager(
    private val preferences: HomeAssistantPreferences,
    private val onStatesUpdated: (DashboardData) -> Unit
) {

    private val trackedLights = listOf(
        "light.kitchen_lights",
        "light.living_room_lights",
        "light.hallway_light",
        "light.landing_light",
        "light.bedroom_light"
    )

    private val haHandler = Handler(Looper.getMainLooper())
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isFetching = false
    private var hasLoadedOnce = false

    private val haPollRunnable = object : Runnable {
        override fun run() {
            fetchStates()
            haHandler.postDelayed(this, 30_000L)
        }
    }

    fun startPolling() {
        haHandler.post(haPollRunnable)
    }

    fun stopPolling() {
        haHandler.removeCallbacks(haPollRunnable)
    }

    private fun fetchStates() {
        if (!preferences.isConfigured()) {
            mainHandler.post {
                onStatesUpdated(
                    DashboardData(
                        solarPct = "—",
                        solarProgress = 0,
                        solarFooter = "Configure HA in Settings",
                        tempMain = "—",
                        tempFooter = "Configure HA in Settings",
                        evPct = "—",
                        evProgress = 0,
                        evFooter = "Configure HA in Settings",
                        neilPct = 0,
                        hyslaPct = 0,
                        finnPct = 0,
                        weatherText = "☁️ —"
                    )
                )
            }
            return
        }

        val baseUrl = preferences.getUrl()
        val haToken = preferences.getToken()

        if (isFetching) return
        isFetching = true

        Thread {
            try {
                val stateMap = mutableMapOf<String, JSONObject>()
                val maxAttempts = 3
                var attempt = 0
                var success = false
                var errorReason = ""

                while (attempt < maxAttempts && !success) {
                    attempt++
                    var conn: HttpURLConnection? = null
                    try {
                        val url = URL("$baseUrl/api/states")
                        conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            setRequestProperty("Authorization", "Bearer $haToken")
                            setRequestProperty("Content-Type", "application/json")
                            connectTimeout = 8000
                            readTimeout = 8000
                        }

                        val responseCode = conn.responseCode
                        if (responseCode == 200) {
                            val response = conn.inputStream.bufferedReader().use { it.readText() }
                            val array = JSONArray(response)
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                val id = obj.optString("entity_id")
                                if (id.isNotEmpty()) {
                                    stateMap[id] = obj
                                }
                            }
                            success = true
                            hasLoadedOnce = true
                            break
                        } else {
                            errorReason = "HTTP $responseCode"
                            if (responseCode == 401 || responseCode == 403) {
                                break
                            }
                        }
                    } catch (_: UnknownHostException) {
                        errorReason = "No Internet / DNS"
                    } catch (_: SocketTimeoutException) {
                        errorReason = "Timeout"
                    } catch (_: ConnectException) {
                        errorReason = "Unreachable"
                    } catch (e: Exception) {
                        errorReason = e.localizedMessage ?: "Error"
                    } finally {
                        conn?.disconnect()
                    }

                    if (!success && attempt < maxAttempts) {
                        if (!hasLoadedOnce && attempt == 1) {
                            mainHandler.post {
                                onStatesUpdated(
                                    DashboardData(
                                        solarPct = "...",
                                        solarProgress = 0,
                                        solarFooter = "Connecting...",
                                        tempMain = "...",
                                        tempFooter = "Connecting...",
                                        evPct = "...",
                                        evProgress = 0,
                                        evFooter = "Connecting...",
                                        neilPct = 0,
                                        hyslaPct = 0,
                                        finnPct = 0,
                                        weatherText = "☁️ —"
                                    )
                                )
                            }
                        }
                        try {
                            Thread.sleep(1500L)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }

                val dashboardData = if (stateMap.isEmpty()) {
                    val statusMsg = if (!hasLoadedOnce) "Connecting..." else errorReason.ifEmpty { "Offline" }
                    DashboardData(
                        solarPct = "—",
                        solarProgress = 0,
                        solarFooter = statusMsg,
                        tempMain = "—",
                        tempFooter = statusMsg,
                        evPct = "—",
                        evProgress = 0,
                        evFooter = statusMsg,
                        neilPct = 0,
                        hyslaPct = 0,
                        finnPct = 0,
                        weatherText = "☁️ —"
                    )
                } else {
                    buildDashboardData(stateMap)
                }

                mainHandler.post {
                    onStatesUpdated(dashboardData)
                }
            } finally {
                isFetching = false
            }
        }.start()
    }

    private fun buildDashboardData(stateMap: Map<String, JSONObject>): DashboardData {
        fun valWithUnit(entityId: String, decimals: Int = -1): String {
            val obj = stateMap[entityId] ?: return "—"
            val raw = obj.optString("state", "—")
            if (raw == "unknown" || raw == "unavailable" || raw == "—") return "—"
            val unit = obj.optJSONObject("attributes")?.optString("unit_of_measurement", "") ?: ""
            val formatted = if (decimals >= 0) {
                raw.toDoubleOrNull()?.let { String.format(Locale.US, "%.${decimals}f", it) } ?: raw
            } else {
                if (raw.endsWith(".0")) raw.substringBefore(".0") else raw
            }
            return if (unit.isNotEmpty()) "$formatted $unit" else formatted
        }

        // 1. Solar & Battery
        val pvLiveWatts = stateMap["sensor.smartsolar_charger_mppt_150_35_id_278_pv_yield_power"]
            ?.optString("state")?.toDoubleOrNull()?.toInt() ?: 0
        val solarToday = valWithUnit("sensor.smartsolar_charger_mppt_150_35_id_278_yield_today", 1)
        val battSoc = valWithUnit("sensor.pylontech_battery_id_512_charge", 0)
        val solarProgress = battSoc.replace("%", "").trim().toIntOrNull() ?: 0
        val acLoad = valWithUnit("sensor.gx_device_consumption_power_l1", 0)
        val solarFooter = "Load $acLoad • Today $solarToday"

        // 2. Temps & Lights
        val downTemp = valWithUnit("sensor.downstairs_temperature", 1)
        val upTemp = valWithUnit("sensor.upstairs_temperature", 1)
        val gardenTemp = valWithUnit("sensor.gardenroom_sensor_temperature", 1)
        val lightsOnCount = trackedLights.count { id ->
            stateMap[id]?.optString("state") == "on"
        }
        val tempFooter = "Up $upTemp • Garden $gardenTemp • Lights $lightsOnCount On"

        // 3. Kia EV3
        val evSoc = valWithUnit("sensor.ev3_ev_battery_level", 0)
        val evProgress = evSoc.replace("%", "").trim().toIntOrNull() ?: 0
        val evRange = valWithUnit("sensor.ev3_ev_range", 0)
        val evPluggedRaw = stateMap["switch.evcs_hq2541kanh4_ev_charging"]?.optString("state") ?: "off"
        val evPlugged = if (evPluggedRaw == "on") "Plugged On" else "Plugged Off"
        val evFooter = "Range $evRange • $evPlugged"

        // 4. Phone Battery Levels
        val neilBatt = valWithUnit("sensor.neil_iphone_neil_iphone1_battery_level", 0)
        val neilPct = neilBatt.replace("%", "").trim().toIntOrNull() ?: 0
        val hyslaBatt = valWithUnit("sensor.hyslas_iphone_battery_level", 0)
        val hyslaPct = hyslaBatt.replace("%", "").trim().toIntOrNull() ?: 0
        val finnBatt = valWithUnit("sensor.finn_iphone_battery_level", 0)
        val finnPct = finnBatt.replace("%", "").trim().toIntOrNull() ?: 0

        // Weather
        val weatherObj = stateMap["weather.met_office"] 
            ?: stateMap.entries.firstOrNull { it.key.startsWith("weather.") }?.value
        val weatherTemp = weatherObj?.optJSONObject("attributes")?.optString("temperature", "")
        val weatherCondition = weatherObj?.optString("state", "") ?: ""
        val weatherEmoji = when (weatherCondition.lowercase()) {
            "sunny", "clear-night" -> "☀️"
            "rainy", "pouring" -> "🌧️"
            "cloudy" -> "☁️"
            "partlycloudy" -> "⛅"
            "snowy" -> "❄️"
            "windy" -> "🌬️"
            "fog", "foggy" -> "🌫️"
            else -> "☁️"
        }
        val weatherText = if (!weatherTemp.isNullOrEmpty()) {
            val formattedTemp = if (weatherTemp.endsWith(".0")) weatherTemp.substringBefore(".0") else weatherTemp
            "$weatherEmoji $formattedTemp°C"
        } else {
            "☁️ —"
        }

        return DashboardData(
            solarPct = battSoc,
            solarProgress = solarProgress,
            solarFooter = solarFooter,
            tempMain = downTemp,
            tempFooter = tempFooter,
            evPct = evSoc,
            evProgress = evProgress,
            evFooter = evFooter,
            neilPct = neilPct,
            hyslaPct = hyslaPct,
            finnPct = finnPct,
            weatherText = weatherText
        )
    }

    data class ConnectionTestResult(
        val success: Boolean,
        val message: String
    )

    companion object {
        fun testConnection(rawUrl: String, rawToken: String): ConnectionTestResult {
            val baseUrl = HomeAssistantPreferences.normalizeUrl(rawUrl)
            val token = rawToken.trim()
            if (baseUrl.isEmpty()) {
                return ConnectionTestResult(false, "Enter a Home Assistant URL")
            }
            if (token.isEmpty()) {
                return ConnectionTestResult(false, "Enter a Long-Lived Access Token")
            }

            var conn: HttpURLConnection? = null
            val startMs = System.currentTimeMillis()
            return try {
                val url = URL("$baseUrl/api/")
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                val code = conn.responseCode
                val elapsedMs = System.currentTimeMillis() - startMs
                when (code) {
                    in 200..299 -> ConnectionTestResult(true, "Connected (HTTP $code • ${elapsedMs} ms)")
                    401, 403 -> ConnectionTestResult(false, "Auth failed (HTTP $code • ${elapsedMs} ms)")
                    else -> ConnectionTestResult(false, "Unexpected HTTP $code (${elapsedMs} ms)")
                }
            } catch (_: UnknownHostException) {
                ConnectionTestResult(false, "DNS / Host not found")
            } catch (_: SocketTimeoutException) {
                ConnectionTestResult(false, "Connection timed out")
            } catch (_: ConnectException) {
                ConnectionTestResult(false, "Host unreachable")
            } catch (e: Exception) {
                ConnectionTestResult(false, e.localizedMessage ?: "Connection error")
            } finally {
                conn?.disconnect()
            }
        }
    }
}
