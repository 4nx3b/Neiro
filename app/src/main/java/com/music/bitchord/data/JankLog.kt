package com.music.bitchord.data

import android.app.Activity
import androidx.metrics.performance.JankStats

/**
 * Janky frames into the crash ring buffer ([CrashLog]), so a report or a
 * shared log from a phone where something still stutters carries evidence:
 * how many frames ran long in the last second, the worst of them, and the UI
 * states JankStats had (which screen, whether lyrics were open).
 *
 * At most one line a second, and nothing when no frame was janky — the
 * buffer holds the app's whole recent log, and a frame-by-frame account would
 * push everything else out of it.
 */
object JankLog {
    private const val TAG = "Jank"
    private const val WINDOW_MS = 1_000L
    private const val JANK_NANOS = 32_000_000L

    private var stats: JankStats? = null
    private var windowStartMs = 0L
    private var janky = 0
    private var worstNanos = 0L
    private var states = ""

    fun install(activity: Activity) {
        runCatching {
            stats?.isTrackingEnabled = false
            stats = JankStats.createAndTrack(activity.window) { frame ->
                if (!frame.isJank || frame.frameDurationUiNanos < JANK_NANOS) return@createAndTrack
                val now = System.currentTimeMillis()
                if (windowStartMs == 0L) windowStartMs = now
                janky++
                if (frame.frameDurationUiNanos > worstNanos) worstNanos = frame.frameDurationUiNanos
                if (frame.states.isNotEmpty()) {
                    states = frame.states.joinToString(",") { "${it.key}=${it.value}" }
                }
                if (now - windowStartMs >= WINDOW_MS) {
                    CrashLog.record(
                        'W',
                        TAG,
                        "$janky janky frames in ${now - windowStartMs} ms, worst ${worstNanos / 1_000_000} ms" +
                            if (states.isNotEmpty()) " [$states]" else "",
                        null,
                    )
                    windowStartMs = 0L
                    janky = 0
                    worstNanos = 0L
                    states = ""
                }
            }
        }
    }

    fun uninstall() {
        stats?.isTrackingEnabled = false
        stats = null
    }
}
