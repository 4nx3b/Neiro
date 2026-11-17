/*
 * Neiro (2026)
 * © 4nx3b — github.com/4nx3b
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package com.music.bitchord.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.canvas.CanvasHealth
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.GlassAlertDialog
import com.music.bitchord.ui.components.IosSwitch
import com.music.bitchord.ui.icons.SfIcons
import kotlinx.coroutines.launch

@Composable
fun CanvasSettingsScreen(
    onSpotifyCanvasAuth: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val animatedCanvas by AppSettings.animatedCanvas.collectAsStateWithLifecycle()
    val spotifyCanvasEnabled by AppSettings.spotifyCanvasEnabled.collectAsStateWithLifecycle()
    val canvasOverCellular by AppSettings.canvasOverCellular.collectAsStateWithLifecycle()
    val canvasInHeaders by AppSettings.canvasInHeaders.collectAsStateWithLifecycle()
    val prioritizeSpotifyCanvas by AppSettings.prioritizeSpotifyCanvas.collectAsStateWithLifecycle()
    val tidalArtworkFallback by AppSettings.tidalArtworkFallback.collectAsStateWithLifecycle()
    val animateStaticArtwork by AppSettings.animateStaticArtwork.collectAsStateWithLifecycle()
    var checking by remember { mutableStateOf(false) }
    var healthReport by remember { mutableStateOf<CanvasHealth.Report?>(null) }
    var showPriorityDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        SettingsPageTitle(stringResource(R.string.canvas))

        SettingsGroup(
            header = stringResource(R.string.canvas_artwork_section),
            footer = stringResource(R.string.canvas_page_footer),
        ) {
            val neiroCanvasTitle = stringResource(R.string.neiro_canvas)
            SettingsRow(
                icon = SfIcons.Video,
                title = neiroCanvasTitle,
                subtitle = stringResource(R.string.neiro_canvas_subtitle),
                trailing = {
                    IosSwitch(
                        checked = animatedCanvas,
                        onCheckedChange = AppSettings::setAnimatedCanvas,
                    )
                },
                onClick = { AppSettings.setAnimatedCanvas(!animatedCanvas) },
            )
            RowDivider()
            val spotifyCanvasTitle = stringResource(R.string.spotify_canvas)
            SettingsRow(
                icon = SfIcons.PlayCircle,
                title = spotifyCanvasTitle,
                subtitle = stringResource(R.string.spotify_canvas_subtitle),
                trailing = {
                    IosSwitch(
                        checked = spotifyCanvasEnabled,
                        onCheckedChange = AppSettings::setSpotifyCanvasEnabled,
                    )
                },
                onClick = { AppSettings.setSpotifyCanvasEnabled(!spotifyCanvasEnabled) },
            )
            RowDivider()
            val coverCellularTitle = stringResource(R.string.animated_cover_cellular)
            SettingsRow(
                icon = SfIcons.SignalCellularAlt,
                title = coverCellularTitle,
                subtitle = stringResource(R.string.animated_cover_cellular_subtitle),
                trailing = {
                    IosSwitch(
                        checked = canvasOverCellular,
                        onCheckedChange = AppSettings::setCanvasOverCellular,
                    )
                },
                onClick = { AppSettings.setCanvasOverCellular(!canvasOverCellular) },
            )
            if (animatedCanvas) {
                RowDivider()
                val integrateTitle = stringResource(R.string.integrate_spotify_canvas)
                SettingsRow(
                    icon = SfIcons.Key,
                    title = integrateTitle,
                    subtitle = stringResource(R.string.integrate_spotify_canvas_subtitle),
                    trailing = { Chevron() },
                    onClick = onSpotifyCanvasAuth,
                )
            }
            RowDivider()
            val headersTitle = stringResource(R.string.canvas_in_headers)
            SettingsRow(
                icon = SfIcons.Album,
                title = headersTitle,
                subtitle = stringResource(R.string.canvas_in_headers_subtitle),
                trailing = {
                    IosSwitch(
                        checked = canvasInHeaders,
                        onCheckedChange = AppSettings::setCanvasInHeaders,
                    )
                },
                onClick = { AppSettings.setCanvasInHeaders(!canvasInHeaders) },
            )
            RowDivider()
            val canvasCheckTitle = stringResource(R.string.canvas_check)
            SettingsRow(
                icon = SfIcons.Cached,
                title = canvasCheckTitle,
                subtitle = stringResource(R.string.canvas_check_subtitle),
                enabled = !checking,
                trailing = {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                onClick = {
                    checking = true
                    scope.launch {
                        healthReport = CanvasHealth.check()
                        checking = false
                    }
                },
            )
            RowDivider()
            val tidalArtworkTitle = stringResource(R.string.use_tidal_artwork)
            SettingsRow(
                icon = SfIcons.Waves,
                title = tidalArtworkTitle,
                subtitle = stringResource(R.string.use_tidal_artwork_subtitle),
                trailing = {
                    IosSwitch(
                        checked = tidalArtworkFallback,
                        onCheckedChange = AppSettings::setTidalArtworkFallback,
                    )
                },
                onClick = { AppSettings.setTidalArtworkFallback(!tidalArtworkFallback) },
            )
            RowDivider()
            val priorityTitle = stringResource(R.string.artwork_priority)
            SettingsRow(
                icon = SfIcons.Tune,
                title = priorityTitle,
                subtitle = if (prioritizeSpotifyCanvas) {
                    stringResource(R.string.artwork_priority_order_spotify_first)
                } else {
                    stringResource(R.string.artwork_priority_order_spotify_last)
                },
                value = stringResource(
                    if (prioritizeSpotifyCanvas) {
                        R.string.artwork_priority_spotify_first
                    } else {
                        R.string.artwork_priority_spotify_last
                    },
                ),
                trailing = { Chevron() },
                onClick = { showPriorityDialog = true },
            )
        }

        SettingsGroup(header = stringResource(R.string.animate_static_album_art)) {
            val animateTitle = stringResource(R.string.animate_static_album_art)
            SettingsRow(
                icon = SfIcons.Animation,
                title = animateTitle,
                subtitle = stringResource(R.string.animate_static_album_art_subtitle),
                trailing = {
                    IosSwitch(
                        checked = animateStaticArtwork,
                        onCheckedChange = AppSettings::setAnimateStaticArtwork,
                    )
                },
                onClick = { AppSettings.setAnimateStaticArtwork(!animateStaticArtwork) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (healthReport != null) {
        val report = healthReport
        GlassAlertDialog(
            onDismissRequest = { healthReport = null },
            title = { Text(stringResource(R.string.canvas_check_dialog_title)) },
            text = {
                Column {
                    healthLine(
                        stringResource(R.string.spotify),
                        report!!.spotify,
                    )
                    healthLine(
                        stringResource(R.string.canvas_source_apple_music),
                        report.appleMusic,
                    )
                    healthLine(
                        stringResource(R.string.canvas_source_tidal),
                        report.tidal,
                    )
                    healthLine(
                        stringResource(R.string.canvas_source_community),
                        report.community,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { healthReport = null }) {
                    Text(stringResource(R.string.ok))
                }
            },
        )
    }

    if (showPriorityDialog) {
        GlassAlertDialog(
            onDismissRequest = { showPriorityDialog = false },
            title = { Text(stringResource(R.string.artwork_priority)) },
            text = {
                Column {
                    priorityOption(
                        label = stringResource(R.string.artwork_priority_spotify_first),
                        detail = stringResource(R.string.artwork_priority_spotify_first_detail),
                        selected = prioritizeSpotifyCanvas,
                    ) {
                        AppSettings.setPrioritizeSpotifyCanvas(true)
                        showPriorityDialog = false
                    }
                    priorityOption(
                        label = stringResource(R.string.artwork_priority_spotify_last),
                        detail = stringResource(R.string.artwork_priority_spotify_last_detail),
                        selected = !prioritizeSpotifyCanvas,
                    ) {
                        AppSettings.setPrioritizeSpotifyCanvas(false)
                        showPriorityDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPriorityDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun healthLine(name: String, status: CanvasHealth.Status) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                when (status) {
                    CanvasHealth.Status.OK -> R.string.canvas_health_ok
                    CanvasHealth.Status.FAIL -> R.string.canvas_health_fail
                    CanvasHealth.Status.NOT_CONNECTED -> R.string.canvas_health_not_connected
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = when (status) {
                CanvasHealth.Status.OK -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.error
            },
        )
    }
}

@Composable
private fun priorityOption(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected) {
                Icon(
                    imageVector = SfIcons.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
