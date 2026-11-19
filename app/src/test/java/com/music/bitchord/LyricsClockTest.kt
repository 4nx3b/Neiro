package com.music.bitchord

import moe.rukamori.archivetune.bridge.LyricsClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The ported lyrics' position feed: lyrics-ui restarts its scroll on any
 * backward step, so the feed must never step back during playback — only
 * on a real seek — and must still converge on the player's position.
 */
class LyricsClockTest {
    private val frame = 1000.0 / 60.0

    @Test
    fun neverStepsBackWhenThePlayerIsCorrectedBackwards() {
        var clock = 10_000.0
        var raw = 10_000.0
        repeat(600) { i ->
            raw += frame
            // A session update pulls the extrapolated position back by 150 ms every second.
            if (i % 60 == 30) raw -= 150.0
            val next = LyricsClock.next(clock, raw, frame)
            assertTrue("stepped back at frame $i", next >= clock)
            clock = next
        }
    }

    @Test
    fun convergesOnThePlayer() {
        var clock = 5_000.0
        var raw = 5_300.0
        repeat(120) {
            raw += frame
            clock = LyricsClock.next(clock, raw, frame)
        }
        assertTrue(abs(raw - clock) < 5.0)
        // Behind too: the player 200 ms behind the clock.
        clock = 5_000.0
        raw = 4_800.0
        repeat(240) {
            raw += frame
            clock = LyricsClock.next(clock, raw, frame)
        }
        assertTrue(abs(raw - clock) < 5.0)
    }

    @Test
    fun aRealSeekPassesThrough() {
        assertEquals(2_000.0, LyricsClock.next(60_000.0, 2_000.0, frame), 0.0)
        assertEquals(90_000.0, LyricsClock.next(60_000.0, 90_000.0, frame), 0.0)
    }

    @Test
    fun aSmallForwardErrorIsEasedNotJumped() {
        val next = LyricsClock.next(1_000.0, 1_200.0, frame)
        assertTrue(next > 1_000.0 + frame)
        assertTrue(next < 1_200.0)
    }
}
