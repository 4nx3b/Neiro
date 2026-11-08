package com.music.bitchord.data.deezer

import com.music.bitchord.data.Http
import com.music.bitchord.data.TrackLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap

object DeezerService {
    private const val TAG = "BitChord"

    const val FORMAT_FLAC = "FLAC"
    const val FORMAT_MP3_320 = "MP3_320"
    const val FORMAT_MP3_128 = "MP3_128"

    const val CODEC_FLAC = "flac"
    const val CODEC_MP3 = "mp3"

    private val FORMAT_TIERS = listOf(FORMAT_FLAC, FORMAT_MP3_320, FORMAT_MP3_128)

    private const val GATEWAY = "https://www.deezer.com/ajax/gw-light.php"
    private const val MEDIA_ENDPOINT = "https://media.deezer.com/v1/get_url"
    private const val PUBLIC_API = "https://api.deezer.com"

    private const val ANONYMOUS_RESOLVER_URL = "https://yesitworkssomehow-funny-deeza-api-and-yeah.hf.space/get_url"
    private const val ANONYMOUS_RESOLVER_URL_128 = "https://api-number-2-poopo.onrender.com/get_url"
    private const val ANONYMOUS_RESOLVER_URL_RENDER = "https://dzmedia-metrofuse.onrender.com/get_url"

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36"

    private const val SESSION_TTL_MS = 45 * 60 * 1000L
    private const val STREAM_CACHE_MS = 30 * 60 * 1000L
    private const val FAILURE_CACHE_MS = 10 * 60 * 1000L
    private const val SEARCH_LIMIT = 25

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    data class Track(
        val trackId: String,
        val title: String,
        val artist: String?,
        val album: String?,
        val durationSec: Long?,
        val isrc: String?,
        val coverUrl: String?,
    )

    data class Stream(
        val uri: String,
        val codec: String,
        val kbps: Int?,
        val sampleRateHz: Int?,
        val bitDepth: Int?,
    )

    data class AccountInfo(
        val name: String,
        val lossless: Boolean,
    )

    internal data class Session(
        val arl: String,
        val apiToken: String,
        val licenseToken: String,
        val lossless: Boolean,
        val establishedAt: Long,
    )

    internal data class MediaAnswer(
        val url: String,
        val format: String,
        val contentLength: Long?,
        val expiresAtMs: Long?,
    )

    private var session: Session? = null
    private val streamCache = ConcurrentHashMap<String, Pair<Stream, Long>>()
    private val failureCache = ConcurrentHashMap<String, Long>()

    internal fun forgetSession() {
        session = null
    }

    suspend fun verifyArl(arl: String): AccountInfo? = withContext(Dispatchers.IO) {
        runCatching {
            accountInfoFromUserData(gateway(arl.trim(), apiToken = "", method = "deezer.getUserData"))
        }.onFailure { TrackLog.w(TAG, "Deezer ARL verification failed: ${it.message}") }
            .getOrNull()
    }

    internal fun accountInfoFromUserData(payload: String): AccountInfo? {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val user = json.optJSONObject("results")?.optJSONObject("USER") ?: return null
        if (user.optLong("USER_ID", 0L) == 0L) return null
        val options = user.optJSONObject("OPTIONS")
        val name = sequenceOf(
            user.optString("BLOG_NAME"),
            user.optString("FIRSTNAME"),
            user.optString("EMAIL"),
        ).firstOrNull { it.isNotBlank() } ?: "Deezer"
        return AccountInfo(
            name = name,
            lossless = options?.optBoolean("web_lossless", false) == true ||
                options?.optBoolean("mobile_lossless", false) == true,
        )
    }

    internal fun sessionFromUserData(payload: String): Session? {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val results = json.optJSONObject("results") ?: return null
        val user = results.optJSONObject("USER") ?: return null
        if (user.optLong("USER_ID", 0L) == 0L) return null
        val options = user.optJSONObject("OPTIONS")
        val licenseToken = options?.optString("license_token").orEmpty()
        if (licenseToken.isBlank()) return null
        val apiToken = results.optString("checkForm").orEmpty()
        if (apiToken.isBlank()) return null
        return Session(
            arl = "",
            apiToken = apiToken,
            licenseToken = licenseToken,
            lossless = options.optBoolean("web_lossless", false) ||
                options.optBoolean("mobile_lossless", false),
            establishedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun currentSession(): Session? {
        val account = DeezerAccountStore.account.value ?: return null
        session?.let { cached ->
            if (cached.arl == account.arl &&
                System.currentTimeMillis() - cached.establishedAt < SESSION_TTL_MS
            ) {
                return cached
            }
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val payload = gateway(account.arl, apiToken = "", method = "deezer.getUserData")
                val parsed = sessionFromUserData(payload) ?: throw IOException("ARL rejected by gateway")
                parsed.copy(arl = account.arl, establishedAt = System.currentTimeMillis())
            }.onFailure {
                TrackLog.w(TAG, "Deezer session failed: ${it.message}")
                session = null
            }.getOrNull()?.also { session = it }
        }
    }

    private fun gateway(
        arl: String,
        apiToken: String,
        method: String,
        payload: JSONObject?,
    ): String {
        val url = GATEWAY.toHttpUrl().newBuilder()
            .addQueryParameter("method", method)
            .addQueryParameter("input", "3")
            .addQueryParameter("api_version", "1.0")
            .addQueryParameter("api_token", apiToken)
            .build()
        val body = (payload ?: JSONObject()).toString().toRequestBody(JSON_MEDIA)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "arl=$arl")
            .post(body)
            .build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("gateway HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    suspend fun searchTracks(query: String, limit: Int = SEARCH_LIMIT): List<Track> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val viaPublicApi = runCatching { searchViaPublicApi(trimmed, limit) }
                .onFailure { TrackLog.w(TAG, "Deezer public search failed: ${it.message}") }
                .getOrDefault(emptyList())
            if (viaPublicApi.isNotEmpty()) {
                viaPublicApi
            } else {
                runCatching { searchViaGateway(trimmed, limit) }
                    .onFailure { TrackLog.w(TAG, "Deezer gateway search failed: ${it.message}") }
                    .getOrDefault(emptyList())
            }
        }

    private fun searchViaPublicApi(query: String, limit: Int): List<Track> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url("$PUBLIC_API/search?q=$encoded&limit=$limit")
            .header("Accept", "application/json")
            .build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            return parsePublicSearchRows(response.body?.string().orEmpty())
        }
    }

    internal fun parsePublicSearchRows(payload: String): List<Track> {
        val data = runCatching { JSONObject(payload) }.getOrNull()?.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val obj = data.optJSONObject(i) ?: return@mapNotNull null
            val trackId = obj.optLong("id", 0L).takeIf { it > 0L }?.toString() ?: return@mapNotNull null
            val title = obj.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Track(
                trackId = trackId,
                title = title,
                artist = obj.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() },
                album = obj.optJSONObject("album")?.optString("title")?.takeIf { it.isNotBlank() },
                durationSec = obj.optLong("duration", 0L).takeIf { it > 0L },
                isrc = obj.optString("isrc").takeIf { it.isNotBlank() },
                coverUrl = obj.optJSONObject("album")?.optString("cover_big")?.takeIf { it.isNotBlank() },
            )
        }
    }

    private suspend fun searchViaGateway(query: String, limit: Int): List<Track> {
        val active = currentSession() ?: return emptyList()
        val payload = JSONObject()
            .put("query", query)
            .put("start", 0)
            .put("nb", limit)
        return parseGatewaySearchRows(gateway(active.arl, active.apiToken, "search.music", payload))
    }

    internal fun parseGatewaySearchRows(payload: String): List<Track> {
        val data = runCatching { JSONObject(payload) }
            .getOrNull()
            ?.optJSONObject("results")
            ?.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val obj = data.optJSONObject(i) ?: return@mapNotNull null
            val trackId = obj.optString("SNG_ID").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = obj.optString("SNG_TITLE").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Track(
                trackId = trackId,
                title = title,
                artist = obj.optString("ART_NAME").takeIf { it.isNotBlank() },
                album = obj.optString("ALB_TITLE").takeIf { it.isNotBlank() },
                durationSec = obj.optLong("DURATION", 0L).takeIf { it > 0L },
                isrc = obj.optString("ISRC").takeIf { it.isNotBlank() },
                coverUrl = null,
            )
        }
    }

    fun formatTiersFor(requested: String, lossless: Boolean): List<String> {
        val start = if (requested == FORMAT_FLAC && !lossless) FORMAT_MP3_320 else requested
        return FORMAT_TIERS.dropWhile { it != start }.ifEmpty { listOf(FORMAT_MP3_320, FORMAT_MP3_128) }
    }

    suspend fun resolveStream(trackId: String, format: String): Stream? {
        if (trackId.isBlank()) return null
        val cacheKey = "id:$trackId:$format"
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
            resolveWithAccount(trackId, format) ?: resolveAnonymously(trackId, format)
        }
        if (resolved == null) {
            failureCache[cacheKey] = System.currentTimeMillis() + FAILURE_CACHE_MS
        } else {
            val expiresAt = minOf(
                resolved.expiresAtMs ?: Long.MAX_VALUE,
                System.currentTimeMillis() + STREAM_CACHE_MS,
            )
            streamCache[cacheKey] = resolved.stream to expiresAt
        }
        return resolved?.stream
    }

    private class ResolvedWithExpiry(val stream: Stream, val expiresAtMs: Long?)

    private suspend fun resolveWithAccount(trackId: String, format: String): ResolvedWithExpiry? {
        val active = currentSession() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val trackJson = gateway(
                    active.arl,
                    active.apiToken,
                    "song.getData",
                    JSONObject().put("sng_id", trackId),
                )
                val media = requestUrl(active, trackJson, trackId, format) ?: return@runCatching null
                ResolvedWithExpiry(streamFor(trackId, media), expiresAtMs = null)
            }.onFailure {
                TrackLog.w(TAG, "Deezer get_url failed for $trackId: ${it.message}")
                session = null
            }.getOrNull()
        }
    }

    private fun requestUrl(
        session: Session,
        trackJson: String,
        trackId: String,
        format: String,
    ): MediaAnswer? {
        val trackToken = runCatching { JSONObject(trackJson) }
            .getOrNull()
            ?.optJSONObject("results")
            ?.optString("TRACK_TOKEN")
            .orEmpty()
        if (trackToken.isBlank()) return null

        val formatArray = JSONArray()
        formatTiersFor(format, session.lossless).forEach { tier ->
            formatArray.put(JSONObject().put("cipher", "BF_CBC_STRIPE").put("format", tier))
        }
        val mediaArray = JSONArray()
            .put(JSONObject().put("type", "FULL").put("formats", formatArray))
        val cipherPayload = JSONObject()
            .put("license_token", session.licenseToken)
            .put("media", mediaArray)
            .put("track_tokens", JSONArray().put(trackToken))

        val request = Request.Builder()
            .url(MEDIA_ENDPOINT)
            .header("User-Agent", USER_AGENT)
            .header("Cookie", "arl=${session.arl}")
            .post(cipherPayload.toString().toRequestBody(JSON_MEDIA))
            .build()
        val payload = Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("get_url HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        return parseGetUrlResponse(payload)
    }

    internal fun parseGetUrlResponse(payload: String): MediaAnswer? {
        val first = runCatching { JSONObject(payload) }.getOrNull()
            ?.optJSONArray("data")
            ?.optJSONObject(0) ?: return null
        val media = first.optJSONArray("media")?.optJSONObject(0) ?: return null
        val source = media.optJSONArray("sources")?.optJSONObject(0) ?: return null
        val url = source.optString("url").takeIf { it.isNotBlank() } ?: return null
        val servedFormat = media.optString("format").ifBlank { FORMAT_MP3_320 }
        return MediaAnswer(
            url = url,
            format = servedFormat,
            contentLength = null,
            expiresAtMs = null,
        )
    }

    private fun streamFor(trackId: String, media: MediaAnswer): Stream = Stream(
        uri = DeezerCrypto.buildUri(media.url, trackId),
        codec = if (media.format.equals(FORMAT_FLAC, ignoreCase = true)) CODEC_FLAC else CODEC_MP3,
        kbps = when (media.format.uppercase()) {
            FORMAT_MP3_320 -> 320
            FORMAT_MP3_128 -> 128
            else -> null
        },
        sampleRateHz = if (media.format.equals(FORMAT_FLAC, ignoreCase = true)) 44_100 else null,
        bitDepth = if (media.format.equals(FORMAT_FLAC, ignoreCase = true)) 16 else null,
    )

    internal fun anonymousResolverUrls(format: String): List<String> = buildList {
        if (format == FORMAT_MP3_128) add(ANONYMOUS_RESOLVER_URL_128)
        add(ANONYMOUS_RESOLVER_URL)
        add(ANONYMOUS_RESOLVER_URL_RENDER)
    }.distinct()

    private fun resolveAnonymously(trackId: String, format: String): ResolvedWithExpiry? {
        for (resolverUrl in anonymousResolverUrls(format)) {
            val media = runCatching { requestAnonymousUrl(resolverUrl, trackId, format) }
                .onFailure { TrackLog.w(TAG, "Deezer anonymous resolver failed: ${it.message}") }
                .getOrNull() ?: continue
            val expiresAtMs = media.expiresAtMs
                ?.minus(30_000L)
                ?.coerceAtLeast(System.currentTimeMillis() + 60_000L)
            return ResolvedWithExpiry(
                stream = streamFor(trackId, media),
                expiresAtMs = expiresAtMs,
            )
        }
        return null
    }

    private fun requestAnonymousUrl(
        resolverUrl: String,
        trackId: String,
        format: String,
    ): MediaAnswer? {
        val body = JSONObject()
            .put("formats", JSONArray().put(format))
            .put("ids", JSONArray().put(trackId.toLongOrNull() ?: trackId))
            .put("fast", false)
        val request = Request.Builder()
            .url(resolverUrl)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("resolver HTTP ${response.code}")
            return parseAnonymousResolverResponse(response.body?.string().orEmpty(), format)
        }
    }

    internal fun parseAnonymousResolverResponse(payload: String, requestedFormat: String): MediaAnswer? {
        val mediaArray = runCatching { JSONObject(payload) }
            .getOrNull()
            ?.optJSONArray("data")
            ?.optJSONObject(0)
            ?.optJSONArray("media") ?: return null
        val media = (0 until mediaArray.length())
            .mapNotNull { i -> mediaArray.optJSONObject(i) }
            .firstOrNull { it.optString("format").equals(requestedFormat, ignoreCase = true) }
            ?: mediaArray.optJSONObject(0)
            ?: return null
        val sources = media.optJSONArray("sources")
        val source = sources?.optJSONObject(1) ?: sources?.optJSONObject(0) ?: return null
        val url = source.optString("url").takeIf { it.isNotBlank() } ?: return null
        val cipherType = media.optJSONObject("cipher")?.optString("type").orEmpty()
        if (!cipherType.equals("BF_CBC_STRIPE", ignoreCase = true)) return null
        val servedFormat = media.optString("format").ifBlank { requestedFormat }
        return MediaAnswer(
            url = url,
            format = servedFormat,
            contentLength = media.optLong("filesize", 0L).takeIf { it > 0L },
            expiresAtMs = media.optLong("exp", 0L).takeIf { it > 0L }?.times(1000L),
        )
    }

    suspend fun lookupByIsrc(isrc: String): Track? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = java.net.URLEncoder.encode(isrc, "UTF-8")
            val request = Request.Builder()
                .url("$PUBLIC_API/track/isrc:$encoded")
                .header("Accept", "application/json")
                .build()
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                parseIsrcTrack(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    internal fun parseIsrcTrack(payload: String): Track? {
        val obj = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        if (obj.has("error")) return null
        val trackId = obj.optLong("id", 0L).takeIf { it > 0L }?.toString() ?: return null
        val title = obj.optString("title").takeIf { it.isNotBlank() } ?: return null
        return Track(
            trackId = trackId,
            title = title,
            artist = obj.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() },
            album = obj.optJSONObject("album")?.optString("title")?.takeIf { it.isNotBlank() },
            durationSec = obj.optLong("duration", 0L).takeIf { it > 0L },
            isrc = obj.optString("isrc").takeIf { it.isNotBlank() },
            coverUrl = obj.optJSONObject("album")?.optString("cover_big")?.takeIf { it.isNotBlank() },
        )
    }

    fun parseStreamUri(uri: String): DeezerCrypto.StreamRef? = DeezerCrypto.parseUri(uri)

    internal fun decryptHead(head: ByteArray, length: Int, key: ByteArray) {
        var offset = 0
        var chunkIndex = 0L
        while (offset < length) {
            val chunkLength = minOf(DeezerCrypto.CHUNK_SIZE, length - offset)
            if (DeezerCrypto.isEncryptedChunk(chunkIndex)) {
                DeezerCrypto.decryptChunk(head, offset, chunkLength, key)
            }
            offset += chunkLength
            chunkIndex++
        }
    }

    suspend fun probeDecryptedHead(
        ref: DeezerCrypto.StreamRef,
        byteCount: Int,
    ): Pair<ByteArray, Long?>? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(ref.url)
                .header("Range", "bytes=0-${byteCount + DeezerCrypto.CHUNK_SIZE - 1}")
                .build()
            Http.client.newCall(request).execute().use { response ->
                if (response.code !in 200..299) return@use null
                val total = if (response.code == 206) {
                    response.header("Content-Range")?.substringAfter('/', "")?.toLongOrNull()
                } else {
                    response.header("Content-Length")?.toLongOrNull()
                }?.takeIf { it > 0 }
                val source = response.body?.byteStream() ?: return@use null
                val head = ByteArray(byteCount)
                var read = 0
                while (read < head.size) {
                    val n = source.read(head, read, head.size - read)
                    if (n < 0) break
                    read += n
                }
                if (read == 0) return@use null
                decryptHead(head, read, DeezerCrypto.deriveKey(ref.trackId, ref.salt))
                head.copyOf(read) to total
            }
        }.getOrNull()
    }

    suspend fun fetchDecrypted(
        ref: DeezerCrypto.StreamRef,
        sink: OutputStream,
        onProgress: (written: Long, total: Long) -> Unit,
    ): Long = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(ref.url).build()
        Http.client.newCall(request).execute().use { response ->
            if (response.code !in 200..299) throw IOException("Download failed (HTTP ${response.code})")
            val body = response.body ?: throw IOException("Download failed: empty response")
            val total = body.contentLength().takeIf { it > 0 }
            val key = DeezerCrypto.deriveKey(ref.trackId, ref.salt)
            val source = body.byteStream()
            val chunk = ByteArray(DeezerCrypto.CHUNK_SIZE)
            var written = 0L
            var chunkIndex = 0L
            while (true) {
                ensureActive()
                var filled = 0
                while (filled < DeezerCrypto.CHUNK_SIZE) {
                    val read = source.read(chunk, filled, DeezerCrypto.CHUNK_SIZE - filled)
                    if (read == -1) break
                    filled += read
                }
                if (filled == 0) break
                if (filled == DeezerCrypto.CHUNK_SIZE && DeezerCrypto.isEncryptedChunk(chunkIndex)) {
                    DeezerCrypto.decryptChunk(chunk, 0, filled, key)
                }
                sink.write(chunk, 0, filled)
                written += filled
                if (total != null) onProgress(written, total)
                chunkIndex++
                if (filled < DeezerCrypto.CHUNK_SIZE) break
            }
            sink.flush()
            written
        }
    }
}
