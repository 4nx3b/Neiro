/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */

package com.music.bitchord.ui.components

import com.music.bitchord.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.data.model.ROW_ART_PX
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.model.artworkAt
import com.music.bitchord.playback.MusicOnlyPlayback
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.sharedui.resources.Res as SharedRes
import com.music.bitchord.sharedui.resources.ic_player_next
import com.music.bitchord.sharedui.resources.ic_player_pause
import com.music.bitchord.sharedui.resources.ic_player_play
import com.music.bitchord.ui.components.thumbnailBorder
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import com.music.bitchord.ui.player.PlayerDock
import com.music.bitchord.ui.player.AndroidMusicVideo
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateDpAsState
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh

private val GLYPH_SLOT = 40.dp

private val GLYPH_SIZE = 32.dp

private val SPINNER_SIZE = 22.dp

private val TRANSPORT_GAP = 8.dp

private val ROW_PADDING_VERTICAL = 8.dp

private val ROW_PADDING_HORIZONTAL = 12.dp

private val ART_CORNER = 8.dp

private val TRACK_SWIPE_THRESHOLD = 72.dp

private const val PULL_BIAS = 1.5f

private val PULL_LIFT_EASE = 72.dp

private const val PULL_LIFT_RATE = 0.5f

private val JELLY_MAX_OFFSET = 36.dp

private const val JELLY_STRETCH_X = 0.12f
private const val JELLY_SQUASH_Y = 0.10f

private const val JELLY_DAMPING = 0.3f

class JellyPush {
    internal val offset = mutableFloatStateOf(0f)
    internal val squish = mutableFloatStateOf(0f)
    internal val phase = mutableFloatStateOf(0f)
    internal val width = mutableFloatStateOf(0f)

    fun advance(towardLeft: Boolean): Float {
        val p = phase.floatValue
        val s = squish.floatValue * p
        val shift = offset.floatValue * p
        val grow = width.floatValue * JELLY_STRETCH_X * s / 2f
        val edge = if (towardLeft) -shift + grow else shift + grow
        return edge.coerceAtLeast(0f)
    }
}

private fun pullLift(pulled: Float, ease: Float): Float =
    if (pulled <= 0f || ease <= 0f) {
        0f
    } else {
        PULL_LIFT_RATE * 2f * ease * (sqrt(1f + pulled / ease) - 1f)
    }

interface MiniPlayerPull {
    fun drag(delta: Float)

    fun release(velocity: Float)
}

@Composable
internal fun Modifier.miniPlayerGestures(
    pull: MiniPlayerPull?,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    locked: Boolean = false,
    onBlocked: () -> Unit = {},
    push: JellyPush? = null,
): Modifier {
    val currentPull by rememberUpdatedState(pull)
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentLocked by rememberUpdatedState(locked)
    val currentOnBlocked by rememberUpdatedState(onBlocked)
    val scope = rememberCoroutineScope()
    val lift = remember { mutableFloatStateOf(0f) }
    val settle = remember { arrayOfNulls<Job>(1) }
    val jOffset = push?.offset ?: remember { mutableFloatStateOf(0f) }
    val jSquish = push?.squish ?: remember { mutableFloatStateOf(0f) }
    val jPhase = push?.phase ?: remember { mutableFloatStateOf(0f) }
    val jellyJob = remember { arrayOfNulls<Job>(1) }
    return pointerInput(Unit) {
        val trackThreshold = TRACK_SWIPE_THRESHOLD.toPx()
        val liftEase = PULL_LIFT_EASE.toPx()
        val jellyMax = JELLY_MAX_OFFSET.toPx()
        val jellyFollow: (Float) -> Unit = { d ->
            val o = jellyMax * tanh(d / (jellyMax * 2.5f))
            jOffset.floatValue = o
            jSquish.floatValue = abs(o) / jellyMax
        }
        val maxVelocity = viewConfiguration.maximumFlingVelocity
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointer = down.id
            var start: PointerInputChange? = null
            var vertical = false
            while (start == null) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == pointer }
                    ?: return@awaitEachGesture
                if (!change.pressed || change.isConsumed) return@awaitEachGesture
                val moved = change.position - down.position
                if (moved.getDistance() < viewConfiguration.touchSlop) continue
                vertical = moved.y < 0f && abs(moved.x) < -moved.y * PULL_BIAS
                if (!vertical && abs(moved.x) < abs(moved.y)) return@awaitEachGesture
                if (vertical && currentPull == null) return@awaitEachGesture
                change.consume()
                start = change
            }
            val slopped = start ?: return@awaitEachGesture

            if (!vertical) {
                jellyJob[0]?.cancel()
                jPhase.floatValue = 1f
                var dragged = slopped.position.x - down.position.x
                jellyFollow(dragged)
                val finished = drag(pointer) { change ->
                    dragged += change.positionChange().x
                    change.consume()
                    jellyFollow(dragged)
                }
                jPhase.floatValue = 1f
                jellyJob[0] = scope.launch {
                    animate(
                        initialValue = 1f,
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = JELLY_DAMPING,
                            stiffness = Spring.StiffnessLow,
                        ),
                    ) { value, _ -> jPhase.floatValue = value }
                    jOffset.floatValue = 0f
                    jSquish.floatValue = 0f
                    jPhase.floatValue = 1f
                }
                if (!finished) return@awaitEachGesture
                val crossed = dragged <= -trackThreshold || dragged >= trackThreshold
                when {
                    currentLocked -> if (crossed) currentOnBlocked()
                    dragged <= -trackThreshold -> currentOnNext()
                    dragged >= trackThreshold -> currentOnPrevious()
                }
                return@awaitEachGesture
            }

            val target = currentPull ?: return@awaitEachGesture
            settle[0]?.cancel()
            val tracker = VelocityTracker()
            tracker.addPointerInputChange(down)
            tracker.addPointerInputChange(slopped)
            target.drag(slopped.position.y - down.position.y)
            var pulled = 0f
            val finished = drag(pointer) { change ->
                val delta = change.positionChange().y
                change.consume()
                tracker.addPointerInputChange(change)
                pulled -= delta
                target.drag(delta)
                lift.floatValue = pullLift(pulled, liftEase)
            }
            target.release(
                if (finished) tracker.calculateVelocity().y.coerceIn(-maxVelocity, maxVelocity) else 0f,
            )
            val from = lift.floatValue
            settle[0] = scope.launch {
                animate(
                    initialValue = from,
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.65f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                ) { value, _ -> lift.floatValue = value }
            }
        }
    }.then(
        if (push == null) Modifier else Modifier.onSizeChanged { push.width.floatValue = it.width.toFloat() },
    ).graphicsLayer {
        val p = jPhase.floatValue
        val s = jSquish.floatValue * p
        translationX = jOffset.floatValue * p
        scaleX = 1f + JELLY_STRETCH_X * s
        scaleY = 1f - JELLY_SQUASH_Y * s
        translationY = -lift.floatValue
    }
}

@Composable
internal fun Modifier.playerDockArt(dock: PlayerDock?, corner: Dp): Modifier {
    if (dock == null) return this
    val placed = remember { arrayOfNulls<LayoutCoordinates>(1) }
    DisposableEffect(dock) {
        onDispose { placed[0]?.let(dock::releaseMiniArt) }
    }
    return this
        .onPlaced { coordinates ->
            placed[0] = coordinates
            dock.reportMiniArt(coordinates, corner)
        }
        .graphicsLayer { alpha = if (dock.coversMiniArt) 0f else 1f }
}

private val VIDEO_ART_WIDTH = 71.dp

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    hazeState: HazeState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onExpand: () -> Unit,
    controlsLocked: Boolean = false,
    onBlockedControl: () -> Unit = {},
    dock: PlayerDock? = null,
    pull: MiniPlayerPull? = null,
    compactFraction: () -> Float = { 0f },
    modifier: Modifier = Modifier,
) {
    val compact = compactFraction().coerceIn(0f, 1f)
    val rowVertical = (ROW_PADDING_VERTICAL - 3.dp * compact).coerceAtLeast(2.dp)
    val rowHorizontal = ROW_PADDING_HORIZONTAL + 40.dp * compact
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .padding(horizontal = BAR_GUTTER + 56.dp * compact)
            .miniPlayerGestures(
                pull = pull,
                onNext = {
                    haptics.play(Haptic.SkipNext)
                    com.music.bitchord.ui.components.TrackFlow.hint(true)
                    onNext()
                },
                onPrevious = {
                    haptics.play(Haptic.SkipPrevious)
                    com.music.bitchord.ui.components.TrackFlow.hint(false)
                    onPrevious()
                },
                locked = controlsLocked,
                onBlocked = onBlockedControl,
            )
            .clip(shape)
            .then(
                if (reduceDynamicBlur) {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                } else {
                    Modifier.optimizedHazeEffect(
                        state = hazeState,
                        style = HazeMaterials.thin(MaterialTheme.colorScheme.surface),
                    )
                },
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(onClick = onExpand),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = rowHorizontal,
                    vertical = rowVertical,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val artWidth by animateDpAsState(
                targetValue = if (song.isVideo && !VideoThumbnails.square && MusicOnlyPlayback.showsPicture(song.videoId)) VIDEO_ART_WIDTH else 40.dp,
                label = "miniArtWidth",
            )
            Box(
                modifier = Modifier
                    .size(width = artWidth, height = 40.dp)
                    .playerDockArt(dock, ART_CORNER)
                    .clip(RoundedCornerShape(ART_CORNER))
                    .thumbnailBorder(RoundedCornerShape(ART_CORNER))
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
            Spacer(Modifier.width(10.dp))
            TrackTextSwitch(song, Modifier.weight(1f)) { shown ->
                Column {
                    ExplicitSongTitle(
                        song = shown,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        badgeColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        fadeOverflow = true,
                    )
                    FadingLine(
                        text = shown.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isLoading) {
                Box(Modifier.size(GLYPH_SLOT), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onBackground,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(SPINNER_SIZE),
                    )
                }
            } else {
                MiniPlayerPlayPauseButton(
                    isPlaying = isPlaying,
                    tint = MaterialTheme.colorScheme.onBackground,
                    slot = GLYPH_SLOT,
                    glyphSize = GLYPH_SIZE,
                    onClick = {
                        haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                        onPlayPause()
                    },
                )
            }
            Spacer(Modifier.width(TRANSPORT_GAP))
            MiniPlayerNextButton(
                tint = MaterialTheme.colorScheme.onBackground
                    .copy(alpha = if (controlsLocked) 0.3f else 1f),
                enabled = !controlsLocked,
                slot = GLYPH_SLOT,
                onClick = {
                    haptics.play(Haptic.SkipNext)
                    com.music.bitchord.ui.components.TrackFlow.hint(true)
                    onNext()
                },
            )
        }
    }
}

private val NEXT_GLYPH_SIZE = 25.dp

private const val MINI_PLAY_PAUSE_SCALE = 1.15f

@Composable
internal fun MiniPlayerPlayPauseButton(
    isPlaying: Boolean,
    tint: Color,
    slot: Dp,
    glyphSize: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed) 0.72f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "miniPlayPausePress",
    )
    val pressDisc by animateFloatAsState(
        targetValue = if (pressed) 0.18f else 0f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 260),
        label = "miniPlayPauseDisc",
    )
    Box(
        modifier = Modifier
            .size(slot)
            .drawBehind {
                if (pressDisc > 0f) {
                    drawCircle(
                        color = tint.copy(alpha = pressDisc),
                        radius = this.size.minDimension / 2f,
                    )
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                (
                    fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                        scaleIn(
                            initialScale = 0.55f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
                        )
                    ) togetherWith (
                    fadeOut(tween(90)) +
                        scaleOut(targetScale = 0.55f, animationSpec = tween(120))
                    )
            },
            contentAlignment = Alignment.Center,
            label = "miniPlayPause",
        ) { playing ->
            Icon(
                painter = painterResource(
                    if (playing) SharedRes.drawable.ic_player_pause else SharedRes.drawable.ic_player_play,
                ),
                contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
                tint = tint,
                modifier = Modifier
                    .requiredSize(glyphSize * MINI_PLAY_PAUSE_SCALE)
                    .graphicsLayer {
                        scaleX = pressScale.value
                        scaleY = pressScale.value
                    },
            )
        }
    }
}

@Composable
internal fun MiniPlayerNextButton(
    tint: Color,
    enabled: Boolean,
    slot: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale = animateFloatAsState(
        targetValue = if (pressed && enabled) 0.72f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "miniNextPress",
    )
    val pressDisc by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.18f else 0f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 260),
        label = "miniNextDisc",
    )
    val scope = rememberCoroutineScope()
    val kick = remember { Animatable(0f) }
    Box(
        modifier = Modifier
            .size(slot)
            .drawBehind {
                if (pressDisc > 0f) {
                    drawCircle(
                        color = tint.copy(alpha = pressDisc),
                        radius = this.size.minDimension / 2f,
                    )
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = androidx.compose.ui.semantics.Role.Button,
            ) {
                scope.launch {
                    kick.snapTo(0f)
                    kick.animateTo(1f, tween(durationMillis = 80, easing = FastOutSlowInEasing))
                    kick.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow))
                }
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(SharedRes.drawable.ic_player_next),
            contentDescription = stringResource(R.string.widget_next),
            tint = tint,
            modifier = Modifier
                .size(NEXT_GLYPH_SIZE)
                .graphicsLayer {
                    val k = kick.value
                    translationX = k * 6.dp.toPx()
                    scaleX = pressScale.value * (1f + abs(k) * 0.15f)
                    scaleY = pressScale.value * (1f - abs(k) * 0.06f) * 0.85f
                },
        )
    }
}
