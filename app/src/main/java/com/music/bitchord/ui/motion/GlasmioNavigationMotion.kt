/*
 * Copyright 2026 Glasmio contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copied from NEORUAA/Glasmio @ 905919bc, glasmio-scaffold
 * navigation/IosNavigationMotion.kt: the push/pop spring, its clock-exact
 * sampling and the covered page's parallax. Upstream declares these internal
 * and types its transition specs on navigation3's Scene; Neiro's router is
 * AppRoute-based, so only the motion itself is taken, unchanged.
 */
package com.music.bitchord.ui.motion

import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FloatAnimationSpec
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorizedFloatAnimationSpec
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt

internal const val IosNavigationParallax = 0.3f
internal const val IosNavigationDurationMillis = 350
internal const val IosNavigationDim = 0.1f
private val IosNavigationOmega = 2.0 * PI / 0.3
internal val IosNavigationStiffness = (IosNavigationOmega * IosNavigationOmega).toFloat()
internal val IosNavigationEasing = Easing { iosNavigationPosition(it, 350_000_000L) }

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

internal class IosNavigationSpring<T> : FiniteAnimationSpec<T> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<T, V>): VectorizedFloatAnimationSpec<V> =
        VectorizedFloatAnimationSpec(IosNavigationFloatSpring)
}

internal fun iosNavigationPosition(fraction: Float, durationNanos: Long): Float {
    val time = fraction.coerceIn(0f, 1f) * durationNanos / 1e9
    return (1.0 - (1.0 + IosNavigationOmega * time) * exp(-IosNavigationOmega * time)).toFloat()
}

internal fun iosNavigationParallaxOffset(widthPixels: Int, density: Float): Int =
    (widthPixels - (widthPixels / density * 0.7f).roundToInt() * density).roundToInt()
