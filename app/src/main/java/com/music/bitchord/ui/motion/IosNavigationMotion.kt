package com.music.bitchord.ui.motion

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FloatAnimationSpec
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFloatAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt

/*
 * The iOS navigation motion, ported
 * — the fork's own iOS navigation motion, verbatim where the code is physics
 * and adapted only where it was typed against navigation3's Scene, which this
 * app's hand-rolled dual-stack navigator has no counterpart of. This is the
 * motion behind the page switch and the album opening: the body slides that
 * UIKit's navigation parallax prescribes, on the exact unnormalised critical
 * spring rather than a tween cut to its duration.
 */

/** UIKit's navigation parallax: the covered page travels thirty percent of its width. */
internal const val IosNavigationParallax = 0.3f
internal const val IosNavigationDurationMillis = 350
private val IosNavigationOmega = 2.0 * PI / 0.3
internal val IosNavigationStiffness = (IosNavigationOmega * IosNavigationOmega).toFloat()

/** The easing: the spring's own position over the 350 ms coordinator window. */
val IosNavigationEasing = Easing { iosNavigationPosition(it, 350_000_000L) }

/** Stock FloatSpringSpec rounds time to milliseconds, which breaks inverse seeking mid-push. */
internal object IosNavigationFloatSpring : FloatAnimationSpec {
    private val duration = FloatSpringSpec(dampingRatio = 1f, stiffness = IosNavigationStiffness)

    override fun getValueFromNanos(playTimeNanos: Long, initialValue: Float, targetValue: Float, initialVelocity: Float): Float {
        val time = playTimeNanos.coerceAtLeast(0L) / 1e9
        val displacement = initialValue.toDouble() - targetValue
        val coefficient = initialVelocity + IosNavigationOmega * displacement
        return (targetValue + (displacement + coefficient * time) * exp(-IosNavigationOmega * time)).toFloat()
    }

    override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: Float, targetValue: Float, initialVelocity: Float): Float {
        val time = playTimeNanos.coerceAtLeast(0L) / 1e9
        val coefficient = initialVelocity + IosNavigationOmega * (initialValue.toDouble() - targetValue)
        return ((initialVelocity - IosNavigationOmega * coefficient * time) * exp(-IosNavigationOmega * time)).toFloat()
    }

    override fun getDurationNanos(initialValue: Float, targetValue: Float, initialVelocity: Float): Long =
        duration.getDurationNanos(initialValue, targetValue, initialVelocity)

    override fun getEndVelocity(initialValue: Float, targetValue: Float, initialVelocity: Float): Float = 0f
}

internal object IosNavigationSlideSpring : FiniteAnimationSpec<IntOffset> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<IntOffset, V>): VectorizedFloatAnimationSpec<V> =
        VectorizedFloatAnimationSpec(IosNavigationFloatSpring)
}

/** Unnormalized critical spring, sampled on the actual transition clock rather than cut at 350ms. */
internal fun iosNavigationPosition(fraction: Float, durationNanos: Long): Float {
    val time = fraction.coerceIn(0f, 1f) * durationNanos / 1e9
    return (1.0 - (1.0 + IosNavigationOmega * time) * exp(-IosNavigationOmega * time)).toFloat()
}

internal fun iosNavigationParallaxOffset(widthPixels: Int, density: Float): Int =
    (widthPixels - (widthPixels / density * 0.7f).roundToInt() * density).roundToInt()
