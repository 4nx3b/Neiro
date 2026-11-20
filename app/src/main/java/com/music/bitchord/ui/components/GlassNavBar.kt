/*
 * The bar's structure and its inline/expanded behaviour are
 * EchoMusicApp/Echo-Music's AppFloatingNavBar + FloatingMiniPlayer (GPL-3.0),
 * over the FloatingTabBar vendored in [com.music.bitchord.ui.components.floatingtabbar].
 * Zenith's own tabs, song model, transport and haptics are wired through it in
 * place of Echo's Screens routing and PlayerConnection.
 */

@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.music.bitchord.ui.components

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.lerp as lerpDp
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.playback.MusicOnlyPlayback
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBar
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBarDefaults
import com.music.bitchord.ui.components.floatingtabbar.FloatingTabBarScrollConnection
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.music.bitchord.ui.components.floatingtabbar.LocalTabPillGeometry
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.player.PlayerDock
import com.music.bitchord.ui.player.AndroidMusicVideo
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize

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
    controlsLocked: Boolean = false,
    onBlockedControl: () -> Unit = {},
    dock: PlayerDock? = null,
    pull: MiniPlayerPull? = null,
    searchSeparate: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    val contentColor = glassContentColor()
val isDarkGlass = contentColor == Color.White
val selectedColor = if (isDarkGlass) {
    lerp(MaterialTheme.colorScheme.primary, Color.White, 0.06f)
} else {
    MaterialTheme.colorScheme.primary
}
val unselectedColor = contentColor.copy(alpha = 0.88f)
val haptics = rememberHaptics()
    val jellyPush = remember { JellyPush() }

    val currentOnTabSelected by rememberUpdatedState(onTabSelected)

    val standaloneIndex = if (searchSeparate) tabs.lastIndex else -1

    val glassSurface: @Composable () -> Modifier = { Modifier.liquidGlass(shape = pillShape) }

    val chromeProgress = remember(dock) { { dock?.openFraction() ?: 0f } }
    val chromeSidePush = remember(dock) {
        dock?.let { d -> { toRight: Boolean -> d.panelSideTravel(toRight) } }
    }
    val chromeDownPush = remember(dock) {
        dock?.let { d -> { d.panelBottomTravel() } }
    }

    FloatingTabBar(
        selectedTabKey = selectedIndex,
        scrollConnection = scrollConnection,
        chromeProgress = chromeProgress,
        chromeSidePush = chromeSidePush,
        chromeDownPush = chromeDownPush,
        jellyPush = jellyPush,
        inlineSplitLast = !searchSeparate,
        modifier = modifier
            .windowInsetsPadding(floatingBarInsets)
            .padding(horizontal = NAV_BAR_GUTTER)
            .padding(bottom = NAV_BAR_LIFT)
            .fillMaxWidth(),
        tabBarContentModifier = glassSurface,
        inlineAccessory = song?.let { current ->
            { accessoryModifier, _ ->
                GlassNowPlaying(
                    song = current,
                    isInline = true,
                    jellyPush = jellyPush,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    contentColor = contentColor,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onExpand = onExpand,
                    controlsLocked = controlsLocked,
                    onBlockedControl = onBlockedControl,
                    dock = dock,
                    pull = pull,
                    modifier = accessoryModifier.then(glassSurface()),
                )
            }
        },
        expandedAccessory = song?.let { current ->
            { accessoryModifier, _ ->
                GlassNowPlaying(
                    song = current,
                    isInline = false,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    contentColor = contentColor,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onExpand = onExpand,
                    controlsLocked = controlsLocked,
                    onBlockedControl = onBlockedControl,
                    dock = dock,
                    pull = pull,
                    modifier = accessoryModifier.fillMaxWidth().then(glassSurface()),
                )
            }
        },
        colors = FloatingTabBarDefaults.colors(
            backgroundColor = Color.Transparent,
            accessoryBackgroundColor = Color.Transparent,
            indicatorColor = if (isDarkGlass) Color.Black.copy(alpha = 0.30f) else glassIndicatorColor(),
            selectedContentColor = selectedColor,
        ),
        elevations = FloatingTabBarDefaults.elevations(
            inlineElevation = 0.dp,
            expandedElevation = 0.dp,
        ),
        sizes = FloatingTabBarDefaults.sizes(
            tabBarContentPadding = PaddingValues(PILL_INSET),
            tabExpandedContentPadding = PaddingValues(vertical = TAB_VERTICAL_PADDING),
            inlineComponentSpacing = INLINE_SPACING,
            inlineBarPadding = PaddingValues(
                start = INLINE_GUTTER - NAV_BAR_GUTTER,
                end = INLINE_GUTTER - NAV_BAR_GUTTER,
                bottom = INLINE_LIFT,
            ),
        ),
        contentKey = remember(selectedIndex, tabs, contentColor, selectedColor, searchSeparate) {
            listOf(selectedIndex, tabs, contentColor, selectedColor, searchSeparate)
        },
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            val onClick = {
                if (!isSelected) haptics.play(Haptic.Select)
                currentOnTabSelected(index)
            }
            if (index == standaloneIndex) {
                standaloneTab(
                    key = index,
                    icon = {
                        PillTinted(isSelected, selectedColor, unselectedColor) { tint ->
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(TAB_ICON_SIZE),
                            )
                        }
                    },
                    onClick = onClick,
                )
            } else {
                tab(
                    key = index,
                    icon = {
                        PillTinted(isSelected, selectedColor, unselectedColor) { tint ->
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = tint,
                                modifier = Modifier.size(TAB_ICON_SIZE),
                            )
                        }
                    },
                    title = {
                        PillTinted(isSelected, selectedColor, unselectedColor) { tint ->
                            Text(
                                text = tab.label,
                                style = tabLabelStyle(),
                                color = tint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = TAB_ICON_LABEL_GAP),
                            )
                        }
                    },
                    onClick = onClick,
                )
            }
        }
    }
}

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
    jellyPush: JellyPush? = null,
    inlineProgress: (() -> Float)? = null,
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
    val inlineMixSpring by animateFloatAsState(
        targetValue = if (isInline) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 700f),
        label = "accessoryDensity",
    )
    val inlineMix = inlineProgress?.invoke() ?: inlineMixSpring
    val glyphSlot = lerpDp(40.dp, 32.dp, inlineMix)
    val glyphSize = lerpDp(EXPANDED_GLYPH_SIZE, 24.dp, inlineMix)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
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
                push = jellyPush,
            )
            .graphicsLayer {
                val factor = pressScale
                scaleX = factor
                scaleY = factor
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
                    start = 16.dp,
                    end = lerpDp(12.dp, 8.dp, inlineMix),
                    top = 4.dp,
                    bottom = 4.dp,
                ),
        ) {
            val artWidth by animateDpAsState(
                targetValue = if (song.isVideo && !VideoThumbnails.square && MusicOnlyPlayback.showsPicture(song.videoId)) ACCESSORY_ART_SIZE * (16f / 9f) else ACCESSORY_ART_SIZE,
                label = "glassMiniArtWidth",
            )
            Box(
                modifier = Modifier
                    .size(width = artWidth, height = ACCESSORY_ART_SIZE)
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
                if (song.isVideo && MusicOnlyPlayback.showsPicture(song.videoId)) {
                    AndroidMusicVideo(
                        videoId = song.videoId,
                        fit = false,
                        onRenderedChanged = {},
                        modifier = Modifier.fillMaxSize(),
                        priority = 0,
                    )
                }
            }
            Spacer(Modifier.width(lerpDp(9.dp, 8.dp, inlineMix)))
            TrackTextSwitch(song, Modifier.weight(1f)) { shown ->
                Column {
                    val secondary = contentColor.copy(alpha = 0.7f)
                    ExplicitSongTitle(
                        song = shown,
                        style = titleStyle,
                        color = contentColor,
                        badgeColor = secondary,
                        fadeOverflow = true,
                    )
                    FadingLine(
                        text = shown.artist,
                        style = artistStyle,
                        color = secondary,
                    )
                }
            }
            if (isLoading) {
                Box(Modifier.size(glyphSlot), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = contentColor,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(lerpDp(20.dp, 18.dp, inlineMix)),
                    )
                }
            } else {
                MiniPlayerPlayPauseButton(
                    isPlaying = isPlaying,
                    tint = contentColor,
                    slot = glyphSlot,
                    glyphSize = glyphSize,
                    onClick = {
                        haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                        onPlayPause()
                    },
                )
            }
            if (inlineMix < 0.999f) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val shown = (1f - inlineMix).coerceIn(0f, 1f)
                            layout((placeable.width * shown).roundToInt(), placeable.height) {
                                placeable.placeWithLayer(0, 0) { alpha = shown }
                            }
                        },
                ) {
                    Spacer(Modifier.width(4.dp))
                    MiniPlayerNextButton(
                        tint = contentColor.copy(alpha = if (controlsLocked) 0.3f else 1f),
                        enabled = !controlsLocked,
                        slot = glyphSlot,
                        onClick = {
                            haptics.play(Haptic.SkipNext)
                            onNext()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun GlassMiniPlayer(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    controlsLocked: Boolean = false,
    onBlockedControl: () -> Unit = {},
    dock: PlayerDock? = null,
    pull: MiniPlayerPull? = null,
    inlineDensity: Boolean = false,
    inlineProgress: (() -> Float)? = null,
    modifier: Modifier = Modifier,
) {
    val pillShape = remember { RoundedCornerShape(percent = 50) }
    Box(modifier = modifier.padding(horizontal = NAV_BAR_GUTTER)) {
        GlassNowPlaying(
            song = song,
            isInline = inlineDensity,
            inlineProgress = inlineProgress,
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
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(Modifier.liquidGlass(shape = pillShape)),
        )
    }
}

private val INLINE_GUTTER = 28.dp
private val INLINE_SPACING = 14.dp

private val INLINE_LIFT = 7.dp

private val ACCESSORY_TITLE_SIZE = 13.sp

private val ACCESSORY_TITLE_WEIGHT = FontWeight.W700
private val ACCESSORY_TITLE_TRACKING = 0.15.sp
private val ACCESSORY_ARTIST_SIZE = 12.sp
private val ACCESSORY_ARTIST_TRACKING = 0.35.sp

private val ACCESSORY_ART_SIZE = 30.4.dp
private val ACCESSORY_ART_CORNER = 5.7.dp

private val EXPANDED_GLYPH_SIZE = 29.dp

@Composable
private fun PillTinted(
    isSelected: Boolean,
    selected: Color,
    unselected: Color,
    content: @Composable (Color) -> Unit,
) {
    val geometry = LocalTabPillGeometry.current
    val hasPill by remember(geometry) { derivedStateOf { !geometry().isUnspecified } }
    if (!hasPill) {
        content(if (isSelected) selected else unselected)
        return
    }
    Box {
        content(unselected)
        Box(
            Modifier
                .matchParentSize()
                .clearAndSetSemantics {}
                .drawWithContent {
                    val g = geometry()
                    if (g.isUnspecified) return@drawWithContent
                    val centre = size.width / 2f + g.x
                    val left = centre - g.y / 2f
                    val right = centre + g.y / 2f
                    if (right <= 0f || left >= size.width) return@drawWithContent
                    clipRect(left = left, top = -size.height, right = right, bottom = size.height * 2f) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            content(selected)
        }
    }
}
