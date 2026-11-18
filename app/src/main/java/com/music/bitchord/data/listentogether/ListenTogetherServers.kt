/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Listen Together public server list — ported from vivi-music (beta branch),
 * vivi-music's listentogether.ListenTogetherServers (GPL-3.0), plus the
 * Metrolist project's community server (metrolistgroup/metrolist, GPL-3.0).
 *
 * PORT-NOTE (Neiro): the retired vivi server (ViviMusic Sync Server,
 * wss://vivimusic-listen-together.onrender.com) is back on the list as a live
 * entry alongside Hugging Face Sync and The Meowery, per the owner's server
 * picker screenshots — so it serves /listen invite links again and no longer
 * needs a separate retired-host fallback.
 */



package com.music.bitchord.data.listentogether

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire protocol a Listen Together server speaks. All vivi-derived servers are
 * JSON; Metrolist's metroserver (The Meowery) is protobuf-only and rejects JSON
 * frames with an `invalid_message` error.
 */
@Serializable
enum class ListenTogetherProtocol {
    JSON,
    PROTOBUF,
}

@Serializable
data class ListenTogetherServer(
    val name: String,
    val url: String,
    val location: String,
    val operator: String,
    val protocol: ListenTogetherProtocol = ListenTogetherProtocol.JSON,
)

object ListenTogetherServers {
    private const val ServersJson = """
        [
          {
            "name": "Hugging Face Sync",
            "url": "wss://devilmi-vivi-music-listen-together.hf.space",
            "location": "Global",
            "operator": "VIVIDH"
          },
          {
            "name": "ViviMusic Sync Server",
            "url": "wss://vivimusic-listen-together.onrender.com",
            "location": "USA",
            "operator": "Vividh"
          },
          {
            "name": "The Meowery",
            "url": "wss://metroserverx.meowery.eu/ws",
            "location": "Poland",
            "operator": "Nyx",
            "protocol": "PROTOBUF"
          }
        ]
    """

    private val json = Json { ignoreUnknownKeys = true }

    val servers: List<ListenTogetherServer> by lazy {
        json.decodeFromString(ServersJson)
    }

    val defaultServerUrl: String
        get() = servers.first().url

    fun findByUrl(url: String): ListenTogetherServer? = servers.firstOrNull { it.url == url }

    /** Host part of a server URL, e.g. `wss://host/ws` -> `host`. */
    fun hostOf(url: String): String? =
        url
            .trim()
            .substringAfter("://", missingDelimiterValue = url.trim())
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore(':')
            .lowercase()
            .takeIf { it.isNotBlank() }

    /**
     * Host of the web invite link for [serverUrl]. Only JSON (vivi) servers serve the `/listen`
     * web client; anything else, including a custom or LAN URL, falls back to the default server.
     */
    fun inviteHost(serverUrl: String): String {
        val server = findByUrl(serverUrl)?.takeIf { it.protocol == ListenTogetherProtocol.JSON }
        return server?.let { hostOf(it.url) }
            ?: hostOf(defaultServerUrl)
            ?: "vivimusic-listen-together.onrender.com"
    }

    /** Hosts whose `https://<host>/listen?code=` links open the app. */
    val inviteHosts: Set<String> by lazy {
        servers
            .filter { it.protocol == ListenTogetherProtocol.JSON }
            .mapNotNull { hostOf(it.url) }
            .toSet()
    }

    fun isInviteHost(host: String?): Boolean = host != null && host.lowercase() in inviteHosts
}
