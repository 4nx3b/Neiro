/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Listen Together invite links — the share URL the room screen hands out
 * (https://<server host>/listen?code=X, see [ListenTogetherServers.inviteHosts])
 * and the direct custom scheme (bitchord://listen?code=X).
 *
 * PORT-NOTE (Neiro): replaces this app's old `data.together.TogetherDeepLink`
 * (a LAN session key link) with the vivi-style room-code link. The chat
 * notification's launch extra ("navigate_to" = "listen_together/chat") rides
 * the same consumer so a shade reply tap lands on the chat page.
 */

package com.music.bitchord.data.listentogether

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ListenTogetherInvite {
    /** The scheme of the app's own deep links, e.g. `bitchord://listen?code=X`. */
    private const val APP_SCHEME = "bitchord"

    /** The host of a deep link about Listen Together, as Uri.host spells it. */
    private const val APP_HOST = "listen"

    /** The path the web client serves its join page on. */
    private const val WEB_PATH = "listen"

    /** The room code parsed out of the last consumed invite, until it is handled. */
    private val _pending = MutableStateFlow<String?>(null)

    /** An invite's code awaiting the room screen's join flow; null when none. */
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Whether a consumed intent asked for the chat page (a shade reply tap). */
    private val _openChat = MutableStateFlow(false)

    /** Cleared by the activity once it has navigated to the chat page. */
    val openChat: StateFlow<Boolean> get() = _openChat.asStateFlow()

    /** The room code a Listen Together link carries, or null when it is not one. */
    fun roomCodeOf(uri: Uri): String? {
        val code = uri.getQueryParameter("code")
            ?: uri.getQueryParameter("room")
            ?: uri.pathSegments.getOrNull(1)
            ?: return null
        if (code.isBlank()) return null
        val isAppLink = uri.scheme?.equals(APP_SCHEME, ignoreCase = true) == true &&
            uri.host?.equals(APP_HOST, ignoreCase = true) == true
        val isWebLink = uri.pathSegments.firstOrNull() == WEB_PATH ||
            ListenTogetherServers.isInviteHost(uri.host)
        return if (isAppLink || isWebLink) code.take(8).uppercase() else null
    }

    /**
     * Reads [intent] for an invite or a chat-navigation extra — the launch and
     * the new-intent paths both come here.
     */
    fun consume(intent: Intent?) {
        if (intent == null) return
        if (intent.getStringExtra("navigate_to") == "listen_together/chat") {
            _openChat.value = true
        }
        val data = intent.data ?: return
        val code = runCatching { roomCodeOf(data) }.getOrNull() ?: return
        _pending.value = code
    }

    /** The room screen has joined (or refused) the pending invite. */
    fun handled() {
        _pending.value = null
    }

    /** The activity has navigated to the chat page. */
    fun chatHandled() {
        _openChat.value = false
    }
}
