package com.nmaclean.minimaltv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Midnight-Navy & Electric-Cyan design tokens for the 10-foot interface.
 * Ported from android-tvheadend-tif (`ui/theme/TvTheme.kt`) so both apps share one visual language.
 */
object MidnightCyanTokens {
    val BgGradientTop = Color(0xFF0B1221)
    val BgGradientBottom = Color(0xFF060A12)
    val CardBg = Color(0xFF162238)
    val CardBgElevated = Color(0xFF1D2D49)
    val ModalBg = Color(0xFF131E33)
    val RailBg = Color(0xFF080E1A)
    val RailSelectedPill = Color(0xFF0E2A40)
    val CyanAccent = Color(0xFF00E5FF)
    val CyanGlow = Color(0x5500E5FF)
    val LiveBadgeRed = Color(0xFFE50914)
    val BadgeBg = Color(0xCC060A12)
    val TextPrimary = Color(0xFFF3F6FC)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
    val PillUnselectedBg = Color(0xFF162238)
    val PillSelectedBg = Color(0xFFFFFFFF)
    val PillSelectedText = Color(0xFF060A12)
    val DividerSubtle = Color(0xFF1E293B)
    val SuccessGreen = Color(0xFF22C55E)
    val SolarAmber = Color(0xFFF59E0B)

    val OverscanHorizontal: Dp = 48.dp
    val OverscanVertical: Dp = 24.dp
    val RailCollapsedWidth: Dp = 64.dp
    val RailExpandedWidth: Dp = 140.dp
    val FocusBorderWidth: Dp = 2.dp
    val CardCornerRadius: Dp = 12.dp

    val BackgroundBrush: Brush = Brush.verticalGradient(
        colors = listOf(BgGradientTop, BgGradientBottom)
    )
    val CardBrush: Brush = Brush.linearGradient(
        colors = listOf(CardBgElevated, CardBg)
    )
    val FallbackCardBrush: Brush = Brush.linearGradient(
        colors = listOf(Color(0xFF1A2942), Color(0xFF0E1728))
    )
    val BottomScrimBrush: Brush = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color(0xCC060A12))
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
private val MidnightCyanColorScheme = darkColorScheme(
    primary = MidnightCyanTokens.CyanAccent,
    onPrimary = MidnightCyanTokens.BgGradientBottom,
    secondary = MidnightCyanTokens.CardBgElevated,
    onSecondary = MidnightCyanTokens.TextPrimary,
    background = MidnightCyanTokens.BgGradientBottom,
    onBackground = MidnightCyanTokens.TextPrimary,
    surface = MidnightCyanTokens.CardBg,
    onSurface = MidnightCyanTokens.TextPrimary,
    surfaceVariant = MidnightCyanTokens.CardBgElevated,
    onSurfaceVariant = MidnightCyanTokens.TextSecondary
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MidnightCyanColorScheme,
        content = content
    )
}

/**
 * 2dp electric-cyan focus ring applied to every D-pad interactive element.
 * Avoids RenderNode elevation shadows which cause GPU frame drops on low-power Android TV devices.
 */
fun Modifier.focusCyanBorder(
    isFocused: Boolean,
    cornerRadius: Dp = MidnightCyanTokens.CardCornerRadius
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return if (isFocused) {
        this.border(
            BorderStroke(MidnightCyanTokens.FocusBorderWidth, MidnightCyanTokens.CyanAccent),
            shape = shape
        )
    } else {
        this
    }
}

/**
 * Top-right live clock: `HH:mm` above `EEEE, d MMMM`.
 */
@Composable
fun LiveClockHeader(modifier: Modifier = Modifier) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }
    val currentDate = remember(nowMs) { Date(nowMs) }

    Column(
        modifier = modifier.widthIn(min = 150.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            text = timeFormat.format(currentDate),
            color = MidnightCyanTokens.TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = dateFormat.format(currentDate),
            color = MidnightCyanTokens.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * 10-foot pill used for category filters, scope chips, and quick actions.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvPillButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelColorOverride: Color? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = when {
        selected -> MidnightCyanTokens.PillSelectedBg
        isFocused -> MidnightCyanTokens.CardBgElevated
        else -> MidnightCyanTokens.PillUnselectedBg
    }
    val textColor = labelColorOverride ?: if (selected) {
        MidnightCyanTokens.PillSelectedText
    } else {
        MidnightCyanTokens.TextPrimary
    }

    Box(
        modifier = modifier
            .focusCyanBorder(isFocused = isFocused, cornerRadius = 50.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
