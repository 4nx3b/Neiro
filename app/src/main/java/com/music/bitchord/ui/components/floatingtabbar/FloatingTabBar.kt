@file:OptIn(ExperimentalSharedTransitionApi::class)

/*
 * FloatingTabBar v1.0.1 by Elyes Mansour
 * https://github.com/elyesmansour/compose-floating-tab-bar
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Vendored from the v1.0.1 tag: the published AAR is compiled against
 * Compose 1.8 and crashes with NoSuchMethodError on
 * SharedTransitionScope.sharedElement under the Compose version this app uses.
 * Compiling the source here keeps it binary-compatible.
 *
 * Local modifications on top of v1.0.1:
 * - Package renamed.
 * - InlineBar/ExpandedBar use Row/Column instead of ConstraintLayout, and the
 *   tab pill + standalone tab cluster is centered (iOS 26 style) instead of
 *   being pinned to opposite screen edges.
 * - The inline/expanded AnimatedContent is center-aligned so the collapse
 *   animation morphs around the center instead of the start edge.
 *
 * Taken from EchoMusicApp/Echo-Music, which carries the three modifications
 * listed above; Zenith's own use of it is in
 * [com.music.bitchord.ui.components.GlassNavBar]. One further change here:
 *
 * - `tabBarContentModifier` is a `@Composable () -> Modifier` factory rather
 *   than a single Modifier value. It is applied to three surfaces of three
 *   different sizes (the tab pill, the circular standalone tab, the accessory),
 *   and a liquid glass Modifier carries a shape-outline cache keyed on the size
 *   it last saw — so one shared instance is invalidated by whichever surface
 *   drew last and re-creates all three outlines every frame. A factory gives
 *   each surface its own.
 * - The expanded tab group's selection pill is a lifted glass lens in motion
 *   (see [GlassSelectionPill]): a tap inflates it past the bar, carries it to
 *   the tab on a spring, squashes it with its acceleration, and sheds the glass
 *   back into a flat fill on landing. Horizontal drags pick it up the same way,
 *   resist at the ends, tick across tab boundaries, and settle on release.
 */

package com.music.bitchord.ui.components.floatingtabbar

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.GlassSpring
import com.music.bitchord.ui.components.JellyPush
import com.music.bitchord.ui.components.LocalGlassExport
import com.music.bitchord.ui.components.PILL_INSET
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.music.bitchord.ui.components.isGlassSupported
import com.music.bitchord.ui.haptics.Haptic
import com.music.bitchord.ui.haptics.rememberHaptics
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val LocalTabPillCoverage = compositionLocalOf<() -> Float> { { Float.NaN } }

val LocalTabPillGeometry = compositionLocalOf<() -> Offset> { { Offset.Unspecified } }

private val DefaultTabBarContentModifier: @Composable () -> Modifier = { Modifier }

private val ZeroChromeProgress: () -> Float = { 0f }

private const val CHROME_PUSH_DONE_AT = 0.7f

private val CHROME_PUSH_MARGIN = 64.dp

private fun chromePushEase(progress: Float): Float {
    val t = (progress / CHROME_PUSH_DONE_AT).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun Modifier.chromePushDown(progress: () -> Float, panelDrop: (() -> Float)?): Modifier = graphicsLayer {
    val drop = panelDrop?.invoke() ?: Float.NaN
    translationY = if (!drop.isNaN()) {
        drop
    } else {
        val e = chromePushEase(progress())
        (size.height + CHROME_PUSH_MARGIN.toPx()) * e
    }
}

private fun Modifier.chromePushSide(
    progress: () -> Float,
    direction: Float,
    edgeTravel: ((Boolean) -> Float)?,
): Modifier = graphicsLayer {
    val travel = edgeTravel?.invoke(direction > 0f) ?: Float.NaN
    translationX = if (!travel.isNaN()) {
        direction * travel
    } else {
        val e = chromePushEase(progress())
        direction * (size.width + CHROME_PUSH_MARGIN.toPx()) * e
    }
}

private val JELLY_PUSH_MARGIN = 10.dp

private fun Modifier.jellyPushSide(push: JellyPush?, direction: Float, gap: Dp): Modifier {
    if (push == null) return this
    return graphicsLayer {
        val room = (gap - JELLY_PUSH_MARGIN).toPx().coerceAtLeast(0f)
        val over = (push.advance(towardLeft = direction < 0f) - room).coerceAtLeast(0f)
        translationX = direction * over
    }
}

@Composable
fun FloatingTabBar(
    isInline: Boolean,
    selectedTabKey: Any?,
    modifier: Modifier = Modifier,
    tabBarContentModifier: @Composable () -> Modifier = DefaultTabBarContentModifier,
    inlineAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    colors: FloatingTabBarColors = FloatingTabBarDefaults.colors(),
    shapes: FloatingTabBarShapes = FloatingTabBarDefaults.shapes(),
    sizes: FloatingTabBarSizes = FloatingTabBarDefaults.sizes(),
    elevations: FloatingTabBarElevations = FloatingTabBarDefaults.elevations(),
    contentKey: Any? = null,
    content: FloatingTabBarScope.() -> Unit
) {
    val scrollConnection = rememberFloatingTabBarScrollConnection(
        initialIsInline = isInline,
        inlineBehavior = FloatingTabBarInlineBehavior.Never
    )

    LaunchedEffect(isInline) {
        if (isInline) scrollConnection.inline() else scrollConnection.expand()
    }

    FloatingTabBar(
        selectedTabKey = selectedTabKey,
        scrollConnection = scrollConnection,
        modifier = modifier,
        tabBarContentModifier = tabBarContentModifier,
        inlineAccessory = inlineAccessory,
        expandedAccessory = expandedAccessory,
        colors = colors,
        shapes = shapes,
        sizes = sizes,
        elevations = elevations,
        contentKey = contentKey,
        content = content
    )
}

@Composable
fun FloatingTabBar(
    selectedTabKey: Any?,
    scrollConnection: FloatingTabBarScrollConnection,
    modifier: Modifier = Modifier,
    chromeProgress: () -> Float = ZeroChromeProgress,
    chromeSidePush: ((Boolean) -> Float)? = null,
    chromeDownPush: (() -> Float)? = null,
    jellyPush: JellyPush? = null,
    inlineSplitLast: Boolean = false,
    tabBarContentModifier: @Composable () -> Modifier = DefaultTabBarContentModifier,
    inlineAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    colors: FloatingTabBarColors = FloatingTabBarDefaults.colors(),
    shapes: FloatingTabBarShapes = FloatingTabBarDefaults.shapes(),
    sizes: FloatingTabBarSizes = FloatingTabBarDefaults.sizes(),
    elevations: FloatingTabBarElevations = FloatingTabBarDefaults.elevations(),
    contentKey: Any? = null,
    content: FloatingTabBarScope.() -> Unit
) {
    val scope = remember(contentKey, inlineSplitLast) {
        FloatingTabBarScopeImpl(inlineSplitLast).apply { content() }
    }

    val isAccessoryShared = inlineAccessory != null && expandedAccessory != null

    val pull = remember { androidx.compose.animation.core.Animatable(0f) }
    val pullPx = with(LocalDensity.current) { 28.dp.toPx() }
    var shownInline by remember { mutableStateOf(scrollConnection.isInline) }
    LaunchedEffect(scrollConnection.isInline) {
        val target = scrollConnection.isInline
        if (target == shownInline) {
            pull.animateTo(
                0f,
                androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 300f),
            )
            return@LaunchedEffect
        }
        val amplitude = if (target) 1f else -0.7f
        pull.animateTo(
            amplitude,
            androidx.compose.animation.core.tween(190, easing = FastOutSlowInEasing),
        )
        shownInline = target
        pull.animateTo(
            0f,
            androidx.compose.animation.core.spring(dampingRatio = 0.42f, stiffness = 210f),
        )
    }

    val inlineWithPull: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? =
        remember(inlineAccessory, pullPx) {
            inlineAccessory?.let { acc ->
                val wrapped: @Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit =
                    { m, s -> acc(Modifier.rubberStretch(pullPx) { pull.value }.then(m), s) }
                wrapped
            }
        }
    val expandedWithPull: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? =
        remember(expandedAccessory, pullPx) {
            expandedAccessory?.let { acc ->
                val wrapped: @Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit =
                    { m, s -> acc(Modifier.rubberStretch(pullPx) { pull.value }.then(m), s) }
                wrapped
            }
        }

    SharedTransitionLayout(modifier = modifier) {
        AnimatedContent(
            targetState = shownInline,
            transitionSpec = {
                (fadeIn() togetherWith fadeOut()).using(
                    androidx.compose.animation.SizeTransform(clip = false) { _, _ ->
                        androidx.compose.animation.core.spring(
                            dampingRatio = 0.62f,
                            stiffness = 320f,
                            visibilityThreshold = IntSize(1, 1),
                        )
                    },
                )
            },
            contentAlignment = Alignment.BottomCenter
        ) { isInline ->
            if (isInline) {
                InlineBar(
                    scope = scope,
                    chromeProgress = chromeProgress,
                    chromeSidePush = chromeSidePush,
                    jellyPush = jellyPush,
                    selectedTabKey = selectedTabKey,
                    accessory = inlineWithPull,
                    isAccessoryShared = isAccessoryShared,
                    onInlineTabClick = { scrollConnection.expand() },
                    neighbourPull = { pull.value },
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = tabBarContentModifier,
                    animatedVisibilityScope = this@AnimatedContent
                )
            } else {
                ExpandedBar(
                    scope = scope,
                    chromeProgress = chromeProgress,
                    chromeDownPush = chromeDownPush,
                    selectedTabKey = selectedTabKey,
                    accessory = expandedWithPull,
                    isAccessoryShared = isAccessoryShared,
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = tabBarContentModifier,
                    animatedVisibilityScope = this@AnimatedContent
                )
            }
        }
    }
}

private val LiquidBounds = androidx.compose.animation.BoundsTransform { _, _ ->
    androidx.compose.animation.core.spring(
        dampingRatio = 0.62f,
        stiffness = 320f,
        visibilityThreshold = androidx.compose.ui.geometry.Rect(0.5f, 0.5f, 0.5f, 0.5f),
    )
}

class FloatingTabBarScrollConnection(
    initialIsInline: Boolean = false,
    private val scrollThresholdPx: Float,
    private val inlineBehavior: FloatingTabBarInlineBehavior = FloatingTabBarInlineBehavior.OnScrollDown,
    private val expandOnlyAtTop: Boolean = false,
    private val expandThresholdPx: Float = scrollThresholdPx
) : NestedScrollConnection {
    var isInline by mutableStateOf(initialIsInline)
        private set

    var topSignal by mutableIntStateOf(0)
        private set

    private var accumulatedScroll = 0f

    fun expand() {
        isInline = false
        accumulatedScroll = 0f
    }

    fun inline() {
        isInline = true
        accumulatedScroll = 0f
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (inlineBehavior == FloatingTabBarInlineBehavior.Never) {
            return Offset.Zero
        }

        val scrollDelta = available.y

        if ((accumulatedScroll > 0 && scrollDelta < 0) || (accumulatedScroll < 0 && scrollDelta > 0)) {
            accumulatedScroll = 0f
        }

        accumulatedScroll += scrollDelta

        when (inlineBehavior) {
            FloatingTabBarInlineBehavior.OnScrollDown -> {
                if (accumulatedScroll <= -scrollThresholdPx && !isInline) {
                    isInline = true
                    accumulatedScroll = 0f
                } else if (accumulatedScroll >= expandThresholdPx && isInline && !expandOnlyAtTop) {
                    isInline = false
                    accumulatedScroll = 0f
                }
            }

            FloatingTabBarInlineBehavior.OnScrollUp -> {
                if (accumulatedScroll >= scrollThresholdPx && !isInline) {
                    isInline = true
                    accumulatedScroll = 0f
                } else if (accumulatedScroll <= -scrollThresholdPx && isInline) {
                    isInline = false
                    accumulatedScroll = 0f
                }
            }

            FloatingTabBarInlineBehavior.Never -> {
            }
        }

        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (expandOnlyAtTop &&
            inlineBehavior == FloatingTabBarInlineBehavior.OnScrollDown &&
            isInline &&
            available.y > 0f
        ) {
            topSignal++
        }
        return Offset.Zero
    }
}

@Composable
fun rememberFloatingTabBarScrollConnection(
    initialIsInline: Boolean = false,
    scrollThreshold: Dp = 50.dp,
    inlineBehavior: FloatingTabBarInlineBehavior = FloatingTabBarInlineBehavior.OnScrollDown,
    expandOnlyAtTop: Boolean = false,
    expandThreshold: Dp = scrollThreshold
): FloatingTabBarScrollConnection = with(LocalDensity.current) {
    val scrollThresholdPx = scrollThreshold.toPx()
    val expandThresholdPx = expandThreshold.toPx()
    remember(scrollThresholdPx, expandThresholdPx, inlineBehavior, initialIsInline, expandOnlyAtTop) {
        FloatingTabBarScrollConnection(
            initialIsInline,
            scrollThresholdPx,
            inlineBehavior,
            expandOnlyAtTop,
            expandThresholdPx,
        )
    }
}

enum class FloatingTabBarInlineBehavior {
    Never,

    OnScrollDown,

    OnScrollUp
}

interface FloatingTabBarScope {
    fun tab(
        key: Any,
        title: @Composable () -> Unit,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)? = { LocalIndication.current }
    )

    fun standaloneTab(
        key: Any,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)? = { LocalIndication.current }
    )
}

@Composable
private fun SharedTransitionScope.InlineBar(
    scope: FloatingTabBarScopeImpl,
    chromeProgress: () -> Float,
    chromeSidePush: ((Boolean) -> Float)?,
    jellyPush: JellyPush?,
    selectedTabKey: Any?,
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    onInlineTabClick: () -> Unit,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    elevations: FloatingTabBarElevations,
    tabBarContentModifier: @Composable () -> Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope,
    neighbourPull: () -> Float = { 0f }
) {
    val inlineTab = scope.getInlineTab(selectedTabKey)
    val standaloneTab = scope.inlineStandaloneTab
    val hasInlineTab = inlineTab != null
    val startSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f

    Row(
        horizontalArrangement = Arrangement.spacedBy(sizes.inlineComponentSpacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(sizes.inlineBarPadding)
            .then(if (accessory == null) Modifier.wrapContentWidth() else Modifier)
            .height(IntrinsicSize.Max)
    ) {
        if (hasInlineTab) {
            InlineTab(
                inlineTab = inlineTab,
                onInlineTabClick = onInlineTabClick,
                shapes = shapes,
                sizes = sizes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                tabBarContentModifier = tabBarContentModifier,
                modifier = Modifier
                    .chromePushSide(chromeProgress, startSign, chromeSidePush)
                    .jellyPushSide(jellyPush, startSign, sizes.inlineComponentSpacing)
                    .neighbourPull(neighbourPull, startSign)
            )
        }

        if (accessory != null) {
            InlineAccessory(
                accessory = accessory,
                isAccessoryShared = isAccessoryShared,
                shapes = shapes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        if (standaloneTab != null) {
            InlineStandaloneTab(
                standaloneTab = standaloneTab,
                shapes = shapes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                tabBarContentModifier = tabBarContentModifier,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .chromePushSide(chromeProgress, -startSign, chromeSidePush)
                    .jellyPushSide(jellyPush, -startSign, sizes.inlineComponentSpacing)
                    .neighbourPull(neighbourPull, -startSign)
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.InlineTab(
    inlineTab: FloatingTabBarTab,
    onInlineTabClick: () -> Unit,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: @Composable () -> Modifier
) {
    Box(
        modifier = modifier
            .sharedElement(
                sharedContentState = rememberSharedContentState("tabGroup"),
                boundsTransform = LiquidBounds,
                animatedVisibilityScope = animatedVisibilityScope,
                zIndexInOverlay = 1f
            )
            .shadow(
                shape = shapes.tabBarShape,
                elevation = elevations.inlineElevation
            )
            .background(
                color = colors.backgroundColor,
                shape = shapes.tabBarShape
            )
            .clip(shapes.tabBarShape)
            .then(tabBarContentModifier())
            .clickable(
                onClick = { onInlineTabClick() },
                indication = inlineTab.indication?.invoke(),
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(sizes.tabInlineContentPadding)
    ) {
        Tab(
            icon = {
                Box(
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("tab#${inlineTab.key}-icon"),
                        boundsTransform = LiquidBounds,
                        animatedVisibilityScope = animatedVisibilityScope,
                        zIndexInOverlay = 1f
                    )
                ) {
                    inlineTab.icon()
                }
            },
            title = { inlineTab.title() },
            isInline = true
        )
    }
}

@Composable
private fun SharedTransitionScope.InlineStandaloneTab(
    standaloneTab: FloatingTabBarTab,
    shapes: FloatingTabBarShapes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: @Composable () -> Modifier
) {
    StandaloneTabBody(
        standaloneTab = standaloneTab,
        isInline = true,
        shapes = shapes,
        colors = colors,
        elevation = elevations.inlineElevation,
        animatedVisibilityScope = animatedVisibilityScope,
        tabBarContentModifier = tabBarContentModifier,
        modifier = modifier,
    )
}

private const val MIN_LIFT_HOLD_MS = 260L

private const val TAP_SELECT_GRACE_MS = 140L

private const val STANDALONE_SWELL = 0.05f

@Composable
private fun SharedTransitionScope.StandaloneTabBody(
    standaloneTab: FloatingTabBarTab,
    isInline: Boolean,
    shapes: FloatingTabBarShapes,
    colors: FloatingTabBarColors,
    elevation: Dp,
    animatedVisibilityScope: AnimatedVisibilityScope,
    tabBarContentModifier: @Composable () -> Modifier,
    modifier: Modifier,
) {
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val glassPill = isGlassSupported() && !reduceDynamicBlur
    val coroutineScope = rememberCoroutineScope()
    val pill = remember { GlassPillMotion(coroutineScope, 0, slow = 1.6) }
    val glassIndicator by AppSettings.glassIndicator.collectAsStateWithLifecycle()
    pill.reduceMotion = reduceAnimation
    pill.squashScale = glassIndicator.squash
    val barGlass = rememberLayerBackdrop()
    val iconLayer = rememberLayerBackdrop()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressedAt = remember { LongArray(1) }

    LaunchedEffect(pressed, reduceAnimation) {
        if (reduceAnimation) return@LaunchedEffect
        if (pressed) {
            pressedAt[0] = SystemClock.uptimeMillis()
            pill.startDrag()
        } else {
            val held = SystemClock.uptimeMillis() - pressedAt[0]
            if (held < MIN_LIFT_HOLD_MS) delay(MIN_LIFT_HOLD_MS - held)
            pill.release(0)
        }
    }

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val insetPx = with(density) { PILL_INSET.toPx() }
    val growPx = with(density) { PILL_GROW_HEIGHT.toPx() } * glassIndicator.lift
    val w = boxSize.width.toFloat()
    val h = boxSize.height.toFloat()
    val restSize = Size((w - 2f * insetPx).coerceAtLeast(1f), (h - 2f * insetPx).coerceAtLeast(1f))
    val liftedSize = Size(w + growPx, h + growPx)

    Box(
        modifier = modifier
            .sharedElement(
                sharedContentState = rememberSharedContentState("standaloneTab"),
                boundsTransform = LiquidBounds,
                animatedVisibilityScope = animatedVisibilityScope,
                zIndexInOverlay = 1f
            )
            .graphicsLayer {
                val swell = if (reduceAnimation) 0f else pill.liftY.coerceAtLeast(0f)
                scaleX = 1f + STANDALONE_SWELL * swell
                scaleY = 1f + STANDALONE_SWELL * swell
            }
            .onSizeChanged { boxSize = it }
    ) {
        CompositionLocalProvider(LocalGlassExport provides barGlass) {
            Tab(
                icon = {
                    Box(Modifier.layerBackdrop(iconLayer)) { standaloneTab.icon() }
                },
                title = standaloneTab.title,
                isInline = isInline,
                isStandalone = true,
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        shape = shapes.standaloneTabShape,
                        elevation = elevation
                    )
                    .background(
                        color = colors.backgroundColor,
                        shape = shapes.standaloneTabShape
                    )
                    .clip(shapes.standaloneTabShape)
                    .then(tabBarContentModifier())
                    .clickable(
                        onClick = standaloneTab.onClick,
                        indication = standaloneTab.indication?.invoke(),
                        interactionSource = interactionSource
                    )
            )
        }

    }
}

@Composable
private fun SharedTransitionScope.InlineAccessory(
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier
) {
    accessory?.let { accessory ->
        Box(
            modifier = modifier
                .then(
                    if (isAccessoryShared) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState("accessory"),
                            boundsTransform = LiquidBounds,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    } else {
                        Modifier.animateEnterExitAccessory(
                            sharedTransitionScope = this,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                )
        ) {
            accessory(
                Modifier
                    .fillMaxSize()
                    .shadow(
                        shape = shapes.accessoryShape,
                        elevation = elevations.inlineElevation
                    )
                    .background(color = colors.accessoryBackgroundColor, shapes.accessoryShape)
                    .clip(shapes.accessoryShape),
                animatedVisibilityScope
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.ExpandedBar(
    scope: FloatingTabBarScopeImpl,
    chromeProgress: () -> Float,
    chromeDownPush: (() -> Float)?,
    selectedTabKey: Any?,
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    elevations: FloatingTabBarElevations,
    tabBarContentModifier: @Composable () -> Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val standaloneTab = scope.standaloneTab
    val hasTabGroup = scope.tabs.isNotEmpty()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (accessory != null) {
            ExpandedAccessory(
                accessory = accessory,
                isAccessoryShared = isAccessoryShared,
                shapes = shapes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max)
                .chromePushDown(chromeProgress, chromeDownPush)
        ) {
            if (hasTabGroup) {
                ExpandedTabs(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    shapes = shapes,
                    sizes = sizes,
                    colors = colors,
                    elevations = elevations,
                    animatedVisibilityScope = animatedVisibilityScope,
                    tabBarContentModifier = tabBarContentModifier,
                    modifier = Modifier.weight(1f)
                )
            }

            if (standaloneTab != null) {
                ExpandedStandaloneTab(
                    standaloneTab = standaloneTab,
                    shapes = shapes,
                    colors = colors,
                    elevations = elevations,
                    animatedVisibilityScope = animatedVisibilityScope,
                    tabBarContentModifier = tabBarContentModifier,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                )
            }
        }
    }
}

@Composable
private fun SharedTransitionScope.ExpandedAccessory(
    accessory: @Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit,
    isAccessoryShared: Boolean,
    colors: FloatingTabBarColors,
    shapes: FloatingTabBarShapes,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .then(
                if (isAccessoryShared) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("accessory"),
                        boundsTransform = LiquidBounds,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                } else {
                    Modifier.animateEnterExitAccessory(
                        sharedTransitionScope = this,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            )
    ) {
        accessory(
            Modifier
                .shadow(
                    shape = shapes.accessoryShape,
                    elevation = elevations.expandedElevation
                )
                .background(color = colors.accessoryBackgroundColor, shapes.accessoryShape)
                .clip(shapes.accessoryShape),
            animatedVisibilityScope
        )
    }
}
@Composable
private fun SharedTransitionScope.ExpandedTabs(
    scope: FloatingTabBarScopeImpl,
    selectedTabKey: Any?,
    shapes: FloatingTabBarShapes,
    sizes: FloatingTabBarSizes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: @Composable () -> Modifier
) {
    val inlineTab = scope.getInlineTab(selectedTabKey)
    val selectedTabIndex = scope.tabs.indexOfFirst { it.key == selectedTabKey }
    val currentSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
    val reduceAnimation by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val selectionSpec: AnimationSpec<Float> = if (reduceAnimation) {
        snap()
    } else {
        GlassSpring
    }
    val haptics = rememberHaptics()

    val coroutineScope = rememberCoroutineScope()
    val pill = remember { GlassPillMotion(coroutineScope, selectedTabIndex.coerceAtLeast(0)) }
    val glassIndicator by AppSettings.glassIndicator.collectAsStateWithLifecycle()
    pill.reduceMotion = reduceAnimation
    pill.squashScale = glassIndicator.squash
    val glassPill = isGlassSupported() && !reduceDynamicBlur
    val barGlass = rememberLayerBackdrop()
    val tabRow = rememberLayerBackdrop()

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    var lastHapticTab by remember { mutableIntStateOf(selectedTabIndex) }
    var lastSelectedTabIndex by remember { mutableIntStateOf(selectedTabIndex) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val tabCount = scope.tabs.size
    val tabSpacingPx = with(density) { sizes.tabSpacing.toPx() }
    val tabWidthPx = if (rowSize.width > 0 && tabCount > 0) {
        (rowSize.width - tabSpacingPx * (tabCount - 1)) / tabCount
    } else {
        0f
    }
    val tabStepPx = if (rowSize.width > 0 && tabCount > 0) {
        (rowSize.width + tabSpacingPx) / tabCount
    } else {
        0f
    }
    pill.stepDp = tabStepPx / density.density
    val currentTabStepPx by rememberUpdatedState(tabStepPx)
    val currentIsRtl by rememberUpdatedState(isRtl)
    val currentTabWidthPx by rememberUpdatedState(tabWidthPx)
    val pressedTab = remember { IntArray(1) { -1 } }

    val contentPadding = sizes.tabBarContentPadding
    val padLeftPx = with(density) { contentPadding.calculateLeftPadding(layoutDirection).toPx() }
    val padTopPx = with(density) { contentPadding.calculateTopPadding().toPx() }
    val padBottomPx = with(density) { contentPadding.calculateBottomPadding().toPx() }
    val rowHeightPx = rowSize.height.toFloat()
    val restSize = Size(tabWidthPx, rowHeightPx)
    val liftedHeightPx = rowHeightPx + padTopPx + padBottomPx +
        with(density) { PILL_GROW_HEIGHT.toPx() * glassIndicator.lift }
    val liftedSize = if (rowHeightPx > 0f) {
        Size(liftedHeightPx * tabWidthPx / rowHeightPx, liftedHeightPx)
    } else {
        restSize
    }
    val currentRestSize by rememberUpdatedState(restSize)
    val currentLiftedSize by rememberUpdatedState(liftedSize)
    val currentGlassPill by rememberUpdatedState(glassPill)
    val pillCenterInRow: (Float) -> Offset = { position ->
        val x = position * tabStepPx + tabWidthPx / 2f
        Offset(if (isRtl) rowSize.width - x else x, rowHeightPx / 2f)
    }
    val showPill = selectedTabIndex >= 0 && tabWidthPx > 0f

    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex >= 0) {
            if (lastSelectedTabIndex < 0) {
                pill.enterFrom(tabCount.toDouble(), selectedTabIndex)
            } else {
                pill.animateTo(selectedTabIndex)
            }
        }
        lastSelectedTabIndex = selectedTabIndex
        lastHapticTab = selectedTabIndex
    }

    Box(
        modifier = modifier
            .sharedElement(
                sharedContentState = rememberSharedContentState("tabGroup"),
                boundsTransform = LiquidBounds,
                animatedVisibilityScope = animatedVisibilityScope,
                zIndexInOverlay = 1f
            )
    ) {
        CompositionLocalProvider(LocalGlassExport provides barGlass) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val swell = if (reduceAnimation) 0f else pill.liftY.coerceAtLeast(0f)
                        scaleX = 1f + 0.03f * swell
                        scaleY = 1f + 0.06f * swell
                    }
                    .shadow(
                        shape = shapes.tabBarShape,
                        elevation = elevations.expandedElevation,
                        clip = false
                    )
                    .background(
                        color = colors.backgroundColor,
                        shape = shapes.tabBarShape
                    )
                    .then(tabBarContentModifier())
            )
            Box(
                modifier = Modifier.padding(contentPadding)
            ) {
                if (showPill) {
                    FlatSelectionPill(
                        color = colors.indicatorColor,
                        barGlass = barGlass,
                        modifier = Modifier
                            .matchParentSize()
                            .drawWithContent {
                                if (!glassPill || pill.isFlat || pill.flatVisible) drawContent()
                            }
                            .pillPlacement(
                                center = { pillCenterInRow(pill.position) },
                                size = { pill.flatSize(restSize, liftedSize) },
                            ),
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(sizes.tabSpacing),
                    modifier = Modifier
                        .animateContentSize()
                        .fillMaxWidth()
                        .onSizeChanged { rowSize = it }
                        .layerBackdrop(tabRow)
                        .pointerInput(Unit) {
                            val slop = viewConfiguration.touchSlop
                            var pending: Job? = null
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                if (currentSelectedTabIndex < 0 || currentTabStepPx <= 0f) return@awaitEachGesture
                                pending?.cancel()
                                val pressedAt = SystemClock.uptimeMillis()
                                val x = if (currentIsRtl) size.width - down.position.x else down.position.x
                                val tapped = (x / currentTabStepPx).toInt().coerceIn(0, scope.tabs.lastIndex)
                                pressedTab[0] = tapped
                                if (tapped != currentSelectedTabIndex) return@awaitEachGesture
                                pill.startDrag()
                                var dragged = false
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    if (abs(change.position.x - down.position.x) > slop) dragged = true
                                }
                                val wait = if (!dragged && tapped == currentSelectedTabIndex) {
                                    (MIN_LIFT_HOLD_MS - (SystemClock.uptimeMillis() - pressedAt)).coerceAtLeast(0L)
                                } else {
                                    TAP_SELECT_GRACE_MS
                                }
                                pending = coroutineScope.launch {
                                    if (wait > 0L) delay(wait)
                                    if (pill.isHeld) pill.release(currentSelectedTabIndex)
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            var totalDrag = 0f
                            var dragStartIndex = currentSelectedTabIndex
                            var direction = 1f
                            var step = 0f
                            var holding = false
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    step = currentTabStepPx
                                    holding = currentSelectedTabIndex >= 0 && step > 0f &&
                                        pressedTab[0] == currentSelectedTabIndex
                                    if (!holding) return@detectHorizontalDragGestures
                                    direction = if (currentIsRtl) -1f else 1f
                                    totalDrag = 0f
                                    dragStartIndex = currentSelectedTabIndex
                                    pill.startDrag()
                                },
                                onDragCancel = {
                                    if (holding) pill.release(currentSelectedTabIndex)
                                    holding = false
                                },
                                onDragEnd = {
                                    if (!holding) return@detectHorizontalDragGestures
                                    holding = false
                                    val standalone = scope.standaloneTab
                                    val dragMax = scope.tabs.lastIndex.toFloat() +
                                        (if (standalone != null) STANDALONE_DRAG_REACH else 0f)
                                    val absoluteIndexFloat = (dragStartIndex + totalDrag / step)
                                        .coerceIn(0f, dragMax)
                                    if (standalone != null &&
                                        absoluteIndexFloat > scope.tabs.lastIndex + 0.5f
                                    ) {
                                        pill.release(scope.tabs.lastIndex)
                                        standalone.onClick()
                                        return@detectHorizontalDragGestures
                                    }
                                    val newIndex = absoluteIndexFloat.roundToInt()
                                        .coerceAtMost(scope.tabs.lastIndex)
                                    pill.release(newIndex)
                                    if (newIndex != currentSelectedTabIndex) {
                                        scope.tabs[newIndex].onClick()
                                    }
                                },
                                onHorizontalDrag = { _, delta ->
                                    if (!holding) return@detectHorizontalDragGestures
                                    totalDrag += delta * direction
                                    val dragMax = scope.tabs.lastIndex.toFloat() +
                                        (if (scope.standaloneTab != null) STANDALONE_DRAG_REACH else 0f)
                                    val absoluteIndexFloat = (dragStartIndex + totalDrag / step)
                                        .coerceIn(0f, dragMax)
                                    pill.dragTo(absoluteIndexFloat)

                                    val approximateTab = absoluteIndexFloat.roundToInt()
                                    if (approximateTab != lastHapticTab) {
                                        haptics.play(Haptic.Tick)
                                        lastHapticTab = approximateTab
                                    }
                                }
                            )
                        }
                ) {
                    scope.tabs.forEachIndexed { tabIndex, tab ->
                        val isSelected = tab.key == selectedTabKey
                        val tabCoverage = remember(tabIndex) {
                            {
                                if (currentSelectedTabIndex < 0) {
                                    0f
                                } else {
                                    (1f - abs(pill.position - tabIndex)).coerceIn(0f, 1f)
                                }
                            }
                        }
                        val tabPillGeometry = remember(tabIndex) {
                            {
                                if (currentSelectedTabIndex < 0) {
                                    Offset(1_000_000f, 0f)
                                } else {
                                    val dx = (pill.position - tabIndex) * currentTabStepPx
                                    Offset(
                                        if (currentIsRtl) -dx else dx,
                                        pill.visibleWidth(currentRestSize, currentLiftedSize, currentGlassPill),
                                    )
                                }
                            }
                        }
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.08f else 1f,
                            animationSpec = selectionSpec,
                            label = "glassTabScale"
                        )
                        Tab(
                            icon = {
                                Box(
                                    modifier = (if (tab.key == inlineTab?.key) {
                                        Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState("tab#${tab.key}-icon"),
                                            boundsTransform = LiquidBounds,
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            zIndexInOverlay = 1f
                                        )
                                    } else {
                                        Modifier.animateEnterExitTab(
                                            sharedTransitionScope = this@ExpandedTabs,
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                    }).graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    }
                                ) {
                                    CompositionLocalProvider(
                                        LocalTabPillCoverage provides tabCoverage,
                                        LocalTabPillGeometry provides tabPillGeometry,
                                    ) {
                                        tab.icon()
                                    }
                                }
                            },
                            title = {
                                Box(
                                    Modifier.animateEnterExitTab(
                                        sharedTransitionScope = this@ExpandedTabs,
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                ) {
                                    CompositionLocalProvider(
                                        LocalTabPillCoverage provides tabCoverage,
                                        LocalTabPillGeometry provides tabPillGeometry,
                                    ) {
                                        tab.title()
                                    }
                                }
                            },
                            isInline = false,
                            modifier = Modifier
                                .weight(1f)
                                .skipToLookaheadSize()
                                .clip(shapes.tabShape)
                                .clickable(
                                    onClick = tab.onClick,
                                    indication = tab.indication?.invoke(),
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                                .padding(sizes.tabExpandedContentPadding)
                        )
                    }
                }
            }
        }

        if (glassPill && showPill) {
            GlassSelectionPill(
                motion = pill,
                fillColor = colors.indicatorColor,
                barGlass = barGlass,
                tabRow = tabRow,
                iconTint = colors.selectedContentColor,
                modifier = Modifier
                    .matchParentSize()
                    .drawWithContent { if (!pill.isFlat) drawContent() }
                    .pillPlacement(
                        center = { pillCenterInRow(pill.position) + Offset(padLeftPx, padTopPx) },
                        size = { pill.liveSize(restSize, liftedSize) },
                    ),
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.ExpandedStandaloneTab(
    standaloneTab: FloatingTabBarTab,
    shapes: FloatingTabBarShapes,
    colors: FloatingTabBarColors,
    elevations: FloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: @Composable () -> Modifier
) {
    StandaloneTabBody(
        standaloneTab = standaloneTab,
        isInline = false,
        shapes = shapes,
        colors = colors,
        elevation = elevations.expandedElevation,
        animatedVisibilityScope = animatedVisibilityScope,
        tabBarContentModifier = tabBarContentModifier,
        modifier = modifier,
    )
}

@Composable
private fun Tab(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    isInline: Boolean,
    modifier: Modifier = Modifier,
    isStandalone: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        icon()
        if (!isStandalone && !isInline) {
            title()
        }
    }
}

@Composable
private fun Modifier.animateEnterExitAccessory(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = with(sharedTransitionScope) {
    with(animatedVisibilityScope) {
        val animatedAlpha by transition.animateFloat { targetState ->
            when (targetState) {
                EnterExitState.Visible -> 1f
                else -> 0f
            }
        }

        this@animateEnterExitAccessory
            .renderInSharedTransitionScopeOverlay()
            .graphicsLayer(
                compositingStrategy = CompositingStrategy.ModulateAlpha,
                alpha = animatedAlpha
            )
    }
}

@Composable
private fun Modifier.animateEnterExitTab(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = with(sharedTransitionScope) {
    with(animatedVisibilityScope) {
        val enterStartFraction = 0.5f
        val enterEndFraction = 0.8f
        val durationMs = 150

        val animatedAlpha by transition.animateFloat(
            transitionSpec = {
                keyframes {
                    durationMillis = durationMs
                    if (targetState == EnterExitState.Visible) {
                        0f atFraction enterStartFraction using FastOutSlowInEasing
                        1f atFraction enterEndFraction
                    }
                }
            }
        ) { targetState ->
            when (targetState) {
                EnterExitState.Visible -> 1f
                else -> 0f
            }
        }

        val blurRadius = with(LocalDensity.current) { 12.dp.toPx() }
        val animatedBlur by transition.animateFloat(
            transitionSpec = {
                keyframes {
                    durationMillis = durationMs
                    if (targetState == EnterExitState.Visible) {
                        blurRadius atFraction enterStartFraction using FastOutSlowInEasing
                        0f atFraction enterEndFraction
                    }
                }
            }
        ) { targetState ->
            when (targetState) {
                EnterExitState.Visible -> 0f
                else -> blurRadius
            }
        }

        graphicsLayer {
            alpha = animatedAlpha
            renderEffect =
                if (animatedBlur > 0f) BlurEffect(animatedBlur, animatedBlur) else null
        }
    }
}

private const val STANDALONE_DRAG_REACH = 1f

private class FloatingTabBarScopeImpl(
    private val inlineSplitLast: Boolean = false,
) : FloatingTabBarScope {
    val tabs = mutableStateListOf<FloatingTabBarTab>()
    var standaloneTab: FloatingTabBarTab? by mutableStateOf(null)
        private set
    private var inlineTab: FloatingTabBarTab? = null

    val inlineStandaloneTab: FloatingTabBarTab?
        get() = standaloneTab ?: if (inlineSplitLast && tabs.size > 1) {
            tabs.last().let { t ->
                FloatingTabBarTab(
                    key = t.key,
                    title = {},
                    icon = t.icon,
                    onClick = t.onClick,
                    indication = t.indication
                )
            }
        } else {
            null
        }

    fun getInlineTab(selectedTabKey: Any?): FloatingTabBarTab? {
        return if (selectedTabKey != inlineStandaloneTab?.key) {
            val selectedTab = tabs.find { it.key == selectedTabKey }
            if (selectedTab != null) {
                inlineTab = selectedTab
                selectedTab
            } else {
                inlineTab ?: tabs.firstOrNull()
            }
        } else {
            inlineTab ?: tabs.firstOrNull()
        }
    }

    override fun tab(
        key: Any,
        title: @Composable () -> Unit,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)?
    ) {
        tabs.add(
            FloatingTabBarTab(
                key = key,
                title = title,
                icon = icon,
                onClick = onClick,
                indication = indication
            )
        )
    }

    override fun standaloneTab(
        key: Any,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)?
    ) {
        standaloneTab = FloatingTabBarTab(
            key = key,
            title = {},
            icon = icon,
            onClick = onClick,
            indication = indication
        )
    }
}

private data class FloatingTabBarTab(
    val key: Any,
    val title: @Composable () -> Unit,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
    val indication: (@Composable () -> Indication)?
)

@Immutable
data class FloatingTabBarColors(
    val backgroundColor: Color,
    val accessoryBackgroundColor: Color,
    val indicatorColor: Color,
    val selectedContentColor: Color = Color.Unspecified,
)

@Immutable
data class FloatingTabBarShapes(
    val tabBarShape: Shape,
    val tabShape: Shape,
    val standaloneTabShape: Shape,
    val accessoryShape: Shape,
)

@Immutable
data class FloatingTabBarElevations(
    val inlineElevation: Dp,
    val expandedElevation: Dp,
)

@Immutable
data class FloatingTabBarSizes(
    val tabBarContentPadding: PaddingValues,
    val tabInlineContentPadding: PaddingValues,
    val tabExpandedContentPadding: PaddingValues,
    val componentSpacing: Dp,
    val tabSpacing: Dp,
    val inlineComponentSpacing: Dp = componentSpacing,
    val inlineBarPadding: PaddingValues = PaddingValues(),
)

object FloatingTabBarDefaults {
    @Composable
    fun colors(
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
        accessoryBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
        indicatorColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        selectedContentColor: Color = Color.Unspecified,
    ): FloatingTabBarColors = FloatingTabBarColors(
        backgroundColor = backgroundColor,
        accessoryBackgroundColor = accessoryBackgroundColor,
        indicatorColor = indicatorColor,
        selectedContentColor = selectedContentColor,
    )

    @Composable
    fun shapes(
        tabBarShape: Shape = RoundedCornerShape(100),
        tabShape: Shape = RoundedCornerShape(100),
        standaloneTabShape: Shape = CircleShape,
        accessoryShape: Shape = RoundedCornerShape(100),
    ): FloatingTabBarShapes = FloatingTabBarShapes(
        tabBarShape = tabBarShape,
        tabShape = tabShape,
        standaloneTabShape = standaloneTabShape,
        accessoryShape = accessoryShape,
    )

    @Composable
    fun sizes(
        tabBarContentPadding: PaddingValues = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
        tabInlineContentPadding: PaddingValues = PaddingValues(10.dp),
        tabExpandedContentPadding: PaddingValues = PaddingValues(vertical = 6.dp, horizontal = 20.dp),
        componentSpacing: Dp = 8.dp,
        tabSpacing: Dp = 0.dp,
        inlineComponentSpacing: Dp = componentSpacing,
        inlineBarPadding: PaddingValues = PaddingValues(),
    ): FloatingTabBarSizes = FloatingTabBarSizes(
        tabBarContentPadding = tabBarContentPadding,
        tabInlineContentPadding = tabInlineContentPadding,
        tabExpandedContentPadding = tabExpandedContentPadding,
        componentSpacing = componentSpacing,
        tabSpacing = tabSpacing,
        inlineComponentSpacing = inlineComponentSpacing,
        inlineBarPadding = inlineBarPadding,
    )

    @Composable
    fun elevations(
        inlineElevation: Dp = 6.dp,
        expandedElevation: Dp = 12.dp,
    ): FloatingTabBarElevations = FloatingTabBarElevations(
        inlineElevation = inlineElevation,
        expandedElevation = expandedElevation,
    )
}
