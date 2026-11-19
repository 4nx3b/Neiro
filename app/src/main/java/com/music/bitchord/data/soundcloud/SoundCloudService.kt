package com.music.bitchord.data.soundcloud

import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object SoundCloudService {
    private const val TAG = "BitChord"

    const val TIER_AAC_160 = "AAC_160"
    const val TIER_MP3_128 = "MP3_128"
    const val TIER_AAC_96 = "AAC_96"

    internal const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

    private const val API_BASE = "https://api-v2.soundcloud.com"
    private const val HOME_URL = "https://soundcloud.com"

    private const val METADATA_TTL_MS = 12 * 60 * 60 * 1000L
    private const val STREAM_CACHE_MS = 5 * 60 * 1000L
    private const val FAILURE_CACHE_MS = 10 * 60 * 1000L
    private const val SEARCH_LIMIT = 25
    private const val MAX_SCRIPTS_SCRAPED = 16

    internal var apiBaseForTest: String? = null
    internal var clientIdForTest: String? = null
    internal var appVersionForTest: String? = null

    private fun apiBase(): String = apiBaseForTest ?: API_BASE

    private val assetScriptRegex = Regex("""https://[a-z0-9-]+\.sndcdn\.com/[^"'<>!]+\.js""")
    private val clientIdRegex = Regex("""client_?id["']?\s*\]?\s*[:=]\s*["']([A-Za-z0-9]{20,})["']""", RegexOption.IGNORE_CASE)
    private val appVersionRegex = Regex("""app_?version["']?\s*\]?\s*[:=]\s*["']([0-9]{8,})["']""", RegexOption.IGNORE_CASE)

    private val tokenRegex =
        Regex("""(?i)(oauth[_-]?token|access[_-]?token|soundcloud[_-]?oauth[_-]?token)["'\s:=]+["']?([A-Za-z0-9._~+/=-]{16,})""")

    private val cookieTokenNames = setOf(
        "oauth_token",
        "oauth-token",
        "soundcloud_oauth_token",
        "soundcloud-oauth-token",
        "access_token",
        "access-token",
    )

    data class Track(
        val trackId: String,
        val title: String,
        val artist: String,
        val artistNames: List<String>,
        val artworkUrl: String?,
        val durationSec: Long?,
        val permalinkUrl: String,
    )

    data class Stream(
        val url: String,
        val isHls: Boolean,
        val codec: String,
        val kbps: Int?,
        val sampleRateHz: Int?,
        val durationSec: Int?,
    )

    internal data class Transcoding(
        val url: String,
        val protocol: String,
        val mimeType: String,
        val preset: String,
        val isHls: Boolean,
        val bitrate: Int?,
        val sampleRate: Int?,
    )

    @Volatile
    private var cachedClientId: String? = null

    @Volatile
    private var cachedAppVersion: String? = null

    @Volatile
    private var metadataExpiresAt: Long = 0L

    private val metadataMutex = Mutex()

    private val streamCache = ConcurrentHashMap<String, Pair<Stream, Long>>()
    private val failureCache = ConcurrentHashMap<String, Long>()

    internal fun forgetMetadata() {
        metadataExpiresAt = 0L
    }

    internal fun forgetCaches() {
        streamCache.clear()
        failureCache.clear()
    }

    suspend fun clientId(forceRefresh: Boolean = false): String = withContext(Dispatchers.IO) {
        clientIdForTest?.let { return@withContext it }
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedClientId?.takeIf { now < metadataExpiresAt }?.let { return@withContext it }
        }
        metadataMutex.withLock {
            val recheck = System.currentTimeMillis()
            if (!forceRefresh) {
                cachedClientId?.takeIf { recheck < metadataExpiresAt }?.let { return@withLock it }
            }
            scrapeMetadata()
            cachedClientId.orEmpty()
        }
    }

    suspend fun appVersion(): String? = withContext(Dispatchers.IO) {
        appVersionForTest?.let { return@withContext it }
        val now = System.currentTimeMillis()
        cachedAppVersion?.takeIf { now < metadataExpiresAt }?.let { return@withContext it }
        metadataMutex.withLock {
            val recheck = System.currentTimeMillis()
            cachedAppVersion?.takeIf { recheck < metadataExpiresAt }?.let { return@withLock it }
            scrapeMetadata()
            cachedAppVersion
        }
    }

    private suspend fun scrapeMetadata() = coroutineScope {
        val (html, scriptUrls) = runCatching {
            Http.client.newCall(
                Request.Builder()
                    .url(HOME_URL)
                    .get()
                    .header("User-Agent", BROWSER_USER_AGENT)
                    .build(),
            ).execute().use { response ->
                if (!response.isSuccessful) return@use "" to emptyList<String>()
                val body = response.body?.string().orEmpty()
                body to assetScriptRegex.findAll(body).map { it.value }.toList().distinct()
            }
        }.getOrDefault("" to emptyList())

        var foundClientId = extractClientId(html)
        var foundAppVersion = extractAppVersion(html)

        if (foundClientId == null || foundAppVersion == null) {
            val bodies = scriptUrls.take(MAX_SCRIPTS_SCRAPED).map { scriptUrl ->
                async(Dispatchers.IO) {
                    runCatching {
                        Http.client.newCall(
                            Request.Builder()
                                .url(scriptUrl)
                                .get()
                                .header("User-Agent", BROWSER_USER_AGENT)
                                .build(),
                        ).execute().use { res ->
                            if (res.isSuccessful) res.body?.string().orEmpty() else ""
                        }
                    }.getOrDefault("")
                }
            }.awaitAll()
            for (body in bodies) {
                if (foundClientId == null) foundClientId = extractClientId(body)
                if (foundAppVersion == null) foundAppVersion = extractAppVersion(body)
                if (foundClientId != null && foundAppVersion != null) break
            }
        }

        cachedClientId = foundClientId
        cachedAppVersion = foundAppVersion
        metadataExpiresAt = System.currentTimeMillis() + METADATA_TTL_MS

        if (foundClientId == null) {
            TrackLog.w(TAG, "SoundCloud client-id scraping failed; search and stream will miss")
        }
    }

    internal fun extractClientId(body: String): String? =
        clientIdRegex.find(body)?.groups?.get(1)?.value

    internal fun extractAppVersion(body: String): String? =
        appVersionRegex.find(body)?.groups?.get(1)?.value

    suspend fun verifyToken(token: String): String? = withContext(Dispatchers.IO) {
        val normalized = normalizeAuthToken(token) ?: return@withContext null
        runCatching {
            val url = "${apiBase()}/me".toHttpUrlOrNull()?.newBuilder()
                ?.addQueryParameter("client_id", clientId())
                ?.build() ?: return@withContext null
            Http.client.newCall(apiRequest(url.toString(), normalized)).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val payload = response.body?.string().orEmpty()
                JSONObject(payload).optString("username").trim().takeIf { it.isNotBlank() }
            }
        }.onFailure { TrackLog.w(TAG, "SoundCloud token verification failed: ${it.message}") }
            .getOrNull()
    }

    suspend fun searchTracks(
        query: String,
        limit: Int = SEARCH_LIMIT,
        token: String? = null,
    ): List<Track> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val id = clientId()
        if (id.isBlank()) return@withContext emptyList()
        val url = "${apiBase()}/search/tracks".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("q", query)
            ?.addQueryParameter("limit", limit.coerceIn(1, 50).toString())
            ?.addQueryParameter("client_id", id)
            ?.addQueryParameter("app_locale", "en")
            ?.build() ?: return@withContext emptyList()
        runCatching {
            Http.client.newCall(apiRequest(url.toString(), token)).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw java.io.IOException("SoundCloud search HTTP ${response.code}: ${payload.take(160)}")
                }
                parseSearchRows(payload)
            }
        }.onFailure { TrackLog.w(TAG, "SoundCloud search failed: ${it.message}") }
            .getOrNull() ?: emptyList()
    }

    internal fun parseSearchRows(payload: String): List<Track> {
        val collection = runCatching {
            JSONObject(payload).optJSONArray("collection") ?: return emptyList()
        }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until collection.length()) {
                val row = collection.optJSONObject(index) ?: continue
                parseTrackRow(row)?.let(::add)
            }
        }
    }

    internal fun parseTrackRow(row: JSONObject): Track? {
        if (!row.optString("kind").equals("track", ignoreCase = true)) return null
        if (!row.optBoolean("streamable", true)) return null
        if (row.optString("policy").equals("SNIP", ignoreCase = true)) return null
        val trackId = row.stringOrNull("id") ?: return null
        val title = row.stringOrNull("title") ?: return null
        val publisherArtist = row.optJSONObject("publisher_metadata")?.stringOrNull("artist")
        val userName = row.optJSONObject("user")?.stringOrNull("username")
        val labelName = row.stringOrNull("label_name")
        val artistNames = listOfNotNull(publisherArtist, userName, labelName)
            .flatMap(::splitArtistNames)
            .distinctBy { it.lowercase(Locale.US).trim() }
        if (artistNames.isEmpty()) return null
        val permalinkUrl = row.stringOrNull("permalink_url") ?: return null
        return Track(
            trackId = trackId,
            title = title,
            artist = artistNames.first(),
            artistNames = artistNames,
            artworkUrl = row.stringOrNull("artwork_url")?.let(::highResArtworkUrl),
            durationSec = (row.longOrNull("full_duration") ?: row.longOrNull("duration"))?.let { it / 1000L },
            permalinkUrl = permalinkUrl,
        )
    }

    suspend fun resolveStream(
        trackId: String,
        tier: String,
        token: String? = null,
    ): Stream? = withContext(Dispatchers.IO) {
        val cacheKey = "$trackId::$tier::${if (token.isNullOrBlank()) "anon" else "auth"}"
        val now = System.currentTimeMillis()
        failureCache[cacheKey]?.takeIf { now < it }?.let { return@withContext null }
        streamCache[cacheKey]?.takeIf { now < it.second }?.let { return@withContext it.first }

        val resolved = runCatching { fetchStream(trackId, tier, token) }
            .onFailure { TrackLog.w(TAG, "SoundCloud stream resolve failed for $trackId: ${it.message}") }
            .getOrNull()

        if (resolved == null) {
            failureCache[cacheKey] = now + FAILURE_CACHE_MS
            return@withContext null
        }
        streamCache[cacheKey] = resolved to now + STREAM_CACHE_MS
        resolved
    }

    private suspend fun fetchStream(
        trackId: String,
        tier: String,
        token: String?,
    ): Stream? = withContext(Dispatchers.IO) {
        val id = clientId()
        if (id.isBlank()) return@withContext null
        attemptStream(trackId, tier, token, id)?.let { return@withContext it }
        val refreshed = clientId(forceRefresh = true)
        if (refreshed.isBlank() || refreshed == id) return@withContext null
        attemptStream(trackId, tier, token, refreshed)
    }

    private suspend fun attemptStream(
        trackId: String,
        tier: String,
        token: String?,
        clientId: String,
    ): Stream? = withContext(Dispatchers.IO) {
        val trackPayload = fetchTrack(trackId, clientId, token) ?: return@withContext null
        val transcodings = parseTranscodings(trackPayload)
        if (transcodings.isEmpty()) return@withContext null
        val candidate = selectTranscoding(transcodings, tier) ?: return@withContext null
        val mediaUrl = fetchTranscodingUrl(candidate, trackPayload, clientId, token) ?: return@withContext null
        val trackJson = runCatching { JSONObject(trackPayload) }.getOrNull()
        val durationMs = trackJson?.let {
            it.longOrNull("full_duration") ?: it.longOrNull("duration")
        }
        Stream(
            url = mediaUrl,
            isHls = candidate.isHls,
            codec = codecOf(candidate.preset, candidate.mimeType),
            kbps = candidate.bitrate,
            sampleRateHz = candidate.sampleRate,
            durationSec = durationMs?.let { (it / 1000L).toInt() },
        )
    }

    private suspend fun fetchTrack(
        trackId: String,
        clientId: String,
        token: String?,
    ): String? = withContext(Dispatchers.IO) {
        val url = "${apiBase()}/tracks/$trackId".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("client_id", clientId)
            ?.addQueryParameter("app_locale", "en")
            ?.build() ?: return@withContext null
        runCatching {
            Http.client.newCall(apiRequest(url.toString(), token)).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw java.io.IOException("SoundCloud track HTTP ${response.code}: ${payload.take(160)}")
                }
                payload.takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    internal fun parseTranscodings(payload: String): List<Transcoding> {
        val media = runCatching { JSONObject(payload).optJSONObject("media") }.getOrNull() ?: return emptyList()
        val transcodings = media?.optJSONArray("transcodings") ?: return emptyList()
        return buildList {
            for (index in 0 until transcodings.length()) {
                val transcoding = transcodings.optJSONObject(index) ?: continue
                val format = transcoding.optJSONObject("format") ?: continue
                val protocol = format.stringOrNull("protocol")?.lowercase(Locale.US) ?: continue
                val mimeType = format.stringOrNull("mime_type")?.lowercase(Locale.US) ?: continue
                if (protocol !in setOf("progressive", "hls")) continue
                val supported = mimeType.contains("mpeg") || mimeType.contains("mp3") ||
                    mimeType.contains("aac") || mimeType.contains("mp4")
                if (!supported) continue
                val streamUrl = transcoding.stringOrNull("url") ?: continue
                add(
                    Transcoding(
                        url = streamUrl,
                        protocol = protocol,
                        mimeType = mimeType,
                        preset = transcoding.stringOrNull("preset").orEmpty(),
                        isHls = protocol == "hls",
                        bitrate = transcoding.intOrNull("bitrate", "bit_rate")
                            ?: format.intOrNull("bitrate", "bit_rate")
                            ?: bitrateFromPreset(transcoding.stringOrNull("preset"), mimeType),
                        sampleRate = transcoding.intOrNull("sample_rate", "sampleRate", "audio_sample_rate")
                            ?: format.intOrNull("sample_rate", "sampleRate", "audio_sample_rate"),
                    ),
                )
            }
        }
    }

    internal fun selectTranscoding(candidates: List<Transcoding>, tier: String): Transcoding? {
        if (candidates.isEmpty()) return null
        val targetBitrate = when (tier) {
            TIER_MP3_128 -> 128_000
            TIER_AAC_96 -> 96_000
            else -> 160_000
        }
        val targetMimePart = if (tier == TIER_MP3_128) "mpeg" else "aac"
        return candidates.maxWithOrNull(
            compareBy<Transcoding> {
                when {
                    it.bitrate == targetBitrate && it.mimeType.contains(targetMimePart) -> 400
                    it.bitrate == targetBitrate -> 300
                    else -> 0
                }
            }.thenBy {
                if (it.mimeType.contains(targetMimePart)) 200 else 0
            }.thenBy {
                if (!it.isHls) 150 else 0
            }.thenBy {
                it.bitrate ?: 0
            },
        )
    }

    private suspend fun fetchTranscodingUrl(
        candidate: Transcoding,
        trackPayload: String,
        clientId: String,
        token: String?,
    ): String? = withContext(Dispatchers.IO) {
        val trackAuthorization = runCatching { JSONObject(trackPayload).stringOrNull("track_authorization") }
            .getOrNull()
        val url = candidate.url.toHttpUrlOrNull()
            ?.newBuilder()
            ?.removeAllQueryParameters("client_id")
            ?.addQueryParameter("client_id", clientId)
            ?.apply {
                trackAuthorization?.takeIf { it.isNotBlank() }?.let {
                    removeAllQueryParameters("track_authorization")
                    addQueryParameter("track_authorization", it)
                }
            }
            ?.build() ?: return@withContext null
        runCatching {
            Http.client.newCall(apiRequest(url.toString(), token)).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw java.io.IOException("SoundCloud transcoding HTTP ${response.code}: ${payload.take(160)}")
                }
                JSONObject(payload).stringOrNull("url")
            }
        }.getOrNull()
    }

    internal fun bitrateFromPreset(preset: String?, mimeType: String): Int? {
        val lowerPreset = preset.orEmpty().lowercase(Locale.US)
        val lowerMime = mimeType.lowercase(Locale.US)
        return when {
            lowerPreset.contains("256") -> 256_000
            lowerPreset.contains("192") -> 192_000
            lowerPreset.contains("160") -> 160_000
            lowerPreset.contains("128") -> 128_000
            lowerPreset.contains("96") -> 96_000
            lowerPreset.contains("64") -> 64_000
            lowerMime.contains("mpeg") || lowerPreset.contains("mp3") -> 128_000
            lowerMime.contains("aac") || lowerMime.contains("mp4") -> 128_000
            else -> null
        }
    }

    internal fun codecOf(preset: String, mimeType: String): String {
        val lowerPreset = preset.lowercase(Locale.US)
        return when {
            lowerPreset.contains("aac") -> "aac"
            lowerPreset.contains("mp3") -> "mp3"
            lowerPreset.contains("opus") -> "opus"
            else -> codecOfMime(mimeType)
        }
    }

    internal fun codecOfMime(mimeType: String): String {
        val lower = mimeType.lowercase(Locale.US)
        return when {
            lower.contains("aac") || lower.contains("mp4") -> "aac"
            lower.contains("mpeg") || lower.contains("mp3") -> "mp3"
            else -> "aac"
        }
    }

    private suspend fun apiRequest(
        url: String,
        token: String?,
    ): Request = withContext(Dispatchers.IO) {
        val version = appVersion()
        val finalUrl = if (version != null && !url.contains("app_version=")) {
            url.toHttpUrlOrNull()?.newBuilder()?.addQueryParameter("app_version", version)?.build()?.toString() ?: url
        } else {
            url
        }
        Request.Builder()
            .url(finalUrl)
            .get()
            .header("Accept", "application/json")
            .header("Referer", "https://soundcloud.com/")
            .header("Origin", "https://soundcloud.com")
            .header("User-Agent", BROWSER_USER_AGENT)
            .apply {
                normalizeAuthToken(token ?: "")?.let { header("Authorization", "OAuth $it") }
            }
            .build()
    }

    fun mediaHeaders(): Map<String, String> = mapOf(
        "User-Agent" to BROWSER_USER_AGENT,
        "Accept" to "audio/*,*/*;q=0.8",
        "Referer" to "https://soundcloud.com/",
        "Origin" to "https://soundcloud.com",
    )

    internal fun normalizeAuthToken(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val prefixed = trimmed
            .removePrefix("OAuth ")
            .removePrefix("oauth ")
            .removePrefix("Bearer ")
            .removePrefix("bearer ")
            .trim()
        if (prefixed.isPlausibleRawToken()) return prefixed

        trimmed
            .split(';', '\n', '\r')
            .asSequence()
            .map { it.trim() }
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val name = part.substring(0, separator).trim().lowercase(Locale.US)
                val value = part.substring(separator + 1).trim().trim('"', '\'')
                value.takeIf { name in cookieTokenNames && it.isPlausibleRawToken() }
            }
            .firstOrNull()
            ?.let { return it }

        return tokenRegex
            .findAll(trimmed)
            .map { it.groupValues.getOrNull(2).orEmpty().trim().trim('"', '\'') }
            .firstOrNull { it.isPlausibleRawToken() }
    }

    private fun String.isPlausibleRawToken(): Boolean {
        if (length < 16) return false
        if (any(Char::isWhitespace)) return false
        if (contains(';')) return false
        if (contains('{') || contains('}')) return false
        return count { it == '=' } <= 2
    }

    private fun highResArtworkUrl(url: String): String {
        val marker = "-large."
        val index = url.lastIndexOf(marker)
        if (index < 0) return url
        return url.substring(0, index) + "-t500x500." + url.substring(index + marker.length)
    }

    private fun splitArtistNames(value: String): List<String> =
        value.split(Regex("""\s*(?:,|/|&|\+|\bfeat\b\.?|\bft\b\.?|\bwith\b)\s*""", RegexOption.IGNORE_CASE))
            .map { it.trim().trimStart('.') }
            .filter { it.isNotBlank() }

    private fun JSONObject.stringOrNull(name: String): String? =
        optString(name).trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

    private fun JSONObject.intOrNull(vararg names: String): Int? {
        names.forEach { name ->
            if (!has(name) || isNull(name)) return@forEach
            runCatching { getInt(name) }.getOrNull()?.let { return it }
            optString(name).trim().toIntOrNull()?.let { return it }
        }
        return null
    }

    private fun JSONObject.longOrNull(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return runCatching { getLong(name) }.getOrElse {
            optString(name).trim().toLongOrNull()
        }
    }
}
