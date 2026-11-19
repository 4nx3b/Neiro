package com.music.bitchord

import com.music.bitchord.ui.motion.IosNavigationDurationMillis
import com.music.bitchord.ui.motion.iosNavigationParallaxOffset
import com.music.bitchord.ui.motion.iosNavigationPosition
import com.music.bitchord.ui.motion.PageMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageMotionTest {
    @Test
    fun pushIsOnTheGlasmioCriticalSpring() {
        // The ported slide: Glasmio's unnormalised critical spring (damping 1,
        // ω = 2π/0.3) sampled over the 350 ms coordinator window — so a frame
        // is a frame of that window: 15% of the travel after one, settled by
        // 170 ms, monotonic throughout.
        val oneFrame = 33f / IosNavigationDurationMillis
        val e = iosNavigationPosition(oneFrame, 350_000_000L)
        assertTrue("one frame in: $e", e in 0.08f..0.22f)
        assertTrue(iosNavigationPosition(170f / IosNavigationDurationMillis, 350_000_000L) > 0.85f)
        // The unnormalised spring asymptotes: 99.5% at the 350 ms window's end —
        // the fork's motion runs on past it rather than being cut to it.
        assertTrue(iosNavigationPosition(1f, 350_000_000L) > 0.99f)
        var last = 0f
        for (i in 0..20) {
            val v = iosNavigationPosition(i / 20f, 350_000_000L)
            assertTrue(v >= last - 1e-4f)
            last = v
        }
    }

    @Test
    fun theUnderlayTravelsTheParallaxRemainder() {
        // Glasmio's parallax offset: the covered page moves the width minus
        // seventy percent of it, pixel-exact through the density round-trip.
        assertEquals(1080 - (1080 / 3f * 0.7f).toInt() * 3, iosNavigationParallaxOffset(1080, 3f))
        val width = 1080
        val offset = iosNavigationParallaxOffset(width, 2.75f)
        assertTrue(offset in (width * 0.28f).toInt()..(width * 0.32f).toInt())
    }

    @Test
    fun theCardStartsOnTheCoverAndEndsAsThePage() {
        val start = PageMotion.card(0f, 1f, coverLeft = 40f, coverTop = 600f, coverWidth = 300f, pageWidth = 1080f, cornerPx = 36f)
        assertEquals(300f / 1080f, start.scale, 1e-4f)
        assertEquals(40f, start.translationX, 1e-3f)
        assertEquals(600f, start.translationY, 1e-3f)

        assertEquals(36f / (300f / 1080f), start.cornerPx, 1e-2f)
        val end = PageMotion.card(1f, 1f, 40f, 600f, 300f, 1080f, 36f)
        assertEquals(1f, end.scale, 1e-4f)
        assertEquals(0f, end.translationX, 1e-4f)
        assertEquals(0f, end.translationY, 1e-4f)
        assertEquals(0f, end.cornerPx, 1e-4f)
    }

    @Test
    fun theZoomEaseMatchesTheMeasuredFrames() {
        val measured = listOf(0.47f, 0.71f, 0.89f, 0.96f)
        measured.forEachIndexed { i, share ->
            val t = 33f * (i + 1) / PageMotion.OPEN_MS
            assertEquals("frame ${i + 1}", share, PageMotion.zoomEase(t), 0.06f)
        }
        assertEquals(0f, PageMotion.zoomEase(0f), 0f)
        assertEquals(1f, PageMotion.zoomEase(1f), 1e-5f)
    }

    @Test
    fun theCloseTravelsFirstThenDissolves() {
        val (g0, a0) = PageMotion.closeFrame(1f)
        assertEquals(1f, g0, 1e-4f)
        assertEquals(1f, a0, 1e-4f)
        val travelEnd = 1f - PageMotion.CLOSE_TRAVEL
        val (g1, a1) = PageMotion.closeFrame(travelEnd)
        assertEquals(0f, g1, 1e-4f)
        assertEquals(1f, a1, 1e-4f)
        val (_, a2) = PageMotion.closeFrame(travelEnd / 2f)
        assertTrue(a2 in 0.3f..0.7f)
        assertEquals(0f, PageMotion.closeFrame(0f).second, 0f)
        val measured = listOf(0.71f, 0.46f, 0.40f, 0.21f, 0.09f, 0.03f)
        measured.forEachIndexed { i, share ->
            val q = 1f - 33f * (i + 1) / PageMotion.CLOSE_MS
            assertEquals("close frame ${i + 1}", share, PageMotion.closeFrame(q).first, 0.12f)
        }
    }
}
