package com.nmaclean.minimaltv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nmaclean.minimaltv.ui.theme.MidnightCyanTokens
import com.nmaclean.minimaltv.ui.theme.focusCyanBorder

/**
 * 16:9 media card: pre-decoded banner art, optional red `LIVE` badge,
 * optional top-right badge, optional bottom cyan progress bar, and title + metadata below.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvMediaCard(
    title: String,
    subtitle: String,
    fallbackLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    bitmap: ImageBitmap? = null,
    showLiveBadge: Boolean = false,
    topRightBadge: String? = null,
    progressPercent: Int = 0
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .focusCyanBorder(isFocused = isFocused)
                .clip(RoundedCornerShape(MidnightCyanTokens.CardCornerRadius))
                .background(if (isFocused) MidnightCyanTokens.CardBgElevated else MidnightCyanTokens.CardBg)
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(onClick = onClick)
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MidnightCyanTokens.CardBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fallbackLabel,
                        color = MidnightCyanTokens.TextPrimary.copy(alpha = 0.85f),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            if (showLiveBadge) {
                CardBadge(
                    text = "LIVE",
                    background = MidnightCyanTokens.LiveBadgeRed,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }

            if (!topRightBadge.isNullOrBlank()) {
                CardBadge(
                    text = topRightBadge,
                    background = MidnightCyanTokens.BadgeBg,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            if (progressPercent > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(MidnightCyanTokens.BottomScrimBrush)
                )
                CyanProgressBar(
                    progressPercent = progressPercent,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            color = if (isFocused) MidnightCyanTokens.TextPrimary else MidnightCyanTokens.TextPrimary.copy(alpha = 0.9f),
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = MidnightCyanTokens.TextSecondary,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun CardBadge(text: String, background: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp
        )
    }
}

/** Thin rounded track with an electric-cyan fill. */
@Composable
fun CyanProgressBar(
    progressPercent: Int,
    modifier: Modifier = Modifier,
    fillColor: Color = MidnightCyanTokens.CyanAccent,
    trackColor: Color = Color.White.copy(alpha = 0.18f),
    thickness: androidx.compose.ui.unit.Dp = 3.dp
) {
    val fraction = progressPercent.coerceIn(0, 100) / 100f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness)
            .clip(RoundedCornerShape(thickness))
            .background(trackColor)
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(thickness)
                    .background(fillColor)
            )
        }
    }
}
