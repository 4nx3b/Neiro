package com.music.bitchord

import com.music.bitchord.data.deezer.DeezerCrypto
import com.music.bitchord.data.deezer.DeezerService
import com.music.bitchord.data.sources.DeezerSource
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.StreamRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Deezer port's own logic: the Blowfish stripe key and chunk schedule,
 * the deezer://stream URI round trip, the gateway and resolver payload
 * decodes, the tier ladder a FLAC-less account falls down, and the decrypting
 * download over a body striped the way the CDN stripes it.
 */
class DeezerSourceTest {

    private fun blowfishEncrypt(
        plain: ByteArray,
        key: ByteArray,
    ): ByteArray {
        val cipher = javax.crypto.Cipher.getInstance("Blowfish/CBC/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "Blowfish"),
            javax.crypto.spec.IvParameterSpec(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7)),
        )
        return cipher.doFinal(plain)
    }

    private fun stripedBody(trackId: String, plainChunks: List<ByteArray>): ByteArray {
        val key = DeezerCrypto.deriveKey(trackId)
        val out = ByteArray(plainChunks.sumOf { it.size })
        var at = 0
        plainChunks.forEachIndexed { index, chunk ->
            if (DeezerCrypto.isEncryptedChunk(index.toLong()) && chunk.size == DeezerCrypto.CHUNK_SIZE) {
                blowfishEncrypt(chunk.copyOf(), key).copyInto(out, at)
            } else {
                chunk.copyInto(out, at)
            }
            at += chunk.size
        }
        return out
    }

    // ---- Crypto --------------------------------------------------------

    @Test
    fun `key derivation is deterministic and track-bound`() {
        val a = DeezerCrypto.deriveKey("1234567")
        val b = DeezerCrypto.deriveKey("1234567")
        val c = DeezerCrypto.deriveKey("7654321")
        assertArrayEquals(a, b)
        assertFalse(a.contentEquals(c))
        assertEquals(16, a.size)
    }

    @Test
    fun `short salts fall back to the default`() {
        assertArrayEquals(
            DeezerCrypto.deriveKey("1234567"),
            DeezerCrypto.deriveKey("1234567", "tooshort"),
        )
    }

    @Test
    fun `every third chunk is encrypted starting at zero`() {
        assertTrue(DeezerCrypto.isEncryptedChunk(0))
        assertFalse(DeezerCrypto.isEncryptedChunk(1))
        assertFalse(DeezerCrypto.isEncryptedChunk(2))
        assertTrue(DeezerCrypto.isEncryptedChunk(3))
        assertTrue(DeezerCrypto.isEncryptedChunk(6))
        assertFalse(DeezerCrypto.isEncryptedChunk(7))
    }

    @Test
    fun `decrypt chunk reverses the stripe encryption in place`() {
        val key = DeezerCrypto.deriveKey("9988776")
        val plain = ByteArray(DeezerCrypto.CHUNK_SIZE) { (it % 251).toByte() }
        val encrypted = blowfishEncrypt(plain, key)
        assertFalse(encrypted.contentEquals(plain))

        DeezerCrypto.decryptChunk(encrypted, 0, encrypted.size, key)
        assertArrayEquals(plain, encrypted)
    }

    // ---- Stream URI ----------------------------------------------------

    @Test
    fun `stream uri round trips the url and track id`() {
        val uri = DeezerCrypto.buildUri(
            url = "https://cdns-proxy-3.dzcdn.net/mobile/1/a1b2c3.flac?token=x&exp=1",
            trackId = "1234567",
        )
        assertTrue(uri.startsWith("deezer://stream?"))
        val ref = DeezerCrypto.parseUri(uri)!!
        assertEquals("https://cdns-proxy-3.dzcdn.net/mobile/1/a1b2c3.flac?token=x&exp=1", ref.url)
        assertEquals("1234567", ref.trackId)
        assertEquals(DeezerCrypto.DEFAULT_KEY_SALT, ref.salt)
    }

    @Test
    fun `custom salt survives the round trip and default salt is omitted`() {
        val withSalt = DeezerCrypto.buildUri("https://x.example/a", "42", "custom-salt-16chars")
        assertTrue(DeezerCrypto.parseUri(withSalt)!!.salt == "custom-salt-16chars")
        val withoutSalt = DeezerCrypto.buildUri("https://x.example/a", "42")
        assertFalse(withoutSalt.contains("s="))
    }

    @Test
    fun `foreign schemes and missing params are rejected`() {
        assertNull(DeezerCrypto.parseUri("https://www.deezer.com/stream"))
        assertNull(DeezerCrypto.parseUri("deezer://stream?k=42"))
        assertNull(DeezerCrypto.parseUri("deezer://stream?u=https%3A%2F%2Fx.example%2Fa"))
    }

    // ---- Payload decodes -------------------------------------------------

    @Test
    fun `account info reads name and lossless from user data`() {
        val info = DeezerService.accountInfoFromUserData(
            """{"results":{"USER":{"USER_ID":77,"BLOG_NAME":"Ruka",
               "OPTIONS":{"license_token":"lt","web_lossless":true}}}}""",
        )!!
        assertEquals("Ruka", info.name)
        assertTrue(info.lossless)
    }

    @Test
    fun `account info rejects a logged out arl`() {
        assertNull(
            DeezerService.accountInfoFromUserData(
                """{"results":{"USER":{"USER_ID":0,"OPTIONS":{}}}}""",
            ),
        )
    }

    @Test
    fun `session reads api token and license token`() {
        val session = DeezerService.sessionFromUserData(
            """{"results":{"checkForm":"api-token","USER":{"USER_ID":5,
               "OPTIONS":{"license_token":"license","mobile_lossless":false}}}}""",
        )!!
        assertEquals("api-token", session.apiToken)
        assertEquals("license", session.licenseToken)
        assertFalse(session.lossless)
    }

    @Test
    fun `public search rows parse`() {
        val rows = DeezerService.parsePublicSearchRows(
            """{"data":[{"id":313074336,"title":"One More Time","duration":320,
               "isrc":"FRuma0100001","artist":{"name":"Daft Punk"},
               "album":{"title":"Discovery","cover_big":"https://e-cdns.com/x.jpg"}}]}""",
        )
        assertEquals(1, rows.size)
        assertEquals("313074336", rows[0].trackId)
        assertEquals("One More Time", rows[0].title)
        assertEquals("Daft Punk", rows[0].artist)
        assertEquals("Discovery", rows[0].album)
        assertEquals(320L, rows[0].durationSec)
        assertEquals("FRuma0100001", rows[0].isrc)
    }

    @Test
    fun `gateway search rows parse`() {
        val rows = DeezerService.parseGatewaySearchRows(
            """{"results":{"data":[{"SNG_ID":"313074336","SNG_TITLE":"One More Time",
               "ART_NAME":"Daft Punk","ALB_TITLE":"Discovery","DURATION":320,"ISRC":"FRuma0100001"}]}}""",
        )
        assertEquals(1, rows.size)
        assertEquals("313074336", rows[0].trackId)
        assertEquals("Daft Punk", rows[0].artist)
    }

    @Test
    fun `get_url response parses its first source`() {
        val media = DeezerService.parseGetUrlResponse(
            """{"data":[{"media":[{"format":"FLAC",
               "sources":[{"url":"https://e-cdns.example/a.flac"}]}]}]}""",
        )!!
        assertEquals("https://e-cdns.example/a.flac", media.url)
        assertEquals("FLAC", media.format)
    }

    @Test
    fun `anonymous resolver prefers the requested format and requires the stripe cipher`() {
        val payload = """
            {"data":[{"media":[
              {"format":"MP3_128","cipher":{"type":"NONE"},"sources":[{"url":"https://a/128.mp3"}]},
              {"format":"FLAC","cipher":{"type":"BF_CBC_STRIPE"},
               "exp": 1893456000, "filesize": 41000000,
               "sources":[{"url":"https://skip/unused"},{"url":"https://e-cdns.example/a.flac"}]}
            ]}]}
        """.trimIndent()
        val media = DeezerService.parseAnonymousResolverResponse(payload, "FLAC")!!
        assertEquals("https://e-cdns.example/a.flac", media.url)
        assertEquals("FLAC", media.format)
        assertEquals(41000000L, media.contentLength)
        assertEquals(1893456000000L, media.expiresAtMs)

        val unciphered = """
            {"data":[{"media":[{"format":"FLAC","cipher":{"type":"PLAIN"},
               "sources":[{"url":"https://x/a.flac"}]}]}]}
        """.trimIndent()
        assertNull(DeezerService.parseAnonymousResolverResponse(unciphered, "FLAC"))
    }

    @Test
    fun `resolver order puts the 128 endpoint first only for 128`() {
        assertTrue(
            DeezerService.anonymousResolverUrls("MP3_128").first().contains("poopo"),
        )
        assertFalse(
            DeezerService.anonymousResolverUrls("FLAC").any { it.contains("poopo") },
        )
    }

    // ---- Tiers ------------------------------------------------------------

    @Test
    fun `a flac-less account falls back to 320 and the ladder continues down`() {
        assertEquals(
            listOf("MP3_320", "MP3_128"),
            DeezerService.formatTiersFor("FLAC", lossless = false),
        )
        assertEquals(
            listOf("FLAC", "MP3_320", "MP3_128"),
            DeezerService.formatTiersFor("FLAC", lossless = true),
        )
        assertEquals(
            listOf("MP3_128"),
            DeezerService.formatTiersFor("MP3_128", lossless = true),
        )
    }

    @Test
    fun `a lossless request honours the picked tier and a lossy pick reads as no pick`() {
        val source = DeezerSource(
            SourceConfig(
                kind = SourceKind.DEEZER,
                qualityOptions = DeezerSource.QUALITY_OPTIONS,
                pickedQuality = "MP3_320",
            ),
        )
        assertEquals("FLAC", source.formatFor(StreamRequest.Lossless))
        assertEquals(
            "FLAC",
            DeezerSource(
                SourceConfig(
                    kind = SourceKind.DEEZER,
                    qualityOptions = DeezerSource.QUALITY_OPTIONS,
                    pickedQuality = "FLAC",
                ),
            ).formatFor(StreamRequest.Lossless),
        )
        assertEquals("MP3_320", source.formatFor(StreamRequest.Best))
        assertEquals("MP3_320", source.formatFor(StreamRequest.Capped(maxKbps = 320)))
        assertEquals("MP3_128", source.formatFor(StreamRequest.Capped(maxKbps = 96)))
    }

    @Test
    fun `stream shape carries the flac specs`() {
        val stream = DeezerService.anonymousStreamFromMedia(
            "313074336",
            DeezerService.MediaAnswer(
                url = "https://e-cdns.example/a.flac",
                format = "FLAC",
                contentLength = 41_000_000L,
                expiresAtMs = null,
            ),
        )
        assertEquals(
            "deezer://stream?u=https%3A%2F%2Fe-cdns.example%2Fa.flac&k=313074336",
            stream.uri,
        )
        assertEquals("flac", stream.codec)
        assertEquals(44_100, stream.sampleRateHz)
        assertEquals(16, stream.bitDepth)
        assertNull(stream.kbps)
    }

    // ---- Decrypting download ----------------------------------------------

    @Test
    fun `decrypt head reveals the flac magic on a striped body`() {
        val trackId = "1234567"
        val chunk0 = ByteArray(DeezerCrypto.CHUNK_SIZE) { 0 }
        chunk0[0] = 'f'.code.toByte()
        chunk0[1] = 'L'.code.toByte()
        chunk0[2] = 'a'.code.toByte()
        chunk0[3] = 'C'.code.toByte()
        val chunk1 = ByteArray(DeezerCrypto.CHUNK_SIZE) { 1 }
        val striped = stripedBody(trackId, listOf(chunk0, chunk1))

        val head = striped.copyOf(512)
        DeezerService.decryptHead(head, head.size, DeezerCrypto.deriveKey(trackId))
        assertEquals('f'.code.toByte(), head[0])
        assertEquals('L'.code.toByte(), head[1])
        assertEquals('a'.code.toByte(), head[2])
        assertEquals('C'.code.toByte(), head[3])
    }

    @Test
    fun `decrypt head only touches the striped chunks`() {
        val trackId = "7654321"
        val chunk0 = ByteArray(DeezerCrypto.CHUNK_SIZE) { 0 }
        val chunk1 = ByteArray(DeezerCrypto.CHUNK_SIZE) { (it % 7).toByte() }
        val striped = stripedBody(trackId, listOf(chunk0, chunk1))

        val head = striped.copyOf(DeezerCrypto.CHUNK_SIZE + 100)
        DeezerService.decryptHead(head, head.size, DeezerCrypto.deriveKey(trackId))
        assertArrayEquals(chunk1.copyOf(100), head.copyOfRange(DeezerCrypto.CHUNK_SIZE, head.size))
    }

    @Test
    fun `fetch decrypted walks a striped body chunk by chunk`() {
        val trackId = "555000111"
        val chunk0 = ByteArray(DeezerCrypto.CHUNK_SIZE) { (it % 3).toByte() }
        val chunk1 = ByteArray(DeezerCrypto.CHUNK_SIZE) { (it % 11).toByte() }
        val chunk2 = ByteArray(1024) { (it % 13).toByte() }
        val striped = stripedBody(trackId, listOf(chunk0, chunk1, chunk2))

        val server = okhttp3.mockwebserver.MockWebServer()
        server.enqueue(
            okhttp3.mockwebserver.MockResponse()
                .setBody(okio.Buffer().write(striped))
                .setHeader("Content-Length", striped.size.toString()),
        )
        server.start()

        val sink = java.io.ByteArrayOutputStream()
        val written = kotlinx.coroutines.runBlocking {
            DeezerService.fetchDecrypted(
                DeezerCrypto.StreamRef(
                    url = server.url("/audio.flac").toString(),
                    trackId = trackId,
                    salt = DeezerCrypto.DEFAULT_KEY_SALT,
                ),
                sink,
            ) { _, _ -> }
        }
        assertEquals(striped.size.toLong(), written)
        val expected = chunk0 + chunk1 + chunk2
        assertArrayEquals(expected, sink.toByteArray())
        server.shutdown()
    }
}
