package com.music.bitchord

import com.music.bitchord.ui.motion.PageMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The page and album motion measured from clip 03-54-11 (docs/ios-glass-reference.md). */
class PageMotionTest {
    @Test
    fun pushIsAboutHalfWayAfterOneFrameAndSettlesInTime() {
        val oneFrame = 33f / PageMotion.SLIDE_MS
        val e = PageMotion.slideEase(oneFrame)
        assertTrue("one frame in: $e", e in 0.35f..0.65f)
        assertTrue(PageMotion.slideEase(170f / PageMotion.SLIDE_MS) > 0.97f)
        assertEquals(1f, PageMotion.slideEase(1f), 0f)
        var last = 0f
        for (i in 0..20) {
            val v = PageMotion.slideEase(i / 20f)
            assertTrue(v >= last - 1e-4f)
            last = v
        }
    }

    @Test
    fun theCardStartsOnTheCoverAndEndsAsThePage() {
        val start = PageMotion.card(0f, 1f, coverLeft = 40f, coverTop = 600f, coverWidth = 300f, pageWidth = 1080f, cornerPx = 36f)
        assertEquals(300f / 1080f, start.scale, 1e-4f)
        assertEquals(40f, start.translationX, 1e-3f)
        assertEquals(600f, start.translationY, 1e-3f)
        // The corner on screen is the cover's: 36 px, so 36 / scale in the page's own pixels.
        assertEquals(36f / (300f / 1080f), start.cornerPx, 1e-2f)
        val end = PageMotion.card(1f, 1f, 40f, 600f, 300f, 1080f, 36f)
        assertEquals(1f, end.scale, 1e-4f)
        assertEquals(0f, end.translationX, 1e-4f)
        assertEquals(0f, end.translationY, 1e-4f)
        assertEquals(0f, end.cornerPx, 1e-4f)
    }

    @Test
    fun theCloseTravelsFirstThenDissolves() {
        val (g0, a0) = PageMotion.closeFrame(1f)
        assertEquals(1f, g0, 1e-4f)
        assertEquals(1f, a0, 1e-4f)
        // Most of the way down the card is on the cover but still opaque …
        val (g1, a1) = PageMotion.closeFrame(1f - PageMotion.CLOSE_TRAVEL)
        assertEquals(0f, g1, 1e-4f)
        assertEquals(1f, a1, 1e-4f)
        // … and only then dissolves.
        val (_, a2) = PageMotion.closeFrame((1f - PageMotion.CLOSE_TRAVEL) / 2f)
        assertTrue(a2 in 0.3f..0.7f)
        assertEquals(0f, PageMotion.closeFrame(0f).second, 0f)
    }
}
