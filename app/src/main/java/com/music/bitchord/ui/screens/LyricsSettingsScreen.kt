package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.lyrics.translationLanguageName
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.IosSwitch
import com.music.bitchord.ui.icons.SfIcons
import java.util.Locale

/**
 * The Lyrics sub-setting page under Playback, in the place ArchiveTune's player
 * settings put it: everything about where lyrics come from and what is done
 * with them once they arrive — the master switch, the look of the unfocused
 * lines, which providers are asked in which order, and the language the
 * translation line is rendered in. The rows only exist while the master
 * switch is on, exactly as they did where these used to sit on the
 * appearance page: nothing to choose between while the feature is off, and
 * the sources are third-party services being reached on the listener's
 * connection — which is the part worth being able to narrow.
 */
@Composable
fun LyricsSettingsScreen(
    onLyricsSources: () -> Unit,
    onTranslationLanguage: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val syncedLyrics by AppSettings.syncedLyrics.collectAsStateWithLifecycle()
    val lyricsBlur by AppSettings.lyricsBlur.collectAsStateWithLifecycle()
    val lyricsSources by AppSettings.lyricsSources.collectAsStateWithLifecycle()
    val translationLanguage by AppSettings.translationLanguage.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        SettingsPageTitle(stringResource(R.string.lyrics))

        SettingsGroup(
            footer = stringResource(R.string.lyrics_settings_footer),
            topSpacing = 0.dp,
        ) {
            val syncedLyricsTitle = stringResource(R.string.synced_lyrics)
            SettingsRow(
                icon = SfIcons.Notes,
                title = syncedLyricsTitle,
                subtitle = stringResource(R.string.synced_lyrics_subtitle),
                trailing = {
                    IosSwitch(
                        checked = syncedLyrics,
                        onCheckedChange = AppSettings::setSyncedLyrics,
                    )
                },
                onClick = { AppSettings.setSyncedLyrics(!syncedLyrics) },
            )
            // Nothing to choose between while the feature is off, and the
            // rows below are third-party services being reached on the
            // listener's connection — which is the part worth being able to
            // narrow.
            if (syncedLyrics) {
                RowDivider()
                val lyricsBlurTitle = stringResource(R.string.blur_unfocused_lyrics)
                SettingsRow(
                    icon = SfIcons.BlurOn,
                    title = lyricsBlurTitle,
                    subtitle = stringResource(R.string.blur_unfocused_lyrics_subtitle),
                    trailing = {
                        IosSwitch(
                            checked = lyricsBlur,
                            onCheckedChange = AppSettings::setLyricsBlur,
                        )
                    },
                    onClick = { AppSettings.setLyricsBlur(!lyricsBlur) },
                )
                RowDivider()
                val lyricsSourcesTitle = stringResource(R.string.lyrics_sources)
                SettingsRow(
                    icon = SfIcons.Language,
                    title = lyricsSourcesTitle,
                    subtitle = lyricsSources
                        .sortedBy { it.ordinal }
                        .joinToString(", ") { it.label }
                        .ifEmpty { stringResource(R.string.no_lyrics_sources_enabled) },
                    trailing = { Chevron() },
                    onClick = onLyricsSources,
                )
                RowDivider()
                val translationLanguageTitle = stringResource(R.string.translation_language)
                SettingsRow(
                    icon = SfIcons.Translate,
                    title = translationLanguageTitle,
                    subtitle = if (translationLanguage.isBlank()) {
                        stringResource(R.string.translation_language_subtitle)
                    } else {
                        translationLanguageName(
                            translationLanguage,
                            AppCompatDelegate.getApplicationLocales().get(0) ?: Locale.getDefault(),
                        )
                    },
                    trailing = { Chevron() },
                    onClick = onTranslationLanguage,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
