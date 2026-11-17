package com.nmaclean.minimaltv

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

/**
 * D-pad-navigable Android TV dialog for checking, downloading, installing,
 * and rolling back GitHub Releases APKs (ported from android-tvheadend-tif).
 */
class UpdatePromptDialog(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var dialog: AlertDialog? = null
    @Volatile
    private var cancelled = false

    private lateinit var messageView: TextView
    private lateinit var progressBar: ProgressBar

    fun show() {
        val density = context.resources.displayMetrics.density
        val pad = (24 * density).toInt()

        messageView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            text = "Checking GitHub Releases for updates (installed v${BuildConfig.VERSION_NAME})..."
        }

        progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            max = 100
            setPadding(0, (16 * density).toInt(), 0, 0)
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (16 * density).toInt(), pad, (8 * density).toInt())
            addView(messageView)
            addView(progressBar)
        }

        val scrollView = ScrollView(context).apply {
            isFocusable = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            addView(contentLayout)
        }

        val alertDialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Software Update")
            .setView(scrollView)
            .setPositiveButton("Download & Install", null)
            .setNeutralButton("Choose Version", null)
            .setNegativeButton("Close") { d, _ ->
                cancelled = true
                d.dismiss()
            }
            .setOnDismissListener {
                cancelled = true
                ioExecutor.shutdownNow()
            }
            .create()

        dialog = alertDialog
        alertDialog.show()

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.let { applyTvFocusStyle(it) }
        alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.let { applyTvFocusStyle(it) }
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.let { applyTvFocusStyle(it) }

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.visibility = View.GONE
        alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.visibility = View.GONE
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.requestFocus()

        startUpdateCheck()
    }

    private fun startUpdateCheck() {
        val d = dialog ?: return
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        messageView.text = "Checking GitHub Releases for updates (installed v${BuildConfig.VERSION_NAME})..."
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.visibility = View.GONE
        d.getButton(AlertDialog.BUTTON_NEUTRAL)?.visibility = View.GONE

        ioExecutor.execute {
            val result = GitHubUpdater.checkForUpdate(BuildConfig.VERSION_NAME)
            mainHandler.post {
                if (cancelled || !d.isShowing) return@post
                when (result) {
                    is GitHubUpdater.UpdateCheckResult.Available -> showAvailable(result.release)
                    is GitHubUpdater.UpdateCheckResult.UpToDate -> showUpToDate(result.currentVersion)
                    is GitHubUpdater.UpdateCheckResult.Error -> showError(result.message)
                }
            }
        }
    }

    private fun showUpToDate(currentVersion: String) {
        val d = dialog ?: return
        if (!d.isShowing) return
        progressBar.visibility = View.GONE
        messageView.text = "You are running the latest version (v$currentVersion)."
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            text = "Check Again"
            visibility = View.VISIBLE
            setOnClickListener { startUpdateCheck() }
            requestFocus()
        }
        d.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
            text = "Choose Version"
            visibility = View.VISIBLE
            setOnClickListener { openVersionSelector() }
        }
    }

    private fun showAvailable(release: GitHubReleaseInfo) {
        val d = dialog ?: return
        if (!d.isShowing) return
        progressBar.visibility = View.GONE
        val isSame = release.versionName.equals(BuildConfig.VERSION_NAME, ignoreCase = true)
        val isDowngrade = !isSame && SemVer.isNewer(BuildConfig.VERSION_NAME, release.tagName)
        val header = when {
            isSame -> "Selected version: ${release.tagName} (currently installed)"
            isDowngrade -> "Older version: ${release.tagName} (installed v${BuildConfig.VERSION_NAME})"
            else -> "Release ${release.tagName} (installed v${BuildConfig.VERSION_NAME})"
        }
        messageView.text = "$header\n\n${release.releaseNotes}"

        d.getButton(AlertDialog.BUTTON_NEGATIVE)?.text = "Later"
        d.getButton(AlertDialog.BUTTON_NEUTRAL)?.apply {
            text = "Choose Version"
            visibility = View.VISIBLE
            setOnClickListener { openVersionSelector() }
        }
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            text = when {
                isSame -> "Reinstall"
                isDowngrade -> "Downgrade..."
                else -> "Download & Install"
            }
            visibility = View.VISIBLE
            setOnClickListener {
                if (isDowngrade) {
                    confirmDowngrade(release)
                } else {
                    startDownloadAndInstall(release)
                }
            }
            requestFocus()
        }
    }

    private fun openVersionSelector() {
        val d = dialog ?: return
        if (!d.isShowing) return
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        messageView.text = "Loading available releases from GitHub..."
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.visibility = View.GONE
        d.getButton(AlertDialog.BUTTON_NEUTRAL)?.visibility = View.GONE

        ioExecutor.execute {
            val res = GitHubUpdater.fetchReleases()
            mainHandler.post {
                if (cancelled || !d.isShowing) return@post
                progressBar.visibility = View.GONE
                res.onSuccess { releases ->
                    if (releases.isEmpty()) {
                        showError("No installable APK releases found.")
                        return@onSuccess
                    }
                    val labels = releases.mapIndexed { idx, rel ->
                        val tags = buildList {
                            if (idx == 0) add("Latest")
                            if (rel.versionName.equals(BuildConfig.VERSION_NAME, ignoreCase = true)) add("Installed")
                        }
                        if (tags.isEmpty()) rel.tagName else "${rel.tagName} (${tags.joinToString(", ")})"
                    }.toTypedArray()

                    AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Select App Version")
                        .setItems(labels) { _, which ->
                            showAvailable(releases[which])
                        }
                        .setNegativeButton("Back") { _, _ ->
                            startUpdateCheck()
                        }
                        .show()
                }.onFailure { err ->
                    showError(err.message ?: "Failed to load release list")
                }
            }
        }
    }

    private fun confirmDowngrade(release: GitHubReleaseInfo) {
        AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Uninstall Newer Version & Downgrade?")
            .setMessage(
                "Android requires uninstalling the newer version (v${BuildConfig.VERSION_NAME}) before installing ${release.tagName}.\n\n" +
                    "We will save ${release.assetName} to your Downloads folder first, then open Uninstall so you can install it from Downloads afterwards."
            )
            .setPositiveButton("Download & Uninstall") { _, _ ->
                startDownloadToDownloadsAndUninstall(release)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startDownloadToDownloadsAndUninstall(release: GitHubReleaseInfo) {
        val d = dialog ?: return
        if (!d.isShowing) return
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        progressBar.progress = 0
        messageView.text = "Saving ${release.assetName} to Downloads..."
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.visibility = View.GONE
        d.getButton(AlertDialog.BUTTON_NEUTRAL)?.visibility = View.GONE

        ioExecutor.execute {
            try {
                val apkFile = GitHubUpdater.downloadApk(context, release.apkDownloadUrl) { done, total ->
                    if (total > 0L) {
                        val pct = ((done * 100L) / total).toInt().coerceIn(0, 100)
                        mainHandler.post {
                            if (!cancelled && d.isShowing) {
                                progressBar.isIndeterminate = false
                                progressBar.progress = pct
                                messageView.text = "Saving ${release.assetName} to Downloads: $pct%"
                            }
                        }
                    }
                }
                val saved = GitHubUpdater.saveToDownloads(context, apkFile, release.assetName)
                mainHandler.post {
                    if (cancelled || !d.isShowing) return@post
                    if (saved) {
                        d.dismiss()
                        GitHubUpdater.launchUninstall(context)
                    } else {
                        showError("Could not save APK to Downloads.")
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (!cancelled && d.isShowing) {
                        showError(e.message ?: "Download failed")
                    }
                }
            }
        }
    }

    private fun startDownloadAndInstall(release: GitHubReleaseInfo) {
        val d = dialog ?: return
        if (!d.isShowing) return
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        progressBar.progress = 0
        messageView.text = "Downloading ${release.assetName}..."
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.visibility = View.GONE
        d.getButton(AlertDialog.BUTTON_NEUTRAL)?.visibility = View.GONE

        ioExecutor.execute {
            try {
                val apkFile = GitHubUpdater.downloadApk(context, release.apkDownloadUrl) { done, total ->
                    if (total > 0L) {
                        val pct = ((done * 100L) / total).toInt().coerceIn(0, 100)
                        mainHandler.post {
                            if (!cancelled && d.isShowing) {
                                progressBar.isIndeterminate = false
                                progressBar.progress = pct
                                messageView.text = "Downloading ${release.assetName}: $pct%"
                            }
                        }
                    }
                }
                mainHandler.post {
                    if (cancelled || !d.isShowing) return@post
                    if (GitHubUpdater.installApk(context, apkFile)) {
                        d.dismiss()
                    } else {
                        showReadyToInstall(release, apkFile)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    if (!cancelled && d.isShowing) {
                        showError(e.message ?: "Download failed")
                    }
                }
            }
        }
    }

    private fun showReadyToInstall(release: GitHubReleaseInfo, apkFile: File) {
        val d = dialog ?: return
        if (!d.isShowing) return
        progressBar.visibility = View.GONE
        messageView.text = "Downloaded ${release.tagName}. Allow 'Install unknown apps' if prompted, then press Install Now."
        d.getButton(AlertDialog.BUTTON_NEGATIVE)?.text = "Close"
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            text = "Install Now"
            visibility = View.VISIBLE
            setOnClickListener {
                if (GitHubUpdater.installApk(context, apkFile)) {
                    d.dismiss()
                }
            }
            requestFocus()
        }
    }

    private fun applyTvFocusStyle(button: Button) {
        val density = context.resources.displayMetrics.density
        val radius = 8f * density
        val padH = (16 * density).toInt()
        val padV = (8 * density).toInt()

        val focusedBg = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.parseColor("#1D2D49"))
            setStroke((2 * density).toInt(), Color.parseColor("#00E5FF"))
        }
        val defaultBg = GradientDrawable().apply {
            cornerRadius = radius
            setColor(Color.parseColor("#162238"))
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focusedBg)
            addState(intArrayOf(android.R.attr.state_selected), focusedBg)
            addState(intArrayOf(), defaultBg)
        }
        button.background = states
        button.setTextColor(Color.WHITE)
        button.setPadding(padH, padV, padH, padV)
    }

    private fun showError(message: String) {
        val d = dialog ?: return
        if (!d.isShowing) return
        progressBar.visibility = View.GONE
        messageView.text = "Update error: $message"
        d.getButton(AlertDialog.BUTTON_NEGATIVE)?.text = "Close"
        d.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            text = "Retry"
            visibility = View.VISIBLE
            setOnClickListener { startUpdateCheck() }
            post { requestFocus() }
        }
    }
}
