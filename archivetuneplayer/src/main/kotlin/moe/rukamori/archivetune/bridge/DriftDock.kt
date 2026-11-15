/*
 * BRIDGE — the BITCHORD player's "drift" collapse, for the ported styles.
 *
 * When the full player is dragged down, the artwork leaves its slot and
 * travels into the mini player's cover along a curve: x moves linearly
 * while y rides the sheet only as far as it can without ever passing the
 * cover, so it lands tangentially, its vertical speed run down to nothing
 * exactly on the cover. The path is the one the app's own sleeve takes
 * (sharedUi NowPlayingScreen's dock: ride = (mini.top - open.top) / travel,
 * rect = lerp(mini, open.translate(0, offset * ride), t)).
 *
 * Pieces:
 *  - [driftRect], the pure path (JVM-tested by DriftDockTest);
 *  - [DriftState], the two measured slots and the sheet's progress;
 *  - [driftArtworkSlot], the one-line hook a style's artwork carries: it
 *    reports the slot while the player is fully open, and hides the artwork
 *    while the drift copy is in flight;
 *  - [DriftArtworkLayer], the flying copy, drawn in BottomSheet's shared
 *    layer and placed entirely in layout / graphicsLayer from the sheet's
 *    progress, so a drag recomposes nothing.
 *
 * All rects are in the sheet's own coordinates with the sheet fully open
 * (the shared layer fills the sheet). The sheet is translated down by
 * (1 - t) * travel; the mini player sits at the sheet's top, so its cover's
 * on-screen rest is its sheet-local rect plus the full travel.
 */

package moe.rukamori.archivetune.bridge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

/**
 * Where the artwork is drawn at open fraction [t] (0 = collapsed, 1 = open),
 * in screen-like coordinates of the open sheet. [mini] is the cover's rest
 * rect (where it lands), [open] the artwork slot with the player open,
 * [travelPx] how far the sheet moves between the two states.
 */
fun driftRect(mini: Rect, open: Rect, t: Float, travelPx: Float): Rect {
    val p = t.coerceIn(0f, 1f)
    if (travelPx <= 0f) return lerp(mini, open, p)
    val offset = (1f - p) * travelPx
    val ride = ((mini.top - open.top) / travelPx).coerceIn(0f, 1f)
    return lerp(mini, open.translate(0f, offset * ride), p)
}

/** The measured slots and the sheet's state. One per mounted player. */
@Stable
class DriftState(
    /** The sheet's open fraction, 0…1. Read only in layout / draw. */
    internal val progress: () -> Float,
    /** Collapsed-to-open travel of the sheet, px. */
    internal val travelPx: () -> Float,
    @Suppress("unused") internal val isExpanded: () -> Boolean,
    internal val isCollapsed: () -> Boolean,
) {
    /** Off for SpatialFlow (its own floating artwork) and under reduced motion. */
    var enabled by mutableStateOf(false)

    /** The cover in the mini player, sheet-local, measured at rest collapsed. */
    var miniArt by mutableStateOf<Rect?>(null)
        internal set

    /** The style's artwork slot, sheet-local, measured with the player open. */
    var fullArt by mutableStateOf<Rect?>(null)
        internal set

    var miniCornerPx by mutableStateOf(0f)
    var fullCornerPx by mutableStateOf(0f)

    /** The sheet itself, from the shared layer that fills it. */
    private var sheet: LayoutCoordinates? = null

    /** A mini rect that arrived before the sheet was measured. */
    private var pendingMini: Rect? = null

    /** The style's artwork slot, kept so it is re-read against the sheet. */
    private var fullSlot: LayoutCoordinates? = null

    internal fun attachSheet(coordinates: LayoutCoordinates) {
        sheet = coordinates
        pendingMini?.let { root ->
            // Only trustworthy if the sheet has not moved since: at rest.
            if (isCollapsed()) setMini(root.translate(-coordinates.positionInRoot()))
            pendingMini = null
        }
        fullSlot?.let { readFull(it) }
    }

    private fun setMini(local: Rect) {
        if (local.width > 0f && local != miniArt) miniArt = local
    }

    /**
     * From the mini player's artwork slot, a rect in root coordinates. The
     * slot is a child of the sheet, so its offset from the sheet read at the
     * same moment is its sheet-local position, whatever the sheet's
     * translation is.
     */
    fun reportMini(root: Rect) {
        val sheet = sheet?.takeIf { it.isAttached }
        if (sheet == null) {
            pendingMini = root
            return
        }
        setMini(root.translate(-sheet.positionInRoot()))
    }

    /** From a style's artwork slot. */
    internal fun reportFull(slot: LayoutCoordinates, cornerPx: Float) {
        fullSlot = slot
        if (cornerPx != fullCornerPx) fullCornerPx = cornerPx
        readFull(slot)
    }

    private fun readFull(slot: LayoutCoordinates) {
        val sheet = sheet?.takeIf { it.isAttached } ?: return
        if (!slot.isAttached) return
        val topLeft = sheet.localPositionOf(slot, Offset.Zero)
        val local = Rect(topLeft, Size(slot.size.width.toFloat(), slot.size.height.toFloat()))
        if (local.width > 0f && local != fullArt) fullArt = local
    }

    /** Whether the copy is flying this frame. Read only in layout / draw. */
    fun inFlight(): Boolean {
        if (!enabled || miniArt == null || fullArt == null) return false
        val t = progress()
        return t > 0.001f && t < 0.999f
    }

    /** The copy's rect this frame, sheet-local; null when there is none. */
    fun currentRect(): Rect? {
        val mini = miniArt ?: return null
        val full = fullArt ?: return null
        val travel = travelPx()
        val t = progress().coerceIn(0f, 1f)
        val screen = driftRect(mini.translate(0f, travel), full, t, travel)
        // The sheet is translated down by (1 - t) * travel; draw in its frame.
        return screen.translate(0f, -(1f - t) * travel)
    }

    fun currentCornerPx(): Float {
        val t = progress().coerceIn(0f, 1f)
        return miniCornerPx + (fullCornerPx - miniCornerPx) * t
    }
}

/**
 * The mounted player's drift, or null where there is none (settings,
 * previews). A holder rather than a CompositionLocal so the vendored
 * Player.kt needs one-line hooks only, not a provider wrapped around its
 * sheet; one ported player is mounted at a time. Set in a remember before
 * the sheet composes, cleared when the player leaves.
 */
object DriftBridge {
    @Volatile
    var current: DriftState? = null
}

/**
 * The hook a style's artwork carries (one `// BRIDGE: drift` line): reports
 * the slot while open and hides the artwork while its copy is in flight.
 * [active] is false for the artwork pages that are not the current one.
 */
fun Modifier.driftArtworkSlot(
    active: Boolean = true,
    cornerDp: Float = 0f,
): Modifier =
    composed {
        val drift = DriftBridge.current ?: return@composed this
        if (!active) return@composed this
        val cornerPx = cornerDp * androidx.compose.ui.platform.LocalDensity.current.density
        this
            .onGloballyPositioned { coordinates -> drift.reportFull(coordinates, cornerPx) }
            .graphicsLayer {
                alpha = if (drift.inFlight()) 0f else 1f
            }
    }

/**
 * The flying copy, for BottomSheet's shared layer. Always composed (it is
 * also what measures the sheet), drawn only while in flight.
 */
@Composable
fun DriftArtworkLayer(
    drift: DriftState,
    artUrl: String?,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { drift.attachSheet(it) },
    ) {
        if (artUrl == null || !drift.enabled) return@Box
        AsyncImage(
            model = artUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    // Measured once at the open slot's size; the flight is a
                    // translate + scale in the layer, so no frame re-lays out.
                    .layout { measurable, _ ->
                        val full = drift.fullArt
                        val w = full?.width?.roundToInt()?.coerceAtLeast(1) ?: 1
                        val h = full?.height?.roundToInt()?.coerceAtLeast(1) ?: 1
                        val placeable = measurable.measure(Constraints.fixed(w, h))
                        layout(w, h) { placeable.placeRelative(0, 0) }
                    }.graphicsLayer {
                        val rect = drift.currentRect()
                        val full = drift.fullArt
                        if (rect == null || full == null || !drift.inFlight()) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        alpha = 1f
                        transformOrigin = TransformOrigin(0f, 0f)
                        val scale = rect.width / full.width
                        scaleX = scale
                        scaleY = rect.height / full.height
                        translationX = rect.left
                        translationY = rect.top
                        // The corner is given in drawn pixels; the layer is
                        // scaled, so undo the scale for the shape.
                        val corner = drift.currentCornerPx() / scale.coerceAtLeast(0.01f)
                        shape = RoundedCornerShape(corner)
                        clip = true
                    },
        )
    }
}
