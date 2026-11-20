/*
 * Neiro
 * GPL-3.0 License | Contributors: see git history
 *
 * The liquid-glass slider, ported from the AndroidLiquidGlass catalog
 * (github.com/guozhiqiang123/AndroidLiquidGlass, the Kyant0/backdrop fork's
 * app module — Apache-2.0): the 6dp capsule track with the accent fill and
 * the 40×24 thumb as a real glass lens that lifts and enlarges under the
 * finger, blurring out as the lens takes over, the squish deformation
 * riding the drag's own velocity. The accent and the track tint are
 * BitChord's own (the theme's primary over the page), and the frost switch
 * (AppSettings.glassPerformance) trades the thumb's lens for a wider blur,
 * the same trade Modifier.liquidGlass makes on every other glass surface.
 */

package com.music.bitchord.ui.components.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidGlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,

    backdrop: Backdrop = emptyBackdrop(),
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accentColor = MaterialTheme.colorScheme.primary
    val trackColor =
        if (dark) Color(0xFF787880).copy(0.36f)
        else Color(0xFF787878).copy(0.2f)
    val frost by AppSettings.glassPerformance.collectAsStateWithLifecycle()

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(SliderHeight)
            .progressSemantics(value.coerceIn(valueRange), valueRange)
            .semantics {
                if (!enabled) disabled()
                setProgress { requested ->
                    if (!enabled || !requested.isFinite()) {
                        false
                    } else {
                        val target = requested.coerceIn(valueRange)
                        if (target == value.coerceIn(valueRange)) {
                            false
                        } else {
                            onValueChange(target)
                            onValueChangeFinished?.invoke()
                            true
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val latestValue = rememberUpdatedState(value)
        val latestOnValueChange = rememberUpdatedState(onValueChange)
        val latestOnValueChangeFinished = rememberUpdatedState(onValueChangeFinished)
        val fingerValue = remember { floatArrayOf(0f) }
        val dampedDragAnimation = remember(animationScope, valueRange) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value.coerceIn(valueRange),
                valueRange = valueRange,
                visibilityThreshold = (valueRange.endInclusive - valueRange.start) / 1000f,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag) {
                        latestOnValueChange.value(fingerValue[0])
                    }
                    didDrag = false
                    latestOnValueChangeFinished.value?.invoke()
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        fingerValue[0] = targetValue
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    fingerValue[0] =
                        if (isLtr) (fingerValue[0] + delta).coerceIn(valueRange)
                        else (fingerValue[0] - delta).coerceIn(valueRange)
                    latestOnValueChange.value(fingerValue[0])
                }
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { latestValue.value.coerceIn(valueRange) }
                .collectLatest { value ->
                    if (dampedDragAnimation.targetValue != value) {
                        dampedDragAnimation.updateValue(value)
                    }
                }
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .pointerInput(animationScope) {
                        detectTapGestures { position ->
                            val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                            val targetValue =
                                (if (isLtr) valueRange.start + delta
                                else valueRange.endInclusive - delta)
                                    .coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(targetValue)
                            latestOnValueChange.value(targetValue)
                        }
                    }
                    .height(LiquidGlassDefaults.SliderTrackHeight)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(Capsule())
                    .background(accentColor)
                    .height(LiquidGlassDefaults.SliderTrackHeight)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur((if (frost) FrostBlurRadius else ThumbBlurRadius).toPx() * (1f - progress))
                        if (!frost) {
                            lens(
                                10f.dp.toPx() * progress,
                                14f.dp.toPx() * progress,
                                chromaticAberration = true
                            )
                        }
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(LiquidGlassDefaults.SliderThumbWidth, LiquidGlassDefaults.SliderThumbHeight)
        )
    }
}

private val SliderHeight = 40.dp


private val ThumbBlurRadius = LiquidGlassDefaults.ThumbBlur
private val FrostBlurRadius = 16.dp
