/*
 * The glass rendering itself is the vendored AndroidLiquidGlass backdrop
 * module (github.com/guozhiqiang123/AndroidLiquidGlass, Kyant's fork —
 * Apache-2.0; see backdrop/build.gradle.kts) whose com.kyant.backdrop
 * package owns backdrop recording and material rendering. This file is
 * the integration glue, adapted from EchoMusicApp/Echo-Music's
 * GlassEffectConfig / Modifier.liquidGlass (GPL-3.0), cut down from Echo's
 * full per-component/vibrancy-slider config to the single on/off switch
 * BitChord exposes in Settings. It is used by the floating nav bar and,
 * while enabled, the app-wide floating top controls.
 */

package com.music.bitchord.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.ui.components.liquid.LiquidGlassDefaults
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LiquidGlassIntensity
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import androidx.compose.ui.unit.dp

val LocalAppBackdrop = staticCompositionLocalOf<Backdrop> { error("No AppBackdrop provided") }

internal val LocalGlassExport = compositionLocalOf<LayerBackdrop?> { null }

fun isGlassSupported(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= Build.VERSION_CODES.S

private const val VIBRANCY = 1f
private val BLUR_RADIUS_DP = LiquidGlassDefaults.TabsBlur.value
private val LENS_HEIGHT = LiquidGlassDefaults.TabsRefractionHeight.value / LENS_MAX_DP
private val LENS_AMOUNT = LiquidGlassDefaults.TabsRefractionAmount.value / LENS_MAX_DP
private const val LENS_MAX_DP = 48f
private const val SURFACE_OPACITY = LiquidGlassDefaults.TabsContainerAlpha

data class LiquidGlassTuning(
    val intensity: LiquidGlassIntensity = LiquidGlassIntensity.STANDARD,
    val refractionHeightFraction: Float = AppSettings.DEFAULT_LIQUID_GLASS_REFRACTION_HEIGHT,
    val refractionAmountFraction: Float = AppSettings.DEFAULT_LIQUID_GLASS_REFRACTION_AMOUNT,
    val blurFraction: Float = AppSettings.DEFAULT_LIQUID_GLASS_BLUR_RADIUS,
    val tintFraction: Float = AppSettings.DEFAULT_LIQUID_GLASS_TINT_OPACITY,
    val shadowFraction: Float = AppSettings.DEFAULT_LIQUID_GLASS_SHADOW_DEPTH,
    val depth3D: Boolean = true,
    val chromaticAberration: Boolean = true,
    val backdropVibrancy: Boolean = true,
    val adaptiveLuminance: Boolean = true,
) {
    private val presetRefraction: Float =
        when (intensity) {
            LiquidGlassIntensity.SUBTLE -> 0.55f
            LiquidGlassIntensity.STANDARD -> 1f
            LiquidGlassIntensity.VIVID -> 1.45f
        }

    private val presetBlur: Float =
        when (intensity) {
            LiquidGlassIntensity.SUBTLE -> 0.70f
            LiquidGlassIntensity.STANDARD -> 1f
            LiquidGlassIntensity.VIVID -> 1.30f
        }

    val refractionHeightFactor: Float =
        (refractionHeightFraction / AppSettings.DEFAULT_LIQUID_GLASS_REFRACTION_HEIGHT) * presetRefraction

    val refractionAmountFactor: Float =
        (refractionAmountFraction / AppSettings.DEFAULT_LIQUID_GLASS_REFRACTION_AMOUNT) * presetRefraction

    val blurFactor: Float = (blurFraction / AppSettings.DEFAULT_LIQUID_GLASS_BLUR_RADIUS) * presetBlur

    val tintFactor: Float = tintFraction / AppSettings.DEFAULT_LIQUID_GLASS_TINT_OPACITY

    val shadowFactor: Float = shadowFraction / AppSettings.DEFAULT_LIQUID_GLASS_SHADOW_DEPTH

    val vibrancyFactor: Float = if (backdropVibrancy) 1f else 0f

    val adaptiveLuminanceFactor: Float = if (adaptiveLuminance) 1f else 0.44f
}

@Composable
internal fun rememberLiquidGlassTuning(): LiquidGlassTuning {
    val intensity by AppSettings.liquidGlassIntensity.collectAsStateWithLifecycle()
    val refractionHeight by AppSettings.liquidGlassRefractionHeight.collectAsStateWithLifecycle()
    val refractionAmount by AppSettings.liquidGlassRefractionAmount.collectAsStateWithLifecycle()
    val blurRadius by AppSettings.liquidGlassBlurRadius.collectAsStateWithLifecycle()
    val tintOpacity by AppSettings.liquidGlassTintOpacity.collectAsStateWithLifecycle()
    val shadowDepth by AppSettings.liquidGlassShadowDepth.collectAsStateWithLifecycle()
    val depth3D by AppSettings.liquidGlassDepth3D.collectAsStateWithLifecycle()
    val chromaticAberration by AppSettings.liquidGlassChromaticAberration.collectAsStateWithLifecycle()
    val backdropVibrancy by AppSettings.liquidGlassBackdropVibrancy.collectAsStateWithLifecycle()
    val adaptiveLuminance by AppSettings.liquidGlassAdaptiveLuminance.collectAsStateWithLifecycle()
    return remember(
        intensity,
        refractionHeight,
        refractionAmount,
        blurRadius,
        tintOpacity,
        shadowDepth,
        depth3D,
        chromaticAberration,
        backdropVibrancy,
        adaptiveLuminance,
    ) {
        LiquidGlassTuning(
            intensity = intensity,
            refractionHeightFraction = refractionHeight,
            refractionAmountFraction = refractionAmount,
            blurFraction = blurRadius,
            tintFraction = tintOpacity,
            shadowFraction = shadowDepth,
            depth3D = depth3D,
            chromaticAberration = chromaticAberration,
            backdropVibrancy = backdropVibrancy,
            adaptiveLuminance = adaptiveLuminance,
        )
    }
}

private const val GLASS_RESOLUTION_SCALE = 0.33f

private const val FROST_BLUR_RADIUS_DP = 16f
private const val FROST_RESOLUTION_SCALE = 0.25f

internal val GLASS_EDGE_WIDTH = 0.5.dp
internal val GLASS_EDGE_COLOR = Color.White.copy(alpha = 0.10f)

@Composable
fun glassContentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White

@Composable
fun glassIndicatorColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color.White.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = DARK_INDICATOR_OPACITY)
    }

private const val DARK_INDICATOR_OPACITY = 0.14f

private fun Modifier.glassRim(shape: Shape): Modifier = drawBackdrop(
    backdrop = emptyBackdrop(),
    shape = { shape },
    effects = {},
    highlight = { Highlight.Default },
    shadow = null,
)

@Composable
fun Modifier.lightweightLiquidGlass(
    shape: CornerBasedShape,
    fallbackColor: Color,
): Modifier {
    val glassHeaderPills by AppSettings.glassHeaderPills.collectAsStateWithLifecycle()
    val useGlass = glassHeaderPills && isGlassSupported()
    val glassTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }

    return clip(shape)
        .background(
            color = if (useGlass) glassTint.copy(alpha = SURFACE_OPACITY) else fallbackColor,
            shape = shape,
        )
        .then(if (useGlass) glassRim(shape) else Modifier)
        .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
}

@Composable
fun Modifier.liquidGlassRim(shape: CornerBasedShape): Modifier {
    val glassHeaderPills by AppSettings.glassHeaderPills.collectAsStateWithLifecycle()
    if (!glassHeaderPills || !isGlassSupported()) return this
    return remember(shape) { glassRim(shape) }
}

@Composable
fun Modifier.liquidGlass(
    shape: CornerBasedShape,

    surfaceTint: Color? = null,
): Modifier {
    if (!isGlassSupported()) return this
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    if (reduceDynamicBlur) {
        return background(MaterialTheme.colorScheme.surface, shape)
            .border(GLASS_EDGE_WIDTH, GLASS_EDGE_COLOR, shape)
    }
    val glassPerformance by AppSettings.glassPerformance.collectAsStateWithLifecycle()
    val tuning = rememberLiquidGlassTuning()
    val backdrop = LocalAppBackdrop.current
    val exportedBackdrop = LocalGlassExport.current
    val density = LocalDensity.current
    val resolutionScale = if (glassPerformance) FROST_RESOLUTION_SCALE else GLASS_RESOLUTION_SCALE
    val blurRadiusDp = (if (glassPerformance) FROST_BLUR_RADIUS_DP else BLUR_RADIUS_DP) * tuning.blurFactor
    val blurPx = with(density) { blurRadiusDp.dp.toPx() } * resolutionScale
    val lensHeightPx = with(density) { (LENS_HEIGHT * LENS_MAX_DP * tuning.refractionHeightFactor).dp.toPx() } * resolutionScale
    val lensAmountPx = with(density) { (LENS_AMOUNT * LENS_MAX_DP * tuning.refractionAmountFactor).dp.toPx() } * resolutionScale
    val themeTint = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        Color(0xFFFAFAFA)
    } else {
        Color(0xFF121212)
    }
    val surfaceTintColor = surfaceTint
        ?: themeTint.copy(alpha = (SURFACE_OPACITY * tuning.tintFactor * tuning.adaptiveLuminanceFactor).coerceIn(0f, 1f))

    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (tuning.vibrancyFactor > 0f) colorControls(saturation = 1f + 0.5f * VIBRANCY * tuning.vibrancyFactor)
            blur(blurPx)
            if (!glassPerformance && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = tuning.depth3D,
                    chromaticAberration = tuning.chromaticAberration,
                )
            }
        },
        highlight = { Highlight.Default },
        shadow = { Shadow.Default.copy(radius = Shadow.Default.radius * tuning.shadowFactor) },
        onDrawSurface = {
            drawRect(color = surfaceTintColor, size = size)
        },
        exportedBackdrop = exportedBackdrop,
        samplingScale = { _, _ -> resolutionScale },
    )
}

fun Modifier.glassGlowOverlay(
    strength: Float,
    shape: Shape,
): Modifier = drawWithContent {
    if (strength <= 0.01f) {
        drawContent()
        return@drawWithContent
    }
    drawContent()
    val outline = shape.createOutline(size, layoutDirection, this)
    val strokePx = GlowEdgeStrokeWidth.toPx()
    val halfStroke = strokePx / 2f
    val insetSize = Size(size.width - strokePx, size.height - strokePx)
    val insetOutline = shape.createOutline(insetSize, layoutDirection, this)

    translate(halfStroke, halfStroke) {
        drawOutline(
            outline = insetOutline,
            brush = Brush.verticalGradient(
                0f to GlowRimStart,
                1f to GlowRimEnd,
            ),
            alpha = strength,
            style = Stroke(strokePx),
        )
    }

    drawOutline(
        outline = outline,
        brush = Brush.verticalGradient(
            0f to GlowSheenTop,
            0.45f to GlowSheenMid,
            1f to GlowSheenEnd,
        ),
        alpha = strength,
        style = Fill,
    )

    drawOutline(
        outline = outline,
        brush = Brush.verticalGradient(
            0.72f to Color.Transparent,
            1f to GlowBottomEnd,
        ),
        alpha = strength * 0.7f,
        style = Fill,
    )
}

private val GlowEdgeStrokeWidth = 0.75.dp

private val GlowRimStart = Color.White.copy(alpha = 0.27f)
private val GlowRimEnd = Color.White.copy(alpha = 0.02f)
private val GlowSheenTop = Color.White.copy(alpha = 0.10f)
private val GlowSheenMid = Color.Transparent
private val GlowSheenEnd = Color.White.copy(alpha = 0.05f)
private val GlowBottomEnd = Color.White.copy(alpha = 0.14f)
