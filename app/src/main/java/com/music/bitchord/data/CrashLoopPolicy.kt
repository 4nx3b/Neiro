package com.music.bitchord.data

/**
 * The decision behind [CrashLoopGuard], kept free of android.* so it can be
 * tested on the JVM.
 *
 * A crash loop is two crashes in a row, each within [QUICK_CRASH_MS] of its
 * process starting, both with the same ported player style active. One
 * crash, a slow crash, two different styles, or the app's own player
 * (BITCHORD) never count, so a one-off bug never takes the listener's
 * chosen style away.
 */
object CrashLoopPolicy {
    const val QUICK_CRASH_MS = 30_000L
    const val WINDOW_MS = 10 * 60_000L
    const val OWN_PLAYER_STYLE = "BITCHORD"

    data class BootCrash(
        val epochMs: Long,
        val uptimeMs: Long,
        val style: String,
    )

    /** One line of the record file: `<epochMs> <uptimeMs> <STYLE>`. */
    fun format(crash: BootCrash): String = "${crash.epochMs} ${crash.uptimeMs} ${crash.style}"

    fun parse(line: String): BootCrash? {
        val parts = line.trim().split(' ')
        if (parts.size != 3) return null
        val epoch = parts[0].toLongOrNull() ?: return null
        val uptime = parts[1].toLongOrNull() ?: return null
        val style = parts[2].takeIf { it.isNotBlank() } ?: return null
        return BootCrash(epoch, uptime, style)
    }

    /** The ported style to fall back from, or null to leave things alone. */
    fun shouldResetStyle(entries: List<BootCrash>, now: Long): String? {
        val recent = entries.filter { now - it.epochMs in 0..WINDOW_MS }.sortedBy { it.epochMs }
        if (recent.size < 2) return null
        val (a, b) = recent.takeLast(2)
        if (a.style != b.style) return null
        if (a.style == OWN_PLAYER_STYLE) return null
        if (a.uptimeMs >= QUICK_CRASH_MS || b.uptimeMs >= QUICK_CRASH_MS) return null
        if (a.uptimeMs < 0 || b.uptimeMs < 0) return null
        return a.style
    }
}
