package com.nmaclean.minimaltv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nmaclean.minimaltv.DashboardData
import com.nmaclean.minimaltv.ui.components.HaMetricCard
import com.nmaclean.minimaltv.ui.components.HaPhonesCard
import com.nmaclean.minimaltv.ui.components.metricCards
import com.nmaclean.minimaltv.ui.theme.LiveClockHeader
import com.nmaclean.minimaltv.ui.theme.MidnightCyanTokens

/**
 * Full-size Home Assistant dashboard in the midnight-cyan style (2×2 focusable slate cards).
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun SmartHomeScreen(
    dashboard: DashboardData,
    haConfigured: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
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
                    text = if (haConfigured) "Home Assistant • ${dashboard.weatherText}" else "Home Assistant is not configured",
                    color = MidnightCyanTokens.TextSecondary,
                    fontSize = 14.sp
                )
            }
            LiveClockHeader(weatherText = dashboard.weatherText)
        }

        Spacer(modifier = Modifier.height(24.dp))

        val metrics = dashboard.metricCards()
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
                metrics.take(2).forEach { metric ->
                    HaMetricCard(
                        title = metric.title,
                        value = metric.value,
                        footer = metric.footer,
                        progressPercent = metric.progress,
                        accent = metric.accent,
                        focusable = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                metrics.drop(2).forEach { metric ->
                    HaMetricCard(
                        title = metric.title,
                        value = metric.value,
                        footer = metric.footer,
                        progressPercent = metric.progress,
                        accent = metric.accent,
                        focusable = true,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                HaPhonesCard(
                    data = dashboard,
                    focusable = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}
