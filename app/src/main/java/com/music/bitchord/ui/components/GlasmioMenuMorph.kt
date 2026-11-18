/*
 * Neiro
 * GPL-3.0 License | Contributors: see git history
 */
package com.music.bitchord.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import io.github.neoruaa.glasmio.glass.LiquidMenuMorphMotion

@Stable
class GlasmioMorphDriver {
    val motion = LiquidMenuMorphMotion()
    var source: Rect? = null
    var target: Rect? = null
    var radius: Float = 0f
    var density: Float = 1f
    private var fresh = true

    val contentProgress: Float get() = if (motion.initialized) motion.frame.contentProgress else 0f

    var fallback by mutableStateOf(false)
        private set

    suspend fun prepare(): Boolean {
        fallback = false
        var waited = 0
        while (!(source.isPositive() && target.isPositive()) && waited < MAX_LAYOUT_FRAMES) {
            withFrameNanos { }
            waited++
        }
        val from = source?.takeIf { it.isPositive() }
        val to = target?.takeIf { it.isPositive() }
        if (from == null || to == null) {
            fallback = true
            return false
        }
        if (fresh || !motion.initialized) {
            motion.initialize(from, to, radius.coerceAtLeast(0f), density)
            fresh = false
        } else {
            motion.updateSource(from)
            motion.updateTarget(to, radius.coerceAtLeast(0f))
        }
        return true
    }

    suspend fun expand() {
        motion.animateTo(true)
    }

    suspend fun close() {
        if (motion.initialized && !fallback) motion.animateTo(false)
        fresh = true
    }
}

fun GraphicsLayerScope.applyGlasmioPanel(driver: GlasmioMorphDriver, panel: Rect) {
    val motion = driver.motion
    if (!motion.initialized || panel.width <= 0f || panel.height <= 0f) {
        alpha = 0f
        return
    }
    val frame = motion.frame
    val bounds = frame.bounds
    transformOrigin = TransformOrigin(0f, 0f)
    scaleX = bounds.width / panel.width
    scaleY = bounds.height / panel.height
    translationX = bounds.left - panel.left
    translationY = bounds.top - panel.top
    alpha = ((1f - frame.sourceOpacity) * PANEL_ALPHA_GAIN).coerceIn(0f, 1f)
}

fun Modifier.glasmioMorphContent(driver: GlasmioMorphDriver): Modifier = graphicsLayer {
    val motion = driver.motion
    if (!motion.initialized) {
        alpha = 0f
        return@graphicsLayer
    }
    val frame = motion.frame
    alpha = frame.contentProgress
    scaleX = frame.contentZoom
    scaleY = frame.contentZoom
    val blur = frame.contentBlur
    renderEffect = if (blur > 0.5f) BlurEffect(blur, blur) else null
}

private const val MAX_LAYOUT_FRAMES = 60
private const val PANEL_ALPHA_GAIN = 3f

private fun Rect?.isPositive(): Boolean =
    this != null && width.isFinite() && height.isFinite() && width > 0f && height > 0f
