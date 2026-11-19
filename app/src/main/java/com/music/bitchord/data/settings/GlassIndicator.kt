/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */
package com.music.bitchord.data.settings

data class GlassIndicator(
    val restLens: Boolean = true,
    val restRefraction: Float = 2.5f,
    val restRefractionWidth: Float = 2.13f,
    val liftRefraction: Float = 0.93f,
    val liftRefractionWidth: Float = 0.46f,
    val rainbow: Float = 1f,
    val depth: Boolean = true,
    val blur: Float = 1f,
    val saturation: Float = 2.16f,
    val brightness: Float = 1f,
    val fill: Float = 1f,
    val rim: Float = 1f,
    val innerShadow: Float = 1f,
    val innerRadius: Float = 1f,
    val shadow: Float = 1f,
    val lift: Float = 0.79f,
    val iconLift: Float = 0.19f,
    val squash: Float = 0.39f,
)
