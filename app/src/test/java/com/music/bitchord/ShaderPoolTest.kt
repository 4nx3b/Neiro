/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */
package com.music.bitchord

import com.kyant.backdrop.ShaderPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class ShaderPoolTest {

    private class Fake(val source: String)

    @Test
    fun acquireBuildsWhenIdleIsEmptyAndReusesReleased() {
        var built = 0
        val pool = ShaderPool(maxIdlePerSource = 8) { s: String -> built++; Fake(s) }
        val a = pool.acquire("lens")
        val b = pool.acquire("lens")
        assertNotSame(a, b)
        assertEquals(2, built)
        pool.release("lens", a)
        assertSame(a, pool.acquire("lens"))
        assertEquals(2, built)
    }

    @Test
    fun eachHolderOwnsItsInstance() {
        val pool = ShaderPool { s: String -> Fake(s) }
        pool.prewarm(listOf("lens"), perSource = 2)
        val a = pool.acquire("lens")
        val b = pool.acquire("lens")
        assertNotSame(a, b)
        assertEquals(0, pool.idleCount("lens"))
    }

    @Test
    fun releaseIsCappedAndIgnoresDuplicates() {
        val pool = ShaderPool(maxIdlePerSource = 2) { s: String -> Fake(s) }
        val a = Fake("x")
        pool.release("x", a)
        pool.release("x", a)
        assertEquals(1, pool.idleCount("x"))
        repeat(5) { pool.release("x", Fake("x")) }
        assertEquals(2, pool.idleCount("x"))
    }

    @Test
    fun prewarmTopsUpToTheTarget() {
        var built = 0
        val pool = ShaderPool { s: String -> built++; Fake(s) }
        pool.prewarm(listOf("a", "b"), perSource = 3)
        pool.prewarm(listOf("a", "b"), perSource = 3)
        assertEquals(6, built)
        assertEquals(3, pool.idleCount("a"))
    }
}
