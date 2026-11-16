package com.music.bitchord

import moe.rukamori.archivetune.bridge.ArchiveTuneSheetHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mini player's pull reports velocity negative upwards; the ported
 * sheet's performFling expands for a positive velocity. A flick up must open
 * the ported player, a flick down must close it.
 */
class ArchiveTuneSheetHandleTest {
    @Test
    fun flickUpExpandsFlickDownCloses() {
        assertTrue(ArchiveTuneSheetHandle.flingVelocity(-1200f) > 250f)
        assertTrue(ArchiveTuneSheetHandle.flingVelocity(1200f) < -250f)
        assertEquals(0f, ArchiveTuneSheetHandle.flingVelocity(0f), 0f)
    }

    @Test
    fun anUnboundHandleIsInert() {
        val handle = ArchiveTuneSheetHandle()
        handle.expand()
        handle.drag(-40f)
        handle.release(-900f)
        handle.close()
        assertTrue(handle.offsetPx().isNaN())
        assertEquals(0f, handle.travelPx(), 0f)
    }
}
