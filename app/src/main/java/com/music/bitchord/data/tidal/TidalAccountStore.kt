package com.music.bitchord.data.tidal

import android.content.Context
import com.music.bitchord.auth.EncryptedPrefs
import com.music.bitchord.data.TrackLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The signed-in Tidal account, held in its own encrypted store.
 *
 * One session, not a pool: the ArchiveTune account-pool machinery exists to
 * race borrowed credentials against per-track rate limits, and none of that is
 * this fork's to recreate — the listener here brings their own account and it
 * is the only one the source speaks with.
 *
 * Three shapes of session can land in here, because the login screen tries
 * them in that order and keeps whatever answered first:
 *
 *  - **PKCE** — an authorization code from the official Android client's
 *    redirect, exchanged for an access token with a durable refresh token.
 *    Refreshable until Tidal revokes it.
 *  - **Web capture** — the Bearer the listen.tidal.com player sends, caught
 *    from the WebView. Good for an hour, and for exactly that hour: there is
 *    no refresh token, so expiry means signing in again.
 *  - **Nothing** — the source reports itself signed out and takes no part in
 *    any walk until a sign-in completes.
 */
object TidalAccountStore {

    private const val TAG = "BitChord"

    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USERNAME = "username"
    private const val KEY_COUNTRY = "country_code"
    private const val KEY_FLOW = "flow"

    const val FLOW_PKCE = "pkce"
    const val FLOW_WEBCAPTURE = "webcapture"

    private const val TOKEN_ENDPOINT = "https://auth.tidal.com/v1/oauth2/token"
    private const val CLIENT_ID = "6BDSRdpK9hqEBTgU"
    private const val CLIENT_SECRET = "xeuPmY7nbpZ9IIbLAcQ93shka1VNheUAqN6IcszjTG8="
    private const val SCOPE = "r_usr+w_usr+w_sub"

    /** What a sign-in produced; the same shape a refresh hands back. */
    data class Session(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMillis: Long,
        val userId: Long?,
        val username: String?,
        val countryCode: String?,
        val flow: String,
    ) {
        fun expiresWithin(marginSecs: Long = 300L): Boolean =
            expiresAtMillis - marginSecs * 1000L <= System.currentTimeMillis()
    }

    private val refreshMutex = Mutex()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    val isSignedIn: Boolean get() = _session.value != null

    private lateinit var prefs: android.content.SharedPreferences

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        prefs = EncryptedPrefs.open(context, "bitchord_tidal", "bitchord_tidal_plain")
        restore()
    }

    private fun restore() {
        val access = prefs.getString(KEY_ACCESS, null)?.takeIf { it.isNotBlank() } ?: return
        _session.value = Session(
            accessToken = access,
            refreshToken = prefs.getString(KEY_REFRESH, null)?.takeIf { it.isNotBlank() },
            expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L),
            userId = prefs.getLong(KEY_USER_ID, 0L).takeIf { it > 0L },
            username = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() },
            countryCode = prefs.getString(KEY_COUNTRY, null)?.takeIf { it.isNotBlank() },
            flow = prefs.getString(KEY_FLOW, null) ?: FLOW_WEBCAPTURE,
        )
    }

    fun adopt(session: Session) {
        _session.value = session
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
            .putLong(KEY_USER_ID, session.userId ?: 0L)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_COUNTRY, session.countryCode)
            .putString(KEY_FLOW, session.flow)
            .apply()
    }

    fun signOut() {
        _session.value = null
        prefs.edit().clear().apply()
    }

    /** The account's standing, as the subscription endpoint last told it. */
    enum class Subscription { PREMIUM, FREE, UNKNOWN }

    class UnauthorizedException : Exception("Tidal rejected the access token (401)")

    /**
     * A usable access token, refreshing first when the stored one is at or near
     * expiry. Null when there is no session or the session cannot be revived.
     *
     * A web-capture session has no refresh token: it reports itself unusable
     * once expired rather than pretend a re-login can be automated — the
     * sources screen says "sign in again" off this same answer.
     */
    suspend fun validAccessToken(): String? = refreshMutex.withLock {
        val current = _session.value ?: return null
        if (!current.expiresWithin()) return current.accessToken
        when (current.flow) {
            FLOW_PKCE -> refreshLocked(current)
            else -> {
                TrackLog.w(TAG, "Tidal web-capture session expired; sign-in required")
                null
            }
        }
    }

    private suspend fun refreshLocked(current: Session): String? =
        withContext(Dispatchers.IO) {
            val refreshToken = current.refreshToken ?: return@withContext null
            val body = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .add("scope", SCOPE)
                .build()
            val request = Request.Builder().url(TOKEN_ENDPOINT).post(body).build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    val payload = response.body?.string().orEmpty()
                    if (response.code == 401) throw UnauthorizedException()
                    if (!response.isSuccessful || payload.isBlank()) {
                        TrackLog.w(TAG, "Tidal token refresh failed: HTTP ${response.code}")
                        return@use null
                    }
                    fromTokenPayload(payload, flow = FLOW_PKCE)
                }
            }.getOrElse {
                if (it is UnauthorizedException) throw it
                TrackLog.w(TAG, "Tidal token refresh error: ${it.message}")
                null
            }?.also { refreshed ->
                adopt(refreshed.copy(userId = refreshed.userId ?: current.userId))
                TrackLog.d(TAG, "Tidal token refreshed for ${refreshed.username ?: "account"}")
            }?.accessToken
        }

    /** Reads an OAuth token response (both the code exchange and the refresh use it). */
    fun fromTokenPayload(payload: String, flow: String): Session? {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val access = json.optString("access_token").takeIf { it.isNotBlank() } ?: return null
        val user = json.optJSONObject("user")
        return Session(
            accessToken = access,
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
            expiresAtMillis = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L,
            userId = user?.optLong("userId")?.takeIf { it > 0L },
            username = user?.optString("username")?.takeIf { it.isNotBlank() },
            countryCode = user?.optString("countryCode")?.takeIf { it.isNotBlank() },
            flow = flow,
        )
    }

    /**
     * Whether a token's own JWT claims put it in the past, for a web-capture
     * session whose expiry was never told to us — the web player's tokens are
     * one-hour JWTs and the claim is the only expiry there is.
     */
    fun jwtExpired(token: String, marginSecs: Long = 300L): Boolean {
        val payload = token.split('.').getOrNull(1) ?: return false
        return runCatching {
            val decoded = java.util.Base64.getUrlDecoder().decode(payload).toString(Charsets.UTF_8)
            val exp = JSONObject(decoded).optLong("exp", 0L)
            exp > 0L && exp - marginSecs <= System.currentTimeMillis() / 1000L
        }.getOrDefault(false)
    }
}
