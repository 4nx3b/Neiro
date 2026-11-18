package com.music.bitchord

import com.music.bitchord.data.lyrics.MuzoLyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Muzo Data lyrics mapping, tested against the shape its site documents
 * rather than against a live call — the syllable runs are the part worth
 * pinning, because their trailing spaces are the only thing saying where one
 * word stops, and losing that turns "e" + "nough" into two words the karaoke
 * sweep would light one at a time.
 */
class MuzoLyricsTest {

    /** The documented response shape, trimmed to two lines and one split word. */
    private val response = MuzoLyrics.Response(
        isEmpty = false,
        hasWordSync = true,
        lines = listOf(
            MuzoLyrics.Line(
                atMs = 15_660,
                durationMs = 990,
                text = "No smoke with no fire",
                syllables = listOf(
                    MuzoLyrics.Syllable(atMs = 15_660, durationMs = 84, text = "No "),
                    MuzoLyrics.Syllable(atMs = 15_744, durationMs = 990, text = "smoke "),
                    MuzoLyrics.Syllable(atMs = 16_734, durationMs = 50, text = "with "),
                    MuzoLyrics.Syllable(atMs = 16_784, durationMs = 866, text = "no "),
                    MuzoLyrics.Syllable(atMs = 17_650, durationMs = 0, text = "fire"),
                ),
            ),
            MuzoLyrics.Line(
                atMs = 20_000,
                durationMs = 500,
                text = "e nough",
                syllables = listOf(
                    MuzoLyrics.Syllable(atMs = 20_000, durationMs = 99, text = "e"),
                    MuzoLyrics.Syllable(atMs = 20_099, durationMs = 401, text = "nough "),
                ),
            ),
        ),
    )

    @Test
    fun `a run's trailing space is the word boundary`() {
        // The intro before the first stamp comes back as an instrumental gap,
        // same as every other word-timed source here.
        val lines = MuzoLyrics.parse(response).filterNot { it.isGap }
        assertEquals(listOf("No smoke with no fire", "enough"), lines.map { it.text })
        assertEquals(
            listOf("No", "smoke", "with", "no", "fire"),
            lines[0].words.map { it.text },
        )
        // Both lines say when their singing ends, which is what tells an
        // interlude apart from a slowly sung line.
        assertEquals(16_650L, lines[0].sungUntilMs)
        assertEquals(20_500L, lines[1].sungUntilMs)
    }

    @Test
    fun `syllables of one word keep their own timing`() {
        val word = MuzoLyrics.parse(response).filterNot { it.isGap }[1].words.single()
        assertEquals("enough", word.text)
        // Two runs without a boundary glue into one word whose sweep follows
        // the syllables rather than the whole word at once.
        assertEquals(2, word.syllables.size)
        assertEquals(20_000L, word.syllables[0].startMs)
        assertEquals(20_099L, word.syllables[0].endMs)
        assertEquals(20_099L, word.syllables[1].startMs)
        assertEquals(20_500L, word.syllables[1].endMs)
    }

    @Test
    fun `a line with no syllables is still a timed line`() {
        val lines = MuzoLyrics.parse(
            MuzoLyrics.Response(
                lines = listOf(MuzoLyrics.Line(atMs = 1_000, durationMs = 700, text = "  sung as one line ")),
            ),
        )
        val line = lines.single()
        assertEquals("sung as one line", line.text)
        assertEquals(1_000L, line.timeMs)
        assertTrue(line.words.isEmpty())
    }

    @Test
    fun `a response with no lines is no lyrics`() {
        assertTrue(MuzoLyrics.parse(MuzoLyrics.Response(isEmpty = true)).isEmpty())
        assertTrue(MuzoLyrics.parse(MuzoLyrics.Response()).isEmpty())
    }
}
