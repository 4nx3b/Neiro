package com.music.bitchord

import com.music.bitchord.ui.components.MORPH_CONTENT_BLUR_DP
import com.music.bitchord.ui.components.MORPH_CONTENT_SCALE
import com.music.bitchord.ui.components.MorphBox
import com.music.bitchord.ui.components.centreSource
import com.music.bitchord.ui.components.morphFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The popup morph from 15-57-35: a toolbar button near the top right swells
 * into a wider capsule that keeps its right edge by the button and reaches
 * left over the title.
 */
class GlassMorphTest {
    private val button = MorphBox(left = 1010f, top = 150f, width = 140f, height = 140f)
    private val menu = MorphBox(left = 330f, top = 140f, width = 836f, height = 220f)

    @Test
    fun startsOnTheButtonAndEndsOnTheMenu() {
        val start = morphFrame(0f, button, menu)
        assertEquals(button.width, start.box.width, 0.5f)
        assertEquals(button.right, start.box.right, 0.5f)
        val end = morphFrame(1f, button, menu)
        assertEquals(1f, end.scaleX, 1e-4f)
        assertEquals(1f, end.scaleY, 1e-4f)
        assertEquals(0f, end.translationX, 0.5f)
        assertEquals(0f, end.translationY, 0.5f)
    }

    @Test
    fun theEdgeByTheButtonStaysWithItWhileTheFarEdgeTravels() {
        for (i in 0..10) {
            val f = morphFrame(i / 10f, button, menu)
            val expectedRight = button.right + (menu.right - button.right) * (i / 10f)
            assertEquals(expectedRight, f.box.right, 0.5f)
        }
        assertTrue(morphFrame(0.5f, button, menu).box.left < button.left)
    }

    @Test
    fun itStretchesMidFlightAndNotAtTheEnds() {
        val mid = morphFrame(0.5f, button, menu)
        val plainWidth = button.width + (menu.width - button.width) * 0.5f
        assertTrue(mid.box.width > plainWidth)
        assertEquals(menu.width, morphFrame(1f, button, menu).box.width, 0.5f)
    }

    @Test
    fun rowsFadeInThroughTheGrowthsMiddleStretch() {
        // Glasmio's content curve — ((progress − 0.12) / 0.64) — starts early,
        // inside the growth's own first stretch, and is sharp by 0.76.
        assertEquals(0f, morphFrame(0.1f, button, menu).contentAlpha, 0f)
        assertEquals(MORPH_CONTENT_BLUR_DP, morphFrame(0.1f, button, menu).contentBlurDp, 0f)
        assertEquals(MORPH_CONTENT_SCALE, morphFrame(0.1f, button, menu).contentScale, 1e-4f)
        val mid = morphFrame(0.5f, button, menu)
        assertTrue(mid.contentAlpha in 0.1f..0.9f)
        val sharp = morphFrame(0.76f, button, menu)
        assertEquals(1f, sharp.contentAlpha, 0f)
        assertEquals(0f, sharp.contentBlurDp, 0f)
        assertEquals(1f, sharp.contentScale, 1e-4f)
        val end = morphFrame(1f, button, menu)
        assertEquals(1f, end.contentAlpha, 0f)
    }

    @Test
    fun aSpringSettleSlightlyPastOneStaysSane() {
        val past = morphFrame(1.05f, button, menu)
        assertTrue(past.scaleX in 1f..1.2f)
        assertEquals(1f, past.contentAlpha, 0f)
    }

    @Test
    fun aDialogWithNoButtonGrowsFromTheCentre() {
        val screen = MorphBox(0f, 0f, 1080f, 2400f)
        val src = centreSource(screen, 120f)
        assertEquals(540f, src.left + src.width / 2f, 0.01f)
        assertEquals(1200f, src.top + src.height / 2f, 0.01f)
    }
}
