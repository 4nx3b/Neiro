package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.IosSwitch
import com.music.bitchord.ui.icons.SfIcons
import kotlin.math.roundToInt

/**
 * The Integration page, ported from ArchiveTune's Integration screen and given
 * the rows this app actually has: a General group for Discord and a
 * Scrobbling group for the last.fm / Libre.fm / ListenBrainz services, with
 * the work-out-genres switch directly under the Last.fm sign-in row, since
 * Last.fm is who the genres are asked of. The per-service accounts, the
 * source manager they stream through and Spotify all live on the Sources
 * page, so this page holds the services left over rather than repeating
 * theirs. ArchiveTune's AI Integration, Apple Music, Telegram, cross-service
 * playlist import and Source Pool rows stay off this page — nothing behind
 * them exists here yet — and the account sign-in itself stays on the Account
 * page, because who is listening is not what the app talks to.
 */
@Composable
fun IntegrationScreen(
    onOpenDiscord: () -> Unit,
    onOpenListenBrainzLogin: () -> Unit,
    onOpenLastfmLogin: () -> Unit,
    onChooseLastfmService: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val lastfmEnabled by AppSettings.lastfmEnabled.collectAsStateWithLifecycle()
    val lastfmUsername by AppSettings.lastfmUsername.collectAsStateWithLifecycle()
    val lastfmSessionKey by AppSettings.lastfmSessionKey.collectAsStateWithLifecycle()
    val lastfmEndpoint by AppSettings.lastfmEndpoint.collectAsStateWithLifecycle()
    val lastfmScrobbleEnabled by AppSettings.lastfmScrobbleEnabled.collectAsStateWithLifecycle()
    val lastfmNowPlayingEnabled by AppSettings.lastfmNowPlaying.collectAsStateWithLifecycle()
    val lastfmPrimaryArtistOnly by AppSettings.lastfmPrimaryArtistOnly.collectAsStateWithLifecycle()
    val scrobbleMinDuration by AppSettings.scrobbleMinDuration.collectAsStateWithLifecycle()
    val scrobbleDelayPercent by AppSettings.scrobbleDelayPercent.collectAsStateWithLifecycle()
    val scrobbleDelaySeconds by AppSettings.scrobbleDelaySeconds.collectAsStateWithLifecycle()
    val listenBrainzEnabled by AppSettings.listenBrainzEnabled.collectAsStateWithLifecycle()
    val listenBrainzToken by AppSettings.listenBrainzToken.collectAsStateWithLifecycle()
    val listenBrainzPrimaryArtistOnly by AppSettings.listenBrainzPrimaryArtistOnly.collectAsStateWithLifecycle()
    val discordToken by AppSettings.discordToken.collectAsStateWithLifecycle()
    val discordUsername by AppSettings.discordUsername.collectAsStateWithLifecycle()
    val discordRpcEnabled by AppSettings.discordRpcEnabled.collectAsStateWithLifecycle()
    val replayGenres by AppSettings.replayGenres.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        SettingsPageTitle(stringResource(R.string.integration))

        SettingsGroup(
            header = stringResource(R.string.general),
            footer = stringResource(R.string.rich_presence_footer),
        ) {
            SettingsRow(
                icon = androidx.compose.ui.res.vectorResource(R.drawable.ic_discord),
                title = "Discord",
                subtitle = when {
                    discordToken.isEmpty() -> stringResource(R.string.tap_to_connect)
                    !discordRpcEnabled -> stringResource(R.string.connected_presence_off)
                    discordUsername.isNotEmpty() -> stringResource(R.string.sharing_as, discordUsername)
                    else -> stringResource(R.string.sharing_listens)
                },
                onClick = onOpenDiscord,
            )
        }

        if (AppSettings.scrobblingAvailable) {
            SettingsGroup(
                header = stringResource(R.string.scrobbling),
                footer = stringResource(R.string.scrobbling_footer),
            ) {
                SettingsRow(
                    icon = SfIcons.Cloud,
                    title = "ListenBrainz",
                    subtitle = if (listenBrainzEnabled && listenBrainzToken.isNotBlank()) {
                        stringResource(R.string.connected)
                    } else {
                        stringResource(R.string.enter_token_to_enable)
                    },
                    trailing = {
                        IosSwitch(
                            checked = listenBrainzEnabled,
                            onCheckedChange = { checked ->
                                if (checked && listenBrainzToken.isBlank()) {
                                    onOpenListenBrainzLogin()
                                } else {
                                    AppSettings.setListenBrainzEnabled(checked)
                                }
                            },
                        )
                    },
                    onClick = onOpenListenBrainzLogin,
                )
                if (listenBrainzEnabled && listenBrainzToken.isNotBlank()) {
                    RowDivider()
                    SettingsRow(
                        icon = SfIcons.GraphicEq,
                        title = stringResource(R.string.scrobble_primary_artist_only),
                        subtitle = stringResource(R.string.scrobble_primary_artist_only_subtitle),
                        trailing = {
                            IosSwitch(
                                checked = listenBrainzPrimaryArtistOnly,
                                onCheckedChange = AppSettings::setListenBrainzPrimaryArtistOnly,
                            )
                        },
                        onClick = { AppSettings.setListenBrainzPrimaryArtistOnly(!listenBrainzPrimaryArtistOnly) },
                    )
                }
                RowDivider()
                SettingsRow(
                    icon = SfIcons.Cloud,
                    title = stringResource(R.string.scrobble_service),
                    subtitle = if (lastfmEndpoint == LastFM.LIBREFM_API_ENDPOINT) {
                        stringResource(R.string.libre_fm)
                    } else {
                        "Last.fm"
                    },
                    onClick = onChooseLastfmService,
                )
                RowDivider()
                SettingsRow(
                    icon = SfIcons.History,
                    title = if (lastfmEndpoint == LastFM.LIBREFM_API_ENDPOINT) {
                        stringResource(R.string.libre_fm)
                    } else {
                        "Last.fm"
                    },
                    subtitle = if (lastfmSessionKey.isNotBlank()) {
                        stringResource(R.string.signed_in_as, lastfmUsername)
                    } else {
                        stringResource(R.string.tap_to_sign_in)
                    },
                    trailing = {
                        IosSwitch(
                            checked = lastfmEnabled,
                            onCheckedChange = { checked ->
                                if (checked && lastfmSessionKey.isBlank()) {
                                    onOpenLastfmLogin()
                                } else {
                                    AppSettings.setLastfmEnabled(checked)
                                }
                            },
                        )
                    },
                    onClick = {
                        if (lastfmSessionKey.isNotBlank()) {
                            AppSettings.setLastfmSessionKey("")
                            AppSettings.setLastfmUsername("")
                            AppSettings.setLastfmEnabled(false)
                            AppSettings.setLastfmScrobbleEnabled(false)
                            AppSettings.setLastfmNowPlaying(false)
                        } else {
                            onOpenLastfmLogin()
                        }
                    },
                )
                // Directly beneath the sign-in row: the genres are asked of
                // Last.fm itself, so the switch travels with the account it
                // needs rather than with the stats it feeds.
                RowDivider()
                SettingsRow(
                    icon = SfIcons.LocalOffer,
                    title = stringResource(R.string.work_out_genres),
                    subtitle = if (replayGenres) {
                        stringResource(R.string.replay_genres_enabled_subtitle)
                    } else {
                        stringResource(R.string.replay_genres_disabled_subtitle)
                    },
                    trailing = {
                        IosSwitch(
                            checked = replayGenres,
                            onCheckedChange = AppSettings::setReplayGenres,
                        )
                    },
                    onClick = { AppSettings.setReplayGenres(!replayGenres) },
                )
                if (lastfmEnabled && lastfmSessionKey.isNotBlank()) {
                    RowDivider()
                    SettingsRow(
                        icon = SfIcons.GraphicEq,
                        title = stringResource(R.string.scrobble_tracks),
                        subtitle = stringResource(R.string.scrobble_tracks_subtitle),
                        trailing = {
                            IosSwitch(
                                checked = lastfmScrobbleEnabled,
                                onCheckedChange = AppSettings::setLastfmScrobbleEnabled,
                            )
                        },
                        onClick = { AppSettings.setLastfmScrobbleEnabled(!lastfmScrobbleEnabled) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = SfIcons.GraphicEq,
                        title = stringResource(R.string.scrobble_primary_artist_only),
                        subtitle = stringResource(R.string.scrobble_primary_artist_only_subtitle),
                        trailing = {
                            IosSwitch(
                                checked = lastfmPrimaryArtistOnly,
                                onCheckedChange = AppSettings::setLastfmPrimaryArtistOnly,
                            )
                        },
                        onClick = { AppSettings.setLastfmPrimaryArtistOnly(!lastfmPrimaryArtistOnly) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = SfIcons.GraphicEq,
                        title = stringResource(R.string.now_playing),
                        subtitle = stringResource(R.string.lastfm_now_playing_subtitle),
                        trailing = {
                            IosSwitch(
                                checked = lastfmNowPlayingEnabled,
                                onCheckedChange = AppSettings::setLastfmNowPlaying,
                            )
                        },
                        onClick = { AppSettings.setLastfmNowPlaying(!lastfmNowPlayingEnabled) },
                    )
                }
            }

            if (lastfmEnabled && lastfmSessionKey.isNotBlank()) {
                SettingsGroup(header = stringResource(R.string.scrobble_timing)) {
                    SliderRow(
                        icon = SfIcons.Tune,
                        title = stringResource(R.string.min_song_duration),
                        subtitle = stringResource(R.string.min_song_duration_subtitle),
                        value = "${scrobbleMinDuration}s",
                        sliderValue = scrobbleMinDuration.toFloat(),
                        onSliderValue = { AppSettings.setScrobbleMinDuration(it.roundToInt()) },
                        valueRange = 15f..120f,
                        steps = 20,
                    )
                    RowDivider()
                    SliderRow(
                        icon = SfIcons.Tune,
                        title = stringResource(R.string.scrobble_delay),
                        subtitle = stringResource(R.string.scrobble_delay_subtitle),
                        value = "${(scrobbleDelayPercent * 100).roundToInt()}%",
                        sliderValue = scrobbleDelayPercent,
                        onSliderValue = { AppSettings.setScrobbleDelayPercent(it) },
                        valueRange = 0.1f..1.0f,
                        steps = 8,
                    )
                    RowDivider()
                    SliderRow(
                        icon = SfIcons.Tune,
                        title = stringResource(R.string.max_delay),
                        subtitle = stringResource(R.string.max_delay_subtitle),
                        value = "${scrobbleDelaySeconds}s",
                        sliderValue = scrobbleDelaySeconds.toFloat(),
                        onSliderValue = { AppSettings.setScrobbleDelaySeconds(it.roundToInt()) },
                        valueRange = 30f..300f,
                        steps = 26,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
