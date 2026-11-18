package com.music.bitchord.ui.components

import kotlin.math.abs

data class MorphBox(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

data class MenuFrame(
    val box: MorphBox,
    val scaleX: Float,
    val scaleY: Float,
    val translationX: Float,
    val translationY: Float,
    val contentAlpha: Float,
    val contentBlurDp: Float,
    val contentScale: Float,
)

const val MORPH_CONTENT_START = 0.45f

const val MORPH_CONTENT_END = 0.96f

const val MORPH_CONTENT_BLUR_DP = 8f

const val MORPH_CONTENT_SCALE = 1.15f

const val MORPH_DAMPING = 0.94f

const val MORPH_STIFFNESS = 1024f

const val MORPH_CLOSE_MS = 230

private val LEAD_AXIS_SHARE = floatArrayOf(0f, 0.36f, 1.05f, 1.0f, 1.0f)
private val TRAIL_AXIS_SHARE = floatArrayOf(0f, 0.43f, 0.77f, 0.9f, 1.0f)

fun morphContentFraction(progress: Float): Float =
    ((progress - MORPH_CONTENT_START) / (MORPH_CONTENT_END - MORPH_CONTENT_START)).coerceIn(0f, 1f)

fun leadAxisShare(trailShare: Float): Float {
    val w = trailShare.coerceIn(0f, 1.2f)
    if (w >= 1f) return w
    for (i in 1 until TRAIL_AXIS_SHARE.size) {
        val a = TRAIL_AXIS_SHARE[i - 1]
        val b = TRAIL_AXIS_SHARE[i]
        if (w <= b) {
            val f = if (b > a) (w - a) / (b - a) else 1f
            return LEAD_AXIS_SHARE[i - 1] + (LEAD_AXIS_SHARE[i] - LEAD_AXIS_SHARE[i - 1]) * f
        }
    }
    return 1f
}

fun morphFrame(progress: Float, from: MorphBox, to: MorphBox): MenuFrame {
    val p = progress.coerceIn(0f, 1.2f)
    val growsDown = abs(to.height - from.height) > 1f && to.height > from.height
    val widthShare = p
    val heightShare = if (growsDown) leadAxisShare(p) else p
    val width = (from.width + (to.width - from.width) * widthShare).coerceAtLeast(1f)
    val height = (from.height + (to.height - from.height) * heightShare).coerceAtLeast(1f)
    val anchorRight = abs(to.right - from.right) <= abs(to.left - from.left)
    val anchorBottom = abs(to.bottom - from.bottom) < abs(to.top - from.top)
    val right = from.right + (to.right - from.right) * widthShare
    val bottom = from.bottom + (to.bottom - from.bottom) * heightShare
    val left = if (anchorRight) right - width else from.left + (to.left - from.left) * widthShare
    val top = if (anchorBottom) bottom - height else from.top + (to.top - from.top) * heightShare
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

fun centreSource(container: MorphBox, size: Float): MorphBox =
    MorphBox(
        left = container.left + (container.width - size) / 2f,
        top = container.top + (container.height - size) / 2f,
        width = size,
        height = size,
    )
