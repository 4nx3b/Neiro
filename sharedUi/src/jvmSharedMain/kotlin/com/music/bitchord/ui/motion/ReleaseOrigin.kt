package com.music.bitchord.ui.motion

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned

object ReleaseOrigin {
    private val bounds = HashMap<String, Rect>()

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
