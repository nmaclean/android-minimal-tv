package com.nmaclean.minimaltv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nmaclean.minimaltv.AppCatalog
import com.nmaclean.minimaltv.AppCategory
import com.nmaclean.minimaltv.AppEntry
import com.nmaclean.minimaltv.R
import com.nmaclean.minimaltv.ui.components.TvMediaCard
import com.nmaclean.minimaltv.ui.theme.LiveClockHeader
import com.nmaclean.minimaltv.ui.theme.MidnightCyanTokens
import com.nmaclean.minimaltv.ui.theme.TvPillButton
import com.nmaclean.minimaltv.ui.theme.focusCyanBorder

/**
 * 10-foot Search over installed launcher apps, plus a "search in YouTube" action row.
 *
 * The text field is excluded from D-pad focus until the search bar is explicitly clicked (Center),
 * so merely navigating past it never pops the on-screen / phone companion keyboard.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    apps: List<AppEntry>,
    youTubeApp: AppEntry?,
    onLaunchApp: (AppEntry) -> Unit,
    onSearchYouTube: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf(AppCategory.ALL) }
    var barFocused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val barFocus = remember { FocusRequester() }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val scopes = remember(apps) { AppCatalog.availableCategories(apps.map { it.packageName }) }
    val matchingApps = remember(apps, scope, query) {
        AppCatalog.filter(apps, scope, query, { it.packageName }, { it.label })
    }

    LaunchedEffect(Unit) { runCatching { barFocus.requestFocus() } }
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
        }
    }

    fun finishEditing() {
        keyboard?.hide()
        editing = false
        runCatching { barFocus.requestFocus() }
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
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 24.dp)
                    .focusCyanBorder(isFocused = barFocused || editing, cornerRadius = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MidnightCyanTokens.CardBg)
                    .focusRequester(barFocus)
                    .onFocusChanged { barFocused = it.hasFocus }
                    .clickable { editing = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_search),
                    contentDescription = "Search",
                    tint = if (barFocused || editing) MidnightCyanTokens.CyanAccent else MidnightCyanTokens.TextSecondary
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = if (editing) "Type to search apps…" else "Press OK to search apps or YouTube…",
                            color = MidnightCyanTokens.TextMuted,
                            fontSize = 15.sp
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = MidnightCyanTokens.TextPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(MidnightCyanTokens.CyanAccent),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = { finishEditing() },
                            onDone = { finishEditing() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fieldFocus)
                            .focusProperties { canFocus = editing }
                            .onFocusChanged { if (!it.isFocused && editing) editing = false }
                    )
                }
            }
            LiveClockHeader()
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.focusRestorer()
        ) {
            items(scopes, key = { it.name }) { category ->
                TvPillButton(
                    label = category.label,
                    selected = category == scope,
                    onClick = { scope = category }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val trimmedQuery = query.trim()
        if (matchingApps.isEmpty() && (trimmedQuery.isEmpty() || youTubeApp == null)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No apps matching \"$query\"",
                    color = MidnightCyanTokens.TextSecondary,
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (matchingApps.isNotEmpty()) {
                    item(key = "apps") {
                        ResultRow(title = "Apps (${matchingApps.size})") {
                            items(matchingApps, key = { it.packageName }) { app ->
                                TvMediaCard(
                                    title = app.label,
                                    subtitle = AppCatalog.categoryFor(app.packageName).label,
                                    fallbackLabel = app.label,
                                    bitmap = app.bitmap,
                                    showLiveBadge = AppCatalog.isLiveApp(app.packageName),
                                    onClick = { onLaunchApp(app) },
                                    modifier = Modifier.width(240.dp)
                                )
                            }
                        }
                    }
                }
                if (trimmedQuery.isNotEmpty() && youTubeApp != null) {
                    item(key = "youtube") {
                        ResultRow(title = "Search in apps") {
                            item(key = "yt_search") {
                                TvMediaCard(
                                    title = "Search YouTube for \"$trimmedQuery\"",
                                    subtitle = youTubeApp.label,
                                    fallbackLabel = youTubeApp.label,
                                    bitmap = youTubeApp.bitmap,
                                    onClick = { onSearchYouTube(trimmedQuery) },
                                    modifier = Modifier.width(240.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun ResultRow(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            color = MidnightCyanTokens.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRestorer(),
            content = content
        )
    }
}
