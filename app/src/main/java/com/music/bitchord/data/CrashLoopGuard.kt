package com.music.bitchord.data

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.PlayerDesignStyle
import java.io.File
import java.io.FileOutputStream

/**
 * Keeps a crashing ported player style from locking the listener out.
 *
 * The chosen player style is saved, so a ported style whose bridge crashes
 * at startup crashes every launch after it too. [record] (called from the
 * uncaught-exception handler) notes each crash with the process uptime and
 * the active style. [checkOnLaunch] (Application.onCreate, after the settings
 * are open) applies [CrashLoopPolicy]: two quick crashes in a row on the
 * same ported style switch back to the app's own player, and the next
 * screen says so ([consumeStyleReset]). A launch that survives
 * [CrashLoopPolicy.QUICK_CRASH_MS] clears the record.
 *
 * Liquid Glass is always on and is never switched off here: a native
 * RenderThread death is not answered by turning the glass off.
 *
 * Plain file I/O only, and nothing here may throw: [record] runs inside the
 * crash handler.
 */
object CrashLoopGuard {
    private const val RECORD = "boot_crashes"
    private const val RESET_MARKER = "player_style_reset"

    /*
     * Deliberately internal storage (files/crash), not CrashReporter's
     * external Android/data/<pkg>/files/crash: the loop record must be
     * writable even when external storage is unavailable, and it is not a
     * report for the user. CrashReporter's prune only scans its own folder;
     * keep the two apart.
     */
    private fun dir(context: Context): File = File(context.filesDir, "crash").also { runCatching { it.mkdirs() } }

    /** From the crash handler: append one line, synchronously. */
    fun record(context: Context) {
        try {
            val uptime = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
            val style = AppSettings.playerDesignStyle.value.name
            val line = CrashLoopPolicy.format(CrashLoopPolicy.BootCrash(System.currentTimeMillis(), uptime, style))
            FileOutputStream(File(dir(context), RECORD), true).use { it.write((line + "\n").toByteArray()) }
        } catch (_: Throwable) {
        }
    }

    /**
     * From Application.onCreate, once AppSettings is open. Returns the style
     * it switched away from, if it did.
     */
    fun checkOnLaunch(context: Context): String? {
        val app = context.applicationContext
        val reset =
            try {
                val file = File(dir(app), RECORD)
                val entries =
                    if (file.isFile) file.readLines().mapNotNull(CrashLoopPolicy::parse) else emptyList()
                val style = CrashLoopPolicy.shouldResetStyle(entries, System.currentTimeMillis())
                if (style != null && AppSettings.playerDesignStyle.value.name == style) {
                    AppSettings.setPlayerDesignStyle(PlayerDesignStyle.BITCHORD)
                    File(dir(app), RESET_MARKER).writeText(style)
                    file.delete()
                    style
                } else {
                    null
                }
            } catch (_: Throwable) {
                null
            }
        // This launch lived long enough: whatever came before was not a loop.
        Handler(Looper.getMainLooper()).postDelayed(
            {
                runCatching {
                    File(dir(app), RECORD).delete()
                }
            },
            CrashLoopPolicy.QUICK_CRASH_MS,
        )
        return reset
    }

    /** The style that was switched away from, once; null otherwise. */
    fun consumeStyleReset(context: Context): PlayerDesignStyle? =
        try {
            val marker = File(dir(context), RESET_MARKER)
            if (!marker.isFile) {
                null
            } else {
                val name = marker.readText().trim()
                marker.delete()
                PlayerDesignStyle.entries.firstOrNull { it.name == name }
            }
        } catch (_: Throwable) {
            null
        }
}
