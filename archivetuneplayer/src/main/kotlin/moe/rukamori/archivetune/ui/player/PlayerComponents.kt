/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import me.saket.squiggles.SquigglySlider
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.PlayerSliderTrack
import moe.rukamori.archivetune.ui.theme.PlayerBackgroundColorUtils
import moe.rukamori.archivetune.ui.theme.PlayerSliderColors
import moe.rukamori.archivetune.ui.utils.fadingEdge
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.utils.rememberLowDataModeActive

private const val PlayerBackgroundMaxBlurRadius = 64f

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
            val sliderState =
                remember(valueRange.start, valueRange.endInclusive) {
                    SliderState(
                        value = value,
                        steps = 0,
                        trackRange = valueRange,
                    )
                }
            sliderState.value = value
            Slider(
                state = sliderState,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.thickSliderColors(activeColor),
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { state ->
                    PlayerSliderTrack(
                        sliderState = state,
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
