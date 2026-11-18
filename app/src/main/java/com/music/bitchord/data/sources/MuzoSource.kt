package com.music.bitchord.data.sources

import com.music.bitchord.data.TrackLog
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "BitChord"

/**
 * What the Check API call came back with, as the Sources screen words it.
 *
 * The numbers ride separately from [SourceHealth] because that type carries a
 * finished sentence, written in code and shown verbatim on the source row —
 * the same as Tidal's session line — while the Muzo Data group formats these
 * through its own localized strings. [Ok] is the one case with anything to
 * format; every other state is a reason.
 */
sealed interface MuzoApiCheck {
    data class Ok(val usageToday: Int, val dailyLimit: Int, val streamLimit: String?) : MuzoApiCheck

    /** The key was refused: 401/403 territory. Not worth retrying with the same key. */
    data class Rejected(val reason: String) : MuzoApiCheck

    /** The server could not be reached at all. Worth retrying later. */
    data class Unreachable(val reason: String) : MuzoApiCheck

    /**
     * The same state as a [SourceHealth], for the rows that show it verbatim —
     * the sources screen's check writes its result into the row health map so
     * the numbered list and the Muzo Data group tell one story.
     */
    fun asSourceHealth(): SourceHealth = when (this) {
        is Ok -> SourceHealth.Ok(MuzoService.usageDetail(this))
        is Rejected -> SourceHealth.Rejected(reason)
        is Unreachable -> SourceHealth.Unreachable(reason)
    }
}

/**
 * The wire protocol of data.kouzu.in (Muzo Data): one API key, one video id,
 * and the three answers that key buys.
 *
 * Auth is a single header — `X-API-Key` — on every JSON endpoint; the one
 * route that never wants it is the canvas video stream, which lives in the
 * canvas resolver instead of here. Everything a call may fail with is folded
 * into a null or a [MuzoApiCheck] state rather than thrown, because every
 * caller of this object is already mid-walk through other sources and a miss
 * is the one failure mode they know how to step over.
 */
internal object MuzoService {

    private const val BASE = "https://data.kouzu.in/api/v1"

    /** The one sentence a keyless state says — also the health reason for it. */
    const val NO_KEY_PROMPT = "API key required"

    private const val STREAMING_UNLIMITED = "unlimited"

    /** The service's own documented search ceiling. */
    private const val MAX_LIMIT = 50

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Tighter than the shared client's stream-oriented timeouts, after
    // TidalService: this sits inside the resolver's walk and the sources
    // screen's probe, both of which want an answer in seconds.
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .build()

    /** One GET done, or null for any failure at all — the lyric clients' convention. */
    private fun fetch(path: String, key: String): Pair<Int, String?>? = runCatching {
        val request = Request.Builder()
            .url("$BASE$path")
            .header("Accept", "application/json")
            .header("X-API-Key", key)
            .build()
        client.newCall(request).execute().use { response ->
            response.code to response.body?.string()?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    /** The one error string a refused answer carries, when it says nothing better. */
    private fun reasonOf(code: Int, body: String?): String =
        body?.let { runCatching { json.decodeFromString<ErrorAnswer>(it).error }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?: "HTTP $code"

    /** Query-component encoding without the `+`-for-space behaviour URLEncoder adds. */
    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    /** The key set in Settings, or null while Muzo Data is unconfigured. */
    fun apiKey(): String? = AppSettings.muzoApiKey.value.takeIf { it.isNotBlank() }

    /**
     * Asks `/api/v1/user/usage` what today looks like — the one question the
     * Check API row asks, and the one a healthy key answers with numbers.
     */
    suspend fun checkApi(key: String): MuzoApiCheck = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext MuzoApiCheck.Rejected(NO_KEY_PROMPT)
        val (code, body) = fetch("/user/usage", key)
            ?: return@withContext MuzoApiCheck.Unreachable("Failed")
        when {
            code == 200 -> {
                val usage = body?.let {
                    runCatching { json.decodeFromString<UsageAnswer>(it) }.getOrNull()
                }
                // An unparseable success is still a success as far as the key
                // is concerned; reported without the numbers it declined to
                // state rather than as a refusal it never made.
                usage ?: return@withContext MuzoApiCheck.Ok(0, 0, null)
                MuzoApiCheck.Ok(
                    usageToday = usage.usageToday ?: 0,
                    dailyLimit = usage.dailyLimit ?: 0,
                    streamLimit = usage.streamLimit?.takeIf { it.isNotBlank() },
                )
            }
            // 401 is the documented refusal; 403 kept in case the service
            // front-door decides to word a bad key that way one day.
            code == 401 || code == 403 -> MuzoApiCheck.Rejected(reasonOf(code, body))
            else -> MuzoApiCheck.Unreachable(reasonOf(code, body))
        }
    }

    /** `/api/v1/ytm/search`, one row per video the catalogue holds FLAC for. */
    suspend fun searchCatalog(key: String, query: String, limit: Int): List<Row> =
        withContext(Dispatchers.IO) {
            if (key.isBlank() || query.isBlank()) return@withContext emptyList()
            val path = "/ytm/search?q=${encode(query)}&filter=songs&limit=${limit.coerceIn(1, MAX_LIMIT)}"
            val (code, body) = fetch(path, key) ?: return@withContext emptyList()
            if (code != 200) return@withContext emptyList()
            val answer = body?.let {
                runCatching { json.decodeFromString<SearchAnswer>(it) }.getOrNull()
            } ?: return@withContext emptyList()
            answer.items.orEmpty().filter { it.videoId.isNotBlank() && !it.title.isNullOrBlank() }
        }

    /** `/api/v1/stream/lossless` for one video id: the CDN url the player opens. */
    suspend fun lossless(key: String, videoId: String): LosslessAnswer? =
        withContext(Dispatchers.IO) {
            if (key.isBlank() || videoId.isBlank()) return@withContext null
            val (code, body) = fetch("/stream/lossless?id=${encode(videoId)}", key)
                ?: return@withContext null
            if (code != 200) return@withContext null
            val answer = body?.let {
                runCatching { json.decodeFromString<LosslessAnswer>(it) }.getOrNull()
            } ?: return@withContext null
            // The documented answer is `url` plus a `urls` mirror list; taking
            // either keeps a working answer when one of the two shapes drifts.
            val url = answer.url?.takeIf { it.isNotBlank() }
                ?: answer.urls?.firstOrNull { !it.isNullOrBlank() }
            url?.let { answer.copy(url = it) }
        }

    /**
     * The sentence the source row shows for a working key: the daily request
     * count and the streaming tier, in the shape the account rows use.
     */
    fun usageDetail(check: MuzoApiCheck.Ok): String =
        "${check.usageToday}/${check.dailyLimit} requests today · streaming ${check.streamLimit ?: STREAMING_UNLIMITED}"

    @Serializable
    private data class ErrorAnswer(val error: String? = null)

    @Serializable
    private data class UsageAnswer(
        val status: String? = null,
        val usageToday: Int? = null,
        val dailyLimit: Int? = null,
        val remainingToday: Int? = null,
        val streamLimit: String? = null,
    )

    @Serializable
    private data class SearchAnswer(
        val query: String? = null,
        val filter: String? = null,
        val count: Int? = null,
        val items: List<Row>? = null,
    )

    @Serializable
    data class Row(
        val type: String? = null,
        val videoId: String = "",
        val title: String? = null,
        val artists: List<Artist> = emptyList(),
        /** Seconds or "M:SS" — whichever the service sends. See [MuzoSource.durationTextOf]. */
        val duration: JsonElement? = null,
        val thumbnails: List<Thumbnail> = emptyList(),
    )

    @Serializable
    data class Artist(
        val name: String = "",
        val id: String? = null,
    )

    @Serializable
    data class Thumbnail(val url: String? = null)

    @Serializable
    data class LosslessAnswer(
        val id: String? = null,
        val format: String? = null,
        val quality: String? = null,
        val cdnBaseUrl: String? = null,
        val cdnInstances: List<String>? = null,
        val url: String? = null,
        val urls: List<String>? = null,
    )
}

/**
 * Muzo Data (data.kouzu.in) as [MusicSource] — one video id, answered with
 * lossless FLAC.
 *
 * An API key the listener pastes (Sources screen → Muzo Data) rather than an
 * account they sign into: [config.baseUrl] stays empty and the key lives in
 * [AppSettings.muzoApiKey], read live at each call so a key typed into the
 * editor takes effect on the next request without rebuilding anything.
 *
 * The id is the whole contract. Every other source here matches by a name and
 * can therefore hand back a different cut of the same song; this one is asked
 * for a video id and answers for that exact recording, which is also why its
 * answer is worth a FLAC — a lossless file for the wrong edit is worthless,
 * and here the edit cannot be wrong.
 *
 * Without a key the source is inert by design rather than broken: [health]
 * says the credential is missing, [search] answers nothing, [stream] is a miss
 * — the resolver steps over it to the next source. The same shape as a
 * signed-out Tidal: the opt-in is the credential itself, not the row's switch.
 */
class MuzoSource(
    override val config: SourceConfig,
) : MusicSource, SourceRegistry.ConfigBacked {

    override val configId: String get() = config.id
    override val kind: SourceKind get() = SourceKind.MUZO
    override val displayName: String get() = config.label.ifBlank { SourceKind.MUZO.label }

    override suspend fun health(): SourceHealth {
        val key = MuzoService.apiKey() ?: return SourceHealth.Rejected(MuzoService.NO_KEY_PROMPT)
        return MuzoService.checkApi(key).asSourceHealth()
    }

    /**
     * [waitForAll] and [request] are moot: one endpoint, one catalogue, and
     * every row in it is the lossless rendition — there is no tier to describe
     * a search at and no backend to wait for.
     */
    override suspend fun search(
        query: String,
        limit: Int,
        waitForAll: Boolean,
        request: StreamRequest?,
    ): List<Song> {
        val key = MuzoService.apiKey() ?: return emptyList()
        val rows = MuzoService.searchCatalog(key, query, limit)
        TrackLog.d(TAG, "Muzo search \"${query.take(48)}\" → ${rows.size} rows")
        return rows.map { row -> row.toSong() }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        val key = MuzoService.apiKey() ?: return null
        // The one rendition this catalogue holds is the 24-bit FLAC, and a
        // capped request is the one caller that cannot pay for it — answering
        // a lossless file to a metered connection would spend the very bytes
        // the cap exists to save. A miss lets the resolver step on to a source
        // with a real transcode to offer.
        if (request is StreamRequest.Capped) return null
        val answer = MuzoService.lossless(key, trackId) ?: return null
        // [MuzoService.lossless] only ever answers with the URL settled — one
        // of the two wire spellings (`url`, `urls[]`) made it through that
        // check — but the decoder's own type still says nullable, so the
        // proof the compiler wants is restated here rather than assumed.
        val url = answer.url ?: return null
        TrackLog.d(TAG, "Muzo stream for $trackId: ${answer.format ?: "flac"} ${url.take(72)}")
        return SourceStream(
            url = url,
            format = StreamFormat(
                codec = answer.format?.lowercase() ?: "flac",
                // The only tier the service names is `lossless_24bit`; stated
                // rather than assumed, so the format line can only ever claim
                // what the answer actually said.
                bitDepth = answer.quality?.takeIf { it.contains("24") }?.let { 24 },
            ),
            // The CDN binds its file URLs to the key they were minted for —
            // this header travels with the fetch and is what the player's data
            // source sends. See [SourceStream.headers].
            headers = mapOf("Authorization" to "Bearer $key"),
        )
    }

    private fun MuzoService.Row.toSong(): Song = Song(
        videoId = SourceRegistry.trackKey(config.id, videoId),
        // Blank titles are filtered out before the rows get here, so this can
        // only be empty in a decode the filter already declined.
        title = title.orEmpty(),
        artist = artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" },
        thumbnailUrl = thumbnails.lastOrNull { !it.url.isNullOrBlank() }?.url,
        durationText = durationTextOf(duration),
        // Everything this catalogue serves is lossless, and its lossless is the
        // 24-bit tier — the same two facts every lossless row from the account
        // sources states, so the picker ranks them on equal terms.
        sourceQuality = "LOSSLESS",
        sourceHiRes = true,
    )

    /** A search row's `duration`, which the service sends as seconds or as "M:SS". */
    private fun durationTextOf(element: JsonElement?): String? {
        val primitive = element as? JsonPrimitive ?: return null
        return when {
            primitive.isString -> primitive.content.trim().takeIf { it.isNotEmpty() }
            else -> primitive.longOrNull?.takeIf { it > 0 }?.let { seconds ->
                String.format("%d:%02d", seconds / 60, seconds % 60)
            }
        }
    }
}
