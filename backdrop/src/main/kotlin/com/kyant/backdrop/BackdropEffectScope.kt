package com.kyant.backdrop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Global render-resolution scale for backdrop effects.
 *
 * A value below `1f` reduces the number of pixels processed by blur and shader effects, while a
 * value above `1f` supersamples them. The supported range is clamped to `0.1f..3f` when drawing.
 */
object BackdropRendering {
    var scale by mutableFloatStateOf(1f)
}

sealed interface BackdropEffectScope : Density, RuntimeShaderCache {

    val size: Size

    val layoutDirection: LayoutDirection

    val shape: Shape

    var padding: Float

    var renderEffect: RenderEffect?
}

internal abstract class BackdropEffectScopeImpl : BackdropEffectScope, RuntimeShaderCache {

    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override var padding: Float = 0f
    internal var renderScale: Float = 1f
    override var renderEffect: RenderEffect? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaderCache.obtainRuntimeShader(key, string)
    }

    fun update(scope: DrawScope, samplingScale: (Int, Int) -> Float = { _, _ -> 1f }): Boolean {
        // The surface's own sampling scale times the global one: each surface
        // can record its backdrop at a reduced resolution (the blur hides the
        // upscale), and BackdropRendering.scale tiers every surface at once.
        val scale = (
            samplingScale(
                scope.size.width.toInt().coerceAtLeast(1),
                scope.size.height.toInt().coerceAtLeast(1)
            ) * BackdropRendering.scale
            ).coerceIn(0.1f, 3f)
        val newDensity = scope.density * scale
        val newFontScale = scope.fontScale
        val newSize = Size(scope.size.width * scale, scope.size.height * scale)
        val newLayoutDirection = scope.layoutDirection

        val changed = newDensity != density ||
                newFontScale != fontScale ||
                newSize != size ||
                scale != renderScale ||
                newLayoutDirection != layoutDirection

        if (changed) {
            renderScale = scale
            density = newDensity
            fontScale = newFontScale
            size = newSize
            layoutDirection = newLayoutDirection
        }

        return changed
    }

    fun apply(effects: BackdropEffectScope.() -> Unit) {
        padding = 0f
        renderEffect = null
        effects()
    }

    fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        layoutDirection = LayoutDirection.Ltr
        padding = 0f
        renderEffect = null
        runtimeShaderCache.clear()
    }
}
