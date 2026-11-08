package com.music.bitchord

import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.StreamRequest
import com.music.bitchord.data.sources.TidalSource
import com.music.bitchord.data.tidal.TidalAccountStore
import com.music.bitchord.data.tidal.TidalService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64

/**
 * The Tidal port's own logic: the manifest decode that decides between a
 * direct URL and a written-out MPD, the tier the request is answered at, and
 * the token payload a sign-in lands on. The search/match half is
 * [SourceResolver][com.music.bitchord.data.sources.SourceResolver]'s, already
 * covered by [SourcesTest].
 */
class TidalSourceTest {

    @get:Rule
    val scratch = TemporaryFolder()

    @Before
    fun pointManifestsAtScratch() {
        TidalService.setManifestsDirForTest(scratch.root)
    }

    private fun b64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())

    // ---- Manifest decode ---------------------------------------------------

    @Test
    fun `json manifest decodes to its direct url`() {
        val decoded = TidalService.decodeManifest(
            manifestB64 = b64(
                """{"mimeType":"audio/mp4","codecs":"mp4a.40.2","bitrate":320000,""" +
                    """"sampleRate":44100,"bitDepth":16,"urls":["https://cdns.tidal.com/a.mp4"]}""",
            ),
            declaredMimeType = "application/vnd.tidal.bts",
            trackId = "77",
            quality = "HIGH",
            durationSec = 201,
        )
        assertEquals("https://cdns.tidal.com/a.mp4", decoded?.url)
        assertEquals("mp4", decoded?.codec)
        assertEquals(320, decoded?.kbps)
        assertEquals(44100, decoded?.sampleRateHz)
        assertEquals(16, decoded?.bitDepth)
    }

    @Test
    fun `flac json manifest keeps the lossless codec`() {
        val decoded = TidalService.decodeManifest(
            manifestB64 = b64(
                """{"mimeType":"audio/flac","codecs":"flac","sampleRate":44100,""" +
                    """"bitDepth":16,"urls":["https://cdns.tidal.com/a.flac"]}""",
            ),
            declaredMimeType = null,
            trackId = "77",
            quality = "LOSSLESS",
            durationSec = null,
        )
        assertEquals("flac", decoded?.codec)
        assertNull(decoded?.kbps)
        assertEquals("https://cdns.tidal.com/a.flac", decoded?.url)
    }

    @Test
    fun `dash manifest is written out as an mpd file`() {
        val mpd = """
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-live:2011">
              <Period>
                <AdaptationSet mimeType="audio" contentType="audio">
                  <Representation id="0" codecs="flac" mimeType="audio/mp4"
                      audioSamplingRate="192000" bitDepth="24" bandwidth="3841000">
                    <BaseURL>https://ts.tidal.com/ondemand/</BaseURL>
                    <SegmentTemplate initialization="${'$'}RepresentationID${'$'}/init.mp4"
                        media="${'$'}RepresentationID${'$'}/${'$'}Number${'$'}.m4s" startNumber="1">
                      <SegmentTimeline><S t="0" d="196608" r="42"/></SegmentTimeline>
                    </SegmentTemplate>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()
        val decoded = TidalService.decodeManifest(
            manifestB64 = b64(mpd),
            declaredMimeType = "application/dash+xml",
            trackId = "12345",
            quality = "HI_RES_LOSSLESS",
            durationSec = null,
        )
        assertTrue(decoded?.url?.startsWith("file:") == true)
        assertTrue(decoded?.url?.endsWith("12345-hi_res_lossless.mpd") == true)
        assertEquals("flac", decoded?.codec)
        assertEquals(192000, decoded?.sampleRateHz)
        assertEquals(24, decoded?.bitDepth)
        val written = java.io.File(java.net.URI(decoded!!.url).path)
        assertTrue(written.isFile)
        assertTrue(written.readText().contains("<MPD"))
    }

    @Test
    fun `dash manifest sanitizes raw ampersands`() {
        val mpd = "<MPD><BaseURL>https://ts.tidal.com/a?sig=1&user=2</BaseURL></MPD>"
        val decoded = TidalService.decodeManifest(
            manifestB64 = b64(mpd),
            declaredMimeType = null,
            trackId = "9",
            quality = "LOSSLESS",
            durationSec = null,
        )
        val written = java.io.File(java.net.URI(decoded!!.url).path)
        assertTrue(written.readText().contains("sig=1&amp;user=2"))
    }

    // ---- Tier selection ----------------------------------------------------

    private fun sourceWithPick(picked: String?): TidalSource =
        TidalSource(
            SourceConfig(
                kind = SourceKind.TIDAL,
                qualityOptions = TidalSource.QUALITY_OPTIONS,
                pickedQuality = picked,
            ),
        )

    @Test
    fun `a lossless request asks at the picked lossless tier`() {
        assertEquals(
            "HI_RES_LOSSLESS",
            sourceWithPick(TidalSource.QUALITY_HI_RES).tierFor(StreamRequest.Lossless),
        )
        assertEquals(
            "LOSSLESS",
            sourceWithPick(TidalSource.QUALITY_LOSSLESS).tierFor(StreamRequest.Lossless),
        )
    }

    @Test
    fun `a lossless pick of a lossy tier falls back to lossless`() {
        assertEquals(
            "LOSSLESS",
            sourceWithPick(TidalSource.QUALITY_HIGH).tierFor(StreamRequest.Lossless),
        )
    }

    @Test
    fun `no pick at all answers lossless`() {
        assertEquals("LOSSLESS", sourceWithPick(null).tierFor(StreamRequest.Lossless))
    }

    @Test
    fun `a best request asks for the best lossy tier`() {
        assertEquals("HIGH", sourceWithPick(TidalSource.QUALITY_HI_RES).tierFor(StreamRequest.Best))
        assertEquals("HIGH", sourceWithPick(null).tierFor(StreamRequest.Capped(128)))
    }

    // ---- Token payload -----------------------------------------------------

    @Test
    fun `token payload carries user and refresh token`() {
        val session = TidalAccountStore.fromTokenPayload(
            """{"access_token":"at","refresh_token":"rt","expires_in":604800,""" +
                """"user":{"userId":1234,"username":"listener","countryCode":"DE"}}""",
            flow = TidalAccountStore.FLOW_PKCE,
        )
        assertEquals("at", session?.accessToken)
        assertEquals("rt", session?.refreshToken)
        assertEquals(1234L, session?.userId)
        assertEquals("listener", session?.username)
        assertEquals("DE", session?.countryCode)
        assertEquals(TidalAccountStore.FLOW_PKCE, session?.flow)
        assertTrue((session?.expiresAtMillis ?: 0) > System.currentTimeMillis())
    }

    @Test
    fun `a payload without an access token is not a session`() {
        assertNull(TidalAccountStore.fromTokenPayload("""{"error":"invalid_grant"}""", "pkce"))
    }

    // ---- Identity ----------------------------------------------------------

    @Test
    fun `search rows key on the source's track key`() {
        val config = SourceConfig(id = "cfg-tidal", kind = SourceKind.TIDAL)
        val key = SourceRegistry.trackKey(config.id, "99123456")
        assertEquals("cfg-tidal" to "99123456", SourceRegistry.parseTrackKey(key))
    }

    // ---- The lossless download path ----------------------------------------

    private fun box(type: String, payload: ByteArray = ByteArray(0)): ByteArray {
        val out = ByteArray(8 + payload.size)
        val size = out.size
        for (i in 0 until 3) out[i] = ((size shr (8 * (3 - i))) and 0xFF).toByte()
        out[3] = (size and 0xFF).toByte()
        System.arraycopy(type.toByteArray(Charsets.ISO_8859_1), 0, out, 4, 4)
        System.arraycopy(payload, 0, out, 8, payload.size)
        return out
    }

    /**
     * A `file://` manifest — what the Tidal source hands a download — is read
     * off disk, its segment references resolved against the manifest's own
     * BaseURL, and the segments joined into one file. This is the whole
     * lossless-download path for the source, against a real HTTP server.
     */
    @Test
    fun `a local dash manifest assembles through its own segment urls`() {
        val server = MockWebServer()
        val init = box("ftyp") + box("moov")
        val first = box("styp") + box("sidx") + box("moof") + box("mdat", byteArrayOf(1, 1, 1))
        val second = box("styp") + box("sidx") + box("moof") + box("mdat", byteArrayOf(2, 2, 2))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when (request.path) {
                    "/segments/0.mp4" -> MockResponse().setBody(okio.Buffer().write(init))
                    "/segments/1.m4s" -> MockResponse().setBody(okio.Buffer().write(first))
                    "/segments/2.m4s" -> MockResponse().setBody(okio.Buffer().write(second))
                    else -> MockResponse().setResponseCode(404)
                }
        }
        server.start()
        try {
            val mpd = """
                <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static">
                  <Period><AdaptationSet contentType="audio">
                    <Representation id="FLAC,44100,16" codecs="flac" bandwidth="928340" audioSamplingRate="44100">
                      <BaseURL>${server.url("/segments/")}</BaseURL>
                      <SegmentTemplate timescale="44100" initialization="0.mp4"
                          media="${'$'}Number${'$'}.m4s" startNumber="1">
                        <SegmentTimeline><S d="88200" r="1"/></SegmentTimeline>
                      </SegmentTemplate>
                    </Representation>
                  </AdaptationSet></Period>
                </MPD>
            """.trimIndent()
            val manifestFile = scratch.newFile("track.mpd")
            manifestFile.writeText(mpd)

            val sink = java.io.ByteArrayOutputStream()
            var lastProgress: Pair<Long, Long>? = null
            runBlocking {
                com.music.bitchord.download.ManifestFile.write(
                    cacheDir = scratch.root,
                    url = manifestFile.toURI().toString(),
                    headers = emptyMap(),
                    dash = true,
                    extension = "m4a",
                    sink = sink,
                    onProgress = { done, total -> lastProgress = done to total },
                )
            }

            val expected = init + box("moof") + box("mdat", byteArrayOf(1, 1, 1)) +
                box("moof") + box("mdat", byteArrayOf(2, 2, 2))
            org.junit.Assert.assertArrayEquals(expected, sink.toByteArray())
            assertEquals(3L to 3L, lastProgress)
        } finally {
            server.shutdown()
        }
    }
}
