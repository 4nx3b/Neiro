package com.music.bitchord

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.theme.BitChordTheme
import moe.rukamori.archivetune.bridge.ArchiveTuneSettingsPage
import moe.rukamori.archivetune.bridge.ArchiveTuneSettingsPageId

/**
 * One of ArchiveTune's own settings pages (Appearance, Player, Lyrics, …),
 * ported byte-for-byte into :archivetuneplayer and opened from the
 * "ArchiveTune player & appearance" group in Settings.
 *
 * A screen of its own rather than a page inside the settings sheet: the
 * ported pages are full-screen LazyColumns with their own top bar, and the
 * sheet lays its pages out in a vertical scroll, inside which a LazyColumn
 * cannot be measured. Themed exactly as the main activity is, so the pages
 * read the same colours and font as the rest of the app.
 */
class ArchiveTuneSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val page = ArchiveTuneSettingsPageId.fromName(intent.getStringExtra(EXTRA_PAGE))
        if (page == null) {
            finish()
            return
        }
        setContent {
            val theme by AppSettings.themeMode.collectAsStateWithLifecycle()
            val appFont by AppSettings.appFont.collectAsStateWithLifecycle()
            val darkTheme = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            BitChordTheme(darkTheme = darkTheme, font = appFont) {
                ArchiveTuneSettingsPage(page = page, onBack = { finish() })
            }
        }
    }

    companion object {
        private const val EXTRA_PAGE = "page"

        fun open(context: Context, page: ArchiveTuneSettingsPageId) {
            context.startActivity(
                Intent(context, ArchiveTuneSettingsActivity::class.java)
                    .putExtra(EXTRA_PAGE, page.name)
                    .addFlags(if (context is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
