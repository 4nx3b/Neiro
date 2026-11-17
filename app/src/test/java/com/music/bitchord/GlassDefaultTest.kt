package com.music.bitchord

import com.music.bitchord.data.settings.GlassDefault
import org.junit.Assert.assertEquals
import org.junit.Test

class GlassDefaultTest {
    @Test
    fun aFreshInstallStartsWithGlassOn() {
        assertEquals(GlassDefault.Decision(true, true), GlassDefault.decide(stored = null, migrated = false))
    }

    @Test
    fun anUpgradeWithGlassOffIsSwitchedOnOnce() {
        assertEquals(GlassDefault.Decision(true, true), GlassDefault.decide(stored = false, migrated = false))
    }

    @Test
    fun afterTheMigrationTheUsersChoiceStands() {
        assertEquals(GlassDefault.Decision(false, false), GlassDefault.decide(stored = false, migrated = true))
        assertEquals(GlassDefault.Decision(true, false), GlassDefault.decide(stored = true, migrated = true))
        assertEquals(GlassDefault.Decision(true, false), GlassDefault.decide(stored = null, migrated = true))
    }
}
