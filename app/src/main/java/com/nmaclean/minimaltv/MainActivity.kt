package com.nmaclean.minimaltv

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.nmaclean.minimaltv.ui.components.TvNavDestination
import com.nmaclean.minimaltv.ui.components.TvNavigationRail
import com.nmaclean.minimaltv.ui.screens.AppsScreen
import com.nmaclean.minimaltv.ui.screens.InputsScreen
import com.nmaclean.minimaltv.ui.screens.SearchScreen
import com.nmaclean.minimaltv.ui.screens.SmartHomeScreen
import com.nmaclean.minimaltv.ui.theme.MidnightCyanTokens
import com.nmaclean.minimaltv.ui.theme.TvTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MinimalTV home launcher hosting the Midnight-Navy & Electric-Cyan Jetpack Compose for TV shell.
 */
class MainActivity : ComponentActivity() {

    private lateinit var homeAssistantPreferences: HomeAssistantPreferences
    private lateinit var homeAssistantManager: HomeAssistantManager
    private lateinit var voiceAssistantManager: VoiceAssistantManager
    private lateinit var appLoader: AppLoader

    private val appsState = mutableStateOf<List<AppEntry>>(emptyList())
    private val inputsState = mutableStateOf<List<TvInputEntry>>(emptyList())
    private val dashboardState = mutableStateOf(INITIAL_DASHBOARD)
    private val voiceStatusState = mutableStateOf<String?>(null)
    private val haConfiguredState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2001)
        }

        appLoader = AppLoader(packageManager)
        loadAppsAsync()

        homeAssistantPreferences = HomeAssistantPreferences(this)
        haConfiguredState.value = homeAssistantPreferences.isConfigured()
        homeAssistantManager = HomeAssistantManager(homeAssistantPreferences) { data ->
            dashboardState.value = data
        }

        voiceAssistantManager = VoiceAssistantManager(this) { status, show ->
            voiceStatusState.value = if (show) status else null
        }
        voiceAssistantManager.handleAssistIntent(intent)

        setContent {
            TvTheme {
                MinimalTvShell()
            }
        }
    }

    @Composable
    private fun MinimalTvShell() {
        var destination by rememberSaveable { mutableStateOf(TvNavDestination.APPS) }
        val apps by appsState
        val inputs by inputsState
        val dashboard by dashboardState
        val voiceStatus by voiceStatusState
        val haConfigured by haConfiguredState

        val launchApp = remember { { app: AppEntry -> appLoader.launchApp(this@MainActivity, app.packageName) } }

        // Launcher contract: Back never leaves the home screen; from other tabs it returns to Apps.
        BackHandler {
            if (destination != TvNavDestination.APPS) destination = TvNavDestination.APPS
        }

        // Overlay rail over a fixed 64dp inset with a solid background to avoid full-screen gradient overdraw.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MidnightCyanTokens.BgGradientTop)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = MidnightCyanTokens.RailCollapsedWidth)
            ) {
                when (destination) {
                    TvNavDestination.SEARCH -> SearchScreen(
                        apps = apps,
                        youTubeApp = apps.firstOrNull { it.packageName == YOUTUBE_PACKAGE },
                        onLaunchApp = launchApp,
                        onSearchYouTube = { voiceAssistantManager.searchYouTube(it) },
                        weatherText = dashboard.weatherText
                    )
                    TvNavDestination.INPUTS -> InputsScreen(
                        inputs = inputs,
                        onSelectInput = { TvInputRepository.launchInput(this@MainActivity, it) },
                        onOpenSystemInputs = { TvInputRepository.openSystemInputs(this@MainActivity) },
                        weatherText = dashboard.weatherText
                    )
                    TvNavDestination.SMART_HOME -> SmartHomeScreen(
                        dashboard = dashboard,
                        haConfigured = haConfigured,
                        onToggleLight = { homeAssistantManager.toggleLight(it) }
                    )
                    TvNavDestination.APPS, TvNavDestination.SETTINGS -> AppsScreen(
                        apps = apps,
                        dashboard = dashboard,
                        voiceStatus = voiceStatus,
                        onLaunchApp = launchApp
                    )
                    TvNavDestination.REBOOT -> Unit
                }
            }

            TvNavigationRail(
                selectedDestination = destination,
                onSelectDestination = { dest ->
                    if (dest == TvNavDestination.SETTINGS) openConfig() else destination = dest
                },
                onReboot = { triggerDeviceReboot() },
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
    }

    private fun openConfig() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        // Refresh after returning from Settings or after apps were (un)installed.
        haConfiguredState.value = homeAssistantPreferences.isConfigured()
        loadAppsAsync()
        homeAssistantManager.startPolling()
    }

    private fun loadAppsAsync() {
        lifecycleScope.launch {
            val (loadedApps, loadedInputs) = withContext(Dispatchers.IO) {
                appLoader.loadInstalledApps() to TvInputRepository.loadInputs(this@MainActivity)
            }
            appsState.value = loadedApps
            inputsState.value = loadedInputs
        }
    }

    override fun onPause() {
        super.onPause()
        homeAssistantManager.stopPolling()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceAssistantManager.destroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ASSIST || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) {
            if (event?.repeatCount == 0) {
                voiceAssistantManager.startListening()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ASSIST || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST) {
            voiceAssistantManager.stopListening()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        voiceAssistantManager.handleAssistIntent(intent)
    }

    companion object {
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube.tv"

        private val INITIAL_DASHBOARD = DashboardData(
            solarPct = "—",
            solarProgress = 0,
            solarFooter = "Connecting...",
            tempMain = "—",
            tempFooter = "Connecting...",
            evPct = "—",
            evProgress = 0,
            evFooter = "Connecting...",
            neilPct = 0,
            hyslaPct = 0,
            finnPct = 0,
            weatherText = "☁️ —"
        )
    }

    private fun triggerDeviceReboot() {
        runCatching {
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            pm?.reboot(null)
        }.onFailure {
            runCatching {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "reboot"))
            }
        }
    }
}
