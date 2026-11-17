package com.nmaclean.minimaltv

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdater {
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/nmaclean/android-minimal-tv/releases/latest"

    sealed class UpdateCheckResult {
        data class Available(
            val latestVersion: String,
            val releaseName: String,
            val releaseNotes: String,
            val apkUrl: String?
        ) : UpdateCheckResult()

        data class UpToDate(
            val currentVersion: String,
            val latestVersion: String,
            val releaseNotes: String
        ) : UpdateCheckResult()

        data class Error(val message: String) : UpdateCheckResult()
    }

    fun checkForUpdate(currentVersion: String): UpdateCheckResult {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MinimalTV-Android")
                connectTimeout = 8000
                readTimeout = 8000
            }

            val code = conn.responseCode
            if (code == 404) {
                return UpdateCheckResult.Error("No published GitHub releases found yet (HTTP 404)")
            }
            if (code !in 200..299) {
                return UpdateCheckResult.Error("GitHub API error (HTTP $code)")
            }

            val bodyText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(bodyText)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isEmpty()) {
                return UpdateCheckResult.Error("Latest release is missing a version tag")
            }

            val releaseName = json.optString("name", tagName).ifBlank { tagName }
            val releaseNotes = json.optString("body", "").ifBlank { "No release notes provided." }
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "").ifBlank { null }
                        if (apkUrl != null) break
                    }
                }
            }

            if (SemVer.isNewer(tagName, currentVersion)) {
                UpdateCheckResult.Available(
                    latestVersion = tagName,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                    apkUrl = apkUrl
                )
            } else {
                UpdateCheckResult.UpToDate(
                    currentVersion = currentVersion,
                    latestVersion = tagName,
                    releaseNotes = releaseNotes
                )
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Failed to check for updates")
        } finally {
            conn?.disconnect()
        }
    }

    fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Int) -> Unit
    ): File {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "MinimalTV-update.apk")
        if (apkFile.exists()) {
            apkFile.delete()
        }

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "MinimalTV-Android")
                connectTimeout = 10000
                readTimeout = 20000
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Download failed (HTTP $code)")
            }

            val totalBytes = conn.contentLengthLong
            var downloadedBytes = 0L
            val buffer = ByteArray(8192)

            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val pct = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            onProgress(pct)
                        }
                    }
                }
            }
            return apkFile
        } finally {
            conn?.disconnect()
        }
    }

    fun installApk(context: Context, apkFile: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            val unknownSourcesIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(unknownSourcesIntent)
            } catch (_: ActivityNotFoundException) {
                val appDetailsIntent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    context.startActivity(appDetailsIntent)
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(
                        Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            return false
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }
}
