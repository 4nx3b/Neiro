package com.music.bitchord

import com.music.bitchord.ui.components.MORPH_CONTENT_BLUR_DP
import com.music.bitchord.ui.components.MORPH_CONTENT_SCALE
import com.music.bitchord.ui.components.MORPH_DAMPING
import com.music.bitchord.ui.components.MORPH_STIFFNESS
import com.music.bitchord.ui.components.MorphBox
import com.music.bitchord.ui.components.centreSource
import com.music.bitchord.ui.components.leadAxisShare
import com.music.bitchord.ui.components.morphFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class GlassMorphTest {
    private val capsule = MorphBox(left = 1053f, top = 150f, width = 265f, height = 110f)
    private val menu = MorphBox(left = 683f, top = 150f, width = 635f, height = 430f)

    private fun spring(tMs: Float): Float {
        val w = sqrt(MORPH_STIFFNESS)
        val z = MORPH_DAMPING
        val t = tMs / 1000f
        val wd = w * sqrt(1f - z * z)
        return 1f - exp(-z * w * t) * (cos(wd * t) + z / sqrt(1f - z * z) * sin(wd * t))
    }

    @Test
    fun theSpringFollowsTheMeasuredWidthFrames() {
        val measured = listOf(47f to 0.43f, 80f to 0.77f, 113f to 0.90f, 147f to 0.96f, 213f to 1.0f)
        measured.forEach { (t, share) -> assertEquals("t=$t", share, spring(t), 0.05f) }
    }

    @Test
    fun theBlobGrowsDownBeforeItWidens() {
        assertEquals(0.36f, leadAxisShare(0.43f), 1e-3f)
        assertTrue(leadAxisShare(0.77f) >= 1f)
        val mid = morphFrame(0.77f, capsule, menu)
        val proportional = capsule.height + (menu.height - capsule.height) * 0.77f
        assertTrue(mid.box.height > proportional)
    }

    @Test
    fun startsOnTheCapsuleAndEndsOnTheMenu() {
        val start = morphFrame(0f, capsule, menu)
        assertEquals(capsule.width, start.box.width, 0.5f)
        assertEquals(capsule.right, start.box.right, 0.5f)
        assertEquals(capsule.top, start.box.top, 0.5f)
        val end = morphFrame(1f, capsule, menu)
        assertEquals(1f, end.scaleX, 1e-4f)
        assertEquals(1f, end.scaleY, 1e-4f)
        assertEquals(0f, end.translationX, 0.5f)
        assertEquals(0f, end.translationY, 0.5f)
    }

    @Test
    fun theTrailingEdgeStaysByTheButton() {
        for (i in 0..10) {
            assertEquals(capsule.right, morphFrame(i / 10f, capsule, menu).box.right, 0.5f)
        }
    }

    @Test
    fun rowsComeInOversizedAndBlurredOnlyOnceTheGlassHasMostlyGrown() {
        assertEquals(0f, morphFrame(0.43f, capsule, menu).contentAlpha, 0f)
        assertEquals(MORPH_CONTENT_BLUR_DP, morphFrame(0.4f, capsule, menu).contentBlurDp, 0f)
        assertEquals(MORPH_CONTENT_SCALE, morphFrame(0.4f, capsule, menu).contentScale, 1e-4f)
        val visible = morphFrame(0.77f, capsule, menu)
        assertTrue(visible.contentAlpha in 0.4f..0.8f)
        val sharp = morphFrame(0.96f, capsule, menu)
        assertEquals(1f, sharp.contentAlpha, 0f)
        assertEquals(0f, sharp.contentBlurDp, 0f)
        assertEquals(1f, sharp.contentScale, 1e-4f)
    }

    @Test
    fun aDialogWithNoButtonGrowsFromTheCentre() {
        val screen = MorphBox(0f, 0f, 1080f, 2400f)
        val src = centreSource(screen, 120f)
        assertEquals(540f, src.left + src.width / 2f, 0.01f)
        assertEquals(1200f, src.top + src.height / 2f, 0.01f)
    }
}
