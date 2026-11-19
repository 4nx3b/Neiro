@file:OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)

package com.music.bitchord.ui.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val ZoomEasing = Easing { PageMotion.zoomEase(it) }

@Stable
class PageMotionState {

    var push by mutableStateOf(true)

    private val zoomed = HashMap<String, String>()

    var zoomingKey by mutableStateOf<String?>(null)

    private val depths = HashMap<String, Int>()

    fun onDestination(key: String, depth: Int, releaseBrowseId: String?, previousKey: String?) {
        val previousDepth = previousKey?.let { depths[it] } ?: 0
        push = depth >= previousDepth
        depths[key] = depth
        zoomingKey = when {
            push && releaseBrowseId != null && ReleaseOrigin.of(releaseBrowseId) != null -> {
                zoomed[key] = releaseBrowseId
                key
            }
            !push && previousKey != null && zoomed.containsKey(previousKey) -> previousKey
            else -> null
        }
    }

    internal fun zoomBrowseId(key: String): String? = zoomed[key]

    fun forget(key: String) {
        if (zoomingKey != key) {
            zoomed.remove(key)
            depths.remove(key)
        }
    }
}

fun AnimatedContentTransitionScope<*>.iosPageTransform(
    motion: PageMotionState,
    reduceMotion: Boolean,
    layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    density: Float = 1f,
): ContentTransform {
    if (reduceMotion) {
        return fadeIn(tween(150, easing = LinearEasing)) togetherWith fadeOut(tween(150, easing = LinearEasing))
    }
    val zooming = motion.zoomingKey != null
    // The ported slide: the foreground travels the full width on the
    // fork's unnormalised critical spring, the underlay the parallax
    // remainder UIKit prescribes — thirty percent of the width, pixel-exact
    // — and RTL travels the mirrored way, as the fork's own spec does.
    val sign = if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    return when {
        zooming && motion.push ->
            (EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished)
        zooming ->
            (EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished)
                .apply { targetContentZIndex = -1f }
        motion.push ->
            slideInHorizontally(IosNavigationSlideSpring) { it * sign } togetherWith
                slideOutHorizontally(IosNavigationSlideSpring) {
                    -iosNavigationParallaxOffset(it, density) * sign
                }
        else ->
            (
                slideInHorizontally(IosNavigationSlideSpring) {
                    -iosNavigationParallaxOffset(it, density) * sign
                } togetherWith
                    slideOutHorizontally(IosNavigationSlideSpring) { it * sign }
                ).apply { targetContentZIndex = -1f }
    }
}

/**
 * The root tab fade, verbatim — iOS swaps its top-level
 * destinations on `fadeIn() togetherWith fadeOut()`, and this is that exact
 * transform for the app's tab swaps.
 */
fun rootTabFade(): ContentTransform = fadeIn() togetherWith fadeOut()

@Composable
fun AnimatedVisibilityScope.IosPageFrame(
    key: String,
    isTab: Boolean,
    motion: PageMotionState,
    reduceMotion: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val floor = MaterialTheme.colorScheme.background
    val zoomBrowseId = if (!reduceMotion && motion.zoomingKey == key) motion.zoomBrowseId(key) else null

    val progress by transition.animateFloat(
        transitionSpec = {
            if (zoomBrowseId != null && targetState != EnterExitState.Visible) {
                tween(PageMotion.CLOSE_MS, easing = LinearEasing)
            } else if (zoomBrowseId != null) {
                tween(PageMotion.OPEN_MS, easing = ZoomEasing)
            } else {
                // The ported easing and window: the spring's position over
                // the 350 ms coordinator duration.
                tween(IosNavigationDurationMillis, easing = IosNavigationEasing)
            }
        },
        label = "page",
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    val moving = transition.currentState != transition.targetState
    var slot by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val cornerPx = with(androidx.compose.ui.platform.LocalDensity.current) { PageMotion.CARD_CORNER_DP.dp.toPx() }
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { slot = it }
            .then(
                if (zoomBrowseId != null) {
                    Modifier.graphicsLayer {
                        val coords = slot
                        val cover = ReleaseOrigin.of(zoomBrowseId)
                        if (coords == null || !coords.isAttached || cover == null) {

                            alpha = progress
                            return@graphicsLayer
                        }
                        val origin: Offset = coords.positionInWindow()
                        val closing = transition.targetState != EnterExitState.Visible
                        val (geometry, alpha0) = if (closing) PageMotion.closeFrame(progress) else progress to 1f
                        val card = PageMotion.card(
                            geometry = geometry,
                            alpha = alpha0,
                            coverLeft = cover.left - origin.x,
                            coverTop = cover.top - origin.y,
                            coverWidth = cover.width,
                            pageWidth = size.width,
                            cornerPx = cornerPx,
                        )
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = card.scale
                        scaleY = card.scale
                        translationX = card.translationX
                        translationY = card.translationY
                        alpha = card.alpha
                        shape = RoundedCornerShape(card.cornerPx)
                        clip = true
                    }
                } else {
                    Modifier
                },
            )

            .then(if (moving && (!isTab || zoomBrowseId != null)) Modifier.background(floor) else Modifier)
            .drawWithContent {
                drawContent()

                if (moving && zoomBrowseId == null && !reduceMotion) {
                    val under = if (motion.push) transition.targetState != EnterExitState.Visible
                    else transition.targetState == EnterExitState.Visible
                    if (under) {
                        val dim = PageMotion.UNDER_DIM * (1f - progress)
                        if (dim > 0.001f) drawRect(Color.Black.copy(alpha = dim))
                    }
                }
            },
        content = content,
    )
}
