package com.nmaclean.minimaltv

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val assetName: String
)

object GitHubUpdater {
    private const val RELEASES_LIST_URL = "https://api.github.com/repos/nmaclean/android-minimal-tv/releases?per_page=20"
    private const val LATEST_RELEASE_URL = "https://api.github.com/repos/nmaclean/android-minimal-tv/releases/latest"
    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    sealed class UpdateCheckResult {
        data class Available(val release: GitHubReleaseInfo) : UpdateCheckResult()
        data class UpToDate(val currentVersion: String, val latestVersion: String) : UpdateCheckResult()
        data class Error(val message: String) : UpdateCheckResult()
    }

    fun checkForUpdate(currentVersion: String): UpdateCheckResult {
        return try {
            val (code, bodyText) = httpGetText(LATEST_RELEASE_URL)
            if (code == 404) {
                return UpdateCheckResult.Error("No published GitHub releases found yet (HTTP 404)")
            }
            if (code !in 200..299) {
                return UpdateCheckResult.Error("GitHub API error (HTTP $code)")
            }

            val release = parseRelease(JSONObject(bodyText))
                ?: return UpdateCheckResult.Error("Latest release is missing a version tag or APK asset")

            if (SemVer.isNewer(release.tagName, currentVersion)) {
                UpdateCheckResult.Available(release)
            } else {
                UpdateCheckResult.UpToDate(currentVersion, release.tagName)
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.localizedMessage ?: "Failed to check for updates")
        }
    }

    fun fetchReleases(): Result<List<GitHubReleaseInfo>> {
        return try {
            val (code, bodyText) = httpGetText(RELEASES_LIST_URL)
            if (code !in 200..299) {
                return Result.failure(IllegalStateException("GitHub API error (HTTP $code)"))
            }
            val array = JSONArray(bodyText)
            val releases = buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    if (obj.optBoolean("draft", false)) continue
                    parseRelease(obj)?.let { add(it) }
                }
            }
            Result.success(releases)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun httpGetText(urlStr: String): Pair<Int, String> {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "MinimalTV-Android")
                connectTimeout = 8000
                readTimeout = 8000
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to body
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseRelease(json: JSONObject): GitHubReleaseInfo? {
        val tagName = json.optString("tag_name", "").trim()
        if (tagName.isEmpty()) return null
        val versionName = tagName.removePrefix("v").removePrefix("V")
        val releaseNotes = json.optString("body", "").ifBlank { "No release notes provided." }
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                return GitHubReleaseInfo(
                    tagName = tagName,
                    versionName = versionName,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = url,
                    assetName = name
                )
            }
        }
        return null
    }

    fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Long, Long) -> Unit
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
                        onProgress(downloadedBytes, totalBytes)
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
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
    }

    /** Copies [apkFile] into the shared Downloads folder so it survives app uninstallation for rollback. */
    fun saveToDownloads(context: Context, apkFile: File, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, APK_MIME_TYPE)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
                resolver.openOutputStream(uri)?.use { out ->
                    apkFile.inputStream().use { input -> input.copyTo(out) }
                } ?: return false
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val dest = File(dir, fileName)
                apkFile.inputStream().use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    fun launchUninstall(context: Context) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
