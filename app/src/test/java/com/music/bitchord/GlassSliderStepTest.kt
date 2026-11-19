package com.music.bitchord

import com.music.bitchord.ui.components.snapToSteps
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassSliderStepTest {
    @Test
    fun continuousValuesOnlyClamp() {
        assertEquals(0.37f, snapToSteps(0.37f, 0f..1f, 0), 1e-6f)
        assertEquals(1f, snapToSteps(1.4f, 0f..1f, 0), 1e-6f)
    }

    @Test
    fun steppedValuesLandOnTheMaterialStops() {
        assertEquals(0.5f, snapToSteps(0.46f, 0f..1f, 1), 1e-6f)
        assertEquals(4f, snapToSteps(3.6f, 0f..10f, 9), 1e-6f)
        assertEquals(-12f, snapToSteps(-13f, -12f..12f, 23), 1e-6f)
    }
}
