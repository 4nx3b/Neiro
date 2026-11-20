/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */
package com.music.bitchord

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassAlwaysOnGuardTest {

    private fun root(): File = listOf(File(".."), File(".")).first { File(it, "settings.gradle.kts").exists() }

    private fun sources(): Sequence<File> = sequenceOf("app/src/main", "sharedUi/src", "tv/src")
        .map { File(root(), it) }
        .filter { it.exists() }
        .flatMap { it.walkTopDown() }
        .filter { it.isFile && it.extension == "kt" }

    @Test
    fun noMasterSwitchRemains() {
        val banned = listOf("AppSettings.liquidGlass.", "setLiquidGlass(", "LocalLiquidGlassEnabled", "GlassDefault.", "object GlassDefault", "liquid_glass_effects")
        sources().forEach { file ->
            val text = file.readText()
            banned.forEach { assertFalse("${file.path} still has $it", text.contains(it)) }
        }
    }

    @Test
    fun dragFollowsTheFingerOnTheSameFrame() {
        val pill = File(root(), "app/src/main/java/com/music/bitchord/ui/components/floatingtabbar/GlassSelectionPill.kt").readText()
        val dragTo = pill.substringAfter("fun dragTo(").substringBefore("private suspend fun handOffDrag")
        assertFalse(dragTo.contains("scope.launch"))
        assertTrue(pill.substringAfter("fun startDrag()").substringBefore("}").contains("if (held) return"))
    }

    @Test
    fun theSwellScalesOnlyTheSurface() {
        val bar = File(root(), "app/src/main/java/com/music/bitchord/ui/components/floatingtabbar/FloatingTabBar.kt").readText()
        val surface = bar.substringAfter("scaleY = 1f + 0.06f * swell").substringBefore("Modifier.padding(contentPadding)")
        assertFalse(surface.contains("Row("))
    }

    @Test
    fun theArchiveTuneHostNeverShowsItsGlassMasterSwitch() {
        val host = File(root(), "archivetuneplayer/src/main/kotlin/moe/rukamori/archivetune/bridge/ArchiveTuneSettingsHost.kt").readText()
        assertFalse(host.contains("LiquidGlassSettings("))
    }
}
