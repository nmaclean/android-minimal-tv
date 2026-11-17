package com.nmaclean.minimaltv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {

    @Test
    fun parse_handlesPrefixAndMissingPatch() {
        assertEquals(SemVer(1, 2, 3), SemVer.parse("v1.2.3"))
        assertEquals(SemVer(1, 2, 0), SemVer.parse("1.2"))
        assertEquals(SemVer(2, 0, 0, "rc.1"), SemVer.parse("V2.0.0-rc.1+42"))
        assertNull(SemVer.parse("not-a-version"))
    }

    @Test
    fun isNewer_comparesMajorMinorPatchAndPreRelease() {
        assertTrue(SemVer.isNewer("v1.1.0", "1.0.0"))
        assertTrue(SemVer.isNewer("1.0.1", "1.0.0"))
        assertTrue(SemVer.isNewer("2.0.0", "1.9.9"))
        assertTrue(SemVer.isNewer("1.2.0", "1.2.0-beta.1"))
        assertTrue(SemVer.isNewer("1.2.0-beta.2", "1.2.0-beta.1"))

        assertFalse(SemVer.isNewer("1.0.0", "v1.0.0"))
        assertFalse(SemVer.isNewer("1.0.0", "1.0.1"))
        assertFalse(SemVer.isNewer("1.2.0-alpha.1", "1.2.0"))
        assertFalse(SemVer.isNewer("invalid", "1.0.0"))
    }
}
