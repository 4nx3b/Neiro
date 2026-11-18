package com.music.bitchord

import com.music.bitchord.ui.motion.PageMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageMotionTest {
    @Test
    fun pushFollowsTheMeasuredFrames() {
        val measured = listOf(33f to 0.25f, 67f to 0.35f, 100f to 0.67f, 133f to 0.77f, 167f to 0.90f, 200f to 0.95f)
        measured.forEach { (t, share) ->
            assertEquals("t=$t", share, PageMotion.slideEase(t / PageMotion.SLIDE_MS), 0.08f)
        }
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
