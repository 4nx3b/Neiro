/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */
package com.music.bitchord

import com.music.bitchord.ui.components.floatingtabbar.PillGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PillGeometryTest {

    @Test
    fun restWidthAtRest() {
        assertEquals(80f, PillGeometry.liveWidth(80f, 120f, 0f, 0f), 1e-4f)
    }

    @Test
    fun liftedWidthWhenFullyLifted() {
        assertEquals(120f, PillGeometry.liveWidth(80f, 120f, 1f, 0f), 1e-4f)
    }

    @Test
    fun flatNeverExceedsRest() {
        assertEquals(80f, PillGeometry.visibleWidth(80f, 120f, 1f, 0.12f, flat = true), 1e-4f)
        assertTrue(PillGeometry.visibleWidth(80f, 120f, 1f, 0.12f, flat = false) > 120f)
    }
}
