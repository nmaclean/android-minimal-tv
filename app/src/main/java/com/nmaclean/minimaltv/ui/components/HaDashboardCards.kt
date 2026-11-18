package com.nmaclean.minimaltv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nmaclean.minimaltv.DashboardData
import com.nmaclean.minimaltv.HaDetailRow
import com.nmaclean.minimaltv.ui.theme.MidnightCyanTokens
import com.nmaclean.minimaltv.ui.theme.focusCyanBorder

/**
 * Slate Home Assistant metric card: header + status dot, large value, cyan/amber progress,
 * optional extra detail rows, and footer.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HaMetricCard(
    title: String,
    value: String,
    footer: String,
    progressPercent: Int,
    modifier: Modifier = Modifier,
    accent: Color = MidnightCyanTokens.CyanAccent,
    compact: Boolean = false,
    focusable: Boolean = false,
    extraRows: List<HaDetailRow> = emptyList(),
    onClick: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(MidnightCyanTokens.CardCornerRadius)

    Column(
        modifier = modifier
            .focusCyanBorder(isFocused = isFocused)
            .clip(shape)
            .background(if (isFocused) MidnightCyanTokens.CardBgElevated else MidnightCyanTokens.CardBg)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                when {
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    focusable -> Modifier.focusable()
                    else -> Modifier
                }
            )
            .padding(horizontal = 18.dp, vertical = if (compact) 12.dp else 18.dp)
    ) {
        CardHeader(title = title, dotColor = MidnightCyanTokens.SuccessGreen)
        Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
        Text(
            text = value,
            color = MidnightCyanTokens.TextPrimary,
            fontSize = if (compact) 22.sp else 32.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))
        CyanProgressBar(
            progressPercent = progressPercent,
            fillColor = accent,
            thickness = if (compact) 4.dp else 6.dp
        )
        if (!compact && extraRows.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            extraRows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = row.label,
                        color = MidnightCyanTokens.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = row.value,
                        color = if (row.highlight) MidnightCyanTokens.CyanAccent else MidnightCyanTokens.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))
        Text(
            text = if (!compact && onClick != null) "$footer • Press OK for details" else footer,
            color = MidnightCyanTokens.TextMuted,
            fontSize = 12.sp,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HaPhonesCard(
    data: DashboardData,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    focusable: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(MidnightCyanTokens.CardCornerRadius)

    Column(
        modifier = modifier
            .focusCyanBorder(isFocused = isFocused)
            .clip(shape)
            .background(if (isFocused) MidnightCyanTokens.CardBgElevated else MidnightCyanTokens.CardBg)
            .onFocusChanged { isFocused = it.isFocused }
            .then(
                when {
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    focusable -> Modifier.focusable()
                    else -> Modifier
                }
            )
            .padding(horizontal = 18.dp, vertical = if (compact) 12.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
    ) {
        CardHeader(title = "PHONES", dotColor = MidnightCyanTokens.SuccessGreen)
        PhoneRow("NEIL", data.neilPct, compact)
        PhoneRow("HYSLA", data.hyslaPct, compact)
        PhoneRow("FINN", data.finnPct, compact)
        if (!compact && onClick != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Press OK for presence & details",
                color = MidnightCyanTokens.TextMuted,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PhoneRow(name: String, pct: Int, compact: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            color = MidnightCyanTokens.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(if (compact) 52.dp else 64.dp)
        )
        CyanProgressBar(
            progressPercent = pct,
            fillColor = MidnightCyanTokens.TextPrimary,
            thickness = if (compact) 4.dp else 6.dp,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        Text(
            text = "$pct%",
            color = MidnightCyanTokens.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CardHeader(title: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = MidnightCyanTokens.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        Text(text = "●", color = dotColor, fontSize = 10.sp)
    }
}

/** Maps dashboard state onto the three metric cards (Temps keeps its legacy fixed 75% bar once data loads). */
fun DashboardData.metricCards(): List<HaMetric> = listOf(
    HaMetric("SOLAR & BATTERY", solarPct, solarFooter, solarProgress, MidnightCyanTokens.SolarAmber),
    HaMetric("TEMPS & LIGHTS", tempMain, tempFooter, if (tempMain == "—" || tempMain == "...") 0 else 75, MidnightCyanTokens.CyanAccent),
    HaMetric("KIA EV3", evPct, evFooter, evProgress, MidnightCyanTokens.CyanAccent)
)

data class HaMetric(
    val title: String,
    val value: String,
    val footer: String,
    val progress: Int,
    val accent: Color
)
