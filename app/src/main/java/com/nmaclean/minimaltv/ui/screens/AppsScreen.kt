package com.nmaclean.minimaltv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nmaclean.minimaltv.AppCatalog
import com.nmaclean.minimaltv.AppCategory
import com.nmaclean.minimaltv.AppEntry
import com.nmaclean.minimaltv.DashboardData
import com.nmaclean.minimaltv.ui.components.HaMetricCard
import com.nmaclean.minimaltv.ui.components.HaPhonesCard
import com.nmaclean.minimaltv.ui.components.TvMediaCard
import com.nmaclean.minimaltv.ui.components.metricCards
import com.nmaclean.minimaltv.ui.theme.LiveClockHeader
import com.nmaclean.minimaltv.ui.theme.MidnightCyanTokens
import com.nmaclean.minimaltv.ui.theme.TvPillButton
import kotlinx.coroutines.delay

/**
 * Launcher home: category filter pills + clock header, 4-column grid of 16:9 app banner cards,
 * and a compact Home Assistant glance strip.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    dashboard: DashboardData,
    voiceStatus: String?,
    onLaunchApp: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    requestInitialFocus: Boolean = true
) {
    val categories = remember(apps) { AppCatalog.availableCategories(apps.map { it.packageName }) }
    var selectedCategory by remember { mutableStateOf(AppCategory.ALL) }
    val filteredApps = remember(apps, selectedCategory) {
        AppCatalog.filter(apps, selectedCategory, "", { it.packageName }, { it.label })
    }
    val firstCardFocus = remember { FocusRequester() }

    LaunchedEffect(filteredApps.isNotEmpty(), requestInitialFocus) {
        if (requestInitialFocus && filteredApps.isNotEmpty()) {
            delay(50)
            runCatching { firstCardFocus.requestFocus() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = MidnightCyanTokens.OverscanHorizontal,
                vertical = MidnightCyanTokens.OverscanVertical
            )
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clipToBounds()
                    .focusRestorer()
            ) {
                items(categories, key = { it.name }) { category ->
                    TvPillButton(
                        label = category.label,
                        selected = category == selectedCategory,
                        onClick = { selectedCategory = category }
                    )
                }
            }
            if (voiceStatus != null) {
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "🎤  $voiceStatus",
                    color = MidnightCyanTokens.CyanAccent,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            LiveClockHeader()
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (filteredApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No ${selectedCategory.label.lowercase()} apps installed.",
                        color = MidnightCyanTokens.TextSecondary,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp, start = 4.dp, end = 4.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer()
                ) {
                    itemsIndexed(
                        filteredApps,
                        key = { _, app -> app.packageName },
                        contentType = { _, _ -> "app_card" }
                    ) { index, app ->
                        TvMediaCard(
                            title = app.label,
                            subtitle = AppCatalog.categoryFor(app.packageName).label,
                            fallbackLabel = app.label,
                            bitmap = app.bitmap,
                            showLiveBadge = AppCatalog.isLiveApp(app.packageName),
                            onClick = { onLaunchApp(app) },
                            modifier = if (index == 0) Modifier.focusRequester(firstCardFocus) else Modifier
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Compact, non-focusable Home Assistant glance strip (full view lives on the Smart Home tab).
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            dashboard.metricCards().forEach { metric ->
                HaMetricCard(
                    title = metric.title,
                    value = metric.value,
                    footer = metric.footer,
                    progressPercent = metric.progress,
                    accent = metric.accent,
                    compact = true,
                    modifier = Modifier.weight(1f)
                )
            }
            HaPhonesCard(data = dashboard, compact = true, modifier = Modifier.weight(1f))
        }
    }
}
