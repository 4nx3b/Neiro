/*
 * Neiro
 * GPL-3.0 License | Contributors: see git history
 */
package com.music.bitchord.ui.components

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.music.bitchord.ui.components.liquid.LiquidGlassSlider
import com.music.bitchord.ui.player.LocalGlassSlider
import kotlin.math.roundToInt

/**
 * Where the liquid-glass controls are drawn: the thumb's lens is an AGSL
 * RuntimeShader, so the slider and the toggle keep their glass only where
 * that is supported — Android 13 (API 33) on. Everywhere else the plain
 * Material and iOS capsules the app has always drawn take over (see
 * IosSwitch, and [GlassSlider]'s Material fallback).
 */
val liquidGlassControlsSupported: Boolean
    get() = com.kyant.backdrop.isRuntimeShaderSupported()

/**
 * The slider with a liquid-glass thumb wherever Liquid Glass is on and
 * supported, and Material's own everywhere else: a degenerate or non-finite
 * range, or a value that is not finite, is a Slider that cannot be asked
 * anything — the Material fallback again.
 *
 * The discrete rows keep their M3 snapping: the drag is quantised to the
 * same steps + 1 intervals M3's own slider used, so every writer of these
 * settings sees the values it always did.
 */
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
    if (!liquidGlassControlsSupported || !usableRange || !value.isFinite()) {
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
    LiquidGlassSlider(
        value = value.coerceIn(valueRange),
        onValueChange = { onValueChange(snap(it)) },
        modifier = modifier,
        valueRange = valueRange,
        enabled = enabled,
        onValueChangeFinished = onValueChangeFinished,
    )
}

/**
 * The thin glass ride the sharedUi scrubbers take through the
 * [LocalGlassSlider] slot: the player's seek bar and every other hairline
 * capsule renders the liquid-glass slider wherever the glass is on and
 * supported, and the M3 capsule everywhere else (see ThinSlider).
 */
internal val ThinGlassSlider: @Composable (Float, (Float) -> Unit, (() -> Unit)?, Modifier) -> Unit =
    { value, onValueChange, onValueChangeFinished, modifier ->
        GlassSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
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
