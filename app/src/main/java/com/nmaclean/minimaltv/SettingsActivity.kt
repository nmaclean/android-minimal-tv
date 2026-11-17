package com.nmaclean.minimaltv

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class SettingsActivity : AppCompatActivity() {

    private lateinit var preferences: HomeAssistantPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private lateinit var urlInput: EditText
    private lateinit var tokenInput: EditText
    private lateinit var currentUrlPreview: TextView
    private lateinit var maskedTokenPreview: TextView
    private lateinit var haStatusView: TextView
    private lateinit var testConnectionButton: TextView
    private lateinit var saveButton: TextView

    private lateinit var updateStatusView: TextView
    private lateinit var releaseNotesView: TextView
    private lateinit var checkUpdatesButton: TextView
    private lateinit var downloadInstallButton: TextView
    private lateinit var localServerBanner: TextView
    private lateinit var toggleWebServerButton: TextView

    private var localConfigWebServer: LocalConfigWebServer? = null
    private var pendingApkUrl: String? = null
    private var isCheckingUpdate = false
    private var isDownloadingApk = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        preferences = HomeAssistantPreferences(this)

        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B1221"))
            isFillViewport = true
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(48), dpToPx(32), dpToPx(48), dpToPx(36))
            clipChildren = false
            clipToPadding = false
        }

        // --- Header ---
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(24) }
            clipChildren = false
            clipToPadding = false
        }

        val backButton = createPillButton("←  Back") { finish() }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(42)
            ).apply { rightMargin = dpToPx(20) }
        }

        val titleView = TextView(this).apply {
            text = "MinimalTV Settings"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        headerRow.addView(backButton)
        headerRow.addView(titleView)
        root.addView(headerRow)

        val webConfigRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(18), dpToPx(10), dpToPx(12), dpToPx(10))
            background = createPillBackground(focused = false)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(18) }
            clipChildren = false
            clipToPadding = false
        }

        localServerBanner = TextView(this).apply {
            text = "Web config server stopped"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { rightMargin = dpToPx(14) }
        }

        toggleWebServerButton = createPillButton("Start Web Config") {
            if (localConfigWebServer == null) {
                startLocalConfigServer()
            } else {
                stopLocalConfigServer()
            }
        }

        webConfigRow.addView(localServerBanner)
        webConfigRow.addView(toggleWebServerButton)
        root.addView(webConfigRow)

        // --- Section 1: Home Assistant Connection ---
        val haCard = createSectionCard("HOME ASSISTANT CONNECTION")

        val urlLabelRow = createFieldHeaderRow("Home Assistant URL")
        currentUrlPreview = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        urlLabelRow.addView(currentUrlPreview)
        haCard.addView(urlLabelRow)

        urlInput = createTvEditText(
            hint = "",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI,
            imeOptions = EditorInfo.IME_ACTION_NEXT or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN,
            isPassword = false
        )
        haCard.addView(urlInput)

        val tokenLabelRow = createFieldHeaderRow("Long-Lived Access Token").apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dpToPx(16)
        }
        maskedTokenPreview = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#94A3B8"))
        }
        tokenLabelRow.addView(maskedTokenPreview)
        haCard.addView(tokenLabelRow)

        tokenInput = createTvEditText(
            hint = "Paste Home Assistant Long-Lived Access Token",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            imeOptions = EditorInfo.IME_ACTION_DONE or
                EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_FLAG_NO_FULLSCREEN,
            isPassword = true
        )
        haCard.addView(tokenInput)

        val haActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(18) }
            clipChildren = false
            clipToPadding = false
        }

        testConnectionButton = createPillButton("Test Connection") {
            runConnectionTest()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(42)
            ).apply { rightMargin = dpToPx(14) }
        }

        saveButton = createPillButton("Save") {
            saveConfiguration()
        }

        haActionsRow.addView(testConnectionButton)
        haActionsRow.addView(saveButton)
        haCard.addView(haActionsRow)

        haStatusView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
        }
        haCard.addView(haStatusView)

        root.addView(haCard)

        // --- Section 2: App Info & Updates ---
        val appCard = createSectionCard("APP INFO & UPDATES").apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dpToPx(20)
        }

        val versionView = TextView(this).apply {
            text = "Installed Version: ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(14) }
        }
        appCard.addView(versionView)

        val updateActionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            clipChildren = false
            clipToPadding = false
        }

        checkUpdatesButton = createPillButton("Check for Updates") {
            runUpdateCheck()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(42)
            ).apply { rightMargin = dpToPx(14) }
        }

        downloadInstallButton = createPillButton("Download & Install") {
            runDownloadAndInstall()
        }.apply {
            visibility = View.GONE
        }

        updateActionsRow.addView(checkUpdatesButton)
        updateActionsRow.addView(downloadInstallButton)
        appCard.addView(updateActionsRow)

        updateStatusView = TextView(this).apply {
            text = "Repository: nmaclean/android-minimal-tv"
            textSize = 13f
            setTextColor(Color.parseColor("#94A3B8"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(12) }
        }
        appCard.addView(updateStatusView)

        releaseNotesView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#CBD5E1"))
            visibility = View.GONE
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            background = createInputBackground(focused = false)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dpToPx(10) }
        }
        appCard.addView(releaseNotesView)

        root.addView(appCard)
        scrollView.addView(root)
        setContentView(scrollView)

        populateCurrentConfig()
        urlInput.post { urlInput.requestFocus() }
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

    private fun startLocalConfigServer() {
        if (localConfigWebServer != null) return
        val ip = LocalConfigWebServer.getLocalIpv4Address(this)
        if (ip == null) {
            localServerBanner.setTextColor(Color.parseColor("#94A3B8"))
            localServerBanner.text = "Local setup server unavailable"
            return
        }

        try {
            val server = LocalConfigWebServer(
                preferences = preferences,
                onConfigSaved = { savedUrl ->
                    populateCurrentConfig()
                    showHaStatus("Saved from browser for $savedUrl", isError = false)
                }
            )
            val port = server.start()
            localConfigWebServer = server
            localServerBanner.setTextColor(Color.parseColor("#00E5FF"))
            localServerBanner.text = "Configure from phone/PC browser: http://$ip:$port"
            toggleWebServerButton.text = "Stop Web Config"
        } catch (_: Exception) {
            localServerBanner.setTextColor(Color.parseColor("#94A3B8"))
            localServerBanner.text = "Local setup server unavailable"
        }
    }

    private fun stopLocalConfigServer() {
        localConfigWebServer?.stop()
        localConfigWebServer = null
        localServerBanner.setTextColor(Color.parseColor("#94A3B8"))
        localServerBanner.text = "Web config server stopped"
        toggleWebServerButton.text = "Start Web Config"
    }

    private fun populateCurrentConfig() {
        val currentUrl = preferences.getUrl()
        val currentToken = preferences.getToken()

        urlInput.setText(currentUrl)
        tokenInput.setText(currentToken)

        currentUrlPreview.text = if (currentUrl.isNotBlank()) currentUrl else "Not configured"
        val masked = HomeAssistantPreferences.maskToken(currentToken)
        maskedTokenPreview.text = if (masked.isNotEmpty()) masked else "Not configured"
    }

    private fun saveConfiguration() {
        val rawUrl = urlInput.text?.toString().orEmpty()
        val rawToken = tokenInput.text?.toString().orEmpty()
        val normalizedUrl = HomeAssistantPreferences.normalizeUrl(rawUrl)
        val trimmedToken = rawToken.trim()

        if (normalizedUrl.isEmpty() || trimmedToken.isEmpty()) {
            showHaStatus("Please enter both a Home Assistant URL and an access token.", isError = true)
            return
        }

        preferences.save(normalizedUrl, trimmedToken)
        populateCurrentConfig()
        showHaStatus("Saved configuration for $normalizedUrl", isError = false)
    }

    private fun runConnectionTest() {
        val url = urlInput.text?.toString().orEmpty()
        val token = tokenInput.text?.toString().orEmpty()
        showHaStatus("Testing connection...", isError = false, neutral = true)
        testConnectionButton.isEnabled = false

        ioExecutor.execute {
            val result = HomeAssistantManager.testConnection(url, token)
            mainHandler.post {
                testConnectionButton.isEnabled = true
                showHaStatus(result.message, isError = !result.success)
            }
        }
    }

    private fun runUpdateCheck() {
        if (isCheckingUpdate || isDownloadingApk) return
        isCheckingUpdate = true
        pendingApkUrl = null
        downloadInstallButton.visibility = View.GONE
        releaseNotesView.visibility = View.GONE
        updateStatusView.setTextColor(Color.parseColor("#94A3B8"))
        updateStatusView.text = "Checking GitHub Releases..."

        ioExecutor.execute {
            val result = GitHubUpdater.checkForUpdate(BuildConfig.VERSION_NAME)
            mainHandler.post {
                isCheckingUpdate = false
                when (result) {
                    is GitHubUpdater.UpdateCheckResult.Available -> {
                        pendingApkUrl = result.apkUrl
                        updateStatusView.setTextColor(Color.parseColor("#00E5FF"))
                        updateStatusView.text = if (result.apkUrl != null) {
                            "Update available: ${result.releaseName} (${result.latestVersion})"
                        } else {
                            "Update ${result.latestVersion} found, but no APK asset is attached."
                        }
                        releaseNotesView.text = result.releaseNotes
                        releaseNotesView.visibility = View.VISIBLE
                        downloadInstallButton.visibility = if (result.apkUrl != null) View.VISIBLE else View.GONE
                    }
                    is GitHubUpdater.UpdateCheckResult.UpToDate -> {
                        updateStatusView.setTextColor(Color.parseColor("#22C55E"))
                        updateStatusView.text = "Up to date (installed ${result.currentVersion}, latest ${result.latestVersion})"
                        releaseNotesView.text = result.releaseNotes
                        releaseNotesView.visibility = View.VISIBLE
                    }
                    is GitHubUpdater.UpdateCheckResult.Error -> {
                        updateStatusView.setTextColor(Color.parseColor("#F87171"))
                        updateStatusView.text = result.message
                    }
                }
            }
        }
    }

    private fun runDownloadAndInstall() {
        val apkUrl = pendingApkUrl ?: return
        if (isDownloadingApk) return
        isDownloadingApk = true
        downloadInstallButton.isEnabled = false
        updateStatusView.setTextColor(Color.parseColor("#00E5FF"))
        updateStatusView.text = "Downloading update... 0%"

        ioExecutor.execute {
            try {
                val apkFile = GitHubUpdater.downloadApk(this, apkUrl) { pct ->
                    mainHandler.post {
                        updateStatusView.text = "Downloading update... $pct%"
                    }
                }
                mainHandler.post {
                    isDownloadingApk = false
                    downloadInstallButton.isEnabled = true
                    val launchedInstaller = GitHubUpdater.installApk(this, apkFile)
                    if (launchedInstaller) {
                        updateStatusView.setTextColor(Color.parseColor("#22C55E"))
                        updateStatusView.text = "Download complete. Launching installer..."
                    } else {
                        updateStatusView.setTextColor(Color.parseColor("#00E5FF"))
                        updateStatusView.text = "Allow 'Install unknown apps' in Settings, then tap Download & Install again."
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    isDownloadingApk = false
                    downloadInstallButton.isEnabled = true
                    updateStatusView.setTextColor(Color.parseColor("#F87171"))
                    updateStatusView.text = e.localizedMessage ?: "Failed to download update"
                }
            }
        }
    }

    private fun showHaStatus(message: String, isError: Boolean, neutral: Boolean = false) {
        haStatusView.visibility = View.VISIBLE
        haStatusView.text = message
        haStatusView.setTextColor(
            when {
                neutral -> Color.parseColor("#94A3B8")
                isError -> Color.parseColor("#F87171")
                else -> Color.parseColor("#22C55E")
            }
        )
    }

    private fun createSectionCard(title: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(20), dpToPx(24), dpToPx(20))
            background = createPillBackground(focused = false)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            clipChildren = false
            clipToPadding = false

            addView(TextView(this@SettingsActivity).apply {
                text = title
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.06f
                setTextColor(Color.parseColor("#00E5FF"))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dpToPx(14) }
            })
        }
    }

    private fun createFieldHeaderRow(label: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dpToPx(6) }

            addView(TextView(this@SettingsActivity).apply {
                text = label
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun createTvEditText(
        hint: String,
        inputType: Int,
        imeOptions: Int,
        isPassword: Boolean
    ): EditText {
        return EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            this.imeOptions = imeOptions
            if (isPassword) {
                transformationMethod = PasswordTransformationMethod.getInstance()
            }
            setSingleLine(true)
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748B"))
            setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12))
            background = createInputBackground(focused = false)
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnFocusChangeListener { v, hasFocus ->
                v.background = createInputBackground(hasFocus)
            }
        }
    }

    private fun createPillButton(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dpToPx(20), 0, dpToPx(20), 0)
            background = createPillBackground(focused = false)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(42)
            )
            setOnFocusChangeListener { v, hasFocus ->
                v.background = createPillBackground(hasFocus)
                v.animate().scaleX(if (hasFocus) 1.05f else 1.0f)
                    .scaleY(if (hasFocus) 1.05f else 1.0f)
                    .setDuration(100).start()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun createPillBackground(focused: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 18f
            setColor(if (focused) Color.parseColor("#1D2D49") else Color.parseColor("#162238"))
            setStroke(2, Color.parseColor(if (focused) "#00E5FF" else "#1E293B"))
        }
    }

    private fun createInputBackground(focused: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = 14f
            setColor(Color.parseColor("#131E33"))
            setStroke(2, Color.parseColor(if (focused) "#00E5FF" else "#1E293B"))
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
