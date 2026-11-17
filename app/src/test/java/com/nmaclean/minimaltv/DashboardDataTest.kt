package com.nmaclean.minimaltv

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardDataTest {
    @Test
    fun testDashboardDataProperties() {
        val data = DashboardData(
            solarPct = "82%",
            solarProgress = 82,
            solarFooter = "Load 420 W • Today 6.4 kWh",
            tempMain = "21.5°C",
            tempFooter = "Up 20.1°C • Garden 14.0°C • Lights 1 On",
            evPct = "74%",
            evProgress = 74,
            evFooter = "Range 290 km • Plugged Off",
            neilPct = 90,
            hyslaPct = 85,
            finnPct = 60,
            weatherText = "🌤️ 15°C"
        )
        assertEquals("82%", data.solarPct)
        assertEquals(82, data.solarProgress)
        assertEquals("21.5°C", data.tempMain)
        assertEquals("74%", data.evPct)
        assertEquals(90, data.neilPct)
        assertEquals("🌤️ 15°C", data.weatherText)
    }
}
