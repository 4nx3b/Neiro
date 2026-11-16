/*
 * BRIDGE — the host's mini player drives the ported player's sheet.
 *
 * There is one mini player in the app, the host's, whatever the player style.
 * With a ported style the ported sheet has no mini player of its own: it rests
 * fully down (dismissed and collapsed are the same bound, 0), and the host's
 * mini player opens it through this handle, by a tap ([expand]) or by a pull
 * that the sheet follows 1:1 ([drag], [release]). The host reads [offsetPx]
 * and [travelPx] back for its PlayerDock, so its mini cover stands aside while
 * the ported player's artwork drifts to and from it.
 *
 * Signs, against BottomSheet.kt: its DraggableState applies
 * `value - delta.toDp()`, so a finger moving up (negative delta, the
 * MiniPlayerPull contract) raises the sheet — the delta is passed through.
 * performFling expands for a positive velocity, while MiniPlayerPull's
 * velocity is negative upwards — so it is negated ([flingVelocity]).
 */
package moe.rukamori.archivetune.bridge

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import moe.rukamori.archivetune.ui.component.BottomSheetState

@Stable
class ArchiveTuneSheetHandle {
    @Volatile
    internal var state: BottomSheetState? = null

    @Volatile
    internal var density: Density? = null

    /** The mini player was tapped. */
    fun expand() {
        state?.expandSoft()
    }

    /** The finger moved this far on the mini player, screen px, negative is up. */
    fun drag(deltaPx: Float) {
        state?.dispatchRawDelta(deltaPx)
    }

    /**
     * The finger lifted, moving this fast (screen px/s, negative is up). No
     * dismiss: letting go low only closes the player, it never stops playback.
     */
    fun release(velocityPx: Float) {
        state?.performFling(flingVelocity(velocityPx), null)
    }

    /** How far the sheet sits below fully open, px; NaN before it is laid out. */
    fun offsetPx(): Float {
        val s = state ?: return Float.NaN
        val d = density ?: return Float.NaN
        return with(d) { (s.expandedBound - s.value).toPx() }
    }

    /** Fully open to fully down, px. */
    fun travelPx(): Float {
        val s = state ?: return 0f
        val d = density ?: return 0f
        return with(d) { (s.expandedBound - s.dismissedBound).toPx() }
    }

    /** Back from the full player: close it, never stop playback. */
    fun close() {
        state?.collapseSoft()
    }

    companion object {
        /** MiniPlayerPull's velocity (negative up) → performFling's (positive expands). */
        fun flingVelocity(pullVelocityPx: Float): Float = -pullVelocityPx
    }
}

/** The host's mini cover on screen, for the ported drift; null where there is none. */
fun interface ExternalMiniArt {
    fun screenRect(): Rect?
}
