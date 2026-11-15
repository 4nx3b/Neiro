package com.music.bitchord

import androidx.compose.ui.geometry.Rect
import moe.rukamori.archivetune.bridge.driftRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ported styles' drift collapse follows the app's own sleeve path: it
 * starts on the open slot, lands on the mini cover, moves linearly across,
 * and comes down a curve that never passes the cover.
 */
class DriftDockTest {
    // A phone-ish layout: the open artwork high on the page, the mini cover
    // near the bottom; the sheet travels 1800 px.
    private val open = Rect(left = 64f, top = 320f, right = 1016f, bottom = 1272f)
    private val mini = Rect(left = 40f, top = 2080f, right = 184f, bottom = 2224f)
    private val travel = 1800f

    private fun assertRect(expected: Rect, actual: Rect) {
        assertEquals(expected.left, actual.left, 0.01f)
        assertEquals(expected.top, actual.top, 0.01f)
        assertEquals(expected.right, actual.right, 0.01f)
        assertEquals(expected.bottom, actual.bottom, 0.01f)
    }

    @Test
    fun endpointsAreTheTwoSlots() {
        assertRect(open, driftRect(mini, open, 1f, travel))
        assertRect(mini, driftRect(mini, open, 0f, travel))
    }

    @Test
    fun xIsLinearInT() {
        for (i in 0..20) {
            val t = i / 20f
            val expected = mini.left + (open.left - mini.left) * t
            assertEquals(expected, driftRect(mini, open, t, travel).left, 0.01f)
        }
    }

    @Test
    fun yIsMonotonicAndNeverPassesTheCover() {
        var previous = Float.NEGATIVE_INFINITY
        for (i in 0..100) {
            // Walking from open (t = 1) down to collapsed (t = 0): y only grows.
            val t = 1f - i / 100f
            val top = driftRect(mini, open, t, travel).top
            assertTrue("y went back up at t=$t", top >= previous - 0.01f)
            assertTrue("passed the cover at t=$t", top <= mini.top + 0.01f)
            previous = top
        }
    }

    @Test
    fun landsTangentially() {
        // The last stretch before the cover covers far less height than a
        // straight line would: the speed runs down to nothing on landing.
        val curved = mini.top - driftRect(mini, open, 0.05f, travel).top
        val straight = (mini.top - open.top) * 0.05f
        assertTrue(curved < straight * 0.2f)
    }

    @Test
    fun outOfRangeTIsClamped() {
        assertRect(open, driftRect(mini, open, 1.5f, travel))
        assertRect(mini, driftRect(mini, open, -0.5f, travel))
    }
}
