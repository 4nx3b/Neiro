/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */
package com.music.bitchord

import com.music.bitchord.data.lyrics.LyricLine
import com.music.bitchord.data.lyrics.LyricWord
import com.music.bitchord.ui.player.toArchiveTuneLrc
import moe.rukamori.archivetune.lyrics.LyricsUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ArchiveTuneLrcRoundTripTest {

    private val original = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    private val lines = listOf(
        LyricLine(
            timeMs = 12_340L,
            text = "hello there world",
            words = listOf(
                LyricWord(startMs = 12_340L, endMs = 12_900L, text = "hello"),
                LyricWord(startMs = 12_900L, endMs = 13_500L, text = "there"),
                LyricWord(startMs = 13_500L, endMs = 14_200L, text = "world"),
            ),
        ),
        LyricLine(
            timeMs = 65_000L,
            text = "second line",
            words = listOf(
                LyricWord(startMs = 65_000L, endMs = 65_600L, text = "second"),
                LyricWord(startMs = 65_600L, endMs = 66_400L, text = "line"),
            ),
        ),
    )

    private fun assertRoundTrip() {
        val lrc = lines.toArchiveTuneLrc()
        assertTrue(lrc, LyricsUtils.isLineSyncedLrc(lrc))
        assertTrue(lrc, LyricsUtils.hasWordSyncedLyrics(lrc))
        val entries = LyricsUtils.parseLyrics(lrc).filter { it.text.isNotBlank() }
        assertEquals(lrc, 2, entries.size)
        val first = entries[0]
        assertEquals(12_340L, first.time)
        assertFalse(first.text, first.text.contains('<') || first.text.contains('['))
        val words = first.words.orEmpty()
        assertEquals(lrc, listOf("hello", "there", "world"), words.map { it.text.trim() })
        assertEquals(12.34, words[0].startTime, 0.011)
        assertEquals(12.9, words[1].startTime, 0.011)
        assertTrue(words[0].endTime > words[0].startTime)
        assertEquals(listOf("second", "line"), entries[1].words.orEmpty().map { it.text.trim() })
    }

    @Test
    fun wordTimingsSurviveTheBridgeInTheDefaultLocale() {
        Locale.setDefault(Locale.US)
        assertRoundTrip()
    }

    @Test
    fun wordTimingsSurviveTheBridgeInANonLatinDigitLocale() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))
        assertRoundTrip()
    }
}
