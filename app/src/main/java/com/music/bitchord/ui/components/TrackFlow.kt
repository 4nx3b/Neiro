/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */
package com.music.bitchord.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.music.bitchord.data.model.Song

/*
 * The direction the now-playing line is about to travel. The skip gestures
 * call hint() in the same beat they call onNext/onPrevious, so the text
 * switch slides the new track's line in the way the queue actually moved —
 * up for a skip next, down for a skip back — rather than guessing from the
 * change alone. Auto-advance keeps the last hinted direction, which is the
 * forward one the queue was last seen moving.
 */
object TrackFlow {
    internal var forward by mutableStateOf(true)
        private set

    /** Tell the text switch which way the next track change is heading. */
    fun hint(forward: Boolean) {
        this.forward = forward
    }
}

/**
 * The mini player's now-playing line: when [song] changes, the caller's
 * [content] for the new track slides in over the old one's way out — up from
 * below on a skip next, down from above on a skip back ([TrackFlow.forward]),
 * both fading across the short travel, as the player's own glyph morphs do.
 * While one [Song] is showing the content simply is that song's, so an
 * unchanged track never re-animates.
 */
@Composable
fun TrackTextSwitch(
    song: Song,
    modifier: Modifier = Modifier,
    content: @Composable (Song) -> Unit,
) {
    AnimatedContent(
        targetState = song,
        transitionSpec = {
            val slide = spring<IntOffset>(stiffness = Spring.StiffnessMediumLow)
            if (TrackFlow.forward) {
                (
                    slideInVertically(slide) { full -> full } +
                        fadeIn(spring(stiffness = Spring.StiffnessMedium))
                    ) togetherWith (
                    slideOutVertically(slide) { full -> -full } +
                        fadeOut(tween(90))
                    )
            } else {
                (
                    slideInVertically(slide) { full -> -full } +
                        fadeIn(spring(stiffness = Spring.StiffnessMedium))
                    ) togetherWith (
                    slideOutVertically(slide) { full -> full } +
                        fadeOut(tween(90))
                    )
            }
        },
        modifier = modifier,
        label = "trackTextSwitch",
    ) { shown ->
        content(shown)
    }
}
