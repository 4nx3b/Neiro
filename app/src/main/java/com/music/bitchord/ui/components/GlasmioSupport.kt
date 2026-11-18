/*
 * Neiro
 * GPL-3.0 License | Contributors: see git history
 */
package com.music.bitchord.ui.components

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import com.music.bitchord.ui.player.LocalGlassSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import io.github.neoruaa.glasmio.core.GlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassAppearance
import io.github.neoruaa.glasmio.core.LocalGlassColors
import io.github.neoruaa.glasmio.core.defaultGlassColors
import io.github.neoruaa.glasmio.glass.LocalBlurBackdrop
import io.github.neoruaa.glasmio.glass.LocalGlassBackdrop
import io.github.neoruaa.glasmio.glass.render.Backdrop
import io.github.neoruaa.glasmio.glass.render.BackdropEffectScope
import io.github.neoruaa.glasmio.glass.render.effects.colorFilter
import io.github.neoruaa.glasmio.ui.GlasmioSlider
import kotlin.math.roundToInt

@get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
val glasmioAvailable: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

@Composable
fun rememberGlasmioLocals(backdrop: Backdrop, liquidGlass: Boolean): Array<ProvidedValue<*>> {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val colors = remember(dark, scheme.primary, scheme.background, scheme.surface, scheme.error) {
        defaultGlassColors(isDark = dark, accent = scheme.primary).copy(
            groupedBackground = scheme.background,
            elevatedBackground = scheme.surface,
            destructive = scheme.error,
        )
    }
    val appearance = remember(liquidGlass, reduceDynamicBlur) {
        GlassAppearance(
            liquidGlassEnabled = liquidGlass && glasmioAvailable,
            reduceTransparency = reduceDynamicBlur || !glasmioAvailable,
        )
    }
    val locals = mutableListOf<ProvidedValue<*>>(
        LocalGlassBackdrop provides backdrop,
        LocalBlurBackdrop provides backdrop,
        LocalGlassColors provides colors,
        LocalGlassAppearance provides appearance,
    )
    if (glasmioAvailable) locals += LocalGlassSlider provides GlasmioThinSlider
    return locals.toTypedArray()
}

private val GlasmioThinSlider: @Composable (Float, (Float) -> Unit, (() -> Unit)?, Modifier) -> Unit =
    { value, onValueChange, onValueChangeFinished, modifier ->
        GlassSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            onValueChangeFinished = onValueChangeFinished,
        )
    }

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

@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val usableRange = valueRange.start.isFinite() && valueRange.endInclusive.isFinite() &&
        valueRange.start < valueRange.endInclusive &&
        (valueRange.endInclusive - valueRange.start).isFinite()
    if (!glasmioAvailable || !usableRange || !value.isFinite()) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
        )
        return
    }
    val snap: (Float) -> Float = remember(valueRange, steps) {
        { raw -> snapToSteps(raw, valueRange, steps) }
    }
    GlasmioSlider(
        value = value.coerceIn(valueRange),
        onValueChange = { onValueChange(snap(it)) },
        modifier = modifier,
        valueRange = valueRange,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
    )
}

fun snapToSteps(raw: Float, valueRange: ClosedFloatingPointRange<Float>, steps: Int): Float {
    val clamped = raw.coerceIn(valueRange)
    if (steps <= 0) return clamped
    val span = valueRange.endInclusive - valueRange.start
    if (span <= 0f) return clamped
    val segments = steps + 1
    val index = ((clamped - valueRange.start) / span * segments).roundToInt()
    return valueRange.start + span * index / segments
}
