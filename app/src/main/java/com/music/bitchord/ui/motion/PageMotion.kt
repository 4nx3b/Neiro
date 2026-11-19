package com.music.bitchord.ui.motion

import kotlin.math.max

/*
 * The album-opening card geometry — the zoom out of the cover the row shows
 * — is the app's own (ui/motion/PageMotion) and stays; the slide between
 * pages now runs Glasmio's navigation motion (IosNavigationMotion.kt),
 * including the under-page dim the fork prescribes.
 */
object PageMotion {

    /* Glasmio's native ten-percent dimming of the covered page. */
    const val UNDER_DIM = 0.10f

    const val OPEN_MS = 260

    const val CLOSE_MS = 340
    const val CLOSE_TRAVEL = 0.68f

    const val ZOOM_EASE_K = 5f

    const val CLOSE_EASE_K = 2.5f

    const val CARD_CORNER_DP = 12f

    fun zoomEase(t: Float): Float = expEase(t, ZOOM_EASE_K)

    fun closeEase(t: Float): Float = expEase(t, CLOSE_EASE_K)

    private fun expEase(t: Float, k: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return ((1.0 - kotlin.math.exp(-k.toDouble() * c)) / (1.0 - kotlin.math.exp(-k.toDouble()))).toFloat()
    }

    data class Card(

        val scale: Float,
        val translationX: Float,
        val translationY: Float,

        val cornerPx: Float,
        val alpha: Float,
    )

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

        val screenCorner = cornerPx * (1f - g).coerceIn(0f, 1f)
        return Card(
            scale = scale,
            translationX = tx,
            translationY = ty,
            cornerPx = screenCorner / max(scale, 0.05f),
            alpha = alpha.coerceIn(0f, 1f),
        )
    }

    fun closeFrame(q: Float): Pair<Float, Float> {
        val elapsed = 1f - q.coerceIn(0f, 1f)
        val travel = (elapsed / CLOSE_TRAVEL).coerceIn(0f, 1f)
        val geometry = 1f - closeEase(travel)
        val alpha = 1f - ((elapsed - CLOSE_TRAVEL) / (1f - CLOSE_TRAVEL)).coerceIn(0f, 1f)
        return geometry to alpha
    }

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
