package com.nmaclean.minimaltv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

@Immutable
data class AppEntry(
    val label: String,
    val packageName: String,
    val bitmap: ImageBitmap? = null
)

class AppLoader(private val packageManager: PackageManager) {
    private val allowedVideoPackages = setOf(
        "org.jellyfin.androidtv",
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.apple.atve.sony.appletv",
        "com.bbc.iplayer.androidtv",
        "com.plexapp.android",
        "org.xbmc.kodi",
        "com.videolan.vlc",
        "com.nmaclean.tvheadend"
    )

    private val preferredAppOrder = listOf(
        "com.nmaclean.tvheadend",
        "com.google.android.youtube.tv",
        "org.jellyfin.androidtv",
        "com.amazon.amazonvideo.livingroom"
    )

    fun loadInstalledApps(): List<AppEntry> {
        val pm = packageManager
        val appsMap = mutableMapOf<String, AppEntry>()

        val leanbackIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(leanbackIntent, 0)

        val standardIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val allResolveInfos = pm.queryIntentActivities(standardIntent, 0)

        val combined = (resolveInfos + allResolveInfos).distinctBy { info -> info.activityInfo.packageName }

        for (info in combined) {
            val pkg = info.activityInfo.packageName
            if (allowedVideoPackages.contains(pkg) && !appsMap.containsKey(pkg)) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val banner = appInfo.loadBanner(pm) ?: appInfo.loadIcon(pm)
                    if (banner != null) {
                        val preloaded = runCatching {
                            val w = if (banner.intrinsicWidth > 0) banner.intrinsicWidth.coerceAtMost(320) else 320
                            val h = if (banner.intrinsicHeight > 0) banner.intrinsicHeight.coerceAtMost(180) else 180
                            banner.toBitmap(width = w, height = h).asImageBitmap()
                        }.getOrNull()
                        appsMap[pkg] = AppEntry(label, pkg, preloaded)
                    }
                } catch (_: Exception) {
                    // Ignore missing packages
                }
            }
        }

        return appsMap.values.sortedWith(compareBy(
            { entry ->
                val index = preferredAppOrder.indexOf(entry.packageName)
                if (index != -1) index else Int.MAX_VALUE
            },
            { it.label }
        ))
    }

    fun launchApp(context: Context, packageName: String) {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent)
            return
        }

        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                setPackage(packageName)
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            }
            var resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            if (resolveInfos.isEmpty()) {
                mainIntent.removeCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                mainIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                resolveInfos = pm.queryIntentActivities(mainIntent, 0)
            }

            if (resolveInfos.isNotEmpty()) {
                val activityInfo = resolveInfos[0].activityInfo
                val explicitIntent = Intent(Intent.ACTION_MAIN).apply {
                    setClassName(activityInfo.packageName, activityInfo.name)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
                context.startActivity(explicitIntent)
            }
        } catch (_: Exception) {
            // Ignore
        }
    }
}
