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
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.unit.dp

/** The iOS push curve (PageMotion.slideEase). */
val IosSlideEasing = Easing { PageMotion.slideEase(it) }

/**
 * Which way the page area is moving, and which release pages were opened by
 * zooming out of a cover — set by the host as its destination changes, read by
 * [iosPageTransform] and [IosPageFrame].
 */
@Stable
class PageMotionState {
    /** True while going deeper (a push), false while coming back (a pop). */
    var push by mutableStateOf(true)

    /** Destination keys whose page zoomed out of a cover and should shrink back onto it. */
    private val zoomed = HashMap<String, String>()

    /** The key of the page currently zooming open or closed, if any. */
    var zoomingKey by mutableStateOf<String?>(null)

    private val depths = HashMap<String, Int>()

    /**
     * Called by the host for every new destination: decides push or pop from
     * the depth of the page stack, and whether a release page zooms.
     */
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

    /** Forget a page that has left the stack. */
    fun forget(key: String) {
        if (zoomingKey != key) {
            zoomed.remove(key)
            depths.remove(key)
        }
    }
}

/**
 * The page area's transition: the iOS push / pop slide, or, for a release
 * opened from (or closing onto) its cover, the album zoom — whose motion is
 * drawn by [IosPageFrame] while this keeps both pages composed.
 */
fun AnimatedContentTransitionScope<*>.iosPageTransform(
    motion: PageMotionState,
    reduceMotion: Boolean,
): ContentTransform {
    if (reduceMotion) {
        return fadeIn(tween(150, easing = LinearEasing)) togetherWith fadeOut(tween(150, easing = LinearEasing))
    }
    val zooming = motion.zoomingKey != null
    return when {
        zooming && motion.push ->
            (EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished)
        zooming ->
            (EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished)
                .apply { targetContentZIndex = -1f }
        motion.push ->
            slideInHorizontally(tween(PageMotion.SLIDE_MS, easing = IosSlideEasing)) { it } togetherWith
                slideOutHorizontally(tween(PageMotion.SLIDE_MS, easing = IosSlideEasing)) {
                    -(it * PageMotion.UNDER_SHIFT).toInt()
                }
        else ->
            (
                slideInHorizontally(tween(PageMotion.SLIDE_MS, easing = IosSlideEasing)) {
                    -(it * PageMotion.UNDER_SHIFT).toInt()
                } togetherWith
                    slideOutHorizontally(tween(PageMotion.SLIDE_MS, easing = IosSlideEasing)) { it }
                ).apply { targetContentZIndex = -1f }
    }
}

/**
 * One page in the page area, with what the transition needs drawn on it: an
 * opaque floor while it slides (pages paint no background of their own, and
 * two sliding pages would show through each other), the dim on the page
 * underneath, and the album zoom — the page as a rounded card growing out of
 * its cover, or shrinking back onto it.
 */
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
    // 0 = under / on its cover, 1 = the page in place; one clock for the dim,
    // the floor and the zoom.
    val progress by transition.animateFloat(
        transitionSpec = {
            if (zoomBrowseId != null && targetState != EnterExitState.Visible) {
                tween(PageMotion.CLOSE_MS, easing = LinearEasing)
            } else if (zoomBrowseId != null) {
                spring(PageMotion.OPEN_DAMPING, PageMotion.OPEN_STIFFNESS)
            } else {
                tween(PageMotion.SLIDE_MS, easing = IosSlideEasing)
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
                            // No cover to grow out of (scrolled away): a plain fade.
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
            // The floor under a sliding or zooming page (never under a tab at
            // rest, which keeps its see-through look over the window).
            .then(if (moving && (!isTab || zoomBrowseId != null)) Modifier.background(floor) else Modifier)
            .drawWithContent {
                drawContent()
                // The page underneath dims as the new one covers it.
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
