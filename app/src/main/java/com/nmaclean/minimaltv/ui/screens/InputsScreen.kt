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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.nmaclean.minimaltv.TvInputEntry
import com.nmaclean.minimaltv.ui.components.TvMediaCard
import com.nmaclean.minimaltv.ui.theme.LiveClockHeader
import com.nmaclean.minimaltv.ui.theme.MidnightCyanTokens
import com.nmaclean.minimaltv.ui.theme.TvPillButton
import kotlinx.coroutines.delay

/**
 * TV Inputs screen listing hardware HDMI/AV and TIF inputs, plus a System Inputs fallback.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun InputsScreen(
    inputs: List<TvInputEntry>,
    onSelectInput: (TvInputEntry) -> Unit,
    onOpenSystemInputs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(inputs.isNotEmpty()) {
        if (inputs.isNotEmpty()) {
            delay(50)
            runCatching { firstFocus.requestFocus() }
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TV Inputs",
                    color = MidnightCyanTokens.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Select an HDMI, AV, or TV tuner input",
                    color = MidnightCyanTokens.TextSecondary,
                    fontSize = 14.sp
                )
            }
            LiveClockHeader()
        }

        Spacer(modifier = Modifier.height(22.dp))

        if (inputs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No HDMI or TV inputs reported on this device.",
                        color = MidnightCyanTokens.TextSecondary,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TvPillButton(
                        label = "Open System Inputs",
                        selected = true,
                        onClick = onOpenSystemInputs
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, start = 4.dp, end = 4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer()
            ) {
                itemsIndexed(
                    inputs,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> "input_card" }
                ) { index, input ->
                    TvMediaCard(
                        title = input.label,
                        subtitle = if (input.isPassthrough) "${input.typeLabel} Input" else "${input.typeLabel} Channels",
                        fallbackLabel = input.label,
                        topRightBadge = input.typeLabel.uppercase(),
                        onClick = { onSelectInput(input) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                    )
                }
            }
        }
    }
}
