package com.music.bitchord.data.soundcloud

import android.content.Context
import com.music.bitchord.auth.EncryptedPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SoundCloudAccountStore {
    data class Account(
        val token: String,
        val username: String,
    )

    private const val KEY_TOKEN = "token"
    private const val KEY_USERNAME = "username"

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    val isSignedIn: Boolean get() = _account.value != null

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = EncryptedPrefs.open(context, "bitchord_soundcloud", "bitchord_soundcloud_plain")
        restore()
    }

    private fun restore() {
        val token = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return
        _account.value = Account(
            token = token,
            username = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: "SoundCloud",
        )
    }

    fun adopt(account: Account) {
        _account.value = account
        prefs.edit()
            .putString(KEY_TOKEN, account.token)
            .putString(KEY_USERNAME, account.username)
            .apply()
    }

    fun signOut() {
        SoundCloudService.forgetCaches()
        _account.value = null
        prefs.edit().clear().apply()
    }
}
