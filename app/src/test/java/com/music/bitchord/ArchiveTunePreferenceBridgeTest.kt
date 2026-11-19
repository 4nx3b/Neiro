package com.music.bitchord

import com.music.bitchord.data.settings.AppFont
import com.music.bitchord.data.settings.AutomixPerformanceMode
import com.music.bitchord.data.settings.DefaultOpenTab
import com.music.bitchord.data.settings.LiquidGlassIntensity
import com.music.bitchord.data.settings.PlayerDesignStyle
import com.music.bitchord.data.settings.ThemeMode
import com.music.bitchord.ui.player.ArchiveTunePreferenceBridge as Bridge
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.screens.settings.NavigationTab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import moe.rukamori.archivetune.constants.AppFontPreference as AtFont
import moe.rukamori.archivetune.constants.AutomixPerformanceMode as AtAutomix
import moe.rukamori.archivetune.constants.LiquidGlassIntensity as AtGlassIntensity
import moe.rukamori.archivetune.constants.PlayerDesignStyle as AtStyle

/**
 * Every bridged enum survives the trip to ArchiveTune and back, both ways —
 * the explicit maps in ArchiveTunePreferenceBridge are total and inverse,
 * except where one side has a value the other lacks (documented there).
 */
class ArchiveTunePreferenceBridgeTest {
    private var legacy = false

    @Before
    fun setUp() {
        Bridge.fontIsLegacy = { legacy }
    }

    @After
    fun tearDown() {
        legacy = false
    }

    @Test
    fun designStyleRoundTrips() {
        PlayerDesignStyle.entries.forEach { assertEquals(it, Bridge.fromAt(Bridge.toAt(it))) }
        AtStyle.entries.forEach { assertEquals(it, Bridge.toAt(Bridge.fromAt(it))) }
    }

    @Test
    fun themeModeRoundTrips() {
        ThemeMode.entries.forEach { assertEquals(it, Bridge.fromAt(Bridge.toAt(it))) }
        DarkMode.entries.forEach { assertEquals(it, Bridge.toAt(Bridge.fromAt(it))) }
        assertEquals(DarkMode.AUTO, Bridge.toAt(ThemeMode.SYSTEM))
    }

    @Test
    fun fontRoundTripsAndKeepsLegacy() {
        AtFont.entries.forEach { assertEquals(it, Bridge.toAt(Bridge.fromAt(it))) }
        listOf(AppFont.DEFAULT, AppFont.SYSTEM, AppFont.CUSTOM).forEach {
            assertEquals(it, Bridge.fromAt(Bridge.toAt(it)))
        }
        legacy = true
        assertEquals(AppFont.LEGACY, Bridge.fromAt(Bridge.toAt(AppFont.LEGACY)))
    }

    @Test
    fun tabRoundTrip() {
        DefaultOpenTab.entries.forEach { assertEquals(it, Bridge.fromAt(Bridge.toAt(it))) }
        NavigationTab.entries.forEach { assertEquals(it, Bridge.toAt(Bridge.fromAt(it))) }
    }

    @Test
    fun automixAndCrossfade() {
        AutomixPerformanceMode.entries.forEach { assertEquals(it, Bridge.fromAt(Bridge.toAt(it))) }
        AtAutomix.entries.forEach { assertEquals(it, Bridge.toAt(Bridge.fromAt(it))) }
        assertEquals(8f, Bridge.crossfadeLength(8, 5), 0f)
        assertEquals(5f, Bridge.crossfadeLength(0, 5), 0f)
    }

    @Test
    fun liquidGlassIntensityRoundTrips() {
        LiquidGlassIntensity.entries.forEach { assertEquals(it, Bridge.fromAt(Bridge.toAt(it))) }
        AtGlassIntensity.entries.forEach { assertEquals(it, Bridge.toAt(Bridge.fromAt(it))) }
    }
}
