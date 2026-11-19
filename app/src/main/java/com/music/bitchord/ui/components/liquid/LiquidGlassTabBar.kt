/*
 * Neiro
 * GPL-3.0 License | Contributors: see git history
 *
 * The liquid-glass tab bar, ported from the AndroidLiquidGlass catalog
 * (github.com/guozhiqiang123/AndroidLiquidGlass, the Kyant0/backdrop
 * fork's app module — Apache-2.0): the glass capsule platter with its
 * selection indicator as a real control lens over the material — the bar
 * records an accent-tinted copy of itself that the lens refracts, so the
 * selected tab reads through the glass in the accent — the damped drag
 * that carries the indicator between tabs, the squish deformation riding
 * the drag's velocity, and the tracking highlight that follows the finger.
 *
 * BitChord's split-search layout rides on it: the main capsule (Home,
 * Explore, Library) with the Search circle as its own glass lens beside
 * it, and the fold — the scroll-driven collapse of the platter into the
 * selected tab's circle — is the app's own, driven by [compactProgress]
 * exactly as the glass bar always has (see MainActivity's nestedScroll).
 */
package com.music.bitchord.ui.components.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.BottomTab
import com.music.bitchord.ui.components.LocalAppBackdrop
import com.music.bitchord.ui.components.glassContentColor
import com.music.bitchord.ui.components.rememberLiquidGlassTuning
import kotlinx.coroutines.flow.collectLatest

/** The tab's own scale while the drag is pressed — the bulge under the finger, read by [LiquidGlassTab]. */
private val LocalLiquidTabScale =
    staticCompositionLocalOf { { 1f } }

/**
 * The liquid-glass tab bar: [tabs] share the capsule platter, [trailingTab]
 * is its own glass circle beside it (BitChord's split-search layout, Search
 * as the trailing control), and [selectedKey] indexes them all — the
 * trailing tab is [tabs.size].
 *
 * The selection indicator is a control lens over the material: the bar
 * records an accent-tinted copy of itself that the lens refracts, so the
 * selected tab reads through the glass in the theme's accent. The whole
 * bar rides the damped drag — the indicator can be carried between tabs
 * with a finger and springs to whichever it lands nearest, the platter
 * squishing with the drag's own velocity — and the tracking highlight
 * follows the finger along it.
 *
 * [compactProgress] is the fold: 0 the bar expanded, 1 collapsed into the
 * selected tab's circle. The tabs' labels and every tab but the selected
 * fade with it, the platter gathers into the circle at the selected tab,
 * and once collapsed a tap anywhere on it is [onExpand] — the bar the
 * scroll left collapsed reopens on its own terms.
 */
@Composable
fun LiquidGlassTabBar(
    tabs: List<BottomTab>,
    trailingTab: BottomTab,
    selectedKey: Int,
    onSelected: (Int) -> Unit,
    compactProgress: State<Float>,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalAppBackdrop.current,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accentColor = MaterialTheme.colorScheme.primary
    val contentColor = glassContentColor()
    val tuning = rememberLiquidGlassTuning()
    val frost by AppSettings.glassPerformance.collectAsStateWithLifecycle()

    val compact = compactProgress.value.coerceIn(0f, 1f)
    val collapsed = compact > COLLAPSED_TAP_THRESHOLD

    val containerColor = (if (dark) Color(0xFF121212) else Color(0xFFFAFAFA))
        .copy(alpha = (0.4f * tuning.tintFactor * tuning.adaptiveLuminanceFactor).coerceIn(0f, 1f))
    val glassSamplingScale = if (frost) FrostSamplingScale else GlassSamplingScale

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier.height(BarHeight).fillMaxWidth(),
    ) {
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val density = LocalDensity.current
        val animationScope = rememberCoroutineScope()
        val mainCount = tabs.size
        val totalWidth = constraints.maxWidth.toFloat()
        val trailingSizePx = with(density) { TrailingSize.toPx() }
        val gapPx = with(density) { TrailingGap.toPx() }
        val platterWidth = (totalWidth - trailingSizePx - gapPx).coerceAtLeast(1f)
        val insetPx = with(density) { PlatterInset.toPx() }
        val barHeightPx = with(density) { BarHeight.toPx() }
        val tabWidth = (platterWidth - insetPx * 2f) / mainCount.coerceAtLeast(1)

        var currentIndex by remember(selectedKey) { mutableIntStateOf(selectedKey) }
        val dampedDragAnimation = remember(animationScope, platterWidth, mainCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedKey.coerceIn(0, mainCount - 1).toFloat(),
                valueRange = 0f..(mainCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, mainCount - 1)
                    if (targetIndex != currentIndex) {
                        currentIndex = targetIndex
                        onSelected(targetIndex)
                    }
                    animateToValue(targetIndex.toFloat())
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (mainCount - 1).toFloat())
                    )
                }
            )
        }
        LaunchedEffect(selectedKey, mainCount) {
            val clamped = selectedKey.coerceIn(0, mainCount - 1)
            if (clamped != currentIndex) currentIndex = clamped
        }
        LaunchedEffect(dampedDragAnimation, currentIndex, mainCount) {
            snapshotFlow { currentIndex.coerceIn(0, mainCount - 1) }
                .collectLatest { index ->
                    if (dampedDragAnimation.targetValue != index.toFloat()) {
                        dampedDragAnimation.animateToValue(index.toFloat())
                    }
                }
        }

        val interactiveHighlight = remember(animationScope, tabWidth, isLtr) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { _, _ ->
                    Offset(
                        (dampedDragAnimation.value + 0.5f) * tabWidth + insetPx,
                        barHeightPx / 2f
                    )
                }
            )
        }

        // Both rows below fill the same box, so the recorded accent copy
        // lands exactly under the visible one and the lens refracts the bar
        // as it is laid out.
        Box(Modifier.fillMaxSize()) {
            // ---- The visible bar: the platter the main tabs share, plus the
            // trailing circle beside it. ----
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TrailingGap),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            // The fold: the platter gathers into the selected
                            // tab's circle, its labels and every tab but the
                            // selected fading with it.
                            scaleX = lerp(1f, CollapsedCircle.toPx() / size.width, compact)
                            scaleY = lerp(1f, CollapsedCircle.toPx() / size.height, compact)
                            transformOrigin = TransformOrigin(
                                ((currentIndex.coerceIn(0, mainCount - 1) + 0.5f) / mainCount)
                                    .coerceIn(0.05f, 0.95f),
                                0.5f,
                            )
                        }
                        .then(
                            if (collapsed) {
                                Modifier.clickable(
                                    interactionSource = null,
                                    indication = null,
                                    onClick = onExpand,
                                )
                            } else {
                                Modifier
                            }
                        )
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                if (tuning.vibrancyFactor > 0f) colorControls(saturation = 1.5f)
                                blur((if (frost) FrostBlurRadius else PlatterBlurRadius).toPx() * tuning.blurFactor)
                                if (!frost) {
                                    lens(
                                        24f.dp.toPx() * tuning.refractionHeightFactor * (1f - compact),
                                        24f.dp.toPx() * tuning.refractionAmountFactor * (1f - compact),
                                        chromaticAberration = tuning.chromaticAberration,
                                    )
                                }
                            },
                            highlight = { Highlight.Default },
                            shadow = {
                                Shadow.Default.copy(radius = Shadow.Default.radius * tuning.shadowFactor)
                            },
                            layerBlock = {
                                val progress = dampedDragAnimation.pressProgress
                                val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                                scaleX *= scale
                                scaleY *= scale
                            },
                            samplingScale = { _, _ -> glassSamplingScale },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight.modifier)
                        .padding(PlatterInset),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = (1f - compact * 1.6f).coerceIn(0f, 1f) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            LiquidGlassTab(
                                tab = tab,
                                selected = index == selectedKey,
                                compact = compact,
                                contentColor = contentColor,
                                accentColor = accentColor,
                                onClick = { onSelected(index) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                            )
                        }
                    }
                }

                TrailingGlassControl(
                    tab = trailingTab,
                    selected = selectedKey == tabs.size,
                    compact = compact,
                    backdrop = backdrop,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    containerColor = containerColor,
                    blurRadius = (if (frost) FrostBlurRadius else PlatterBlurRadius) * tuning.blurFactor,
                    refractionHeightFactor = tuning.refractionHeightFactor,
                    refractionAmountFactor = tuning.refractionAmountFactor,
                    chromaticAberration = tuning.chromaticAberration,
                    samplingScale = glassSamplingScale,
                    onClick = { onSelected(tabs.size) },
                    modifier = Modifier.size(TrailingSize),
                )
            }

            // ---- The recorded copy: the same platter and tabs, drawn nowhere
            // but into the layer the selection lens samples — the glass with
            // its tint, and the glyphs in the accent. ----
            Row(
                Modifier
                    .fillMaxSize()
                    .alpha(0f)
                    .clearAndSetSemantics {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TrailingGap),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer {
                            scaleX = lerp(1f, CollapsedCircle.toPx() / size.width, compact)
                            scaleY = lerp(1f, CollapsedCircle.toPx() / size.height, compact)
                            transformOrigin = TransformOrigin(
                                ((currentIndex.coerceIn(0, mainCount - 1) + 0.5f) / mainCount)
                                    .coerceIn(0.05f, 0.95f),
                                0.5f,
                            )
                        }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                if (tuning.vibrancyFactor > 0f) colorControls(saturation = 1.5f)
                                blur((if (frost) FrostBlurRadius else PlatterBlurRadius).toPx() * tuning.blurFactor)
                                if (!frost) {
                                    lens(
                                        24f.dp.toPx() * tuning.refractionHeightFactor * (1f - compact),
                                        24f.dp.toPx() * tuning.refractionAmountFactor * (1f - compact),
                                        chromaticAberration = tuning.chromaticAberration,
                                    )
                                }
                            },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Default.copy(alpha = progress)
                            },
                            samplingScale = { _, _ -> glassSamplingScale },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .padding(PlatterInset),
                ) {
                    CompositionLocalProvider(
                        LocalLiquidTabScale provides {
                            lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                        }
                    ) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                LiquidGlassTab(
                                    tab = tab,
                                    selected = index == selectedKey,
                                    compact = compact,
                                    contentColor = Color.Unspecified,
                                    accentColor = Color.Unspecified,
                                    onClick = {},
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }

            // ---- The selection indicator: the control lens over the
            // material, following the damped drag. ----
            Box(
                Modifier
                    .graphicsLayer {
                        translationX =
                            (if (isLtr) dampedDragAnimation.value * tabWidth + insetPx
                            else platterWidth - insetPx - (dampedDragAnimation.value + 1f) * tabWidth)
                    }
                    .graphicsLayer { alpha = (1f - compact * 1.4f).coerceIn(0f, 1f) }
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier)
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            lens(
                                10f.dp.toPx() * progress * tuning.refractionHeightFactor,
                                14f.dp.toPx() * progress * tuning.refractionAmountFactor,
                                chromaticAberration = true,
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        shadow = {
                            val progress = dampedDragAnimation.pressProgress
                            Shadow(alpha = progress)
                        },
                        innerShadow = {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 8f.dp * progress,
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
                            drawRect(
                                if (dark) Color.Black.copy(0.1f) else Color.White.copy(0.1f),
                                alpha = 1f - progress,
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        },
                    )
                    .align(Alignment.CenterStart)
                    .size(with(density) { tabWidth.toDp() }, IndicatorHeight)
            )
        }
    }
}

/** One tab of the platter: the glyph and its label, fading with the fold. */
@Composable
private fun LiquidGlassTab(
    tab: BottomTab,
    selected: Boolean,
    compact: Float,
    contentColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale = LocalLiquidTabScale.current
    Column(
        modifier
            .clip(Capsule())
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            }
            .graphicsLayer {
                // Everything but the selected tab fades with the fold; the
                // selected glyph is what the collapsed circle carries.
                alpha = if (selected) 1f else (1f - compact * 2f).coerceIn(0f, 1f)
            }
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = if (selected) accentColor else contentColor,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = tab.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.W500,
            textAlign = TextAlign.Center,
            color = if (selected) accentColor else contentColor,
            maxLines = 1,
        )
    }
}

/** The trailing control: its own glass circle, fading away with the fold. */
@Composable
private fun TrailingGlassControl(
    tab: BottomTab,
    selected: Boolean,
    compact: Float,
    backdrop: Backdrop,
    contentColor: Color,
    accentColor: Color,
    containerColor: Color,
    blurRadius: Dp,
    refractionHeightFactor: Float,
    refractionAmountFactor: Float,
    chromaticAberration: Boolean,
    samplingScale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pressSource = remember { MutableInteractionSource() }
    val pressed by pressSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "trailingPressScale",
    )

    Box(
        modifier
            .graphicsLayer {
                alpha = (1f - compact * 1.6f).coerceIn(0f, 1f)
                scaleX = 1f - 0.24f * compact
                scaleY = 1f - 0.24f * compact
            }
            .graphicsLayer {
                scaleX *= pressScale
                scaleY *= pressScale
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    blur(blurRadius.toPx())
                    lens(
                        24f.dp.toPx() * refractionHeightFactor,
                        24f.dp.toPx() * refractionAmountFactor,
                        chromaticAberration = chromaticAberration,
                    )
                },
                highlight = { Highlight.Default },
                shadow = { Shadow.Default },
                samplingScale = { _, _ -> samplingScale },
                onDrawSurface = { drawRect(containerColor) },
            )
            .clip(Capsule())
            .clickable(
                interactionSource = pressSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = if (selected) accentColor else contentColor,
            modifier = Modifier.size(26.dp),
        )
    }
}

/** The bar's height — the platter the tabs share. */
private val BarHeight = 64.dp

/** The platter's inset: the air between its glass edge and the tabs. */
private val PlatterInset = 4.dp

/** The trailing control's circle, and the circle the fold gathers into. */
private val TrailingSize = 64.dp
private val TrailingGap = 8.dp
private val CollapsedCircle = 56.dp

/** The selection indicator's height — the platter's content. */
private val IndicatorHeight = 56.dp

/** The platter's blur, and the frost's wider one. */
private val PlatterBlurRadius = 8.dp
private val FrostBlurRadius = 16.dp

/** The resolution the bar records its backdrop at — see Modifier.liquidGlass. */
private const val GlassSamplingScale = 0.33f
private const val FrostSamplingScale = 0.25f

/** Past this fold the collapsed circle is what is tapped, not the tabs. */
private const val COLLAPSED_TAP_THRESHOLD = 0.5f
