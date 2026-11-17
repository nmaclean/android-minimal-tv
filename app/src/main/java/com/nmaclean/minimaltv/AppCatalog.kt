package com.nmaclean.minimaltv

/**
 * Pure (Android-free) launcher catalogue logic: category mapping, filtering and search.
 * Kept separate from Compose so it can be unit-tested on the JVM.
 */
enum class AppCategory(val label: String) {
    ALL("All"),
    LIVE_TV("Live TV"),
    STREAMING("Streaming"),
    MEDIA("Media Players")
}

object AppCatalog {
    private val categoryByPackage = mapOf(
        "com.nmaclean.tvheadend" to AppCategory.LIVE_TV,
        "com.google.android.youtube.tv" to AppCategory.STREAMING,
        "com.netflix.ninja" to AppCategory.STREAMING,
        "com.amazon.amazonvideo.livingroom" to AppCategory.STREAMING,
        "com.disney.disneyplus" to AppCategory.STREAMING,
        "com.apple.atve.sony.appletv" to AppCategory.STREAMING,
        "com.bbc.iplayer.androidtv" to AppCategory.STREAMING,
        "org.jellyfin.androidtv" to AppCategory.MEDIA,
        "com.plexapp.android" to AppCategory.MEDIA,
        "org.xbmc.kodi" to AppCategory.MEDIA,
        "com.videolan.vlc" to AppCategory.MEDIA
    )

    fun categoryFor(packageName: String): AppCategory =
        categoryByPackage[packageName] ?: AppCategory.STREAMING

    /** Live TV apps get a red `LIVE` badge on their card. */
    fun isLiveApp(packageName: String): Boolean = categoryFor(packageName) == AppCategory.LIVE_TV

    fun <T> filter(
        items: List<T>,
        category: AppCategory,
        query: String,
        packageOf: (T) -> String,
        labelOf: (T) -> String
    ): List<T> {
        val q = query.trim().lowercase()
        return items.filter { item ->
            val pkg = packageOf(item)
            val matchesCategory = category == AppCategory.ALL || categoryFor(pkg) == category
            val matchesQuery = q.isEmpty() ||
                labelOf(item).lowercase().contains(q) ||
                pkg.lowercase().contains(q) ||
                categoryFor(pkg).label.lowercase().contains(q)
            matchesCategory && matchesQuery
        }
    }

    /** Only show category pills that actually contain installed apps (plus "All"). */
    fun availableCategories(packageNames: Collection<String>): List<AppCategory> {
        val present = packageNames.map { categoryFor(it) }.toSet()
        return AppCategory.entries.filter { it == AppCategory.ALL || it in present }
    }
}
