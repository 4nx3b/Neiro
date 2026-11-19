/*
 * Neiro
 * GPL-3.0 License | Contributors: see git history
 */
package com.music.bitchord

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class GlasmioVersionGuardTest {

    private val repo: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private val buildFiles = listOf("app", "tv", "archivetuneplayer").map { File(repo, "$it/build.gradle.kts") }

    private val coordinate = Regex("""com\.github\.myself-tsuki\.Glasmio:[a-z-]+:(v[0-9][^"]*)""")

    private val crashing = setOf("v0.2.2", "v0.2.3", "v0.2.4")

    private fun fixedIn(): Set<String> =
        File(repo, "third_party/glasmio-patches/FIXED_IN").takeIf { it.isFile }
            ?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") }?.toSet()
            .orEmpty()

    @Test
    fun noModuleShipsAGlasmioTagWithTheCappedGlassCrash() {
        val allowed = setOf("v0.2.1") + fixedIn()
        val found = buildFiles.filter { it.isFile }.flatMap { file ->
            coordinate.findAll(file.readText()).map { file.parentFile.name to it.groupValues[1] }.toList()
        }
        assertTrue("no Glasmio fork coordinates found", found.isNotEmpty())
        found.forEach { (module, version) ->
            if (version in crashing) {
                fail("$module pins Glasmio $version, whose capped glass crashes RenderThread — see docs/glasmio-crash-2026-11-19.md")
            }
            if (version !in allowed) {
                fail("$module pins Glasmio $version; add it to third_party/glasmio-patches/FIXED_IN once that tag carries the fix patches")
            }
        }
    }

    @Test
    fun theFixPatchesAndTheirBaseAreCheckedIn() {
        val patches = File(repo, "third_party/glasmio-patches")
        assertTrue(File(patches, "UPSTREAM_SHA").readText().trim().matches(Regex("[0-9a-f]{40}")))
        assertTrue(File(patches, "fix").listFiles().orEmpty().count { it.name.endsWith(".patch") } >= 1)
    }
}
