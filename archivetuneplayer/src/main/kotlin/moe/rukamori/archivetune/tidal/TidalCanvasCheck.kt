/*
 * BRIDGE — replaces ArchiveTune's tidal/TidalCanvasCheck.kt.
 *
 * The canvas diagnostics dialog probes Tidal through ArchiveTune's Tidal
 * account (TidalAccountManager and its OAuth tokens). This app has no Tidal
 * account integration, so the row reports itself as skipped instead of
 * probing with credentials that cannot exist.
 */
package moe.rukamori.archivetune.tidal

import android.content.Context
import moe.rukamori.archivetune.canvas.CanvasSourceDiagnosis

object TidalCanvasCheck {
    @Suppress("UNUSED_PARAMETER")
    suspend fun diagnose(
        context: Context,
        title: String,
        artist: String?,
    ): CanvasSourceDiagnosis = CanvasSourceDiagnosis.Skipped("Tidal sign-in is not part of this app")
}
