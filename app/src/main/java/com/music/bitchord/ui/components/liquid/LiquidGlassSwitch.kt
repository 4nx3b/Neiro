/*
 * Neiro
 * GPL-3.0 License | Contributors: see git history
 *
 * The liquid-glass toggle, ported from the AndroidLiquidGlass catalog
 * (github.com/guozhiqiang123/AndroidLiquidGlass, the Kyant0/backdrop fork's
 * app module — Apache-2.0): the 64×28 capsule track lerping to iOS green
 * and the 40×24 thumb as a real glass lens that lifts and enlarges under
 * the finger — the same capsule the plain IosSwitch draws below the glass,
 * so the two switches are one shape at both densities. On is iOS green
 * whatever the theme's accent, the one colour the platform keeps its own.
 */
package com.music.bitchord.ui.components.liquid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
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
import com.music.bitchord.ui.components.LocalAppBackdrop
import kotlinx.coroutines.flow.collectLatest

/**
 * The app's glass settings toggle, on the vendored AndroidLiquidGlass
 * renderer: the track's own layer backdrop fused with the page beneath it
 * under the thumb's lens, which lifts, blurs out and enlarges under the
 * finger, the squish deformation riding the drag's velocity. Tapping flips
 * through the spring; dragging commits at half travel and springs home
 * either way.
 *
 * Composed only where Liquid Glass is on and the glass is supported (see
 * IosSwitch, which gates it); the plain iOS 26 capsule is the fallback
 * everywhere else. Under "reduce dynamic blur" the lens and the sampling
 * are skipped for a solid track and a white thumb — what that setting
 * promises everywhere else in the app.
 */
@Composable
fun LiquidGlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: Backdrop = LocalAppBackdrop.current,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accentColor =
        if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    val trackColor =
        if (dark) Color(0xFF787880).copy(0.36f)
        else Color(0xFF787878).copy(0.2f)
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val frost by AppSettings.glassPerformance.collectAsStateWithLifecycle()

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    val currentEnabled by rememberUpdatedState(enabled)
    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (!currentEnabled) return@DampedDragAnimation
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                } else if (currentChecked) {
                    fraction = 0f
                } else {
                    fraction = 1f
                }
                didDrag = false
                currentOnCheckedChange(fraction == 1f)
            },
            onDrag = { _, dragAmount ->
                if (!currentEnabled) return@DampedDragAnimation
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                fraction =
                    if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                    else (fraction - delta).fastCoerceIn(0f, 1f)
            },
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { fraction ->
                dampedDragAnimation.updateValue(fraction)
            }
    }
    LaunchedEffect(checked, enabled) {
        val target = if (checked) 1f else 0f
        if (target != fraction || target != dampedDragAnimation.targetValue) {
            didDrag = false
            fraction = target
            // Restored preferences and parent-row changes are not thumb presses.
            dampedDragAnimation.animateToValue(target)
        }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .size(TrackWidth, TrackHeight)
            .semantics {
                role = Role.Switch
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                if (!enabled) disabled()
                onClick {
                    if (currentEnabled) currentOnCheckedChange(!currentChecked)
                    currentEnabled
                }
            }
            .then(if (enabled) dampedDragAnimation.modifier else Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .then(if (!reduceDynamicBlur) Modifier.layerBackdrop(trackBackdrop) else Modifier)
                .clip(Capsule())
                .drawBehind {
                    val fraction = dampedDragAnimation.value
                    drawRect(lerp(trackColor, accentColor, fraction))
                }
                .size(TrackWidth, TrackHeight)
        )

        Box(
            Modifier
                .graphicsLayer {
                    val fraction = dampedDragAnimation.value
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, fraction)
                        else lerp(-padding, -(padding + dragWidth), fraction)
                }
                .drawBackdrop(
                    backdrop = if (reduceDynamicBlur) {
                        emptyBackdrop()
                    } else {
                        rememberCombinedBackdrop(
                            backdrop,
                            rememberBackdrop(trackBackdrop) { drawBackdrop ->
                                val progress = dampedDragAnimation.pressProgress
                                val scaleX = lerp(2f / 3f, 0.75f, progress)
                                val scaleY = lerp(0f, 0.75f, progress)
                                scale(scaleX, scaleY) {
                                    drawBackdrop()
                                }
                            }
                        )
                    },
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur((if (frost) FrostBlurRadius else ThumbBlurRadius).toPx() * (1f - progress))
                        if (!frost) {
                            lens(
                                5f.dp.toPx() * progress,
                                10f.dp.toPx() * progress,
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
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        if (reduceDynamicBlur) {
                            drawRect(Color.White)
                        } else {
                            drawRect(Color.White.copy(alpha = 1f - progress))
                        }
                    }
                )
                .size(ThumbWidth, ThumbHeight)
        )
    }
}

/** The switch's track — the same capsule the plain IosSwitch draws. */
private val TrackWidth = 64.dp
private val TrackHeight = 28.dp

/** The glass thumb, the lens that rides the track. */
private val ThumbWidth = 40.dp
private val ThumbHeight = 24.dp

/** The thumb's blur while unpressed — and the frost's wider one, always down. */
private val ThumbBlurRadius = 8.dp
private val FrostBlurRadius = 16.dp
