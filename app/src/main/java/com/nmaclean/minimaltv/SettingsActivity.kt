package com.nmaclean.minimaltv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.nmaclean.minimaltv.ui.theme.LiveClockHeader
import com.nmaclean.minimaltv.ui.theme.MidnightCyanTokens
import com.nmaclean.minimaltv.ui.theme.TvPillButton
import com.nmaclean.minimaltv.ui.theme.TvTheme
import com.nmaclean.minimaltv.ui.theme.focusCyanBorder
import java.util.concurrent.Executors

private enum class SettingsPage {
    HUB,
    GENERAL,
    HOME_ASSISTANT
}

private data class SettingsHubOption(
    val title: String,
    val subtitle: String,
    @DrawableRes val iconRes: Int,
    val onClick: () -> Unit
)

/**
 * Midnight-Cyan Compose Settings Hub + sub-screens (General Settings, Home Assistant, App Updates),
 * structured after android-tvheadend-tif's InputManagerActivity / GeneralSettingsActivity.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var preferences: HomeAssistantPreferences
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var localConfigWebServer: LocalConfigWebServer? = null

    private val urlState = mutableStateOf("")
    private val tokenState = mutableStateOf("")
    private val savedUrlPreviewState = mutableStateOf("")
    private val maskedTokenPreviewState = mutableStateOf("")
    private val haStatusState = mutableStateOf<Pair<String, Color>?>(null)
    private val isTestingState = mutableStateOf(false)

    private val webServerRunningState = mutableStateOf(false)
    private val webServerBannerState = mutableStateOf("Web config server stopped" to MidnightCyanTokens.TextSecondary)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = HomeAssistantPreferences(this)
        populateCurrentConfig()

        setContent {
            TvTheme {
                SettingsHost()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stopLocalConfigServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocalConfigServer()
        ioExecutor.shutdownNow()
    }

    @Composable
    private fun SettingsHost() {
        var page by remember { mutableStateOf(SettingsPage.HUB) }

        BackHandler(enabled = page != SettingsPage.HUB) {
            if (page == SettingsPage.HOME_ASSISTANT) {
                stopLocalConfigServer()
            }
            page = SettingsPage.HUB
        }

        when (page) {
            SettingsPage.HUB -> SettingsHubScreen(
                onOpenGeneral = { page = SettingsPage.GENERAL },
                onOpenHomeAssistant = { page = SettingsPage.HOME_ASSISTANT },
                onOpenUpdates = { UpdatePromptDialog(this@SettingsActivity).show() }
            )
            SettingsPage.GENERAL -> GeneralSettingsScreen(
                onBack = { page = SettingsPage.HUB }
            )
            SettingsPage.HOME_ASSISTANT -> HomeAssistantSettingsScreen(
                onBack = {
                    stopLocalConfigServer()
                    page = SettingsPage.HUB
                }
            )
        }
    }

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
    @Composable
    private fun SettingsHubScreen(
        onOpenGeneral: () -> Unit,
        onOpenHomeAssistant: () -> Unit,
        onOpenUpdates: () -> Unit
    ) {
        val options = remember {
            listOf(
                SettingsHubOption(
                    title = "General Settings",
                    subtitle = "System settings, network, TV inputs & permissions",
                    iconRes = R.drawable.ic_nav_settings,
                    onClick = onOpenGeneral
                ),
                SettingsHubOption(
                    title = "Home Assistant",
                    subtitle = "Server URL, access token, browser setup & test",
                    iconRes = R.drawable.ic_nav_home,
                    onClick = onOpenHomeAssistant
                ),
                SettingsHubOption(
                    title = "App Updates",
                    subtitle = "Installed: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    iconRes = R.drawable.ic_nav_update,
                    onClick = onOpenUpdates
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightCyanTokens.BgGradientTop)
                .padding(
                    horizontal = MidnightCyanTokens.OverscanHorizontal,
                    vertical = MidnightCyanTokens.OverscanVertical
                )
        ) {
            SettingsHeader(
                title = "Settings",
                subtitle = "Configure MinimalTV, Home Assistant & app updates"
            )

            Spacer(modifier = Modifier.height(28.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, start = 4.dp, end = 4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer()
            ) {
                items(options, key = { it.title }) { option ->
                    SettingsHubCard(option = option)
                }
            }
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun SettingsHubCard(option: SettingsHubOption) {
        var isFocused by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .focusCyanBorder(isFocused = isFocused)
                .clip(RoundedCornerShape(MidnightCyanTokens.CardCornerRadius))
                .background(if (isFocused) MidnightCyanTokens.CardBgElevated else MidnightCyanTokens.CardBg)
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(onClick = option.onClick)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Icon(
                painter = painterResource(option.iconRes),
                contentDescription = option.title,
                tint = if (isFocused) MidnightCyanTokens.CyanAccent else MidnightCyanTokens.TextSecondary,
                modifier = Modifier.size(34.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = option.title,
                    color = MidnightCyanTokens.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = option.subtitle,
                    color = MidnightCyanTokens.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun GeneralSettingsScreen(onBack: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightCyanTokens.BgGradientTop)
                .padding(
                    horizontal = MidnightCyanTokens.OverscanHorizontal,
                    vertical = MidnightCyanTokens.OverscanVertical
                )
        ) {
            SettingsHeader(
                title = "General Settings",
                subtitle = "Android TV system settings, inputs & app permissions",
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(22.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, start = 4.dp, end = 4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer()
            ) {
                item { SectionLabel("SYSTEM") }
                item {
                    SettingsRowItem(
                        title = "Android TV Settings",
                        summary = "Open device display, sound, apps & accounts settings",
                        onClick = { launchAction(Settings.ACTION_SETTINGS) }
                    )
                }
                item {
                    SettingsRowItem(
                        title = "Network & Wi-Fi",
                        summary = "Configure Wi-Fi, Ethernet & IP address",
                        onClick = { launchAction(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SETTINGS) }
                    )
                }
                item {
                    SettingsRowItem(
                        title = "System TV Inputs",
                        summary = "Open Android TV hardware input selector",
                        onClick = { launchAction("com.android.tv.action.VIEW_INPUTS", Settings.ACTION_SETTINGS) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionLabel("APP & PERMISSIONS")
                }
                item {
                    SettingsRowItem(
                        title = "Install Unknown Apps",
                        summary = "Required for in-app GitHub Releases APK updates",
                        onClick = {
                            val pkgUri = Uri.parse("package:$packageName")
                            if (runCatching {
                                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkgUri))
                                }.isFailure
                            ) {
                                launchAction(Settings.ACTION_SETTINGS)
                            }
                        }
                    )
                }
                item {
                    SettingsRowItem(
                        title = "MinimalTV Version",
                        summary = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE}) • nmaclean/android-minimal-tv",
                        badgeText = "v${BuildConfig.VERSION_NAME}",
                        onClick = { UpdatePromptDialog(this@SettingsActivity).show() }
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
    @Composable
    private fun HomeAssistantSettingsScreen(onBack: () -> Unit) {
        val url by urlState
        val token by tokenState
        val savedUrlPreview by savedUrlPreviewState
        val maskedTokenPreview by maskedTokenPreviewState
        val haStatus by haStatusState
        val isTesting by isTestingState
        val webRunning by webServerRunningState
        val webBanner by webServerBannerState

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightCyanTokens.BgGradientTop)
                .padding(
                    horizontal = MidnightCyanTokens.OverscanHorizontal,
                    vertical = MidnightCyanTokens.OverscanVertical
                )
        ) {
            SettingsHeader(
                title = "Home Assistant",
                subtitle = "Server URL, long-lived token & browser paste setup",
                onBack = onBack
            )

            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, start = 4.dp, end = 4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer()
            ) {
                item { SectionLabel("BROWSER SETUP") }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MidnightCyanTokens.CardCornerRadius))
                            .background(MidnightCyanTokens.CardBg)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = "Local Web Config Server",
                                color = MidnightCyanTokens.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = webBanner.first,
                                color = webBanner.second,
                                fontSize = 13.sp
                            )
                        }
                        TvPillButton(
                            label = if (webRunning) "Stop Web Config" else "Start Web Config",
                            selected = webRunning,
                            onClick = {
                                if (webRunning) stopLocalConfigServer() else startLocalConfigServer()
                            }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionLabel("HOME ASSISTANT CONNECTION")
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MidnightCyanTokens.CardCornerRadius))
                            .background(MidnightCyanTokens.CardBg)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        TvInputField(
                            label = "Home Assistant URL",
                            preview = savedUrlPreview.ifBlank { "Not configured" },
                            value = url,
                            onValueChange = { urlState.value = it },
                            placeholder = "Press OK to enter URL",
                            keyboardType = KeyboardType.Uri,
                            isPassword = false
                        )

                        TvInputField(
                            label = "Long-Lived Access Token",
                            preview = maskedTokenPreview.ifBlank { "Not configured" },
                            value = token,
                            onValueChange = { tokenState.value = it },
                            placeholder = "Press OK to enter token",
                            keyboardType = KeyboardType.Password,
                            isPassword = true
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvPillButton(
                                label = if (isTesting) "Testing…" else "Test Connection",
                                selected = false,
                                onClick = { if (!isTesting) runConnectionTest() }
                            )
                            TvPillButton(
                                label = "Save",
                                selected = false,
                                onClick = ::saveConfiguration
                            )
                            haStatus?.let { (msg, color) ->
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = msg,
                                    color = color,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun SettingsHeader(
        title: String,
        subtitle: String,
        onBack: (() -> Unit)? = null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (onBack != null) {
                    TvPillButton(label = "← Back", selected = false, onClick = onBack)
                }
                Column {
                    Text(
                        text = title,
                        color = MidnightCyanTokens.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = MidnightCyanTokens.TextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
            LiveClockHeader()
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun SettingsRowItem(
        title: String,
        summary: String,
        badgeText: String? = null,
        onClick: () -> Unit
    ) {
        var isFocused by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusCyanBorder(isFocused = isFocused)
                .clip(RoundedCornerShape(MidnightCyanTokens.CardCornerRadius))
                .background(if (isFocused) MidnightCyanTokens.CardBgElevated else MidnightCyanTokens.CardBg)
                .onFocusChanged { isFocused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = MidnightCyanTokens.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = summary,
                    color = MidnightCyanTokens.TextSecondary,
                    fontSize = 13.sp
                )
            }
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MidnightCyanTokens.BgGradientBottom)
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = MidnightCyanTokens.CyanAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)
    @Composable
    private fun TvInputField(
        label: String,
        preview: String,
        value: String,
        onValueChange: (String) -> Unit,
        placeholder: String,
        keyboardType: KeyboardType,
        isPassword: Boolean
    ) {
        var boxFocused by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf(false) }
        val boxFocus = remember { FocusRequester() }
        val fieldFocus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current

        LaunchedEffect(editing) {
            if (editing) {
                runCatching { fieldFocus.requestFocus() }
                keyboard?.show()
            }
        }

        fun finishEditing() {
            keyboard?.hide()
            editing = false
            runCatching { boxFocus.requestFocus() }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = MidnightCyanTokens.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = preview,
                    color = MidnightCyanTokens.TextSecondary,
                    fontSize = 12.sp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusCyanBorder(isFocused = boxFocused || editing, cornerRadius = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MidnightCyanTokens.ModalBg)
                    .focusRequester(boxFocus)
                    .onFocusChanged { boxFocused = it.hasFocus }
                    .clickable { editing = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = MidnightCyanTokens.TextMuted,
                        fontSize = 14.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    textStyle = TextStyle(color = MidnightCyanTokens.TextPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(MidnightCyanTokens.CyanAccent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = keyboardType,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { finishEditing() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocus)
                        .focusProperties { canFocus = editing }
                        .onFocusChanged { if (!it.isFocused && editing) editing = false }
                )
            }
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    @Composable
    private fun SectionLabel(text: String) {
        Text(
            text = text,
            color = MidnightCyanTokens.CyanAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }

    private fun launchAction(primary: String, fallback: String? = null) {
        if (runCatching { startActivity(Intent(primary)) }.isFailure && fallback != null) {
            runCatching { startActivity(Intent(fallback)) }
        }
    }

    private fun startLocalConfigServer() {
        if (localConfigWebServer != null) return
        val ip = LocalConfigWebServer.getLocalIpv4Address(this)
        if (ip == null) {
            webServerBannerState.value = "Local setup server unavailable" to MidnightCyanTokens.TextSecondary
            return
        }

        try {
            val server = LocalConfigWebServer(
                preferences = preferences,
                onConfigSaved = { savedUrl ->
                    populateCurrentConfig()
                    haStatusState.value = "Saved from browser for $savedUrl" to MidnightCyanTokens.SuccessGreen
                    Toast.makeText(this, "Saved Home Assistant config from browser", Toast.LENGTH_LONG).show()
                }
            )
            val port = server.start()
            localConfigWebServer = server
            webServerRunningState.value = true
            webServerBannerState.value = "Configure from phone/PC browser: http://$ip:$port" to MidnightCyanTokens.CyanAccent
        } catch (_: Exception) {
            webServerBannerState.value = "Local setup server unavailable" to MidnightCyanTokens.TextSecondary
        }
    }

    private fun stopLocalConfigServer() {
        localConfigWebServer?.stop()
        localConfigWebServer = null
        webServerRunningState.value = false
        webServerBannerState.value = "Web config server stopped" to MidnightCyanTokens.TextSecondary
    }

    private fun populateCurrentConfig() {
        val currentUrl = preferences.getUrl()
        val currentToken = preferences.getToken()
        urlState.value = currentUrl
        tokenState.value = currentToken
        savedUrlPreviewState.value = currentUrl
        maskedTokenPreviewState.value = HomeAssistantPreferences.maskToken(currentToken)
    }

    private fun saveConfiguration() {
        val normalizedUrl = HomeAssistantPreferences.normalizeUrl(urlState.value)
        val trimmedToken = tokenState.value.trim()

        if (normalizedUrl.isEmpty() || trimmedToken.isEmpty()) {
            haStatusState.value = "Enter both a Home Assistant URL and an access token." to Color(0xFFF87171)
            return
        }

        preferences.save(normalizedUrl, trimmedToken)
        populateCurrentConfig()
        haStatusState.value = "Saved configuration for $normalizedUrl" to MidnightCyanTokens.SuccessGreen
    }

    private fun runConnectionTest() {
        val url = urlState.value
        val token = tokenState.value
        isTestingState.value = true
        haStatusState.value = "Testing connection..." to MidnightCyanTokens.TextSecondary

        ioExecutor.execute {
            val result = HomeAssistantManager.testConnection(url, token)
            runOnUiThread {
                isTestingState.value = false
                haStatusState.value = result.message to if (result.success) {
                    MidnightCyanTokens.SuccessGreen
                } else {
                    Color(0xFFF87171)
                }
            }
        }
    }
}
