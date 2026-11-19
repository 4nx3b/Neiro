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
 * The same net catches Liquid Glass dying in the *renderer*. A native
 * RenderThread death — the ported glass stack's AGSL refraction and blur
 * chains have done this on OEM builds (ColorOS's GED/MEOW hooks, Mali,
 * Android 16: `Fatal signal 5 (SIGTRAP), code 1 (TRAP_BRKPT)` in
 * RenderThread, no tombstone, three times in a row at launch) — never
 * reaches the Java handler, so [record] cannot see it. Instead every launch
 * writes the glass session marker while Liquid Glass is armed, and a launch
 * that survives [CrashLoopPolicy.QUICK_CRASH_MS] deletes it: a marker still
 * standing at the next [checkOnLaunch] is the remains of a launch that died
 * young, and [CrashLoopPolicy.isNativeDeathLoop] decides when two of those
 * in a row are a loop. The loop is broken by switching Liquid Glass off —
 * MainActivity then draws the Haze bars instead of the ported glass, the
 * path the app ran on this hardware all along — and the next screen says
 * so once ([consumeGlassReset]). The setting can be turned back on at any
 * time; a healthy device never writes a death, and one young death — an
 * ordinary kill, a Java crash ([record] clears the marker: that death is
 * the style guard's to account for, not this one's) — never counts.
 *
 * Plain file I/O only, and nothing here may throw: [record] runs inside the
 * crash handler.
 */
object CrashLoopGuard {
    private const val RECORD = "boot_crashes"
    private const val RESET_MARKER = "player_style_reset"
    private const val GLASS_SESSION = "glass_boot"
    private const val GLASS_DEATHS = "glass_deaths"
    private const val GLASS_RESET = "glass_reset"

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
            // A death the Java handler sees is accounted for above; it is not a
            // native renderer death, so it must not also count against glass.
            File(dir(context), GLASS_SESSION).delete()
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
        // The native half: judge the last launch by what it left behind.
        runCatching { checkNativeGlassLoop(app) }
        // This launch lived long enough: whatever came before was not a loop.
        Handler(Looper.getMainLooper()).postDelayed(
            {
                runCatching {
                    File(dir(app), RECORD).delete()
                    File(dir(app), GLASS_SESSION).delete()
                    File(dir(app), GLASS_DEATHS).delete()
                }
            },
            CrashLoopPolicy.QUICK_CRASH_MS,
        )
        return reset
    }

    /**
     * The glass session marker dance, one launch at a time.
     *
     * A marker left behind (only [record] and a surviving launch remove it)
     * means the previous launch died within [CrashLoopPolicy.QUICK_CRASH_MS]
     * with Liquid Glass about to render. It counts as a death unless it is
     * older than [CrashLoopPolicy.WINDOW_MS] — a force-stop yesterday is not
     * a loop today. Two deaths in a row turn Liquid Glass off and leave the
     * notice for [consumeGlassReset]; the marker is then rewritten for this
     * launch only while glass is still armed, so a device without glass (or
     * with it switched off) never accumulates deaths that are not glass's.
     */
    private fun checkNativeGlassLoop(app: Context) {
        val glassArmed =
            AppSettings.liquidGlass.value && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val session = File(dir(app), GLASS_SESSION)
        val deaths = File(dir(app), GLASS_DEATHS)
        if (glassArmed && session.isFile) {
            val startedAt = runCatching { session.readText().trim().toLongOrNull() }.getOrNull()
            val now = System.currentTimeMillis()
            if (startedAt != null && now - startedAt in 0..CrashLoopPolicy.WINDOW_MS) {
                FileOutputStream(deaths, true).use { it.write(("$now\n").toByteArray()) }
                val observed =
                    runCatching { deaths.readLines().mapNotNull { it.trim().toLongOrNull() } }
                        .getOrDefault(emptyList())
                if (CrashLoopPolicy.isNativeDeathLoop(observed, now)) {
                    AppSettings.setLiquidGlass(false)
                    File(dir(app), GLASS_RESET).writeText("1")
                    deaths.delete()
                }
            }
        }
        // Rewrite the marker for this launch only while glass is still armed —
        // a reset above disarms it mid-check, and a device without glass never
        // accumulates deaths that are not glass's.
        if (glassArmed && AppSettings.liquidGlass.value) {
            File(dir(app), GLASS_SESSION).writeText(System.currentTimeMillis().toString())
        } else {
            session.delete()
        }
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

    /** Whether Liquid Glass was switched off after a native crash loop, once. */
    fun consumeGlassReset(context: Context): Boolean =
        try {
            val marker = File(dir(context), GLASS_RESET)
            if (marker.isFile) {
                marker.delete()
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
}
