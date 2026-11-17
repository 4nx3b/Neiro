/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.constants.PlayerDesignStyle

data class FadeThresholds(
    val titleMinChars: Int,
    val artistMinChars: Int,
    val fadeWidth: Dp = 24.dp,
)

object PlayerFadeConfig {
    val forStyle: Map<PlayerDesignStyle, FadeThresholds> = mapOf(
        PlayerDesignStyle.V9 to FadeThresholds(titleMinChars = 30, artistMinChars = 32),
        PlayerDesignStyle.APPLE_MUSIC to FadeThresholds(titleMinChars = 28, artistMinChars = 30),
    )

    val miniPlayer = FadeThresholds(titleMinChars = 22, artistMinChars = 24, fadeWidth = 16.dp)

    fun forStyle(style: PlayerDesignStyle): FadeThresholds =
        forStyle[style] ?: FadeThresholds(titleMinChars = 24, artistMinChars = 26)
}
