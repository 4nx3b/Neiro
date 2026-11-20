/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 * Defaults from guozhiqiang123/AndroidLiquidGlass GlassPlaygroundContent and
 * rezapranata089-bit/Zenith GlassClarity / GlassPreset.DEFAULT.
 */
package com.music.bitchord.data.settings

object LiquidGlassUpstream {
    const val CORNER_RADIUS_DEFAULT = 0.5f
    const val CHROMATIC_AMOUNT_DEFAULT = 0f

    const val CLARITY_DEFAULT = 0.13f
    const val SATURATION_DEFAULT = 1f
    const val REFRACTION_WIDTH_DEFAULT = 0.36f
    const val RAINBOW_DEFAULT = 0f
    const val RIM_DEFAULT = 1f
    const val QUALITY_DEFAULT = 1f

    private fun mix(level: Float, clear: Float, balanced: Float, frosted: Float, soft: Boolean = false): Float {
        val t = level.coerceIn(0f, 1f)
        return if (t <= 0.5f) {
            val u = t / 0.5f
            clear + (balanced - clear) * (if (soft) u * u else u)
        } else {
            balanced + (frosted - balanced) * ((t - 0.5f) / 0.5f)
        }
    }

    fun clarityTint(level: Float): Float = mix(level, 0.12f, 0.30f, 0.46f)
    fun clarityBlur(level: Float): Float = mix(level, 0.1f, 1f, 1.35f, soft = true)
    fun clarityResolution(level: Float): Float = mix(level, 0.75f, 0.33f, 0.33f, soft = true)

    fun clarityBlurFactor(level: Float): Float = clarityBlur(level) / clarityBlur(CLARITY_DEFAULT)
    fun clarityTintFactor(level: Float): Float = clarityTint(level) / clarityTint(CLARITY_DEFAULT)
    fun clarityResolutionFactor(level: Float): Float = clarityResolution(level) / clarityResolution(CLARITY_DEFAULT)

    fun cornerFactor(value: Float): Float = value.coerceIn(0f, 1f) / CORNER_RADIUS_DEFAULT
    fun refractionWidthFactor(value: Float): Float = value.coerceIn(0f, 1f) / REFRACTION_WIDTH_DEFAULT
    fun dispersionStrength(chromaticAmount: Float, rainbow: Float): Float =
        1f + chromaticAmount.coerceIn(0f, 1f) + rainbow.coerceIn(0f, 1f)
}
