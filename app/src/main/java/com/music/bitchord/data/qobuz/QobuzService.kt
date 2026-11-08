package com.music.bitchord.data.qobuz

import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object QobuzService {
    private const val TAG = "BitChord"

    const val FORMAT_ID_MP3_320 = 5
    const val FORMAT_ID_FLAC = 6
    const val FORMAT_ID_HI_RES = 7
    const val FORMAT_ID_MAX = 27

    private const val API_BASE = "https://www.qobuz.com/api.json/0.2"

    internal var apiBaseForTest: String? = null

    private fun apiBase(): String = apiBaseForTest ?: API_BASE
    private const val USER_AGENT = "Neiro-Android"

    private const val STREAM_CACHE_MS = 30 * 60 * 1000L
    private const val FAILURE_CACHE_MS = 10 * 60 * 1000L
    private const val SEARCH_LIMIT = 25

    data class Track(
        val trackId: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val durationSec: Long?,
        val thumbnailUrl: String?,
    )

    data class Stream(
        val url: String,
        val codec: String,
        val kbps: Int?,
        val sampleRateHz: Int?,
        val bitDepth: Int?,
        val isPreview: Boolean,
    )

    data class AccountStanding(
        val premium: Boolean,
    )

    private val streamCache = ConcurrentHashMap<String, Pair<Stream, Long>>()
    private val failureCache = ConcurrentHashMap<String, Long>()

    internal fun forgetCaches() {
        streamCache.clear()
        failureCache.clear()
    }

    fun requestSignature(
        trackId: String,
        formatId: Int,
        timestampSec: Long,
        appSecret: String,
    ): String = md5(
        "trackgetFileUrlformat_id${formatId}intentstreamtrack_id$trackId$timestampSec$appSecret",
    )

    private fun md5(input: String): String =
        java.security.MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun authedRequest(
        url: String,
        account: QobuzAccountStore.Account,
    ): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/json")
        .header("X-App-Id", account.appId)
        .header("X-User-Auth-Token", account.userAuthToken)
        .get()
        .build()

    suspend fun accountStanding(account: QobuzAccountStore.Account): AccountStanding? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = apiBase().toHttpUrl().newBuilder()
                    .addPathSegment("user")
                    .addPathSegment("get")
                    .addQueryParameter("app_id", account.appId)
                    .addQueryParameter("user_auth_token", account.userAuthToken)
                    .build()
                Http.client.newCall(authedRequest(url.toString(), account)).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    standingFromUserPayload(response.body?.string().orEmpty())
                }
            }.onFailure { TrackLog.w(TAG, "Qobuz account check failed: ${it.message}") }
                .getOrNull()
        }

    internal fun standingFromUserPayload(payload: String): AccountStanding? {
        val normalized = payload.lowercase(Locale.US)
        val valid = normalized.contains("\"id\"") || normalized.contains("credential")
        if (!valid) return null
        return AccountStanding(
            premium = PREMIUM_MARKERS.containsMatchIn(normalized),
        )
    }

    private val PREMIUM_MARKERS =
        Regex("""lossless|hi-res|hires|studio|sublime|"format_id"\s*:\s*(6|7|27)""")

    suspend fun searchTracks(
        account: QobuzAccountStore.Account,
        query: String,
        limit: Int = SEARCH_LIMIT,
    ): List<Track> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        runCatching {
            val url = apiBase().toHttpUrl().newBuilder()
                .addPathSegment("track")
                .addPathSegment("search")
                .addQueryParameter("query", trimmed)
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("app_id", account.appId)
                .build()
            Http.client.newCall(authedRequest(url.toString(), account)).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                parseSearchRows(response.body?.string().orEmpty())
            }
        }.onFailure { TrackLog.w(TAG, "Qobuz search failed: ${it.message}") }
            .getOrDefault(emptyList())
    }

    internal fun parseSearchRows(payload: String): List<Track> {
        val items = runCatching { JSONObject(payload) }.getOrNull()?.findTrackItems() ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val trackId = item.trackIdOrNull() ?: return@mapNotNull null
            val title = item.stringOrNull("title") ?: return@mapNotNull null
            Track(
                trackId = trackId,
                title = title,
                artist = item.optJSONObject("performer")?.stringOrNull("name")
                    ?: item.optJSONObject("album")?.optJSONObject("artist")?.stringOrNull("name"),
                album = item.optJSONObject("album")?.stringOrNull("title"),
                durationSec = item.longOrNull("duration"),
                thumbnailUrl = extractThumbnail(item.optJSONObject("album"), item),
            )
        }
    }

    suspend fun resolveStream(
        account: QobuzAccountStore.Account,
        trackId: String,
        formatId: Int,
    ): Stream? {
        if (trackId.isBlank()) return null
        val cacheKey = "id:$trackId:$formatId"
        val now = System.currentTimeMillis()
        streamCache[cacheKey]?.let { (stream, expiresAt) ->
            if (expiresAt > now) return stream
            streamCache.remove(cacheKey)
        }
        failureCache[cacheKey]?.let { failedUntil ->
            if (failedUntil > now) return null
            failureCache.remove(cacheKey)
        }
        val resolved = withContext(Dispatchers.IO) {
            runCatching {
                val timestampSec = System.currentTimeMillis() / 1000L
                val signature = requestSignature(trackId, formatId, timestampSec, account.appSecret)
                val url = apiBase().toHttpUrl().newBuilder()
                    .addPathSegment("track")
                    .addPathSegment("getFileUrl")
                    .addQueryParameter("request_ts", timestampSec.toString())
                    .addQueryParameter("request_sig", signature)
                    .addQueryParameter("track_id", trackId)
                    .addQueryParameter("format_id", formatId.toString())
                    .addQueryParameter("intent", "stream")
                    .addQueryParameter("app_id", account.appId)
                    .build()
                Http.client.newCall(authedRequest(url.toString(), account)).execute().use { response ->
                    if (!response.isSuccessful) {
                        TrackLog.w(TAG, "Qobuz getFileUrl HTTP ${response.code} for $trackId")
                        return@use null
                    }
                    parseFileUrlPayload(response.body?.string().orEmpty())
                }
            }.onFailure { TrackLog.w(TAG, "Qobuz getFileUrl failed for $trackId: ${it.message}") }
                .getOrNull()
        }
        if (resolved == null || resolved.isPreview) {
            failureCache[cacheKey] = System.currentTimeMillis() + FAILURE_CACHE_MS
            return null
        }
        streamCache[cacheKey] = resolved to System.currentTimeMillis() + STREAM_CACHE_MS
        return resolved
    }

    internal fun parseFileUrlPayload(payload: String): Stream? {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val streamUrl = root.stringOrNull("url")?.takeIf { it.startsWith("http") } ?: return null
        val isPreview = root.optBoolean("sample", false)
        val apiMime = root.stringOrNull("mime_type").orEmpty()
        val flac = !(apiMime.contains("mpeg", true) || apiMime.contains("mp3", true))
        return Stream(
            url = streamUrl,
            codec = if (flac) "flac" else "mp3",
            kbps = if (flac) null else 320,
            sampleRateHz = root.longOrNull("sampling_rate")?.toInt(),
            bitDepth = root.longOrNull("bit_depth")?.toInt(),
            isPreview = isPreview,
        )
    }

    private fun JSONObject.findTrackItems(): JSONArray? {
        val data = optJSONObject("data") ?: this
        data.optJSONObject("tracks")?.optJSONArray("items")?.let { return it }
        data.optJSONArray("items")?.let { return it }
        data.optJSONArray("tracks")?.let { return it }
        optJSONArray("items")?.let { return it }
        optJSONArray("tracks")?.let { return it }
        return null
    }

    private fun JSONObject.trackIdOrNull(): String? =
        stringOrNull("id") ?: longOrNull("id")?.toString() ?: stringOrNull("track_id")

    private fun extractThumbnail(albumObj: JSONObject?, item: JSONObject): String? {
        albumObj?.opt("image")?.let { image ->
            when (image) {
                is JSONArray ->
                    for (i in 0 until image.length()) {
                        image.optJSONObject(i)?.stringOrNull("url")?.let { return it }
                    }
                is JSONObject ->
                    image.stringOrNull("large")?.let { return it }
                        ?: image.stringOrNull("medium")?.let { return it }
                        ?: image.stringOrNull("small")?.let { return it }
                        ?: image.stringOrNull("thumbnail")?.let { return it }
                        ?: image.stringOrNull("url")?.let { return it }
                is String -> if (image.startsWith("http")) return image
            }
        }
        albumObj?.stringOrNull("cover_url")?.let { return it }
        albumObj?.stringOrNull("thumbnail_url")?.let { return it }
        return item.stringOrNull("thumbnail") ?: item.stringOrNull("cover")
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (has(key)) optLong(key).takeIf { it > 0L } else null
}
