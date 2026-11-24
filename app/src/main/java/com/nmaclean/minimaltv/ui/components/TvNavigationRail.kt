package com.nmaclean.minimaltv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nmaclean.minimaltv.R
import com.nmaclean.minimaltv.ui.theme.MidnightCyanTokens
import com.nmaclean.minimaltv.ui.theme.focusCyanBorder

enum class TvNavDestination(val label: String, @DrawableRes val iconRes: Int) {
    SEARCH("Search", R.drawable.ic_nav_search),
    APPS("Apps", R.drawable.ic_nav_apps),
    INPUTS("Inputs", R.drawable.ic_nav_input),
    SMART_HOME("Smart Home", R.drawable.ic_nav_home),
    SETTINGS("Settings", R.drawable.ic_nav_settings),
    REBOOT("Reboot", R.drawable.ic_nav_update)
}

/**
 * Collapsible left rail: 64dp icon-only while browsing, expanding to 140dp with labels
 * and a glowing cyan pill indicator while it holds D-pad focus.
 */
@Composable
fun TvNavigationRail(
    selectedDestination: TvNavDestination,
    onSelectDestination: (TvNavDestination) -> Unit,
    onReboot: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var railHasFocus by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (railHasFocus) MidnightCyanTokens.RailExpandedWidth else MidnightCyanTokens.RailCollapsedWidth,
        animationSpec = tween(durationMillis = 100),
        label = "railWidth"
    )

    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(MidnightCyanTokens.RailBg)
            .onFocusChanged { railHasFocus = it.hasFocus }
            .padding(vertical = MidnightCyanTokens.OverscanVertical, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                TvNavDestination.SEARCH,
                TvNavDestination.APPS,
                TvNavDestination.INPUTS,
                TvNavDestination.SMART_HOME
            ).forEach { destination ->
                RailNavItem(
                    destination = destination,
                    selected = selectedDestination == destination,
                    expanded = railHasFocus,
                    onClick = { onSelectDestination(destination) }
                )
            }
        }

        RailNavItem(
            destination = TvNavDestination.SETTINGS,
            selected = selectedDestination == TvNavDestination.SETTINGS,
            expanded = railHasFocus,
            onClick = { onSelectDestination(TvNavDestination.SETTINGS) }
        )

        Spacer(modifier = Modifier.height(6.dp))

        RailNavItem(
            destination = TvNavDestination.REBOOT,
            selected = false,
            expanded = railHasFocus,
            onClick = onReboot
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RailNavItem(
    destination: TvNavDestination,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    var itemFocused by remember { mutableStateOf(false) }
    val pillColor = when {
        selected -> MidnightCyanTokens.RailSelectedPill
        itemFocused -> MidnightCyanTokens.CardBgElevated
        else -> Color.Transparent
    }
    val contentColor = if (selected || itemFocused) {
        MidnightCyanTokens.CyanAccent
    } else {
        MidnightCyanTokens.TextSecondary
    }
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .focusCyanBorder(isFocused = itemFocused, cornerRadius = 12.dp)
            .clip(shape)
            .background(pillColor)
            .onFocusChanged { itemFocused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
    ) {
        Icon(
            painter = painterResource(destination.iconRes),
            contentDescription = destination.label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        if (expanded) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = destination.label,
                color = if (selected || itemFocused) MidnightCyanTokens.TextPrimary else MidnightCyanTokens.TextSecondary,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
