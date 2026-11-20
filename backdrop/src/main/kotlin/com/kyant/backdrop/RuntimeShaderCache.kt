package com.kyant.backdrop

import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, Pair<String, RuntimeShader>>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        runtimeShaders[key]?.let { return it.second }
        val shader = RuntimeShaderPool.acquire(string)
        runtimeShaders[key] = string to shader
        return shader
    }

    fun clear() {
        runtimeShaders.values.forEach { (source, shader) -> RuntimeShaderPool.release(source, shader) }
        runtimeShaders.clear()
    }
}
