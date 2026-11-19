/*
 * Neiro (2026)
 * © 4nx3b — github.com/4nx3b
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package com.music.bitchord.data.canvas

import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object CanvasHealth {

    enum class Status { OK, FAIL, NOT_CONNECTED }

    data class Report(
        val spotify: Status,
        val appleMusic: Status,
        val tidal: Status,
        val community: Status,
    )

    suspend fun check(): Report = coroutineScope {
        val spotify = async {
            if (AppSettings.spotifySpdcToken.value.isBlank()) {
                Status.NOT_CONNECTED
            } else {
                withTimeoutOrNull(SPOTIFY_PROBE_MS) {
                    SpotifyCanvas.probeHealthy()
                }?.toStatus() ?: Status.FAIL
            }
        }
        val appleMusic = async {
            withTimeoutOrNull(PROBE_MS) {
                withContext(Dispatchers.IO) { AppleMusicCanvas.probeHealthy() }
            }?.toStatus() ?: Status.FAIL
        }
        val tidal = async {
            withTimeoutOrNull(PROBE_MS) {
                withContext(Dispatchers.IO) { TidalCanvas.probeHealthy() }
            }?.toStatus() ?: Status.FAIL
        }
        val community = async {
            withTimeoutOrNull(PROBE_MS) {
                withContext(Dispatchers.IO) { CommunityCanvas.probeHealthy() }
            }?.toStatus() ?: Status.FAIL
        }
        Report(
            spotify = spotify.await(),
            appleMusic = appleMusic.await(),
            tidal = tidal.await(),
            community = community.await(),
        )
    }

    private fun Boolean.toStatus(): Status = if (this) Status.OK else Status.FAIL

    private const val PROBE_MS = 20_000L
    private const val SPOTIFY_PROBE_MS = 45_000L
}
