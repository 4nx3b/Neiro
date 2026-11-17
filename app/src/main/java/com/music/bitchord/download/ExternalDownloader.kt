/*
 * Neiro (2026)
 * © 4nx3b — github.com/4nx3b
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package com.music.bitchord.download

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.SourceTrackKeys

object ExternalDownloader {

    enum class LaunchResult { STARTED, NOT_CONFIGURED, NOT_INSTALLED }

    /**
     * A YouTube track's watch page — the one address an external downloader
     * can reach on its own. Null for anything else: a source-backed track
     * rides this app's accounts and decryption, which no other app holds.
     */
    fun watchUrlOf(song: Song): String? {
        if (song.localUri != null) return null
        val id = song.videoId
        if (id.isBlank() || SourceTrackKeys.parse(id) != null) return null
        return "https://music.youtube.com/watch?v=$id"
    }

    fun openFor(context: Context, song: Song): LaunchResult {
        val packageName = AppSettings.externalDownloaderPackage.value.trim()
            .removePrefix("package:")
            .trim()
            .takeIf(String::isNotBlank)
            ?: return LaunchResult.NOT_CONFIGURED
        val url = watchUrlOf(song) ?: return LaunchResult.NOT_CONFIGURED
        val uri = Uri.parse(url)
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            context.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        return intents.firstOrNull { tryStart(context, it) }
            ?.let { LaunchResult.STARTED }
            ?: LaunchResult.NOT_INSTALLED
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
