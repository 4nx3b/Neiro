package com.music.bitchord.ui.motion

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Where each release's cover sits on screen right now, so its page can zoom
 * out of the cover that was tapped and shrink back onto it — the iOS 26 Apple
 * Music album open (docs/ios-glass-reference.md, clip 03-54-11 at 2.50 s and
 * 3.20 s).
 *
 * A card reports its cover's bounds in window coordinates on every layout,
 * and forgets them when it leaves composition, so a page is never zoomed out
 * of a cover that is no longer there. Read in draw only.
 */
object ReleaseOrigin {
    private val bounds = HashMap<String, Rect>()

    /** The cover of [browseId] on screen now, window coordinates; null when none is laid out. */
    fun of(browseId: String): Rect? = synchronized(bounds) { bounds[browseId] }

    internal fun report(browseId: String, rect: Rect, owner: Any) {
        synchronized(bounds) {
            bounds[browseId] = rect
            owners[browseId] = owner
        }
    }

    internal fun release(browseId: String, owner: Any) {
        synchronized(bounds) {
            if (owners[browseId] === owner) {
                bounds.remove(browseId)
                owners.remove(browseId)
            }
        }
    }

    private val owners = HashMap<String, Any>()
}

/** Reports this cover as [browseId]'s origin for the album zoom. */
fun Modifier.releaseOrigin(browseId: String?): Modifier =
    if (browseId.isNullOrEmpty()) {
        this
    } else {
        composed {
            val owner = remember { Any() }
            DisposableEffect(browseId, owner) {
                onDispose { ReleaseOrigin.release(browseId, owner) }
            }
            onGloballyPositioned { coordinates ->
                if (coordinates.isAttached) ReleaseOrigin.report(browseId, coordinates.boundsInWindow(), owner)
            }
        }
    }
