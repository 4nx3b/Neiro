/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */
package com.music.bitchord

import com.music.bitchord.data.settings.LiquidGlassUpstream
import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassUpstreamTest {

    @Test
    fun defaultsMatchTheUpstreamSources() {
        assertEquals(0.5f, LiquidGlassUpstream.CORNER_RADIUS_DEFAULT, 0f)
        assertEquals(0f, LiquidGlassUpstream.CHROMATIC_AMOUNT_DEFAULT, 0f)
        assertEquals(0.13f, LiquidGlassUpstream.CLARITY_DEFAULT, 0f)
        assertEquals(1f, LiquidGlassUpstream.SATURATION_DEFAULT, 0f)
        assertEquals(0.36f, LiquidGlassUpstream.REFRACTION_WIDTH_DEFAULT, 0f)
        assertEquals(0f, LiquidGlassUpstream.RAINBOW_DEFAULT, 0f)
        assertEquals(1f, LiquidGlassUpstream.RIM_DEFAULT, 0f)
        assertEquals(1f, LiquidGlassUpstream.QUALITY_DEFAULT, 0f)
    }

    @Test
    fun theDefaultsLeaveTodaysGlassUnchanged() {
        assertEquals(1f, LiquidGlassUpstream.cornerFactor(LiquidGlassUpstream.CORNER_RADIUS_DEFAULT), 1e-6f)
        assertEquals(2f, LiquidGlassUpstream.cornerFactor(1f), 1e-6f)
        assertEquals(0f, LiquidGlassUpstream.cornerFactor(0f), 1e-6f)
        assertEquals(1f, LiquidGlassUpstream.clarityBlurFactor(LiquidGlassUpstream.CLARITY_DEFAULT), 1e-6f)
        assertEquals(1f, LiquidGlassUpstream.clarityTintFactor(LiquidGlassUpstream.CLARITY_DEFAULT), 1e-6f)
        assertEquals(1f, LiquidGlassUpstream.clarityResolutionFactor(LiquidGlassUpstream.CLARITY_DEFAULT), 1e-6f)
        assertEquals(1f, LiquidGlassUpstream.refractionWidthFactor(LiquidGlassUpstream.REFRACTION_WIDTH_DEFAULT), 1e-6f)
        assertEquals(
            1f,
            LiquidGlassUpstream.dispersionStrength(
                LiquidGlassUpstream.CHROMATIC_AMOUNT_DEFAULT,
                LiquidGlassUpstream.RAINBOW_DEFAULT,
            ),
            1e-6f,
        )
    }

    @Test
    fun zenithClarityCurveKeepsItsAnchors() {
        assertEquals(0.12f, LiquidGlassUpstream.clarityTint(0f), 1e-6f)
        assertEquals(0.30f, LiquidGlassUpstream.clarityTint(0.5f), 1e-6f)
        assertEquals(0.46f, LiquidGlassUpstream.clarityTint(1f), 1e-6f)
        assertEquals(1f, LiquidGlassUpstream.clarityBlur(0.5f), 1e-6f)
        assertEquals(1.35f, LiquidGlassUpstream.clarityBlur(1f), 1e-6f)
    }
}
