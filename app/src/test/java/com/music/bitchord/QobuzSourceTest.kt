package com.music.bitchord

import com.music.bitchord.data.qobuz.QobuzAccountStore
import com.music.bitchord.data.qobuz.QobuzService
import com.music.bitchord.data.sources.QobuzSource
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.StreamRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Qobuz port's own logic: the request signature Qobuz's getFileUrl
 * demands, the payload decodes for search and stream answers, the account
 * standing a sign-in reads, and the tier mapping the request is answered at.
 */
class QobuzSourceTest {

    @Test
    fun `request signature is the documented md5 over the sorted parameters`() {
        val signature = QobuzService.requestSignature(
            trackId = "123456",
            formatId = 6,
            timestampSec = 1700000000L,
            appSecret = "abcdef0123456789abcdef0123456789",
        )
        val expected = md5Of(
            "trackgetFileUrlformat_id6intentstreamtrack_id1234561700000000" +
                "abcdef0123456789abcdef0123456789",
        )
        assertEquals(expected, signature)
        assertEquals(32, signature.length)
    }

    private fun md5Of(input: String): String =
        java.security.MessageDigest.getInstance("MD5")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    @Test
    fun `search rows parse the qobuz track shape`() {
        val rows = QobuzService.parseSearchRows(
            """
            {"tracks":{"items":[
              {"id":5966783,"title":"One More Time","duration":320,
               "performer":{"name":"Daft Punk"},
               "album":{"title":"Discovery","image":
                 {"large":"https://static.qobuz.com/x.jpg"},"artist":{"name":"Daft Punk"}}}
            ]}}
            """.trimIndent(),
        )
        assertEquals(1, rows.size)
        assertEquals("5966783", rows[0].trackId)
        assertEquals("One More Time", rows[0].title)
        assertEquals("Daft Punk", rows[0].artist)
        assertEquals("Discovery", rows[0].album)
        assertEquals(320L, rows[0].durationSec)
        assertEquals("https://static.qobuz.com/x.jpg", rows[0].thumbnailUrl)
    }

    @Test
    fun `search rows fall through the flat item shapes`() {
        val rows = QobuzService.parseSearchRows(
            """{"data":{"items":[{"id":"99","title":"Flat"}]}}""",
        )
        assertEquals(1, rows.size)
        assertEquals("99", rows[0].trackId)
        assertNull(rows[0].artist)
    }

    @Test
    fun `file url payload reads url sample and specs`() {
        val stream = QobuzService.parseFileUrlPayload(
            """
            {"url":"https://streaming.qobuz.com/500/somefile.flac",
             "mime_type":"audio/flac","sample":false,
             "sampling_rate":44100,"bit_depth":16}
            """.trimIndent(),
        )!!
        assertEquals("https://streaming.qobuz.com/500/somefile.flac", stream.url)
        assertEquals("flac", stream.codec)
        assertEquals(44100, stream.sampleRateHz)
        assertEquals(16, stream.bitDepth)
        assertFalse(stream.isPreview)
    }

    @Test
    fun `sample answers are flagged as previews`() {
        val stream = QobuzService.parseFileUrlPayload(
            """{"url":"https://streaming.qobuz.com/500/sample.flac","sample":true}""",
        )!!
        assertTrue(stream.isPreview)
    }

    @Test
    fun `payloads without a url are rejected`() {
        assertNull(QobuzService.parseFileUrlPayload("""{"mime_type":"audio/flac"}"""))
        assertNull(QobuzService.parseFileUrlPayload("not json"))
    }

    @Test
    fun `account standing reads the subscription markers`() {
        val premium = QobuzService.standingFromUserPayload(
            """{"id":1234,"credential":{"subscription":{"lossless_streaming":true}}}""",
        )!!
        assertTrue(premium.premium)

        val free = QobuzService.standingFromUserPayload(
            """{"id":1234,"credential":{"subscription":{"mp3_streaming":true}}}""",
        )!!
        assertFalse(free.premium)

        assertNull(QobuzService.standingFromUserPayload("""{"error":"wrong token"}"""))
    }

    @Test
    fun `a lossless request honours the picked tier and defaults to 16-bit flac`() {
        val source = QobuzSource(
            SourceConfig(
                kind = SourceKind.QOBUZ,
                qualityOptions = QobuzSource.QUALITY_OPTIONS,
            ),
        )
        assertEquals(QobuzService.FORMAT_ID_FLAC, source.formatIdFor(StreamRequest.Lossless))
        assertEquals(
            QobuzService.FORMAT_ID_MAX,
            QobuzSource(
                SourceConfig(
                    kind = SourceKind.QOBUZ,
                    qualityOptions = QobuzSource.QUALITY_OPTIONS,
                    pickedQuality = QobuzService.FORMAT_ID_MAX.toString(),
                ),
            ).formatIdFor(StreamRequest.Lossless),
        )
        assertEquals(
            QobuzService.FORMAT_ID_HI_RES,
            QobuzSource(
                SourceConfig(
                    kind = SourceKind.QOBUZ,
                    qualityOptions = QobuzSource.QUALITY_OPTIONS,
                    pickedQuality = QobuzService.FORMAT_ID_HI_RES.toString(),
                ),
            ).formatIdFor(StreamRequest.Lossless),
        )
        assertEquals(
            QobuzService.FORMAT_ID_FLAC,
            QobuzSource(
                SourceConfig(
                    kind = SourceKind.QOBUZ,
                    qualityOptions = QobuzSource.QUALITY_OPTIONS,
                    pickedQuality = QobuzService.FORMAT_ID_MP3_320.toString(),
                ),
            ).formatIdFor(StreamRequest.Lossless),
        )
        assertEquals(QobuzService.FORMAT_ID_MP3_320, source.formatIdFor(StreamRequest.Best))
        assertEquals(QobuzService.FORMAT_ID_MP3_320, source.formatIdFor(StreamRequest.Capped(maxKbps = 96)))
    }

    @Test
    fun `resolveStream signs the request and rejects a preview answer`() {
        kotlinx.coroutines.runBlocking {
            val server = okhttp3.mockwebserver.MockWebServer()
            server.start()
            QobuzService.apiBaseForTest = server.url("/api.json/0.2").toString().trimEnd('/')
            val previewBody = """{"url":"https://streaming.qobuz.com/500/sample.flac","sample":true}"""
            val fullBody = """
                {"url":"${server.url("/full.flac")}",
                 "mime_type":"audio/flac","sampling_rate":44100,"bit_depth":16}
            """.trimIndent()
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(previewBody))
            server.enqueue(okhttp3.mockwebserver.MockResponse().setBody(fullBody))
            try {
                val account = QobuzAccountStore.Account(
                    userAuthToken = "user-token",
                    appId = "app-id",
                    appSecret = "app-secret",
                    label = "test",
                    premium = true,
                )
                val preview = QobuzService.resolveStream(account, "42", QobuzService.FORMAT_ID_FLAC)
                assertNull(preview)

                val full = QobuzService.resolveStream(account, "43", QobuzService.FORMAT_ID_FLAC)
                assertEquals("flac", full?.codec)
                assertEquals(44100, full?.sampleRateHz)
                assertEquals(16, full?.bitDepth)

                val first = server.takeRequest()
                assertTrue(first.path!!.contains("/track/getFileUrl"))
                assertTrue(first.path!!.contains("request_sig="))
                assertTrue(first.path!!.contains("format_id=6"))
                assertTrue(first.path!!.contains("track_id=42"))
                assertEquals("app-id", first.getHeader("X-App-Id"))
                assertEquals("user-token", first.getHeader("X-User-Auth-Token"))
                val second = server.takeRequest()
                assertTrue(second.path!!.contains("track_id=43"))
            } finally {
                QobuzService.apiBaseForTest = null
                server.shutdown()
            }
        }
    }
}
