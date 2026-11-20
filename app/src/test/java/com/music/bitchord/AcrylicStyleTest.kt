/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */
package com.music.bitchord

import com.music.bitchord.ui.components.ACRYLIC_BLUR_DP
import com.music.bitchord.ui.components.ACRYLIC_NOISE
import com.music.bitchord.ui.components.ACRYLIC_TINT_ALPHA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AcrylicStyleTest {

    private fun root(): File = listOf(File(".."), File(".")).first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun acrylicMatchesArchiveTuneMaterial() {
        assertEquals(32f, ACRYLIC_BLUR_DP, 0f)
        assertEquals(0.12f, ACRYLIC_NOISE, 0f)
        assertEquals(0.72f, ACRYLIC_TINT_ALPHA, 0f)
    }

    @Test
    fun acrylicIsAPersistedAppearanceToggle() {
        val settings = File(root(), "app/src/main/java/com/music/bitchord/data/settings/AppSettings.kt").readText()
        assertTrue(settings.contains("KEY_ACRYLIC_BLUR = \"acrylic_blur\""))
        val sheet = File(root(), "app/src/main/java/com/music/bitchord/ui/screens/SettingsSheet.kt").readText()
        assertTrue(sheet.contains("AppSettings::setAcrylicBlur"))
    }
}
