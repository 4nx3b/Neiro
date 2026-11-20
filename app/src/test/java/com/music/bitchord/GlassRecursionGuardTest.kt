/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */
package com.music.bitchord

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassRecursionGuardTest {

    private fun source(path: String): String {
        val candidates = listOf(File(path), File("app", path))
        return candidates.first { it.exists() }.readText()
    }

    @Test
    fun recordedPagesNeverSampleTheBackdropThatRecordsThem() {
        val main = source("src/main/java/com/music/bitchord/MainActivity.kt")
        val recorder = main.indexOf("Modifier.layerBackdrop(appBackdrop)")
        val pageLambda = main.indexOf(") { destination ->", recorder)
        val guard = main.indexOf("LocalAppBackdrop provides emptyBackdrop()", pageLambda)
        assertTrue(recorder >= 0)
        assertTrue(pageLambda > recorder)
        assertTrue(guard > pageLambda)
        assertFalse(main.substring(pageLambda, guard).contains("pageContent"))
    }

    @Test
    fun inPageLiquidControlsDefaultToNoBackdrop() {
        listOf(
            "src/main/java/com/music/bitchord/ui/components/liquid/LiquidGlassSwitch.kt",
            "src/main/java/com/music/bitchord/ui/components/liquid/LiquidGlassSlider.kt",
        ).forEach { path ->
            val text = source(path)
            assertFalse(path, text.contains("backdrop: Backdrop = LocalAppBackdrop.current"))
            assertTrue(path, text.contains("backdrop: Backdrop = emptyBackdrop()"))
        }
    }

    @Test
    fun theNavBarIsDrawnOutsideTheRecordedPage() {
        val main = source("src/main/java/com/music/bitchord/MainActivity.kt")
        val guard = main.indexOf("LocalAppBackdrop provides emptyBackdrop()")
        val bar = main.indexOf("GlassNavBar(")
        assertTrue(guard >= 0)
        assertTrue(bar > guard)
        assertFalse(File("app/src/main/java/com/music/bitchord/ui/components/liquid/LiquidGlassTabBar.kt").exists())
        assertFalse(File("src/main/java/com/music/bitchord/ui/components/liquid/LiquidGlassTabBar.kt").exists())
    }
}
