package com.music.bitchord

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseOriginCoverageTest {
    private val root: File = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun source(path: String): String = File(root, path).readText()

    private val releaseCovers = mapOf(
        "sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/screens/HomeScreen.kt" to 1,
        "sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/screens/SearchScreen.kt" to 1,
        "app/src/main/java/com/music/bitchord/ui/screens/DetailScreen.kt" to 2,
        "app/src/main/java/com/music/bitchord/ui/screens/LocalFolder.kt" to 1,
    )

    @Test
    fun everyReleaseCoverIsAZoomOrigin() {
        releaseCovers.forEach { (path, expected) ->
            val count = Regex("""\.releaseOrigin\(""").findAll(source(path)).count()
            assertTrue("$path tags $count release covers, expected at least $expected", count >= expected)
        }
    }

    @Test
    fun thePageAreaStillZooms() {
        val main = source("app/src/main/java/com/music/bitchord/MainActivity.kt")
        assertTrue(main.contains("iosPageTransform(pageMotion"))
        assertTrue(main.contains("IosPageFrame("))
        assertTrue(main.contains("pageMotion.onDestination("))
    }
}
