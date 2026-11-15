/*
 * BRIDGE — the two symbols the ported settings pages take from ArchiveTune's
 * ui/screens/HomeFeedDesign.kt (a 559-line home-feed file this port does not
 * carry: it is built on ArchiveTune's Room entities, InnerTube items and
 * menus). Same names and signatures. The header blur is drawn with the haze
 * version this module already rides rather than ArchiveTune's
 * haze-materials progressive fade, so it reads the same way: the page
 * content blurs softly as it scrolls under the status bar.
 */
package moe.rukamori.archivetune.ui.screens

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import moe.rukamori.archivetune.constants.LiquidGlassEnabledKey
import moe.rukamori.archivetune.utils.rememberPreference

@Composable
fun rememberScreenHeaderHaze(): HazeState = remember { HazeState() }

@Composable
fun ScreenHeaderHaze(
    hazeState: HazeState,
    systemBarsTopPadding: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scrolled: Boolean = true,
) {
    if (!enabled) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val liquidGlassEnabled by rememberPreference(LiquidGlassEnabledKey, defaultValue = true)
    if (!liquidGlassEnabled) return

    val intensity by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "screenHeaderHazeIntensity",
    )
    val surface = MaterialTheme.colorScheme.surface
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(systemBarsTopPadding + ScreenHeaderHazeBarZone)
                .graphicsLayer { alpha = intensity }
                .hazeEffect(
                    state = hazeState,
                    style =
                        HazeStyle(
                            // haze 1.3.1 throws "backgroundColor not specified"
                            // at draw time without one.
                            backgroundColor = surface,
                            blurRadius = 24.dp,
                            tint = HazeTint(surface.copy(alpha = 0.55f)),
                            noiseFactor = 0f,
                        ),
                ),
    )
}

private val ScreenHeaderHazeBarZone = 64.dp
