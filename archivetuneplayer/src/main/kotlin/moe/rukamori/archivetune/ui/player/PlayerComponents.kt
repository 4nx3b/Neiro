/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import moe.rukamori.archivetune.bridge.driftArtworkSlot // BRIDGE: drift
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import me.saket.squiggles.SquigglySlider
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerHorizontalPadding
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.codecLabel
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.PlayerSliderTrack
import moe.rukamori.archivetune.ui.component.ResizableIconButton
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.player.PlayerFadeConfig
import moe.rukamori.archivetune.ui.theme.PlayerBackgroundColorUtils
import moe.rukamori.archivetune.ui.theme.PlayerSliderColors
import moe.rukamori.archivetune.ui.utils.fadingEdge
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlin.math.abs
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val PlayerBackgroundMaxBlurRadius = 64f
private const val ExplicitBadgeInlineId = "explicitBadge"

@Composable
internal fun PlayerTitleText(
    title: String,
    explicit: Boolean,
    color: Color,
    style: TextStyle,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = TextUnit.Unspecified,
    textAlign: TextAlign? = null,
    titleThreshold: Int = PlayerFadeConfig.forStyle(PlayerDesignStyle.V9).titleMinChars,
    fadeWidth: Dp = 24.dp,
) {
    val annotatedTitle =
        remember(title, explicit) {
            buildAnnotatedString {
                append(title)
                if (explicit) {
                    append(" ")
                    appendInlineContent(ExplicitBadgeInlineId, "\uFFFC")
                }
            }
        }
    val badgePainter = painterResource(R.drawable.player_explicit)
    val inlineContent =
        remember(badgePainter, color, explicit) {
            if (explicit) {
                mapOf(
                    ExplicitBadgeInlineId to
                        InlineTextContent(
                            placeholder =
                                Placeholder(
                                    width = 0.82.em,
                                    height = 0.82.em,
                                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                                ),
                        ) {
                            Icon(
                                painter = badgePainter,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
                )
            } else {
                emptyMap()
            }
        }

    val titleLayout = remember { mutableStateOf<TextLayoutResult?>(null) }
    val titleViewportWidth = remember { mutableStateOf(0) }
    val shouldFade =
        titleViewportWidth.value > 0 &&
            (titleLayout.value?.size?.width ?: 0) > titleViewportWidth.value
    Box(
        modifier =
            (if (shouldFade) modifier.viewportEdgeFade(fadeWidth) else modifier)
                .clipToBounds()
                .onSizeChanged { titleViewportWidth.value = it.width },
    ) {
        Text(
            text = annotatedTitle,
            inlineContent = inlineContent,
            color = color,
            style = style,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = textAlign,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { titleLayout.value = it },
            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
        )
    }
}

@Composable
internal fun PlayerTextBackdrop(
    textColor: Color,
    modifier: Modifier = Modifier,
    edgeFadeWidth: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
    }
}

internal fun Modifier.viewportEdgeFade(
    width: Dp = 24.dp,
): Modifier = fadingEdge(horizontal = width)

@Composable
internal fun Modifier.marqueeEdgeFade(
    layoutState: State<TextLayoutResult?>,
    width: Dp = 24.dp,
): Modifier = viewportEdgeFade(width)

@Composable
internal fun Modifier.marqueeEdgeFade(
    width: Dp = 24.dp,
): Modifier = viewportEdgeFade(width)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledPlaybackSlider(
    sliderStyle: SliderStyle,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    activeColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    when (sliderStyle) {
        SliderStyle.Standard -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.standardSliderColors(activeColor),
                modifier = modifier,
            )
        }

        SliderStyle.Wavy -> {
            SquigglySlider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.wavySliderColors(activeColor),
                modifier = modifier,
                squigglesSpec =
                    SquigglySlider.SquigglesSpec(
                        amplitude = if (isPlaying) 2.dp else 0.dp,
                        strokeWidth = 6.dp,
                    ),
            )
        }

        SliderStyle.Thick -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.thickSliderColors(activeColor),
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = PlayerSliderColors.thickSliderColors(activeColor),
                        trackHeight = 12.dp,
                    )
                },
                modifier = modifier,
            )
        }

        SliderStyle.Circular -> {
            SquigglySlider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.circularSliderColors(activeColor),
                modifier = modifier,
                squigglesSpec =
                    SquigglySlider.SquigglesSpec(
                        amplitude = if (isPlaying) 2.dp else 0.dp,
                        strokeWidth = 6.dp,
                    ),
            )
        }

        SliderStyle.Simple -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.simpleSliderColors(activeColor),
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = PlayerSliderColors.simpleSliderColors(activeColor),
                        trackHeight = 3.dp,
                    )
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
fun V9PlayerContent(
    mediaMetadata: MediaMetadata,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    canvasSource: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    onCollapseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    lyricsOpen: Boolean,
    onCloseLyrics: () -> Unit,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
    gradientColors: List<Color> = emptyList(),
) {
    val baseArtworkUrl = mediaMetadata.thumbnailUrl?.highRes()
    val thumbnailSwapState =
        rememberThumbnailSwapState(
            videoId = mediaMetadata.id,
            ytmUrl = baseArtworkUrl,
            lowDataMode = rememberLowDataModeActive(),
            isMusicVideo = mediaMetadata.isMusicVideo,
        )
    val artworkUrl = thumbnailSwapState.displayUrl
    val titleActions = rememberPlayerTitleActions(mediaMetadata, navController, state)
    val onTitleClick = titleActions.onTitleClick
    val onArtistClick = titleActions.onArtistClick
    val onPlayPauseClick = {
        if (playbackState == STATE_ENDED) {
            playerConnection.player.seekTo(0, 0)
            playerConnection.player.playWhenReady = true
        } else {
            playerConnection.player.togglePlayPause()
        }
    }

    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val repeatMode by playerConnection.repeatMode.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val liked = currentSong?.song?.liked == true
    val onToggleLike = playerConnection::toggleLike
    val menuState = LocalMenuState.current

    val onShuffleClick = {
        playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
    }
    val onRepeatClick = {
        playerConnection.player.repeatMode =
            when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
    }
    val onMenuClick = {
        menuState.showImmersive {
            PlayerMenu(
                mediaMetadata = mediaMetadata,
                navController = navController,
                playerBottomSheetState = state,
                onShowDetailsDialog = {},
                onDismiss = menuState::dismiss,
            )
        }
    }

    if (landscape) {
        V9LandscapeContent(
            mediaMetadata = mediaMetadata,
            title = mediaMetadata.title,
            explicit = mediaMetadata.explicit,
            artists = mediaMetadata.artists,
            artworkUrl = artworkUrl,
            canvasSource = canvasSource,
            canvasPrimaryUrl = canvasPrimaryUrl,
            canvasFallbackUrl = canvasFallbackUrl,
            playbackState = playbackState,
            isPlaying = isPlaying,
            isLoading = isLoading,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            textBackgroundColor = textBackgroundColor,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            onCollapseClick = onCollapseClick,
            onQueueClick = onQueueClick,
            onLyricsClick = onLyricsClick,
            lyricsOpen = lyricsOpen,
            onCloseLyrics = onCloseLyrics,
            lyricsSyncOffset = lyricsSyncOffset,
            onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
            onTitleClick = onTitleClick,
            onArtistClick = onArtistClick,
            onPreviousClick = playerConnection::seekToPrevious,
            onPlayPauseClick = onPlayPauseClick,
            onNextClick = playerConnection::seekToNext,
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
            shuffleModeEnabled = shuffleModeEnabled,
            repeatMode = repeatMode,
            onShuffleClick = onShuffleClick,
            onRepeatClick = onRepeatClick,
            onMenuClick = onMenuClick,
            modifier = modifier,
        )
    } else {
        V9PortraitContent(
            mediaMetadata = mediaMetadata,
            title = mediaMetadata.title,
            explicit = mediaMetadata.explicit,
            artists = mediaMetadata.artists,
            artworkUrl = artworkUrl,
            canvasSource = canvasSource,
            canvasPrimaryUrl = canvasPrimaryUrl,
            canvasFallbackUrl = canvasFallbackUrl,
            playbackState = playbackState,
            isPlaying = isPlaying,
            isLoading = isLoading,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            textBackgroundColor = textBackgroundColor,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            onCollapseClick = onCollapseClick,
            onQueueClick = onQueueClick,
            onLyricsClick = onLyricsClick,
            lyricsOpen = lyricsOpen,
            onCloseLyrics = onCloseLyrics,
            lyricsSyncOffset = lyricsSyncOffset,
            onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
            onTitleClick = onTitleClick,
            onArtistClick = onArtistClick,
            onPreviousClick = playerConnection::seekToPrevious,
            onPlayPauseClick = onPlayPauseClick,
            onNextClick = playerConnection::seekToNext,
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
            liked = liked,
            onToggleLike = onToggleLike,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun V9PortraitContent(
    mediaMetadata: MediaMetadata,
    title: String,
    explicit: Boolean,
    artists: List<MediaMetadata.Artist>,
    artworkUrl: String?,
    canvasSource: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    onCollapseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    lyricsOpen: Boolean,
    onCloseLyrics: () -> Unit,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    liked: Boolean,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalPadding = if (maxWidth < 380.dp) 16.dp else 20.dp
        val compactHeight = maxHeight < 760.dp
        val veryCompactHeight = maxHeight < 700.dp

        val headerGap =
            when {
                veryCompactHeight -> 14.dp
                compactHeight -> 18.dp
                else -> 26.dp
            }

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (compactHeight) 8.dp else 14.dp))

            V9Header(
                textColor = textBackgroundColor,
                containerColor = textButtonColor.copy(alpha = 0.16f),
                iconColor = textBackgroundColor,
                onCollapseClick = onCollapseClick,
                onLyricsClick = onLyricsClick,
                onQueueClick = onQueueClick,
            )

            Spacer(Modifier.height(headerGap))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !lyricsOpen,
                    enter = androidx.compose.animation.fadeIn(
                        androidx.compose.animation.core.tween(300)
                    ),
                    exit = androidx.compose.animation.fadeOut(
                        androidx.compose.animation.core.tween(200)
                    ),
                ) {
                    V9Artwork(
                        artworkUrl = artworkUrl,
                        canvasSource = canvasSource,
                        canvasPrimaryUrl = canvasPrimaryUrl,
                        canvasFallbackUrl = canvasFallbackUrl,
                        isPlaying = isPlaying,
                        placeholderColor = textBackgroundColor.copy(alpha = 0.08f),
                        modifier = Modifier.aspectRatio(1f)
                            .driftArtworkSlot() // BRIDGE: drift
                    )
                }

            }

            Spacer(Modifier.height(headerGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PlayerTitleText(
                        title = title,
                        explicit = explicit,
                        color = textBackgroundColor,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee()
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = onTitleClick,
                            ),
                    )
                    ClickableArtists(
                        artists = artists,
                        onArtistClick = onArtistClick,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = textBackgroundColor.copy(alpha = 0.72f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(),
                    )
                }

                Spacer(Modifier.width(16.dp))

                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                        contentDescription = stringResource(if (liked) R.string.action_remove_like else R.string.action_like),
                        tint = if (liked) MaterialTheme.colorScheme.primary else textBackgroundColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(headerGap))

            val (smoothProgressFraction, displayedPosition) = rememberSmoothProgress(
                isPlayingProvider = { isPlaying },
                currentPositionProvider = { sliderPosition ?: position },
                totalDuration = duration.coerceAtLeast(0L),
                isVisible = true
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                WavySliderExpressive(
                    value = { smoothProgressFraction.value },
                    onValueChange = { fraction ->
                        onSliderValueChange((fraction * duration.coerceAtLeast(0L)).toLong())
                    },
                    onValueCommit = { fraction ->
                        onSliderValueChangeFinished()
                    },
                    enabled = duration > 0L,
                    activeTrackColor = textBackgroundColor,
                    inactiveTrackColor = textBackgroundColor.copy(alpha = 0.24f),
                    thumbColor = textBackgroundColor,
                    isPlaying = isPlaying,
                    isVisible = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = makeTimeString(sliderPosition ?: displayedPosition.value),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textBackgroundColor.copy(alpha = 0.78f),
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )

                    Text(
                        text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = textBackgroundColor.copy(alpha = 0.78f),
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }

            Spacer(Modifier.height(if (compactHeight) 16.dp else 24.dp))

            val motionScheme = remember { MotionScheme.standard() }
            val controlSpatialSpec = remember { motionScheme.fastSpatialSpec<Float>() }
            V9AnimatedPlaybackControls(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                isPlayingProvider = { isPlaying },
                onPrevious = onPreviousClick,
                onPlayPause = onPlayPauseClick,
                onNext = onNextClick,
                height = 80.dp,
                pressAnimationSpec = controlSpatialSpec,
                releaseDelay = 220L,
                colorOtherButtons = textBackgroundColor.copy(alpha = 0.08f),
                colorPlayPause = textBackgroundColor,
                tintPlayPauseIcon = if ((textBackgroundColor.red + textBackgroundColor.green + textBackgroundColor.blue) > 1.5f) Color.Black else Color.White,
                tintOtherIcons = textBackgroundColor,
            )

            Spacer(Modifier.height(if (compactHeight) 16.dp else 24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun V9LandscapeContent(
    mediaMetadata: MediaMetadata,
    title: String,
    explicit: Boolean,
    artists: List<MediaMetadata.Artist>,
    artworkUrl: String?,
    canvasSource: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    onCollapseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    lyricsOpen: Boolean,
    onCloseLyrics: () -> Unit,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val artworkSize =
            (maxHeight * 0.74f)
                .coerceAtMost(maxWidth * 0.4f)
                .coerceAtLeast(236.dp)

        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Box(
                modifier = Modifier.width(artworkSize),
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = !lyricsOpen,
                    enter = androidx.compose.animation.fadeIn(
                        androidx.compose.animation.core.tween(300)
                    ),
                    exit = androidx.compose.animation.fadeOut(
                        androidx.compose.animation.core.tween(200)
                    ),
                ) {
                    V9Artwork(
                        artworkUrl = artworkUrl,
                        canvasSource = canvasSource,
                        canvasPrimaryUrl = canvasPrimaryUrl,
                        canvasFallbackUrl = canvasFallbackUrl,
                        isPlaying = isPlaying,
                        size = artworkSize,
                        placeholderColor = textBackgroundColor.copy(alpha = 0.08f),
                    )
                }

            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                V9Header(
                    textColor = textBackgroundColor,
                    containerColor = textButtonColor.copy(alpha = 0.16f),
                    iconColor = textBackgroundColor,
                    onCollapseClick = onCollapseClick,
                    onLyricsClick = onLyricsClick,
                    onQueueClick = onQueueClick,
                )

                Spacer(Modifier.height(14.dp))

                V9Metadata(
                    title = title,
                    explicit = explicit,
                    artists = artists,
                    textColor = textBackgroundColor,
                    onTitleClick = onTitleClick,
                    onArtistClick = onArtistClick,
                )

                Spacer(Modifier.height(12.dp))

                V9PlaybackProgress(
                    sliderPosition = sliderPosition,
                    position = position,
                    duration = duration,
                    isPlaying = isPlaying,
                    activeColor = textBackgroundColor,
                    inactiveColor = textBackgroundColor.copy(alpha = 0.24f),
                    textColor = textBackgroundColor,
                    onSliderValueChange = onSliderValueChange,
                    onSliderValueChangeFinished = onSliderValueChangeFinished,
                )

                Spacer(Modifier.height(12.dp))

                val motionScheme = remember { MotionScheme.standard() }
                val controlSpatialSpec = remember { motionScheme.fastSpatialSpec<Float>() }
                V9AnimatedPlaybackControls(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    isPlayingProvider = { isPlaying },
                    onPrevious = onPreviousClick,
                    onPlayPause = onPlayPauseClick,
                    onNext = onNextClick,
                    height = 64.dp,
                    pressAnimationSpec = controlSpatialSpec,
                    releaseDelay = 220L,
                    colorOtherButtons = textBackgroundColor.copy(alpha = 0.08f),
                    colorPlayPause = textBackgroundColor,
                    tintPlayPauseIcon = if ((textBackgroundColor.red + textBackgroundColor.green + textBackgroundColor.blue) > 1.5f) Color.Black else Color.White,
                    tintOtherIcons = textBackgroundColor,
                )

                Spacer(Modifier.height(10.dp))

                V9BottomToggleRow(
                    shuffleModeEnabled = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    onShuffleClick = onShuffleClick,
                    onRepeatClick = onRepeatClick,
                    onMenuClick = onMenuClick,
                    activeColor = textBackgroundColor,
                    inactiveColor = textBackgroundColor.copy(alpha = 0.24f),
                    textColor = textBackgroundColor,
                    containerColor = textBackgroundColor.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                )
            }
        }
    }
}

@Composable
private fun V9Header(
    textColor: Color,
    containerColor: Color,
    iconColor: Color,
    onCollapseClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        V9HeaderButton(
            iconRes = R.drawable.expand_more,
            contentDescription = null,
            containerColor = containerColor,
            iconColor = iconColor,
            shape = CircleShape,
            onClick = onCollapseClick,
        )

        Text(
            text = stringResource(R.string.now_playing),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .basicMarquee(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            V9HeaderButton(
                iconRes = R.drawable.lyrics,
                contentDescription = stringResource(R.string.lyrics),
                containerColor = containerColor,
                iconColor = iconColor,
                shape = RoundedCornerShape(22.dp),
                onClick = onLyricsClick,
            )
            V9HeaderButton(
                iconRes = R.drawable.queue_music,
                contentDescription = stringResource(R.string.queue),
                containerColor = containerColor,
                iconColor = iconColor,
                shape = RoundedCornerShape(22.dp),
                onClick = onQueueClick,
            )
        }
    }
}

@Composable
private fun V9HeaderButton(
    iconRes: Int,
    contentDescription: String?,
    containerColor: Color,
    iconColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        modifier = Modifier.size(56.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun V9Artwork(
    artworkUrl: String?,
    canvasSource: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    isPlaying: Boolean,
    placeholderColor: Color,
    modifier: Modifier = Modifier,
    size: Dp? = null,
) {
    val artworkRequest = rememberOfflineArtworkImageRequest(artworkUrl)
    val baseModifier = if (size != null) Modifier.size(size) else Modifier
    Box(
        modifier =
            modifier
                .then(baseModifier)
                .clip(RoundedCornerShape(36.dp))
                .background(placeholderColor),
    ) {
        AsyncImage(
            model = artworkRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()) {
            CanvasArtworkPlayer(
                primaryUrl = canvasPrimaryUrl,
                fallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun V9Metadata(
    title: String,
    explicit: Boolean,
    artists: List<MediaMetadata.Artist>,
    textColor: Color,
    onTitleClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PlayerTitleText(
            title = title,
            explicit = explicit,
            color = textColor,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .basicMarquee()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onTitleClick,
                    ),
        )
        ClickableArtists(
            artists = artists,
            onArtistClick = onArtistClick,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = textColor.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .basicMarquee(),
        )
    }
}

@Composable
private fun V9PlaybackProgress(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    textColor: Color,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val (smoothProgressFraction, displayedPosition) = rememberSmoothProgress(
        isPlayingProvider = { isPlaying },
        currentPositionProvider = { sliderPosition ?: position },
        totalDuration = duration.coerceAtLeast(0L),
        isVisible = true
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        WavySliderExpressive(
            value = { smoothProgressFraction.value },
            onValueChange = { fraction ->
                onSliderValueChange((fraction * duration.coerceAtLeast(0L)).toLong())
            },
            onValueCommit = { fraction ->
                onSliderValueChangeFinished()
            },
            enabled = duration > 0L,
            activeTrackColor = activeColor,
            inactiveTrackColor = inactiveColor,
            thumbColor = activeColor,
            isPlaying = isPlaying,
            isVisible = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = makeTimeString(sliderPosition ?: displayedPosition.value),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun V9BottomToggleRow(
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onMenuClick: () -> Unit,
    activeColor: Color,
    inactiveColor: Color,
    textColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            color = containerColor,
            shape = RoundedCornerShape(60.dp)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .background(Color.Transparent),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val commonModifier = Modifier.weight(1f)

            ToggleSegmentButton(
                modifier = commonModifier,
                active = shuffleModeEnabled,
                activeColor = activeColor,
                activeCornerRadius = 60.dp,
                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                inactiveColor = inactiveColor,
                inactiveContentColor = textColor.copy(alpha = 0.6f),
                onClick = onShuffleClick,
                iconId = R.drawable.shuffle,
                contentDesc = stringResource(R.string.shuffle)
            )

            val repeatActive = repeatMode != Player.REPEAT_MODE_OFF
            val repeatIcon = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                Player.REPEAT_MODE_ALL -> R.drawable.repeat
                else -> R.drawable.repeat
            }
            ToggleSegmentButton(
                modifier = commonModifier,
                active = repeatActive,
                activeColor = activeColor,
                activeCornerRadius = 60.dp,
                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                inactiveColor = inactiveColor,
                inactiveContentColor = textColor.copy(alpha = 0.6f),
                onClick = onRepeatClick,
                iconId = repeatIcon,
                contentDesc = stringResource(R.string.repeat_mode_all)
            )

            ToggleSegmentButton(
                modifier = commonModifier,
                active = false,
                activeColor = activeColor,
                activeCornerRadius = 60.dp,
                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                inactiveColor = inactiveColor,
                inactiveContentColor = textColor.copy(alpha = 0.6f),
                onClick = onMenuClick,
                iconId = R.drawable.more_vert,
                contentDesc = stringResource(R.string.more)
            )
        }
    }
}

@Composable
fun PlayerBackground(
    playerBackground: PlayerBackgroundStyle,
    mediaMetadata: MediaMetadata?,
    gradientColors: List<Color>,
    disableBlur: Boolean,
    blurRadius: Float,
    playerCustomImageUri: String,
    playerCustomBlur: Float,
    playerCustomContrast: Float,
    playerCustomBrightness: Float,
) {
    val effectiveBlurRadius = blurRadius.coerceIn(0f, PlayerBackgroundMaxBlurRadius)
    val shouldApplyBlur = !disableBlur && effectiveBlurRadius > 0f

    val backgroundSwapState =
        rememberThumbnailSwapState(
            videoId = mediaMetadata?.id,
            ytmUrl = mediaMetadata?.thumbnailUrl,
            lowDataMode = rememberLowDataModeActive(),
            isMusicVideo = mediaMetadata?.isMusicVideo ?: false,
        )
    val backgroundThumbnailUrl = backgroundSwapState.displayUrl
    val styleAppliesBlur =
        effectiveBlurRadius > 0f && effectiveBlurRadius >= 0.5f
    Box(modifier = Modifier.fillMaxSize()) {
        when (playerBackground) {
            PlayerBackgroundStyle.BLUR -> {
                val context = LocalContext.current
                val backgroundHazeState = remember { HazeState() }
                AnimatedContent(
                    targetState = backgroundThumbnailUrl,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = "",
                ) { thumbnailUrl ->
                    if (thumbnailUrl != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model =
                                    ImageRequest
                                        .Builder(context)
                                        .data(thumbnailUrl)
                                        .size(256, 256)
                                        .allowHardware(false)
                                        .build(),
                                contentDescription = "Blurred background",
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .hazeSource(state = backgroundHazeState),
                            )

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .hazeEffect(
                                            state = backgroundHazeState,
                                            style =
                                                HazeStyle(
                                                    backgroundColor = Color.Black, // BRIDGE: haze 1.3.1 throws without it
                                                    blurRadius = 80.dp,
                                                    tint = HazeTint(Color.Black.copy(alpha = 0.30f)),
                                                    noiseFactor = 0.15f,
                                                ),
                                        ),
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.GRADIENT -> {
                AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = "",
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val gradientColorStops =
                                if (colors.size >= 3) {
                                    arrayOf(
                                        0.0f to colors[0].copy(alpha = 0.92f),
                                        0.5f to colors[1].copy(alpha = 0.75f),
                                        1.0f to colors[2].copy(alpha = 0.65f),
                                    )
                                } else {
                                    arrayOf(
                                        0.0f to colors[0].copy(alpha = 0.9f),
                                        0.6f to colors[0].copy(alpha = 0.55f),
                                        1.0f to Color.Black.copy(alpha = 0.7f),
                                    )
                                }
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(colorStops = gradientColorStops)),
                            )

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.18f)),
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.COLORING -> {
                AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = "",
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        val baseColor = PlayerBackgroundColorUtils.ensureComfortableColor(colors.first())
                        val gradientStops = PlayerBackgroundColorUtils.buildColoringStops(baseColor)
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxSize().background(baseColor))
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(colorStops = gradientStops)),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.25f)),
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.BLUR_GRADIENT -> {
                AnimatedContent(
                    targetState = backgroundThumbnailUrl,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = "",
                ) { thumbnailUrl ->
                    if (thumbnailUrl != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = thumbnailUrl.highRes(),
                                contentDescription = "Blurred background",
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier.fillMaxSize().let {
                                        if (styleAppliesBlur) it.blur(radius = effectiveBlurRadius.dp) else it
                                    },
                            )
                            val gradientColorStops =
                                PlayerBackgroundColorUtils.buildBlurGradientStops(gradientColors)
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Brush.verticalGradient(colorStops = gradientColorStops)),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.05f)),
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.CUSTOM -> {
                AnimatedContent(
                    targetState = playerCustomImageUri,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = "",
                ) { uri ->
                    if (uri.isNotBlank()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val blurPx = playerCustomBlur
                            val contrastVal = playerCustomContrast
                            val brightnessVal = playerCustomBrightness

                            val t = (1f - contrastVal) * 128f + (brightnessVal - 1f) * 255f
                            val matrix =
                                floatArrayOf(
                                    contrastVal,
                                    0f,
                                    0f,
                                    0f,
                                    t,
                                    0f,
                                    contrastVal,
                                    0f,
                                    0f,
                                    t,
                                    0f,
                                    0f,
                                    contrastVal,
                                    0f,
                                    t,
                                    0f,
                                    0f,
                                    0f,
                                    1f,
                                    0f,
                                )

                            val cm = ColorMatrix(matrix)

                            AsyncImage(
                                model = Uri.parse(uri),
                                contentDescription = "Custom background",
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier.fillMaxSize().let {
                                        if (disableBlur) it else it.blur(radius = blurPx.dp)
                                    },
                                colorFilter = ColorFilter.colorMatrix(cm),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f)),
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.GLOW -> {
                AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(1200)) togetherWith fadeOut(tween(1200))
                    },
                    label = "",
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .drawWithCache {
                                        val width = size.width
                                        val height = size.height

                                        val baseColor = Color(0xFF050505)

                                        val color1 = colors.getOrElse(0) { Color.DarkGray }
                                        val color2 = colors.getOrElse(1) { color1 }
                                        val color3 = colors.getOrElse(2) { color2 }
                                        val color4 = colors.getOrElse(3) { color1 }
                                        val color5 = colors.getOrElse(4) { color2 }
                                        val color6 = colors.getOrElse(5) { color3 }

                                        val brush1 =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        color1.copy(alpha = 0.8f),
                                                        color1.copy(alpha = 0.5f),
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(width * 0.2f, height * 0.25f),
                                                radius = width * 1.2f,
                                            )

                                        val brush2 =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        color2.copy(alpha = 0.75f),
                                                        color2.copy(alpha = 0.45f),
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(width * 0.85f, height * 0.8f),
                                                radius = width * 1.1f,
                                            )

                                        val brush3 =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        color3.copy(alpha = 0.7f),
                                                        color3.copy(alpha = 0.4f),
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(width * 0.9f, height * 0.15f),
                                                radius = width * 1.0f,
                                            )

                                        val brush4 =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        color4.copy(alpha = 0.65f),
                                                        color4.copy(alpha = 0.35f),
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(width * 0.1f, height * 0.9f),
                                                radius = width * 1.0f,
                                            )

                                        val brush5 =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        color5.copy(alpha = 0.6f),
                                                        color5.copy(alpha = 0.3f),
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(width * 0.5f, height * 0.1f),
                                                radius = width * 0.9f,
                                            )

                                        val brush6 =
                                            Brush.radialGradient(
                                                colors =
                                                    listOf(
                                                        color6.copy(alpha = 0.6f),
                                                        color6.copy(alpha = 0.3f),
                                                        Color.Transparent,
                                                    ),
                                                center = Offset(width * 0.5f, height * 0.95f),
                                                radius = width * 0.9f,
                                            )

                                        onDrawBehind {
                                            drawRect(color = baseColor)
                                            drawRect(brush = brush1)
                                            drawRect(brush = brush2)
                                            drawRect(brush = brush3)
                                            drawRect(brush = brush4)
                                            drawRect(brush = brush5)
                                            drawRect(brush = brush6)
                                        }
                                    },
                        )
                    }
                }
            }

            PlayerBackgroundStyle.GLOW_ANIMATED -> {
                AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(1200)) togetherWith fadeOut(tween(1200))
                    },
                    label = "GlowAnimatedContent",
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        val infiniteTransition = rememberInfiniteTransition(label = "GlowAnimation")

                        val progressState =
                            infiniteTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec =
                                    infiniteRepeatable(
                                        animation = tween(20000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart,
                                    ),
                                label = "glowProgress",
                            )

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .drawWithCache {
                                        val width = size.width
                                        val height = size.height
                                        val baseColor = Color(0xFF050505)

                                        fun rotatedColorAt(
                                            index: Int,
                                            progress: Float,
                                        ): Color {
                                            val size = colors.size
                                            val idx = index.toFloat() + progress * size
                                            val a = kotlin.math.floor(idx).toInt() % size
                                            val b = (a + 1) % size
                                            val frac = idx - kotlin.math.floor(idx)
                                            return androidx.compose.ui.graphics.lerp(
                                                colors.getOrElse(a) { Color.DarkGray },
                                                colors.getOrElse(b) { Color.DarkGray },
                                                frac,
                                            )
                                        }

                                        fun oscillate(
                                            min: Float,
                                            max: Float,
                                            phase: Float,
                                            progress: Float,
                                            speed: Float = 1f,
                                        ): Float {
                                            val v = kotlin.math.sin(2f * kotlin.math.PI.toFloat() * (progress * speed + phase)).toFloat()
                                            return min + (max - min) * ((v + 1f) * 0.5f)
                                        }

                                        onDrawBehind {
                                            val progress = progressState.value

                                            val color1 = rotatedColorAt(0, progress)
                                            val color2 = rotatedColorAt(1, progress)
                                            val color3 = rotatedColorAt(2, progress)
                                            val color4 = rotatedColorAt(3, progress)
                                            val color5 = rotatedColorAt(4, progress)
                                            val color6 = rotatedColorAt(5, progress)

                                            val o1x = oscillate(0.0f, 1.0f, 0.00f, progress, 1.0f)
                                            val o1y = oscillate(0.0f, 0.5f, 0.07f, progress, 1.0f)
                                            val r1 = oscillate(0.8f, 1.6f, 0.12f, progress, 1.0f)

                                            val o2x = oscillate(1.0f, 0.0f, 0.2f, progress, 1.0f)
                                            val o2y = oscillate(0.5f, 1.0f, 0.25f, progress, 1.0f)
                                            val r2 = oscillate(0.7f, 1.5f, 0.18f, progress, 1.0f)

                                            val o3x = oscillate(0.2f, 0.8f, 0.33f, progress, 1.0f)
                                            val o3y = oscillate(0.8f, 0.2f, 0.36f, progress, 1.0f)
                                            val r3 = oscillate(0.6f, 1.4f, 0.29f, progress, 1.0f)

                                            val o4x = oscillate(0.3f, 0.7f, 0.44f, progress, 1.0f)
                                            val o4y = oscillate(0.2f, 0.8f, 0.41f, progress, 1.0f)
                                            val r4 = oscillate(0.9f, 1.7f, 0.47f, progress, 1.0f)

                                            val o5x = oscillate(0.4f, 0.6f, 0.55f, progress, 1.0f)
                                            val o5y = oscillate(0.0f, 1.0f, 0.51f, progress, 1.0f)
                                            val r5 = oscillate(0.7f, 1.5f, 0.58f, progress, 1.0f)

                                            val o6x = oscillate(0.0f, 1.0f, 0.66f, progress, 1.0f)
                                            val o6y = oscillate(0.5f, 0.7f, 0.62f, progress, 1.0f)
                                            val r6 = oscillate(0.8f, 1.8f, 0.69f, progress, 1.0f)

                                            val brush1 =
                                                Brush.radialGradient(
                                                    colors = listOf(color1.copy(alpha = 0.85f), color1.copy(alpha = 0.5f), Color.Transparent),
                                                    center = Offset(width * o1x, height * o1y),
                                                    radius = width * r1,
                                                )
                                            val brush2 =
                                                Brush.radialGradient(
                                                    colors = listOf(color2.copy(alpha = 0.8f), color2.copy(alpha = 0.45f), Color.Transparent),
                                                    center = Offset(width * o2x, height * o2y),
                                                    radius = width * r2,
                                                )
                                            val brush3 =
                                                Brush.radialGradient(
                                                    colors = listOf(color3.copy(alpha = 0.75f), color3.copy(alpha = 0.4f), Color.Transparent),
                                                    center = Offset(width * o3x, height * o3y),
                                                    radius = width * r3,
                                                )
                                            val brush4 =
                                                Brush.radialGradient(
                                                    colors = listOf(color4.copy(alpha = 0.7f), color4.copy(alpha = 0.35f), Color.Transparent),
                                                    center = Offset(width * o4x, height * o4y),
                                                    radius = width * r4,
                                                )
                                            val brush5 =
                                                Brush.radialGradient(
                                                    colors = listOf(color5.copy(alpha = 0.65f), color5.copy(alpha = 0.3f), Color.Transparent),
                                                    center = Offset(width * o5x, height * o5y),
                                                    radius = width * r5,
                                                )
                                            val brush6 =
                                                Brush.radialGradient(
                                                    colors = listOf(color6.copy(alpha = 0.6f), color6.copy(alpha = 0.25f), Color.Transparent),
                                                    center = Offset(width * o6x, height * o6y),
                                                    radius = width * r6,
                                                )

                                            drawRect(color = baseColor)
                                            drawRect(brush = brush1)
                                            drawRect(brush = brush2)
                                            drawRect(brush = brush3)
                                            drawRect(brush = brush4)
                                            drawRect(brush = brush5)
                                            drawRect(brush = brush6)
                                        }
                                    },
                        )
                    }
                }
            }

            else -> {
            }
        }
    }
}
