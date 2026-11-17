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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.auth.clearSpotifyWebSession
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.spotify_logo
import com.music.bitchord.ui.components.IosSwitch
import com.music.bitchord.ui.icons.SfIcons
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.painterResource

/**
 * The Integration page, ported from ArchiveTune's Integration screen and given
 * the rows this app actually has. The categorization is upstream's: a General
 * group for Discord, Music sources for the per-service accounts and the
 * source manager, External sources for Spotify, and Scrobbling for the
 * last.fm / Libre.fm / ListenBrainz services. ArchiveTune's AI Integration,
 * Apple Music, Telegram, cross-service playlist import and Source Pool rows
 * stay off this page — nothing behind them exists here yet — and the account
 * sign-in itself stays on the Account page, because who is listening is not
 * what the app talks to.
 */
@Composable
fun IntegrationScreen(
    onOpenSources: () -> Unit,
    onOpenDiscord: () -> Unit,
    onOpenSpotify: () -> Unit,
    onOpenListenBrainzLogin: () -> Unit,
    onOpenLastfmLogin: () -> Unit,
    onChooseLastfmService: () -> Unit,
    onSignInTidal: () -> Unit,
    onSignOutTidal: () -> Unit,
    onSignInDeezer: () -> Unit,
    onSignOutDeezer: () -> Unit,
    onSignInQobuz: () -> Unit,
    onSignOutQobuz: () -> Unit,
    onSignInSoundCloud: () -> Unit,
    onSignOutSoundCloud: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val party by ListenTogether.state.collectAsStateWithLifecycle()
    val directSourceStream by AppSettings.directSourceStream.collectAsStateWithLifecycle()

    val tidalSession by com.music.bitchord.data.tidal.TidalAccountStore.session.collectAsStateWithLifecycle()
    val tidalSignedIn = tidalSession != null
    val deezerAccount by com.music.bitchord.data.deezer.DeezerAccountStore.account.collectAsStateWithLifecycle()
    val deezerSignedIn = deezerAccount != null
    val qobuzAccount by com.music.bitchord.data.qobuz.QobuzAccountStore.account.collectAsStateWithLifecycle()
    val qobuzSignedIn = qobuzAccount != null
    val soundCloudAccount by com.music.bitchord.data.soundcloud.SoundCloudAccountStore.account.collectAsStateWithLifecycle()
    val soundCloudSignedIn = soundCloudAccount != null

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
    val spotifyConnected by AppSettings.spotifySpdcToken.collectAsStateWithLifecycle()

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
                icon = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.ic_discord),
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

        SettingsGroup(
            header = stringResource(R.string.music_sources),
        ) {
            val sourceTitle = stringResource(R.string.source)
            val sourceSubtitle = stringResource(R.string.sources_subtitle)
            SettingsRow(
                icon = SfIcons.Extension,
                title = sourceTitle,
                subtitle = sourceSubtitle,
                onClick = onOpenSources,
            )
            RowDivider()
            val directSourceTitle = stringResource(R.string.direct_source_stream)
            SettingsSubRow(
                title = directSourceTitle,
                checked = directSourceStream && !party.inParty,
                onCheckedChange = AppSettings::setDirectSourceStream,
                subtitle = stringResource(R.string.direct_source_stream_in_party).takeIf { party.inParty },
                enabled = !party.inParty,
            )
            RowDivider()
            SettingsRow(
                icon = SfIcons.Waves,
                title = stringResource(R.string.tidal_account),
                subtitle = tidalSession?.username
                    ?: stringResource(R.string.tidal_not_signed_in),
                value = when (tidalSession?.flow) {
                    com.music.bitchord.data.tidal.TidalAccountStore.FLOW_PKCE -> stringResource(R.string.tidal_full_access)
                    null -> null
                    else -> stringResource(R.string.tidal_session_limited)
                },
                onClick = if (tidalSignedIn) onSignOutTidal else onSignInTidal,
            )
            RowDivider()
            SettingsRow(
                icon = SfIcons.MusicNote,
                title = stringResource(R.string.deezer_account),
                subtitle = deezerAccount?.name
                    ?: stringResource(R.string.deezer_not_signed_in),
                value = if (deezerAccount?.lossless == true) {
                    stringResource(R.string.deezer_lossless_account)
                } else {
                    null
                },
                onClick = if (deezerSignedIn) onSignOutDeezer else onSignInDeezer,
            )
            RowDivider()
            SettingsRow(
                icon = SfIcons.SurroundSound,
                title = stringResource(R.string.qobuz_account),
                subtitle = qobuzAccount?.label
                    ?: stringResource(R.string.qobuz_not_signed_in),
                value = if (qobuzAccount?.premium == true) {
                    stringResource(R.string.qobuz_lossless_account)
                } else {
                    null
                },
                onClick = if (qobuzSignedIn) onSignOutQobuz else onSignInQobuz,
            )
            RowDivider()
            SettingsRow(
                icon = SfIcons.Cloud,
                title = stringResource(R.string.soundcloud_account),
                subtitle = soundCloudAccount?.username
                    ?: stringResource(R.string.soundcloud_not_signed_in),
                value = if (soundCloudSignedIn) {
                    stringResource(R.string.soundcloud_signed_in_value)
                } else {
                    null
                },
                onClick = if (soundCloudSignedIn) onSignOutSoundCloud else onSignInSoundCloud,
            )
        }

        SettingsGroup(
            header = stringResource(R.string.external_sources),
            footer = stringResource(R.string.spotify_connect_subtitle),
        ) {
            SettingsRow(
                iconPainter = painterResource(Res.drawable.spotify_logo),
                title = stringResource(R.string.spotify),
                subtitle = stringResource(
                    if (spotifyConnected.isNotBlank()) R.string.connected else R.string.tap_to_connect,
                ),
                onClick = onOpenSpotify,
            )
            if (spotifyConnected.isNotBlank()) {
                RowDivider()
                DestructiveRow(
                    label = stringResource(R.string.spotify_disconnect),
                    onClick = {
                        clearSpotifyWebSession()
                        AppSettings.setSpotifySpdcToken("")
                    },
                )
            }
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
