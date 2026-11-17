package com.music.bitchord.ui.components

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The geometry of a popup morphing out of the control that opened it, after
 * iOS 26 Apple Music's Liquid Glass (docs/bottom-stack-reference.md, clips
 * 15-57-35 and 02-46-29): the pressed glass control itself swells into the
 * panel — its rect travels from the control's bounds to the panel's,
 * stretching a little along the way it travels — and the rows come in only
 * once the glass has mostly grown, blurred and slightly oversized, settling
 * sharp at their own size. Closing runs the same path back, the rows leading.
 *
 * Pure, so it is tested on the JVM (GlassMorphTest). Plain floats rather than
 * Compose geometry: the caller maps its Rects in and the frame back out.
 */
data class MorphBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/**
 * One frame of the morph, as a layer transform for a panel laid out at its
 * final bounds with transformOrigin (0, 0): scale it by [scaleX]/[scaleY] and
 * move it by [translationX]/[translationY], and it covers [box].
 */
data class MenuFrame(
    val box: MorphBox,
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
    /** The rows' opacity, 0 until the glass has mostly grown. */
    val contentAlpha: Float,
    /** The rows' blur radius in dp, 8 → 0 as they come in. */
    val contentBlurDp: Float,
    /** The rows' own scale, slightly oversized as they come in (02-46-29). */
    val contentScale: Float,
)

/** Where the rows start to come in, as a share of the morph. */
const val MORPH_CONTENT_START = 0.35f

/** The rows' blur at the start of their fade-in, dp. */
const val MORPH_CONTENT_BLUR_DP = 8f

/** The rows' scale at the start of their fade-in. */
const val MORPH_CONTENT_SCALE = 1.12f

/** How far the glass stretches along its travel at mid-flight. */
const val MORPH_STRETCH = 0.06f

/** The spring fitted to the clips: ≈300 ms with a small settle, never a bounce. */
const val MORPH_DAMPING = 0.82f
const val MORPH_STIFFNESS = 300f

/** The close, ≈250 ms. */
const val MORPH_CLOSE_MS = 250

/** The rows' progress (0..1) for a morph [progress]. */
fun morphContentFraction(progress: Float): Float =
    ((progress - MORPH_CONTENT_START) / (1f - MORPH_CONTENT_START)).coerceIn(0f, 1f)

/**
 * The frame at [progress] (0 = the control, 1 = the panel; a spring may carry
 * it slightly past 1 and back, which is the settle). [from] is the control's
 * bounds, [to] the panel's, both in the same coordinates.
 */
fun morphFrame(progress: Float, from: MorphBox, to: MorphBox): MenuFrame {
    val p = progress.coerceIn(0f, 1.2f)
    fun mix(a: Float, b: Float) = a + (b - a) * p
    var width = mix(from.width, to.width).coerceAtLeast(1f)
    var height = mix(from.height, to.height).coerceAtLeast(1f)
    // A gooey stretch along the direction the glass travels in, gone at both
    // ends; volume-keeping, so it reads as one blob changing shape.
    val travelsSideways = abs(to.width - from.width) >= abs(to.height - from.height)
    val stretch = MORPH_STRETCH * sin(PI.toFloat() * p.coerceIn(0f, 1f))
    if (travelsSideways) {
        width *= 1f + stretch
        height *= 1f - stretch / 2f
    } else {
        height *= 1f + stretch
        width *= 1f - stretch / 2f
    }
    // The edge nearest the control stays with it while the far edge travels.
    val anchorRight = abs(to.right - from.right) <= abs(to.left - from.left)
    val anchorBottom = abs(to.bottom - from.bottom) < abs(to.top - from.top)
    val right = mix(from.right, to.right)
    val bottom = mix(from.bottom, to.bottom)
    val left = if (anchorRight) right - width else mix(from.left, to.left)
    val top = if (anchorBottom) bottom - height else mix(from.top, to.top)
    val c = morphContentFraction(progress)
    return MenuFrame(
        box = MorphBox(left, top, width, height),
        scaleX = width / to.width.coerceAtLeast(1f),
        scaleY = height / to.height.coerceAtLeast(1f),
        translationX = left - to.left,
        translationY = top - to.top,
        contentAlpha = c,
        contentBlurDp = MORPH_CONTENT_BLUR_DP * (1f - c),
        contentScale = MORPH_CONTENT_SCALE + (1f - MORPH_CONTENT_SCALE) * c,
    )
}

/**
 * The control a popup with no button of its own grows out of: a small capsule
 * at the centre of [container] (a dialog opened from nowhere in particular).
 */
fun centreSource(container: MorphBox, size: Float): MorphBox =
    MorphBox(
        left = container.left + (container.width - size) / 2f,
        top = container.top + (container.height - size) / 2f,
        width = size,
        height = size,
    )
