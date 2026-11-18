package com.nmaclean.minimaltv

import androidx.compose.runtime.Immutable

@Immutable
data class HaDetailRow(
    val label: String,
    val value: String,
    val highlight: Boolean = false
)

@Immutable
data class HaLightItem(
    val entityId: String,
    val name: String,
    val isOn: Boolean,
    val brightnessPct: Int? = null
)

@Immutable
data class SmartHomeDetails(
    val solarRows: List<HaDetailRow> = emptyList(),
    val climateRows: List<HaDetailRow> = emptyList(),
    val lights: List<HaLightItem> = emptyList(),
    val evRows: List<HaDetailRow> = emptyList(),
    val phoneRows: List<HaDetailRow> = emptyList(),
    val lightsOnCount: Int = 0,
    val pvLiveText: String = "—"
)

@Immutable
data class DashboardData(
    val solarPct: String,
    val solarProgress: Int,
    val solarFooter: String,
    val tempMain: String,
    val tempFooter: String,
    val evPct: String,
    val evProgress: Int,
    val evFooter: String,
    val neilPct: Int,
    val hyslaPct: Int,
    val finnPct: Int,
    val weatherText: String,
    val details: SmartHomeDetails = SmartHomeDetails()
)
