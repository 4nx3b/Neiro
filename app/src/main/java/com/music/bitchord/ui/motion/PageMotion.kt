package com.music.bitchord.ui.motion

import kotlin.math.max

/**
 * The page and album motion of iOS 26 Apple Music, measured frame by frame from
 * the user's recording 03-54-11 (docs/ios-glass-reference.md). Pure, so it is
 * tested on the JVM (PageMotionTest); the Compose side is IosPageTransitions.
 *
 * Push (1.73 → 1.87 s): the new page slides in from the right edge, about half
 * way after one frame and settled in about 170 ms, decelerating hard; the page
 * underneath moves left by about 30 % of the width and dims. Pop is the mirror.
 *
 * Album open (2.53 → 2.77 s): the tapped cover grows into the album page as a
 * rounded card — the page laid out full size and scaled uniformly inside it —
 * full width by about 200 ms, corners settling over about 70 ms more, no
 * overshoot. Close (3.23 → 3.60 s): the card shrinks back onto its grid cell
 * along a gentle curve, still page-shaped, reaching the cell at about 260 ms,
 * then dissolves into the cover over about 100 ms.
 */
object PageMotion {
    /** Push / pop slide, ms. */
    const val SLIDE_MS = 220

    /** How far the page underneath travels, as a share of the width. */
    const val UNDER_SHIFT = 0.30f

    /** How dark the page underneath gets at the end of a push. */
    const val UNDER_DIM = 0.22f

    /** The open spring: critically damped, ≈270 ms to settle. */
    const val OPEN_DAMPING = 1f
    const val OPEN_STIFFNESS = 600f

    /** The close, ms, and the share of it spent travelling (the rest dissolves). */
    const val CLOSE_MS = 360
    const val CLOSE_TRAVEL = 0.72f

    /** The card's corner, dp, while it is a card. */
    const val CARD_CORNER_DP = 12f

    /**
     * The push curve: an ease-out steep enough that the page is about half way
     * after one frame of [SLIDE_MS] at 30 fps, as recorded (cubic-bezier
     * 0.25, 1, 0.5, 1).
     */
    fun slideEase(t: Float): Float = cubicBezier(0.25f, 1f, 0.5f, 1f, t.coerceIn(0f, 1f))

    data class Card(
        /** Uniform scale of the page laid out at full size. */
        val scale: Float,
        val translationX: Float,
        val translationY: Float,
        /** The clip's corner in the page's own (unscaled) pixels. */
        val cornerPx: Float,
        val alpha: Float,
    )

    /**
     * The page as a card at [geometry] (0 = on the cover, 1 = the page) and
     * [alpha]. [cover] is the cover's rect in the page slot's coordinates,
     * [pageWidth]/[pageHeight] the slot's size, [cornerPx] the card's corner on
     * screen while it is small.
     */
    fun card(
        geometry: Float,
        alpha: Float,
        coverLeft: Float,
        coverTop: Float,
        coverWidth: Float,
        pageWidth: Float,
        cornerPx: Float,
    ): Card {
        val g = geometry.coerceIn(0f, 1.05f)
        val startScale = (coverWidth / pageWidth).coerceIn(0.05f, 1f)
        val scale = startScale + (1f - startScale) * g
        val tx = coverLeft * (1f - g)
        val ty = coverTop * (1f - g)
        // Corner on screen: the cover's while small, none once full size.
        val screenCorner = cornerPx * (1f - g).coerceIn(0f, 1f)
        return Card(
            scale = scale,
            translationX = tx,
            translationY = ty,
            cornerPx = screenCorner / max(scale, 0.05f),
            alpha = alpha.coerceIn(0f, 1f),
        )
    }

    /** The close's raw linear progress [q] (1 → 0) as (geometry, alpha). */
    fun closeFrame(q: Float): Pair<Float, Float> {
        val dissolve = 1f - CLOSE_TRAVEL
        val travel = ((q - dissolve) / CLOSE_TRAVEL).coerceIn(0f, 1f)
        val geometry = cubicBezier(0.4f, 0f, 0.2f, 1f, travel)
        val alpha = (q / dissolve).coerceIn(0f, 1f)
        return geometry to alpha
    }

    /** CSS-style cubic-bezier easing from (0,0) to (1,1). */
    fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, t: Float): Float {
        if (t <= 0f) return 0f
        if (t >= 1f) return 1f
        fun bx(u: Float) = 3f * (1 - u) * (1 - u) * u * x1 + 3f * (1 - u) * u * u * x2 + u * u * u
        fun by(u: Float) = 3f * (1 - u) * (1 - u) * u * y1 + 3f * (1 - u) * u * u * y2 + u * u * u
        var lo = 0f
        var hi = 1f
        var u = t
        repeat(30) {
            u = (lo + hi) / 2f
            if (bx(u) < t) lo = u else hi = u
        }
        return by(u)
    }
}
