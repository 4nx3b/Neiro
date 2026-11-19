/*
 * Neiro — a BitChord fork. GNU General Public License v3.0 or later.
 */
package com.kyant.backdrop

class ShaderPool<T : Any>(
    private val maxIdlePerSource: Int = 8,
    private val factory: (String) -> T,
) {

    private val idle = HashMap<String, ArrayDeque<T>>()

    fun acquire(source: String): T {
        val pooled = synchronized(idle) { idle[source]?.removeLastOrNull() }
        return pooled ?: factory(source)
    }

    fun release(source: String, shader: T) {
        synchronized(idle) {
            val queue = idle.getOrPut(source) { ArrayDeque() }
            if (queue.size < maxIdlePerSource && queue.none { it === shader }) queue.addLast(shader)
        }
    }

    fun prewarm(sources: List<String>, perSource: Int) {
        for (source in sources) {
            val missing = synchronized(idle) { perSource - (idle[source]?.size ?: 0) }
            repeat(missing.coerceAtLeast(0)) { release(source, factory(source)) }
        }
    }

    fun idleCount(source: String): Int = synchronized(idle) { idle[source]?.size ?: 0 }
}

object RuntimeShaderPool {

    internal val pool = ShaderPool { source: String -> RuntimeShader(source) }

    internal fun acquire(source: String): RuntimeShader = pool.acquire(source)

    internal fun release(source: String, shader: RuntimeShader) = pool.release(source, shader)
}

private val BuiltInShaderSources: List<String>
    get() = listOf(
        com.kyant.backdrop.internal.RoundedRectRefractionShaderString,
        com.kyant.backdrop.internal.RoundedRectRefractionWithDispersionShaderString,
        com.kyant.backdrop.internal.DefaultHighlightShaderString,
        com.kyant.backdrop.internal.AmbientHighlightShaderString,
    )

fun prewarmBackdropShaders(perSource: Int = 4) {
    if (!isRuntimeShaderSupported()) return
    RuntimeShaderPool.pool.prewarm(BuiltInShaderSources, perSource)
}
