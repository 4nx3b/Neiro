/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */

package com.music.bitchord.ui.components.floatingtabbar

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

internal fun Modifier.rubberStretch(liftPx: Float, stretch: () -> Float): Modifier = graphicsLayer {
    val p = stretch()
    transformOrigin = TransformOrigin(0.5f, if (p >= 0f) 1f else 0f)
    translationY = -liftPx * p
    scaleY = 1f + 0.12f * kotlin.math.abs(p)
    scaleX = 1f - 0.05f * kotlin.math.abs(p)
    clip = false
}

private val NEIGHBOUR_LIFT = 8.dp
private val NEIGHBOUR_TUG = 6.dp

internal fun Modifier.neighbourPull(pull: () -> Float, sign: Float): Modifier = graphicsLayer {
    val p = pull()
    translationY = -NEIGHBOUR_LIFT.toPx() * p
    translationX = -sign * NEIGHBOUR_TUG.toPx() * kotlin.math.abs(p)
}
