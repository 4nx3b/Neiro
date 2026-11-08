package com.music.bitchord.data.deezer

import android.content.Context
import com.music.bitchord.auth.EncryptedPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DeezerAccountStore {
    data class Account(
        val arl: String,
        val name: String,
        val lossless: Boolean,
    )

    private const val KEY_ARL = "arl"
    private const val KEY_NAME = "name"
    private const val KEY_LOSSLESS = "lossless"

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    val isSignedIn: Boolean get() = _account.value != null

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = EncryptedPrefs.open(context, "bitchord_deezer", "bitchord_deezer_plain")
        restore()
    }

    private fun restore() {
        val arl = prefs.getString(KEY_ARL, null)?.takeIf { it.isNotBlank() } ?: return
        _account.value = Account(
            arl = arl,
            name = prefs.getString(KEY_NAME, null)?.takeIf { it.isNotBlank() } ?: "Deezer",
            lossless = prefs.getBoolean(KEY_LOSSLESS, false),
        )
    }

    fun adopt(account: Account) {
        _account.value = account
        prefs.edit()
            .putString(KEY_ARL, account.arl)
            .putString(KEY_NAME, account.name)
            .putBoolean(KEY_LOSSLESS, account.lossless)
            .apply()
    }

    fun signOut() {
        DeezerService.forgetSession()
        _account.value = null
        prefs.edit().clear().apply()
    }
}
