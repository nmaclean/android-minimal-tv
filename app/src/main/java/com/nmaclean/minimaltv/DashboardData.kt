package com.nmaclean.minimaltv

import androidx.compose.runtime.Immutable

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
    val weatherText: String
)
