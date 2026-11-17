/*
 * BRIDGE — the position the ported lyrics view is fed while a song plays.
 *
 * lyrics-ui 2.0's auto-scroll treats any step backwards in the position it is
 * given (more than 1 ms), or a jump forwards of more than 600 ms, as a seek:
 * it re-issues its follow request and restarts the scroll animation. The
 * ported feed stepped backwards whenever the MediaController's extrapolated
 * position was corrected by a session update (its drift correction, and its
 * snap beyond ±80/180 ms) — a few seconds into a track, and then over and over,
 * so the list kept restarting its scroll and the lyrics lagged.
 *
 * This clock never goes backwards during playback: it advances by the frame
 * time, and eases towards the player's position — slower when the player is
 * behind, faster when ahead — so it converges without ever stepping back. Only
 * a real seek (the player back by more than [SEEK_BACK_MS], or ahead by more
 * than [SNAP_FORWARD_MS]) moves it in one step.
 */
package moe.rukamori.archivetune.bridge

object LyricsClock {
    /** Share of the remaining error taken back each frame. */
    const val GAIN = 0.08

    /** The player this far behind is a seek back, not drift. */
    const val SEEK_BACK_MS = 1000.0

    /** The player this far ahead is jumped to (still under lyrics-ui's 600 ms seek). */
    const val SNAP_FORWARD_MS = 400.0

    /**
     * The next position, from the last one fed ([prev]), the player's
     * position now ([raw]) and how far playback advanced this frame
     * ([advanceMs], frame time × speed).
     */
    fun next(prev: Double, raw: Double, advanceMs: Double): Double {
        val predicted = prev + advanceMs.coerceAtLeast(0.0)
        val error = raw - predicted
        if (error > SNAP_FORWARD_MS || error < -SEEK_BACK_MS) return raw.coerceAtLeast(0.0)
        val corrected = predicted + error * GAIN
        return if (corrected > prev) corrected else prev
    }
}
