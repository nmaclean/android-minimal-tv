package com.nmaclean.minimaltv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nmaclean.minimaltv.DashboardData
import com.nmaclean.minimaltv.HaDetailRow
import com.nmaclean.minimaltv.HaLightItem
import com.nmaclean.minimaltv.ui.components.HaMetricCard
import com.nmaclean.minimaltv.ui.components.HaPhonesCard
import com.nmaclean.minimaltv.ui.components.metricCards
import com.nmaclean.minimaltv.ui.theme.LiveClockHeader
import com.nmaclean.minimaltv.ui.theme.MidnightCyanTokens
import com.nmaclean.minimaltv.ui.theme.TvPillButton
import com.nmaclean.minimaltv.ui.theme.focusCyanBorder

private enum class SmartHomeDetailKind(val title: String) {
    SOLAR("Solar & Battery Details"),
    CLIMATE_LIGHTS("Climate & Lights"),
    EV("Kia EV3 & Charger Details"),
    PHONES("Phones & Presence")
}

/**
 * Full-size Home Assistant dashboard in the midnight-cyan style (2×2 focusable slate cards)
 * with click-through detail modals and live light toggling.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun SmartHomeScreen(
    dashboard: DashboardData,
    haConfigured: Boolean,
    onToggleLight: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeDetail by remember { mutableStateOf<SmartHomeDetailKind?>(null) }

    BackHandler(enabled = activeDetail != null) {
        activeDetail = null
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = MidnightCyanTokens.OverscanHorizontal,
                    vertical = MidnightCyanTokens.OverscanVertical
                )
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Home",
                        color = MidnightCyanTokens.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (haConfigured) "Home Assistant • Select any card for details & controls" else "Home Assistant is not configured",
                        color = MidnightCyanTokens.TextSecondary,
                        fontSize = 14.sp
                    )
                }
                LiveClockHeader(weatherText = dashboard.weatherText)
            }

            Spacer(modifier = Modifier.height(22.dp))

            val metrics = dashboard.metricCards()
            val solarExtra = remember(dashboard.details.solarRows) { dashboard.details.solarRows.take(3) }
            val climateExtra = remember(dashboard.details.climateRows) { dashboard.details.climateRows.take(3) }
            val evExtra = remember(dashboard.details.evRows) { dashboard.details.evRows.take(3) }

            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .focusRestorer()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    HaMetricCard(
                        title = metrics[0].title,
                        value = metrics[0].value,
                        footer = metrics[0].footer,
                        progressPercent = metrics[0].progress,
                        accent = metrics[0].accent,
                        focusable = true,
                        extraRows = solarExtra,
                        onClick = { activeDetail = SmartHomeDetailKind.SOLAR },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    HaMetricCard(
                        title = metrics[1].title,
                        value = metrics[1].value,
                        footer = metrics[1].footer,
                        progressPercent = metrics[1].progress,
                        accent = metrics[1].accent,
                        focusable = true,
                        extraRows = climateExtra,
                        onClick = { activeDetail = SmartHomeDetailKind.CLIMATE_LIGHTS },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    HaMetricCard(
                        title = metrics[2].title,
                        value = metrics[2].value,
                        footer = metrics[2].footer,
                        progressPercent = metrics[2].progress,
                        accent = metrics[2].accent,
                        focusable = true,
                        extraRows = evExtra,
                        onClick = { activeDetail = SmartHomeDetailKind.EV },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    HaPhonesCard(
                        data = dashboard,
                        focusable = true,
                        onClick = { activeDetail = SmartHomeDetailKind.PHONES },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        activeDetail?.let { kind ->
            SmartHomeDetailModal(
                kind = kind,
                dashboard = dashboard,
                onToggleLight = onToggleLight,
                onDismiss = { activeDetail = null }
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SmartHomeDetailModal(
    kind: SmartHomeDetailKind,
    dashboard: DashboardData,
    onToggleLight: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(kind) {
        runCatching { closeFocus.requestFocus() }
    }

    val rows = when (kind) {
        SmartHomeDetailKind.SOLAR -> dashboard.details.solarRows
        SmartHomeDetailKind.CLIMATE_LIGHTS -> dashboard.details.climateRows
        SmartHomeDetailKind.EV -> dashboard.details.evRows
        SmartHomeDetailKind.PHONES -> dashboard.details.phoneRows
    }
    val lights = if (kind == SmartHomeDetailKind.CLIMATE_LIGHTS) dashboard.details.lights else emptyList()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC060A12)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 520.dp, max = 720.dp)
                    .fillMaxHeight(0.84f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MidnightCyanTokens.ModalBg)
                    .focusCyanBorder(isFocused = false, cornerRadius = 16.dp)
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = kind.title,
                        color = MidnightCyanTokens.CyanAccent,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    TvPillButton(
                        label = "Close",
                        selected = false,
                        onClick = onDismiss,
                        modifier = Modifier.focusRequester(closeFocus)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp, start = 4.dp, end = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (lights.isNotEmpty()) {
                        item {
                            Text(
                                text = "LIGHTS (PRESS OK TO TOGGLE)",
                                color = MidnightCyanTokens.CyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                        items(lights, key = { it.entityId }) { light ->
                            LightControlRow(light = light, onToggle = { onToggleLight(light.entityId) })
                        }
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "TEMPERATURES & WEATHER",
                                color = MidnightCyanTokens.CyanAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }

                    items(rows) { row ->
                        DetailKeyValueRow(row = row)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LightControlRow(light: HaLightItem, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusCyanBorder(isFocused = focused, cornerRadius = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) MidnightCyanTokens.CardBgElevated else MidnightCyanTokens.CardBg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = light.name,
            color = MidnightCyanTokens.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
        val badgeText = when {
            light.isOn && light.brightnessPct != null -> "ON • ${light.brightnessPct}%"
            light.isOn -> "ON"
            else -> "OFF"
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(if (light.isOn) MidnightCyanTokens.CyanAccent else MidnightCyanTokens.BgGradientBottom)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = badgeText,
                color = if (light.isOn) MidnightCyanTokens.PillSelectedText else MidnightCyanTokens.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailKeyValueRow(row: HaDetailRow) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusCyanBorder(isFocused = focused, cornerRadius = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) MidnightCyanTokens.CardBgElevated else MidnightCyanTokens.CardBg)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.label,
            color = MidnightCyanTokens.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = row.value,
            color = if (row.highlight) MidnightCyanTokens.CyanAccent else MidnightCyanTokens.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
