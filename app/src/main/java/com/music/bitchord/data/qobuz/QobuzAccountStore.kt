package com.music.bitchord.data.qobuz

import android.content.Context
import com.music.bitchord.auth.EncryptedPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object QobuzAccountStore {
    data class Account(
        val userAuthToken: String,
        val appId: String,
        val appSecret: String,
        val label: String,
        val premium: Boolean,
    )

    private const val KEY_TOKEN = "user_auth_token"
    private const val KEY_APP_ID = "app_id"
    private const val KEY_APP_SECRET = "app_secret"
    private const val KEY_LABEL = "label"
    private const val KEY_PREMIUM = "premium"

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    val isSignedIn: Boolean get() = _account.value != null

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = EncryptedPrefs.open(context, "bitchord_qobuz", "bitchord_qobuz_plain")
        restore()
    }

    private fun restore() {
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return
        val appId = prefs.getString(KEY_APP_ID, null)?.takeIf { it.isNotBlank() } ?: return
        val appSecret = prefs.getString(KEY_APP_SECRET, null)?.takeIf { it.isNotBlank() } ?: return
        _account.value = Account(
            userAuthToken = token,
            appId = appId,
            appSecret = appSecret,
            label = prefs.getString(KEY_LABEL, null)?.takeIf { it.isNotBlank() } ?: "Qobuz",
            premium = prefs.getBoolean(KEY_PREMIUM, false),
        )
    }

    fun adopt(account: Account) {
        _account.value = account
        prefs.edit()
            .putString(KEY_TOKEN, account.userAuthToken)
            .putString(KEY_APP_ID, account.appId)
            .putString(KEY_APP_SECRET, account.appSecret)
            .putString(KEY_LABEL, account.label)
            .putBoolean(KEY_PREMIUM, account.premium)
            .apply()
    }

    fun signOut() {
        QobuzService.forgetCaches()
        _account.value = null
        prefs.edit().clear().apply()
    }
}
