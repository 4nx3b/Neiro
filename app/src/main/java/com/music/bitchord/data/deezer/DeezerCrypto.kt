package com.music.bitchord.data.deezer

import java.net.URLDecoder
import java.net.URLEncoder

object DeezerCrypto {
    const val CHUNK_SIZE = 2048
    const val ENCRYPTED_CHUNK_STRIDE = 3
    const val SCHEME = "deezer"
    const val DEFAULT_KEY_SALT = "g4el58wc0zvf9na1"

    private const val STREAM_AUTHORITY = "$SCHEME://stream"
    private const val PARAM_URL = "u"
    private const val PARAM_KEY = "k"
    private const val PARAM_SALT = "s"

    private val CHUNK_IV = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)

    fun deriveKey(
        trackId: String,
        salt: String = DEFAULT_KEY_SALT,
    ): ByteArray {
        val effective = if (salt.length >= 16) salt else DEFAULT_KEY_SALT
        val md5Hex =
            java.security.MessageDigest
                .getInstance("MD5")
                .digest(trackId.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        return ByteArray(16) { i ->
            (md5Hex[i].code xor md5Hex[i + 16].code xor effective[i].code).toByte()
        }
    }

    fun isEncryptedChunk(chunkIndex: Long): Boolean = chunkIndex % ENCRYPTED_CHUNK_STRIDE == 0L

    fun decryptChunk(
        chunk: ByteArray,
        offset: Int,
        length: Int,
        key: ByteArray,
    ) {
        if (length < 8) return
        val decryptable = length - (length % 8)
        val cipher = javax.crypto.Cipher.getInstance("Blowfish/CBC/NoPadding")
        cipher.init(
            javax.crypto.Cipher.DECRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "Blowfish"),
            javax.crypto.spec.IvParameterSpec(CHUNK_IV),
        )
        cipher.doFinal(chunk, offset, decryptable, chunk, offset)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")

    fun buildUri(
        url: String,
        trackId: String,
        salt: String? = null,
    ): String = buildString {
        append(STREAM_AUTHORITY)
        append('?')
        append(PARAM_URL)
        append('=')
        append(encode(url))
        append('&')
        append(PARAM_KEY)
        append('=')
        append(encode(trackId))
        if (!salt.isNullOrBlank() && salt != DEFAULT_KEY_SALT) {
            append('&')
            append(PARAM_SALT)
            append('=')
            append(encode(salt))
        }
    }

    data class StreamRef(
        val url: String,
        val trackId: String,
        val salt: String,
    )

    fun parseUri(uri: String): StreamRef? {
        if (!uri.startsWith(STREAM_AUTHORITY, ignoreCase = true)) return null
        val params = uri.substringAfter('?', "")
            .split('&')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) {
                    null
                } else {
                    pair.take(index) to pair.substring(index + 1)
                }
            }
            .toMap()
        val url = params[PARAM_URL]?.takeIf { it.isNotBlank() }?.let(::decode) ?: return null
        val trackId = params[PARAM_KEY]?.takeIf { it.isNotBlank() }?.let(::decode) ?: return null
        val salt = params[PARAM_SALT]?.takeIf { it.isNotBlank() }?.let(::decode) ?: DEFAULT_KEY_SALT
        return StreamRef(url = url, trackId = trackId, salt = salt)
    }
}
