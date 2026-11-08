package com.music.bitchord.data.tidal

import android.content.Context
import com.music.bitchord.data.TrackLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Tidal's account API: the search, the playbackinfo manifest, and the PKCE
 * login, ported from ArchiveTune's `TidalAccountManager`/`TidalAudioProvider`
 * onto this app's source contract.
 *
 * Two manifest shapes come back from
 * `tracks/{id}/playbackinfopostpaywall`, and the difference decides
 * everything downstream:
 *
 *  - **AAC (LOW/HIGH)** — a JSON envelope with one direct `urls[0]` the
 *    player opens as-is.
 *  - **FLAC (LOSSLESS/HI_RES_LOSSLESS)** — a base64 MPEG-DASH document. It is
 *    decoded to `cacheDir/tidal-manifests/<track>-<tier>.mpd` and handed back
 *    as a `file://` URL, which is exactly the shape a module source's Tidal
 *    manifest already had: Media3's `DashMediaSource` plays it off the `.mpd`
 *    extension and downloads reassemble it through
 *    [ManifestFile][com.music.bitchord.download.ManifestFile]. The signed
 *    segment URLs inside live for hours, and a manifest file older than
 *    [MANIFEST_MAX_AGE_MS] is not worth serving — it is rewritten on every
 *    resolve anyway, and pruned here so the directory cannot grow forever.
 */
object TidalService {

    private const val TAG = "BitChord"

    private const val API_BASE = "https://api.tidal.com/v1"
    private const val AUTHORIZE_ENDPOINT = "https://login.tidal.com/authorize"
    const val PKCE_REDIRECT_URI = "https://tidal.com/android/login/auth"
    private const val TOKEN_ENDPOINT = "https://auth.tidal.com/v1/oauth2/token"
    private const val CLIENT_ID = "6BDSRdpK9hqEBTgU"
    private const val CLIENT_SECRET = "xeuPmY7nbpZ9IIbLAcQ93shka1VNheUAqN6IcszjTG8="
    private const val SCOPE = "r_usr+w_usr+w_sub"
    private const val COUNTRY_CODE = "US"
    private const val LOCALE = "en_US"

    private const val MANIFEST_MAX_AGE_MS = 6 * 60 * 60 * 1000L

    /** One search row, carrying what the matcher and the picker need. */
    data class Track(
        val id: String,
        val title: String,
        val artistNames: List<String>,
        val album: String?,
        val albumCoverId: String?,
        val durationSec: Int?,
        val audioQuality: String?,
        val sampleRate: Int?,
        val isExplicit: Boolean,
    )

    /** What playbackinfo decoded into, ready for a [com.music.bitchord.data.sources.SourceStream]. */
    data class DecodedManifest(
        val url: String,
        val codec: String,
        val kbps: Int?,
        val kbpsInferred: Boolean,
        val sampleRateHz: Int?,
        val bitDepth: Int?,
        val durationSec: Int?,
    )

    class PreviewException : Exception("Tidal playbackinfo returned PREVIEW (no FULL asset)")

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(16, TimeUnit.SECONDS)
        .build()

    private val loginClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    private lateinit var manifestsDir: File

    fun init(context: Context) {
        manifestsDir = File(context.cacheDir, "tidal-manifests")
    }

    /** Points the manifest directory at a scratch dir, so the decode is testable on the JVM. */
    internal fun setManifestsDirForTest(dir: File) {
        manifestsDir = dir
    }

    // ── Login ─────────────────────────────────────────────────────────────

    data class PkceChallenge(
        val verifier: String,
        val uniqueKey: String,
        val authUrl: String,
    )

    fun buildPkceChallenge(): PkceChallenge {
        val random = SecureRandom()
        fun base64UrlNoPad(bytes: ByteArray): String =
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val verifier = base64UrlNoPad(ByteArray(64).also(random::nextBytes))
        val challenge = base64UrlNoPad(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
        )
        val uniqueKey = ByteArray(8).also(random::nextBytes())
            .joinToString("") { "%02x".format(it) }
        fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
        val authUrl = buildString {
            append(AUTHORIZE_ENDPOINT)
            append("?response_type=code")
            append("&redirect_uri=").append(enc(PKCE_REDIRECT_URI))
            append("&client_id=").append(CLIENT_ID)
            append("&lang=EN")
            append("&appMode=android")
            append("&client_unique_key=").append(uniqueKey)
            append("&code_challenge=").append(challenge)
            append("&code_challenge_method=S256")
            append("&restrict_signup=true")
        }
        return PkceChallenge(verifier, uniqueKey, authUrl)
    }

    suspend fun exchangePkceCode(code: String, verifier: String, uniqueKey: String) =
        tokenRequest(
            FormBody.Builder()
                .add("code", code)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("grant_type", "authorization_code")
                .add("redirect_uri", PKCE_REDIRECT_URI)
                .add("scope", SCOPE)
                .add("code_verifier", verifier)
                .add("client_unique_key", uniqueKey)
                .build(),
            TidalAccountStore.FLOW_PKCE,
        )

    /**
     * Validates a Bearer caught from the web player by asking /sessions for
     * the account behind it — the web-capture login's only proof that the
     * token works.
     */
    suspend fun sessionFromBearer(accessToken: String): TidalAccountStore.Session? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$API_BASE/sessions")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            runCatching {
                loginClient.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful || payload.isBlank()) {
                        TrackLog.w(TAG, "Tidal bearer session validation failed: HTTP ${response.code}")
                        return@use null
                    }
                    val json = JSONObject(payload)
                    TidalAccountStore.Session(
                        accessToken = accessToken,
                        refreshToken = null,
                        expiresAtMillis = System.currentTimeMillis() + 3600L * 1000L,
                        userId = json.optLong("userId").takeIf { it > 0L },
                        username = json.optString("username").takeIf { it.isNotBlank() },
                        countryCode = json.optString("countryCode").takeIf { it.isNotBlank() },
                        flow = TidalAccountStore.FLOW_WEBCAPTURE,
                    )
                }
            }.getOrElse {
                TrackLog.w(TAG, "Tidal bearer session validation error: ${it.message}")
                null
            }
        }

    suspend fun fetchSubscription(
        accessToken: String,
        userId: Long,
    ): TidalAccountStore.Subscription = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API_BASE/users/$userId/subscription?countryCode=$COUNTRY_CODE")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        runCatching {
            loginClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use TidalAccountStore.Subscription.UNKNOWN
                val json = JSONObject(response.body?.string().orEmpty())
                val type = json.optJSONObject("subscription")?.optString("type")?.uppercase().orEmpty()
                val soundQuality = json.optString("highestSoundQuality").uppercase()
                when {
                    json.has("premiumAccess") -> if (json.optBoolean("premiumAccess", false)) {
                        TidalAccountStore.Subscription.PREMIUM
                    } else {
                        TidalAccountStore.Subscription.FREE
                    }
                    soundQuality.contains("LOSSLESS") || soundQuality.contains("HI_RES") ->
                        TidalAccountStore.Subscription.PREMIUM
                    type.contains("HIFI") || type.contains("PREMIUM") || type.contains("PLUS") ->
                        TidalAccountStore.Subscription.PREMIUM
                    type.contains("FREE") -> TidalAccountStore.Subscription.FREE
                    else -> TidalAccountStore.Subscription.UNKNOWN
                }
            }
        }.getOrElse { TidalAccountStore.Subscription.UNKNOWN }
    }

    private suspend fun tokenRequest(body: FormBody, flow: String) =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(TOKEN_ENDPOINT).post(body).build()
            runCatching {
                loginClient.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (!response.isSuccessful || payload.isBlank()) {
                        TrackLog.w(TAG, "Tidal token exchange failed: HTTP ${response.code}")
                        return@use null
                    }
                    TidalAccountStore.fromTokenPayload(payload, flow)
                }
            }.getOrElse {
                TrackLog.w(TAG, "Tidal token exchange error: ${it.message}")
                null
            }
        }

    // ── Search ────────────────────────────────────────────────────────────

    suspend fun searchTracks(
        accessToken: String,
        query: String,
        limit: Int,
        countryCode: String? = null,
    ): List<Track> = withContext(Dispatchers.IO) {
        val country = countryCode?.takeIf { it.isNotBlank() } ?: COUNTRY_CODE
        val url = buildString {
            append("$API_BASE/search/tracks?query=")
            append(URLEncoder.encode(query, "UTF-8"))
            append("&limit=").append(limit)
            append("&offset=0&countryCode=").append(country)
            append("&locale=").append(LOCALE)
        }
        val request = bearer(accessToken, url)
        runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) throw TidalAccountStore.UnauthorizedException()
                if (!response.isSuccessful) {
                    TrackLog.w(TAG, "Tidal search failed: HTTP ${response.code}")
                    return@use emptyList()
                }
                val items = JSONObject(response.body?.string().orEmpty()).optJSONArray("items")
                    ?: return@use emptyList()
                buildList {
                    for (index in 0 until items.length()) {
                        items.optJSONObject(index)?.toTrack()?.let(::add)
                    }
                }
            }
        }.getOrElse {
            if (it is TidalAccountStore.UnauthorizedException) throw it
            TrackLog.w(TAG, "Tidal search error: ${it.message}")
            emptyList()
        }
    }

    private fun JSONObject.toTrack(): Track? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val title = optString("title").takeIf { it.isNotBlank() } ?: return null
        val artists = mutableListOf<String>()
        optJSONArray("artists")?.let { array ->
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.optString("name")?.takeIf { it.isNotBlank() }?.let(artists::add)
            }
        }
        if (artists.isEmpty()) {
            optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() }?.let(artists::add)
        }
        val albumObj = optJSONObject("album")
        return Track(
            id = id,
            title = title,
            artistNames = artists.distinct(),
            album = albumObj?.optString("title")?.takeIf { it.isNotBlank() },
            albumCoverId = albumObj?.optString("cover")?.takeIf { it.isNotBlank() },
            durationSec = optInt("duration").takeIf { it > 0 },
            audioQuality = optString("audioQuality").takeIf { it.isNotBlank() },
            sampleRate = optDouble("sampleRate").takeIf { it > 0.0 }?.toInt(),
            isExplicit = optBoolean("explicit", false),
        )
    }

    fun artworkUrl(coverId: String, size: Int = 640): String =
        "https://resources.tidal.com/images/${coverId.replace('-', '/')}/${size}x$size.jpg"

    // ── Stream resolution ─────────────────────────────────────────────────

    /**
     * playbackinfo at [audioQuality], downgrading the tier once per PREVIEW
     * answer exactly as ArchiveTune did — a tier the account cannot stream in
     * full is not an error, it is a lower tier that can be.
     */
    suspend fun playbackInfo(
        accessToken: String,
        trackId: String,
        audioQuality: String,
        durationSec: Int?,
        countryCode: String? = null,
    ): DecodedManifest? = withContext(Dispatchers.IO) {
        try {
            playbackInfoOnce(accessToken, trackId, audioQuality, durationSec, countryCode)
        } catch (e: PreviewException) {
            val fallback = when (audioQuality) {
                "HI_RES_LOSSLESS" -> "LOSSLESS"
                "LOSSLESS" -> "HIGH"
                else -> null
            }
            if (fallback == null) {
                TrackLog.w(TAG, "Tidal playbackinfo PREVIEW at every tier for $trackId")
                throw e
            }
            TrackLog.w(TAG, "Tidal playbackinfo PREVIEW at $audioQuality; retrying at $fallback")
            playbackInfoOnce(accessToken, trackId, fallback, durationSec, countryCode)
        }
    }

    private suspend fun playbackInfoOnce(
        accessToken: String,
        trackId: String,
        audioQuality: String,
        durationSec: Int?,
        countryCode: String?,
    ): DecodedManifest? = withContext(Dispatchers.IO) {
        val country = countryCode?.takeIf { it.isNotBlank() } ?: COUNTRY_CODE
        val url = "$API_BASE/tracks/$trackId/playbackinfopostpaywall" +
            "?audioquality=$audioQuality&playbackmode=STREAM&assetpresentation=FULL&countryCode=$country"
        val request = bearer(accessToken, url)
        runCatching {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) throw TidalAccountStore.UnauthorizedException()
                val payload = response.body?.string() ?: return@use null
                if (!response.isSuccessful || payload.isBlank()) {
                    TrackLog.w(TAG, "Tidal playbackinfo failed: HTTP ${response.code}")
                    return@use null
                }
                val json = JSONObject(payload)
                if (json.optString("assetPresentation").equals("PREVIEW", ignoreCase = true)) {
                    throw PreviewException()
                }
                val manifestB64 = json.optString("manifest").takeIf { it.isNotBlank() } ?: return@use null
                val manifestMime = json.optString("manifestMimeType").takeIf { it.isNotBlank() }
                decodeManifest(manifestB64, manifestMime, trackId, audioQuality, durationSec)
            }
        }.getOrElse {
            if (it is TidalAccountStore.UnauthorizedException || it is PreviewException) throw it
            TrackLog.w(TAG, "Tidal playbackinfo error: ${it.message}")
            null
        }
    }

    private fun bearer(token: String, url: String): Request =
        Request.Builder().url(url).header("Authorization", "Bearer $token").get().build()

    /**
     * The base64 manifest, split by what it actually is. Internal so a test
     * can drive it with recorded payloads.
     */
    internal fun decodeManifest(
        manifestB64: String,
        declaredMimeType: String?,
        trackId: String,
        quality: String,
        durationSec: Int?,
    ): DecodedManifest? {
        val text = runCatching {
            String(java.util.Base64.getDecoder().decode(manifestB64.trim()), Charsets.UTF_8).trim()
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return if (text.startsWith("{")) decodeJsonManifest(text) else decodeDashManifest(text, trackId, quality)
    }

    private fun decodeJsonManifest(text: String): DecodedManifest? {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val urls = root.optJSONArray("urls") ?: return null
        val url = (0 until urls.length()).firstNotNullOfOrNull { urls.optString(it).takeIf(String::isNotBlank) }
            ?: return null
        val mimeType = root.optString("mimeType").ifBlank { "audio/mp4" }
        val codecs = root.optString("codecs").ifBlank {
            if (mimeType.contains("flac", true)) "flac" else "mp4a.40.2"
        }
        val isFlac = codecs.contains("flac", true) || mimeType.contains("flac", true)
        val statedRate = root.optInt("bitrate").takeIf { it > 0 }
        val kbps = when {
            isFlac -> null
            statedRate == null -> 320
            statedRate > 10000 -> statedRate / 1000
            else -> statedRate
        }
        return DecodedManifest(
            url = url,
            codec = if (isFlac) "flac" else "mp4",
            kbps = kbps,
            kbpsInferred = !isFlac && statedRate == null,
            sampleRateHz = root.optDouble("sampleRate").takeIf { it > 0.0 }?.toInt(),
            bitDepth = root.optInt("bitDepth").takeIf { it > 0 },
            durationSec = durationSec,
        )
    }

    /**
     * An MPD: written out as a file so the rest of the app treats it exactly
     * like a module source's manifest. Only the representation attributes are
     * read here — the segment URLs are Media3's business on playback and
     * [OfflineDash][com.music.bitchord.download.OfflineDash]'s on download.
     */
    private fun decodeDashManifest(text: String, trackId: String, quality: String): DecodedManifest? {
        if (!text.contains("<MPD", ignoreCase = true)) return null
        if (!::manifestsDir.isInitialized) return null
        fun firstAttr(name: String): String? =
            Regex("""\b$name\s*=\s*"([^"]*)"""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        val codecs = firstAttr("codecs").orEmpty().ifBlank { "flac" }
        val isFlac = codecs.contains("flac", true)
        manifestsDir.mkdirs()
        pruneOldManifests()
        val safeTrack = trackId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(manifestsDir, "$safeTrack-${quality.lowercase(Locale.US)}.mpd")
        file.writeText(sanitizeXmlEntities(text))
        return DecodedManifest(
            url = file.toURI().toString(),
            codec = if (isFlac) "flac" else "mp4",
            kbps = firstAttr("bandwidth")?.toIntOrNull()?.div(1000)?.takeIf { it > 0 },
            kbpsInferred = false,
            sampleRateHz = firstAttr("audioSamplingRate")?.toIntOrNull(),
            bitDepth = firstAttr("bitDepth")?.toIntOrNull(),
            durationSec = null,
        )
    }

    private fun pruneOldManifests() {
        val cutoff = System.currentTimeMillis() - MANIFEST_MAX_AGE_MS
        manifestsDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
        }
    }

    private fun sanitizeXmlEntities(text: String): String =
        text.replace(Regex("""&(?!amp;|lt;|gt;|quot;|apos;|#\d+;|#x[0-9a-fA-F]+;)"""), "&amp;")
}
