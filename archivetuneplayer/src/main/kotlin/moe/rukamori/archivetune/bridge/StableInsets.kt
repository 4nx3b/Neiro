/*
 * BRIDGE — the top inset the ported stack reads as LocalStableSystemBarsTopPadding.
 *
 * ArchiveTune's activity provided the status bar height measured while it
 * was visible, so hiding the status bar never moved the player's controls.
 * WindowInsets.statusBars reads 0 while the bar is hidden; the stable value
 * is statusBarsIgnoringVisibility, and on a phone whose cutout is taller
 * than its status bar the cutout wins.
 */
package moe.rukamori.archivetune.bridge

import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun stableTopInset(): Dp {
    val statusBar = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val cutout = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    return if (statusBar > cutout) statusBar else cutout
}
