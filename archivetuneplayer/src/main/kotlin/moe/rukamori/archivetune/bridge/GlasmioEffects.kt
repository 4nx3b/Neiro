/*
 * ArchiveTune player port — BRIDGE.
 *
 * The copied player sources drew their glass through Kyant's backdrop
 * library, which this app replaced with Glasmio's renderer. Glasmio keeps the
 * same capture API but has no colorControls, its lens does not check the
 * platform itself, and its default rim highlight and shadows draw through
 * RuntimeShader, which only exists from API 33. The copied files therefore
 * import colorControls, lens and drawBackdrop from here: below API 33 this
 * drawBackdrop drops the highlight and both shadows, so only the blur and the
 * tint (each self-guarded on RenderEffect) reach the platform.
 */

package moe.rukamori.archivetune.bridge

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.BackdropLuminance
import io.github.neoruaa.glasmio.glass.render.backdrops.LayerBackdrop
import io.github.neoruaa.glasmio.glass.render.highlight.Highlight
import io.github.neoruaa.glasmio.glass.render.shadow.InnerShadow
import io.github.neoruaa.glasmio.glass.render.shadow.Shadow
import io.github.neoruaa.glasmio.glass.render.drawBackdrop as glasmioDrawBackdrop
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.effects.colorFilter
import io.github.neoruaa.glasmio.glass.render.isRuntimeShaderSupported
import io.github.neoruaa.glasmio.glass.render.effects.lens as glasmioLens

fun BackdropEffectScope.colorControls(
    brightness: Float = 0f,
    contrast: Float = 1f,
    saturation: Float = 1f,
) {
    if (brightness == 0f && contrast == 1f && saturation == 1f) return
    val inverse = 1f - saturation
    val r = 0.213f * inverse * contrast
    val g = 0.715f * inverse * contrast
    val b = 0.072f * inverse * contrast
    val s = saturation * contrast
    val t = (0.5f - contrast * 0.5f + brightness) * 255f
    colorFilter(
        ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    r + s, g, b, 0f, t,
                    r, g + s, b, 0f, t,
                    r, g, b + s, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        ),
    )
}

fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Boolean = false,
) {
    if (!isRuntimeShaderSupported()) return
    glasmioLens(
        refractionHeight = refractionHeight,
        refractionAmount = refractionAmount,
        depthEffect = depthEffect,
        chromaticAberration = chromaticAberration,
    )
}

private val BridgeDefaultHighlight: () -> Highlight? = { Highlight.Default }
private val BridgeDefaultShadow: () -> Shadow? = { Shadow.Default }
private val BridgeFullResolution: (Size, Density) -> Float = { _, _ -> 1f }
private val BridgeDefaultOnDrawBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit = { it() }

fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    highlight: (() -> Highlight?)? = BridgeDefaultHighlight,
    shadow: (() -> Shadow?)? = BridgeDefaultShadow,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    luminanceState: BackdropLuminance? = null,
    samplingScale: (Size, Density) -> Float = BridgeFullResolution,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = BridgeDefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
): Modifier {
    val shaders = isRuntimeShaderSupported()
    return glasmioDrawBackdrop(
        backdrop = backdrop,
        shape = shape,
        effects = effects,
        highlight = if (shaders) highlight else null,
        shadow = if (shaders) shadow else null,
        innerShadow = if (shaders) innerShadow else null,
        layerBlock = layerBlock,
        exportedBackdrop = exportedBackdrop,
        luminanceState = luminanceState,
        samplingScale = samplingScale,
        onDrawBehind = onDrawBehind,
        onDrawBackdrop = onDrawBackdrop,
        onDrawSurface = onDrawSurface,
    )
}
