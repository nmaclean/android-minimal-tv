package com.nmaclean.minimaltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCatalogTest {

    private data class DummyApp(val pkg: String, val label: String)

    private val sampleApps = listOf(
        DummyApp("com.nmaclean.tvheadend", "TVHeadend Live"),
        DummyApp("com.google.android.youtube.tv", "YouTube"),
        DummyApp("org.jellyfin.androidtv", "Jellyfin")
    )

    @Test
    fun categoryFor_mapsKnownPackagesAndLiveFlag() {
        assertEquals(AppCategory.LIVE_TV, AppCatalog.categoryFor("com.nmaclean.tvheadend"))
        assertTrue(AppCatalog.isLiveApp("com.nmaclean.tvheadend"))

        assertEquals(AppCategory.STREAMING, AppCatalog.categoryFor("com.google.android.youtube.tv"))
        assertFalse(AppCatalog.isLiveApp("com.google.android.youtube.tv"))

        assertEquals(AppCategory.MEDIA, AppCatalog.categoryFor("org.jellyfin.androidtv"))
    }

    @Test
    fun filter_filtersByCategoryAndQuery() {
        val liveOnly = AppCatalog.filter(sampleApps, AppCategory.LIVE_TV, "", { it.pkg }, { it.label })
        assertEquals(listOf("com.nmaclean.tvheadend"), liveOnly.map { it.pkg })

        val byQuery = AppCatalog.filter(sampleApps, AppCategory.ALL, "jelly", { it.pkg }, { it.label })
        assertEquals(listOf("org.jellyfin.androidtv"), byQuery.map { it.pkg })
    }
}
