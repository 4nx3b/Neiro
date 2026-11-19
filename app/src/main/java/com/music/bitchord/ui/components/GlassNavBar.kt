/*
 * The glass navigation bar, ported onto Glasmio's tab bar (the myself-tsuki
 * fork from JitPack): the iOS 27 split-search tab bar — the capsule platter
 * with the trailing Search circle fused into it through the fork's
 * BottomNavigationContact CASDF union, the selection indicator as a native
 * control lens sampling the material backplate, and the platter's own morph
 * into the selected tab's circle on the fold. The now-playing accessory
 * rides above it as its own glass capsule rather than docking into the pill,
 * as the fork's sample lays its floating controls out. The bar's structure
 * and the fold behaviour are BitChord's own, first drawn after
 * EchoMusicApp/Echo-Music's AppFloatingNavBar (GPL-3.0); the shell under
 * them is Glasmio's now.
 */
package com.music.bitchord.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.playback.MusicOnlyPlayback
import com.music.bitchord.ui.player.PlayerDock
import com.music.bitchord.ui.player.AndroidMusicVideo
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.font.FontWeight
import io.github.neoruaa.glasmio.icons.SfSymbol
import io.github.neoruaa.glasmio.scaffold.GlasmioNavigationBarWithTrailingItem
import io.github.neoruaa.glasmio.ui.GlasmioTabItem

/**
 * The liquid glass navigation bar, ported from Glasmio.
 *
 * The tab platter is the fork's own [GlasmioNavigationBarWithTrailingItem]:
 * the Home/Explore/Library capsule with the Search circle as its trailing
 * control, the two fused through the fork's contact-field union shape, the
 * selection lens a native control lens over the material. Scrolling down
 * collapses the platter into the selected tab's circle — Glasmio's compact
 * morph, driven here by [scrollConnection] the page's scroll is dispatched
 * into, so the fold behaves as it always has (see MainActivity's
 * `nestedScroll`). Tapping the collapsed circle expands the bar again.
 *
 * The now-playing accessory is the fork's sample's layout: its own glass
 * capsule above the platter while the bar is expanded, fading away with the
 * fold, rather than iOS 26's one-component dock. [song] null means nothing
 * is playing and the platter and Search stand alone.
 *
 * Every surface here samples the app backdrop — the platter through Glasmio's
 * own GlassSurface, the accessory through [Modifier.liquidGlass] — so this is
 * only ever used where that is supported and switched on; off either,
 * MainActivity draws [MiniPlayer] and [FloatingBottomBar] instead.
 */
@Composable
fun GlassNavBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    scrollConnection: FloatingTabBarScrollConnection,
    song: Song?,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    /** @see com.music.bitchord.data.listentogether.ListenTogether.State.controlsLocked */
    controlsLocked: Boolean = false,
    onBlockedControl: () -> Unit = {},
    /** Where the player's artwork lands when it closes into this bar. */
    dock: PlayerDock? = null,
    /** Dragging the bar's player up into the full player. */
    pull: MiniPlayerPull? = null,
    modifier: Modifier = Modifier,
) {
    // The fold: Glasmio's compact morph running on the fork's own
    // navigation endpoint spring — critically damped, 0.3 s response, the
    // stiffness (2π/0.3)² the fork's navigation motion uses throughout.
    val compactProgressState: State<Float> = animateFloatAsState(
        targetValue = if (scrollConnection.isInline) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = GLASS_NAV_COMPACT_STIFFNESS,
        ),
        label = "glassNavCompact",
    )
    val compact = compactProgressState.value

    // Search is the trailing control in the fork's split-search layout — the
    // circle of its own beside the platter — which is the last tab in
    // BitChord's order anyway.
    val navItems = remember(tabs) {
        tabs.dropLast(1).mapIndexed { index, tab ->
            GlasmioTabItem(
                key = index,
                label = tab.label,
                symbol = when (index) {
                    0 -> SfSymbol.House
                    1 -> SfSymbol.Safari
                    else -> SfSymbol.MusicNoteList
                },
            )
        }
    }
    val trailingItem = remember(tabs) {
        GlasmioTabItem(
            key = tabs.lastIndex,
            label = tabs.last().label,
            symbol = SfSymbol.Search,
        )
    }

    Column(
        modifier
            .windowInsetsPadding(floatingBarInsets)
            .padding(horizontal = NAV_BAR_GUTTER)
            .padding(bottom = NAV_BAR_LIFT)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        song?.let { current ->
            // The now-playing capsule, its own glass surface above the
            // platter, gone with the fold: the compact circle is the whole
            // bar by then, as the fork's sample lays it out.
            GlassNowPlaying(
                song = current,
                isInline = false,
                isPlaying = isPlaying,
                isLoading = isLoading,
                contentColor = glassContentColor(),
                onPlayPause = onPlayPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onExpand = onExpand,
                controlsLocked = controlsLocked,
                onBlockedControl = onBlockedControl,
                dock = dock,
                pull = pull,
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .graphicsLayer {
                        alpha = (1f - compact * 1.6f).coerceIn(0f, 1f)
                        scaleX = 1f - 0.24f * compact
                        scaleY = 1f - 0.24f * compact
                    }
                    .liquidGlass(shape = GlassNowPlayingShape),
            )
            Spacer(Modifier.height(8.dp))
        }

        GlasmioNavigationBarWithTrailingItem(
            items = navItems,
            trailingItem = trailingItem,
            selectedKey = selectedIndex,
            onSelected = onTabSelected,
            onExpand = { scrollConnection.expand() },
            compactProgress = compactProgressState,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The now-playing capsule's silhouette — the accessory it always was. */
private val GlassNowPlayingShape = RoundedCornerShape(percent = 50)

/** Glasmio's navigation motion spring: (2π/0.3)², the 0.3 s native response. */
private const val GLASS_NAV_COMPACT_STIFFNESS = 438.64908f

/**
 * The now playing controls docked above [GlassNavBar] as its accessory.
 *
 * [MiniPlayer]'s content — same artwork, same transport, same haptics — on
 * the glass capsule of its own the fork's sample gives its floating controls,
 * with Echo's press response (a surface you can push on is the whole point
 * of the material). The inline density is gone with the dock: the capsule
 * only ever draws expanded, the fold fades the whole accessory.
 */
@Composable
private fun GlassNowPlaying(
    song: Song,
    isInline: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    contentColor: Color,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    controlsLocked: Boolean,
    onBlockedControl: () -> Unit,
    dock: PlayerDock?,
    pull: MiniPlayerPull?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val pressSource = remember { MutableInteractionSource() }
    val isPressed by pressSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 1.04f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "accessoryPressScale",
    )

    val titleStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = ACCESSORY_TITLE_SIZE,
        fontWeight = ACCESSORY_TITLE_WEIGHT,
        letterSpacing = ACCESSORY_TITLE_TRACKING,
    )
    val artistStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = ACCESSORY_ARTIST_SIZE,
        letterSpacing = ACCESSORY_ARTIST_TRACKING,
    )
    val glyphSlot = if (isInline) 32.dp else 40.dp
    val glyphSize = if (isInline) 24.dp else EXPANDED_GLYPH_SIZE

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            // Outside the press scale, which it lifts along with everything else.
            .miniPlayerGestures(
                pull = pull,
                onNext = {
                    haptics.play(Haptic.SkipNext)
                    onNext()
                },
                onPrevious = {
                    haptics.play(Haptic.SkipPrevious)
                    onPrevious()
                },
                locked = controlsLocked,
                onBlocked = onBlockedControl,
            )
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(modifier)
            .clickable(
                interactionSource = pressSource,
                indication = null,
                onClick = onExpand,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    // The artwork sits in the end of a capsule, which curves
                    // away from it; at 8dp its corner met the curve.
                    start = 16.dp,
                    end = 12.dp,
                    // Expanded, the 40dp play button and these come to the 48dp
                    // Apple's accessory is.
                    top = 4.dp,
                    bottom = 4.dp,
                ),
        ) {
            // A music video plays in the cover's place at its own 16:9, as in
            // the floating mini player; the full player takes it over when up.
            val showsPicture = song.isVideo && MusicOnlyPlayback.showsPicture(song.videoId)
            val artWidth by animateDpAsState(
                targetValue = if (showsPicture && !VideoThumbnails.square) ACCESSORY_ART_SIZE * (16f / 9f) else ACCESSORY_ART_SIZE,
                label = "glassMiniArtWidth",
            )
            Box(
                modifier = Modifier
                    .size(width = artWidth, height = ACCESSORY_ART_SIZE)
                    // Whichever of the two densities is on screen is the one
                    // the player flies to: only one is composed at rest.
                    .playerDockArt(dock, ACCESSORY_ART_CORNER)
                    .clip(RoundedCornerShape(ACCESSORY_ART_CORNER))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model = rememberRemoteArtworkUrl(song)?.artworkAt(ROW_ART_PX),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (showsPicture) {
                    AndroidMusicVideo(
                        videoId = song.videoId,
                        fit = false,
                        onRenderedChanged = {},
                        modifier = Modifier.fillMaxSize(),
                        priority = 0,
                    )
                }
            }
            Spacer(Modifier.width(9.dp))
            // Title and artist at both densities, as Apple Music has them, in its
            // small, openly spaced type (see [ACCESSORY_TITLE_SIZE]).
            Column(Modifier.weight(1f)) {
                val secondary = contentColor.copy(alpha = 0.7f)
                ExplicitSongTitle(
                    song = song,
                    style = titleStyle,
                    color = contentColor,
                    badgeColor = secondary,
                    fadeOverflow = true,
                )
                FadingLine(
                    text = song.artist,
                    style = artistStyle,
                    color = secondary,
                )
            }
            if (isLoading) {
                Box(Modifier.size(glyphSlot), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = contentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                IconButton(
                    onClick = {
                        haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                        onPlayPause()
                    },
                    modifier = Modifier.size(glyphSlot),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                        tint = contentColor,
                        modifier = Modifier.size(glyphSize),
                    )
                }
            }
            // Glyph centres 44dp apart, as Apple's are.
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = {
                    haptics.play(Haptic.SkipNext)
                    onNext()
                },
                enabled = !controlsLocked,
                modifier = Modifier.size(glyphSlot),
            ) {
                MiniPlayerNextGlyph(
                    tint = contentColor.copy(alpha = if (controlsLocked) 0.3f else 1f),
                )
            }
        }
    }
}

/**
 * The accessory's type, measured off Apple's, collapsed and expanded alike:
 * 13pt over 12pt, set looser than the scale's 16 and 14 as SF Pro Text spaces
 * these sizes — the title less so than measured, tuned by eye on device: in
 * Semibold +0.4 read loose and the cut's own spacing tight.
 */
private val ACCESSORY_TITLE_SIZE = 13.sp

/**
 * W700, which in this app's family draws the Semibold cut (see SFProDisplay):
 * Apple's accessory sets its title in Semibold, and the scale's W600 — the
 * Medium cut — read thin at 13sp.
 */
private val ACCESSORY_TITLE_WEIGHT = FontWeight.W700
private val ACCESSORY_TITLE_TRACKING = 0.15.sp
private val ACCESSORY_ARTIST_SIZE = 12.sp
private val ACCESSORY_ARTIST_TRACKING = 0.35.sp

/** The accessory's artwork, about Apple's 30pt at both densities, and its corner. */
private val ACCESSORY_ART_SIZE = 30.4.dp
private val ACCESSORY_ART_CORNER = 5.7.dp

/** The expanded play glyph: its triangle about Apple's 15pt tall. */
private val EXPANDED_GLYPH_SIZE = 29.dp
