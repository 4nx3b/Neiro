package com.music.bitchord.data.settings

/**
 * Liquid Glass is on by default, for a fresh install and once for everybody
 * on an upgrade: the app's bar, mini player and menus are designed as glass.
 * Turned off after that, it stays off — the migration runs once, marked by
 * its own flag. Pure, so it is tested on the JVM (GlassDefaultTest).
 */
object GlassDefault {
    const val DEFAULT_ON = true

    data class Decision(val liquidGlass: Boolean, val markMigrated: Boolean)

    /**
     * [stored] is the saved value (null when never saved), [migrated] whether
     * the one-time switch-on has run.
     */
    fun decide(stored: Boolean?, migrated: Boolean): Decision =
        if (!migrated) Decision(liquidGlass = true, markMigrated = true)
        else Decision(liquidGlass = stored ?: DEFAULT_ON, markMigrated = false)
}
