package com.music.bitchord

import com.music.bitchord.data.CrashLoopPolicy
import com.music.bitchord.data.CrashLoopPolicy.BootCrash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CrashLoopGuardTest {
    private val now = 1_800_000_000_000L

    private fun crash(agoMs: Long, uptimeMs: Long, style: String) = BootCrash(now - agoMs, uptimeMs, style)

    @Test
    fun twoQuickCrashesOnTheSamePortedStyleReset() {
        val entries = listOf(crash(40_000, 3_000, "CINEMATIC"), crash(5_000, 2_500, "CINEMATIC"))
        assertEquals("CINEMATIC", CrashLoopPolicy.shouldResetStyle(entries, now))
    }

    @Test
    fun onlyTheLastTwoCount() {
        val entries =
            listOf(
                crash(90_000, 2_000, "LOOPER"),
                crash(40_000, 3_000, "CINEMATIC"),
                crash(5_000, 2_500, "CINEMATIC"),
            )
        assertEquals("CINEMATIC", CrashLoopPolicy.shouldResetStyle(entries, now))
    }

    @Test
    fun oneCrashIsNotALoop() {
        assertNull(CrashLoopPolicy.shouldResetStyle(listOf(crash(5_000, 2_000, "CINEMATIC")), now))
        assertNull(CrashLoopPolicy.shouldResetStyle(emptyList(), now))
    }

    @Test
    fun theAppsOwnPlayerIsNeverReset() {
        val entries = listOf(crash(40_000, 3_000, "BITCHORD"), crash(5_000, 2_500, "BITCHORD"))
        assertNull(CrashLoopPolicy.shouldResetStyle(entries, now))
    }

    @Test
    fun slowCrashesAreNotALoop() {
        val entries = listOf(crash(400_000, 120_000, "CINEMATIC"), crash(5_000, 2_500, "CINEMATIC"))
        assertNull(CrashLoopPolicy.shouldResetStyle(entries, now))
    }

    @Test
    fun differentStylesAreNotALoop() {
        val entries = listOf(crash(40_000, 3_000, "CINEMATIC"), crash(5_000, 2_500, "LOOPER"))
        assertNull(CrashLoopPolicy.shouldResetStyle(entries, now))
    }

    @Test
    fun crashesOutsideTheWindowAreIgnored() {
        val entries = listOf(crash(11 * 60_000L, 3_000, "CINEMATIC"), crash(5_000, 2_500, "CINEMATIC"))
        assertNull(CrashLoopPolicy.shouldResetStyle(entries, now))
    }

    @Test
    fun recordLinesRoundTrip() {
        val c = BootCrash(now, 2_345, "TIKTOK")
        assertEquals(c, CrashLoopPolicy.parse(CrashLoopPolicy.format(c)))
        assertNull(CrashLoopPolicy.parse("garbage"))
        assertNull(CrashLoopPolicy.parse("1 x STYLE"))
    }
}
