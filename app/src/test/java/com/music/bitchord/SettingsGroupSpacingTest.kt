package com.music.bitchord

import com.music.bitchord.ui.screens.SettingsGroupChrome
import com.music.bitchord.ui.screens.settingsGroupChrome
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsGroupSpacingTest {
    @Test
    fun mainPageKeepsEveryHeaderAndGap() {
        assertEquals(SettingsGroupChrome(showHeader = true, flush = false), settingsGroupChrome(false, true, "Player", null))
        assertEquals(SettingsGroupChrome(showHeader = false, flush = false), settingsGroupChrome(false, true, null, null))
    }

    @Test
    fun firstCardRepeatingThePageTitleSitsFlushWithoutHeader() {
        assertEquals(
            SettingsGroupChrome(showHeader = false, flush = true),
            settingsGroupChrome(inPage = true, first = true, header = "Appearance", pageTitle = "appearance "),
        )
    }

    @Test
    fun firstCardWithoutHeaderSitsFlush() {
        assertEquals(SettingsGroupChrome(false, true), settingsGroupChrome(true, true, null, "Appearance"))
    }

    @Test
    fun laterCardsKeepTheirHeadersAndGaps() {
        assertEquals(SettingsGroupChrome(true, false), settingsGroupChrome(true, false, "Player", "Appearance"))
        assertEquals(SettingsGroupChrome(true, false), settingsGroupChrome(true, false, "Always-on display", "Appearance"))
        // No header: not flush, so SettingsGroup's own 26 dp gap stands.
        assertEquals(SettingsGroupChrome(false, false), settingsGroupChrome(true, false, null, "Appearance"))
    }

    @Test
    fun aFirstCardWithItsOwnHeaderKeepsIt() {
        assertEquals(SettingsGroupChrome(true, false), settingsGroupChrome(true, true, "Audio quality", "Playback"))
    }
}
