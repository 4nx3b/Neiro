package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Lyrics from Muzo Data (data.kouzu.in), keyed on the YouTube video id.
 *
 * The same guarantee [SimpMusicLyrics] is kept in the enum for, at the
 * syllable grain LyricsPlus works at: every other offered provider matches on
 * a name and can hand back a different edit of the same song, while this one
 * is answering for the exact video that is playing. Its syllable runs carry
 * word-internal timing — the sweep lights "e" before "nough" — and the word
 * boundary is the run's own trailing space, exactly the spacing convention
 * [wordsFromRuns] was written to read.
 *
 * The key is pushed in from the app's settings rather than read here — the
 * shared module cannot see the app's [AppSettings][com.music.bitchord.data.settings.AppSettings],
 * and threading a key through [LyricsRepository]'s fixed (videoId, title,
 * artist) fan-out would mean widening every provider's signature for one
 * provider's sake. PaxSenix solved this first with a pushed-in credential, so
 * Muzo follows it: the app calls [setApiKey] on load and on every change, and
 * this object reads the volatile at request time.
 *
 * PORT: keyless lyrics were also live when this was written — a 401 today is
 * still a plain miss, so the chain moves on either way.
 */
object MuzoLyrics {

    private const val BASE = "https://data.kouzu.in/api/v1/lyrics"

    /** Pushed in by the app; see the class note for why it is not a parameter. */
    @Volatile
    private var apiKey: String = ""

    fun setApiKey(value: String) {
        apiKey = value.trim()
    }

    suspend fun lyrics(videoId: String, durationMs: Long): List<LyricLine>? =
        withContext(Dispatchers.IO) {
            if (videoId.isBlank()) return@withContext null
            val url = BASE.toHttpUrl().newBuilder()
                .addQueryParameter("id", videoId)
                .build()
            // The key is sent whenever one is set — the official documentation
            // wants it here — and a refusal either way is simply a miss the
            // chain can step over, not an error worth colouring red.
            val body = apiKey.takeIf(String::isNotBlank)
                ?.let { lyricsGetBearer(url.toString(), it) }
                ?: lyricsGet(url.toString())
                ?: return@withContext null
            val response = runCatching { lyricsJson.decodeFromString<Response>(body) }.getOrNull()
            if (response == null || response.isEmpty) return@withContext null
            parse(response).takeIf { it.isNotEmpty() }
        }

    /**
     * One response's lines, syllable runs glued back into words and the
     * instrumental stretches marked, after the LyricsPlus mapping.
     */
    fun parse(response: Response): List<LyricLine> {
        val sung = response.lines.orEmpty().mapNotNull { line ->
            val start = line.atMs ?: return@mapNotNull null
            // A run's own trailing space is its word boundary — the exact
            // spacing these runs are emitted with — so the glue is trusted
            // even where a whole line is one long split word.
            val words = wordsFromRuns(
                line.syllables.orEmpty().mapNotNull { syllable ->
                    val text = syllable.text ?: return@mapNotNull null
                    val at = syllable.atMs ?: return@mapNotNull null
                    TimedRun(at, at + (syllable.durationMs ?: 0L), text)
                },
                spacingIsExplicit = true,
            )
            val until = line.durationMs?.takeIf { it > 0 }?.let { start + it }
            when {
                words.isNotEmpty() -> LyricLine(
                    timeMs = minOf(start, words.first().startMs),
                    text = words.joinToString(" ") { it.text },
                    words = words,
                    sungUntilMs = until,
                )
                // A line-synced answer from here is no better than LRCLIB's,
                // but it is still better than nothing — same call as the
                // SimpMusic mapping makes on its plain LRC.
                !line.text.isNullOrBlank() -> LyricLine(
                    timeMs = start,
                    text = line.text.trim(),
                    sungUntilMs = until,
                )
                else -> null
            }
        }.sortedBy { it.timeMs }
        return sung.withInstrumentalGaps()
    }

    @Serializable
    data class Response(
        val id: String? = null,
        val videoId: String? = null,
        val name: String? = null,
        val artist: String? = null,
        val provider: String? = null,
        val synced: Boolean? = null,
        val isEmpty: Boolean = false,
        val hasWordSync: Boolean? = null,
        val plain: String? = null,
        val lines: List<Line>? = null,
    )

    @Serializable
    data class Line(
        val atMs: Long? = null,
        val durationMs: Long? = null,
        val text: String? = null,
        val syllables: List<Syllable>? = null,
    )

    @Serializable
    data class Syllable(
        val atMs: Long? = null,
        val durationMs: Long? = null,
        /** As emitted: carries the trailing space that ends its word. */
        val text: String? = null,
    )
}
