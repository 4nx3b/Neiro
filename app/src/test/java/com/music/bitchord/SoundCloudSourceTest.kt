package com.music.bitchord

import com.music.bitchord.data.soundcloud.SoundCloudService
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SoundCloudSource
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.StreamRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SoundCloud port's own logic: the client-id/app-version extraction, the
 * search payload decode with its SNIP and unstreamable filters, the
 * transcoding ladder each tier lands on, the preset bitrate fallbacks, the
 * OAuth token normalization the login sweep feeds, and an end-to-end resolve
 * over a MockWebServer that asserts the request shapes.
 */
class SoundCloudSourceTest {

    private fun transcoding(
        url: String,
        protocol: String,
        mime: String,
        preset: String,
        bitrate: Int? = null,
    ) = SoundCloudService.Transcoding(
        url = url,
        protocol = protocol,
        mimeType = mime,
        preset = preset,
        isHls = protocol == "hls",
        bitrate = bitrate,
        sampleRate = null,
    )

    // ---- Metadata scraping ---------------------------------------------

    @Test
    fun `client id is extracted from a page or bundle body`() {
        assertEquals(
            "aBcDeFgHiJkLmNoPqRsTuV",
            SoundCloudService.extractClientId("""<script>var c={client_id:"aBcDeFgHiJkLmNoPqRsTuV",x:1}</script>"""),
        )
        assertEquals(
            "0123456789abcdefghij",
            SoundCloudService.extractClientId("""config["clientId"]='0123456789abcdefghij'"""),
        )
        assertNull(SoundCloudService.extractClientId("no config here"))
        assertNull(SoundCloudService.extractClientId("""client_id:"tooShort""""))
    }

    @Test
    fun `app version is extracted as an eight digit stamp`() {
        assertEquals("20261101", SoundCloudService.extractAppVersion("""{app_version:"20261101"}"""))
        assertNull(SoundCloudService.extractAppVersion("""{app_version:"1.2.3"}"""))
    }

    // ---- Search decode ---------------------------------------------------

    @Test
    fun `search rows decode with artists duration and artwork`() {
        val payload = """
            {"collection":[
              {"kind":"track","id":"111","title":"Night Drive","streamable":true,"policy":"ALLOW",
               "permalink_url":"https://soundcloud.com/artist/night-drive",
               "full_duration":213000,
               "artwork_url":"https://i1.sndcdn.com/artworks-large.jpg",
               "user":{"username":"Artist One"},
               "publisher_metadata":{"artist":"Artist One feat. Artist Two"}},
              {"kind":"track","id":"112","title":"Snipped","streamable":true,"policy":"SNIP",
               "permalink_url":"https://soundcloud.com/x/snipped","user":{"username":"X"}},
              {"kind":"track","id":"113","title":"Not streamable","streamable":false,
               "permalink_url":"https://soundcloud.com/y/z","user":{"username":"Y"}},
              {"kind":"user","id":"114","username":"Not A Track"}
            ]}
        """.trimIndent()
        val rows = SoundCloudService.parseSearchRows(payload)
        assertEquals(1, rows.size)
        val track = rows.first()
        assertEquals("111", track.trackId)
        assertEquals("Night Drive", track.title)
        assertEquals("Artist One", track.artist)
        assertEquals(listOf("Artist One", "Artist Two"), track.artistNames)
        assertEquals(213L, track.durationSec)
        assertEquals("https://i1.sndcdn.com/artworks-t500x500.jpg", track.artworkUrl)
    }

    @Test
    fun `an unparseable payload answers no rows instead of throwing`() {
        assertTrue(SoundCloudService.parseSearchRows("not json").isEmpty())
        assertTrue(SoundCloudService.parseSearchRows("{}").isEmpty())
    }

    // ---- Transcoding ladder ----------------------------------------------

    @Test
    fun `aac 160 prefers the hls aac transcoding over progressive mp3`() {
        val picked = SoundCloudService.selectTranscoding(
            listOf(
                transcoding("https://t/progressive", "progressive", "audio/mpeg", "mp3_128", bitrate = 128_000),
                transcoding("https://t/hls", "hls", "audio/mp4", "aac_160", bitrate = 160_000),
            ),
            SoundCloudService.TIER_AAC_160,
        )
        assertEquals("https://t/hls", picked?.url)
        assertTrue(picked?.isHls == true)
    }

    @Test
    fun `mp3 128 prefers the progressive mp3 transcoding`() {
        val picked = SoundCloudService.selectTranscoding(
            listOf(
                transcoding("https://t/progressive", "progressive", "audio/mpeg", "mp3_128", bitrate = 128_000),
                transcoding("https://t/hls", "hls", "audio/mp4", "aac_160", bitrate = 160_000),
            ),
            SoundCloudService.TIER_MP3_128,
        )
        assertEquals("https://t/progressive", picked?.url)
        assertFalse(picked?.isHls == true)
    }

    @Test
    fun `aac 96 lands on the low rendition`() {
        val picked = SoundCloudService.selectTranscoding(
            listOf(
                transcoding("https://t/hls160", "hls", "audio/mp4", "aac_160", bitrate = 160_000),
                transcoding("https://t/hls96", "hls", "audio/mp4", "aac_96", bitrate = 96_000),
            ),
            SoundCloudService.TIER_AAC_96,
        )
        assertEquals("https://t/hls96", picked?.url)
    }

    @Test
    fun `an exact bitrate beats a mime mismatch and unknown bitrates fall back`() {
        val picked = SoundCloudService.selectTranscoding(
            listOf(
                transcoding("https://t/a", "hls", "audio/mp4", "unknown_preset"),
                transcoding("https://t/b", "hls", "audio/mp4", "aac_96", bitrate = 96_000),
            ),
            SoundCloudService.TIER_AAC_96,
        )
        assertEquals("https://t/b", picked?.url)
    }

    @Test
    fun `preset and mime drive the bitrate fallback`() {
        assertEquals(160_000, SoundCloudService.bitrateFromPreset("aac_160", "audio/mp4"))
        assertEquals(96_000, SoundCloudService.bitrateFromPreset("aac_96", "audio/mp4"))
        assertEquals(128_000, SoundCloudService.bitrateFromPreset(null, "audio/mpeg"))
        assertEquals(128_000, SoundCloudService.bitrateFromPreset("mp3_64", "audio/mpeg"))
        assertEquals(256_000, SoundCloudService.bitrateFromPreset("Opus 256", "audio/webm"))
        assertNull(SoundCloudService.bitrateFromPreset(null, "audio/ogg"))
    }

    @Test
    fun `the media mime names the codec`() {
        assertEquals("aac", SoundCloudService.codecOfMime("audio/mp4"))
        assertEquals("mp3", SoundCloudService.codecOfMime("audio/mpeg"))
    }

    // ---- Token normalization ----------------------------------------------

    @Test
    fun `token inputs normalize from raw values cookies and labelled pairs`() {
        val raw = "2-123456-789012345-abcdef"
        assertEquals(raw, SoundCloudService.normalizeAuthToken(raw))
        assertEquals(raw, SoundCloudService.normalizeAuthToken("OAuth $raw"))
        assertEquals(raw, SoundCloudService.normalizeAuthToken("Bearer $raw"))
        assertEquals(
            raw,
            SoundCloudService.normalizeAuthToken("other=1; oauth_token=$raw; theme=dark"),
        )
        assertEquals(
            raw,
            SoundCloudService.normalizeAuthToken("""{"oauth_token":"$raw"}"""),
        )
        assertNull(SoundCloudService.normalizeAuthToken(""))
        assertNull(SoundCloudService.normalizeAuthToken("short"))
        assertNull(SoundCloudService.normalizeAuthToken("has space in it apparently"))
        assertNull(SoundCloudService.normalizeAuthToken("""{"json":"nested value"}"""))
    }

    // ---- Request tier mapping ---------------------------------------------

    @Test
    fun `the request tier follows the pick and the connection cap`() {
        val source = SoundCloudSource(
            SourceConfig(
                kind = SourceKind.SOUNDCLOUD,
                qualityOptions = SoundCloudSource.QUALITY_OPTIONS,
            ),
        )
        assertEquals(SoundCloudService.TIER_AAC_160, source.tierFor(StreamRequest.Lossless))
        assertEquals(SoundCloudService.TIER_AAC_160, source.tierFor(StreamRequest.Best))
        assertEquals(SoundCloudService.TIER_AAC_160, source.tierFor(StreamRequest.Capped(maxKbps = 160)))
        assertEquals(SoundCloudService.TIER_MP3_128, source.tierFor(StreamRequest.Capped(maxKbps = 128)))
        assertEquals(SoundCloudService.TIER_AAC_96, source.tierFor(StreamRequest.Capped(maxKbps = 96)))

        val picked = SoundCloudSource(
            SourceConfig(
                kind = SourceKind.SOUNDCLOUD,
                qualityOptions = SoundCloudSource.QUALITY_OPTIONS,
                pickedQuality = SoundCloudService.TIER_MP3_128,
            ),
        )
        assertEquals(SoundCloudService.TIER_MP3_128, picked.tierFor(StreamRequest.Lossless))
        assertEquals(SoundCloudService.TIER_AAC_160, picked.tierFor(StreamRequest.Capped(maxKbps = 200)))
        assertEquals(SoundCloudService.TIER_MP3_128, picked.tierFor(StreamRequest.Capped(maxKbps = 140)))
    }

    // ---- End to end over a MockWebServer ---------------------------------

    @Test
    fun `resolveStream walks the track and transcoding endpoints`() {
        kotlinx.coroutines.runBlocking {
            val server = MockWebServer()
            server.start()
            SoundCloudService.apiBaseForTest = server.url("/api-v2").toString().trimEnd('/')
            SoundCloudService.clientIdForTest = "testClientId0000000000"
            SoundCloudService.appVersionForTest = "20261101"
            SoundCloudService.forgetCaches()
            val trackBody = """
                {"kind":"track","id":"42","title":"Night Drive",
                 "policy":"ALLOW","streamable":true,
                 "full_duration":213000,
                 "track_authorization":"auth-token-1",
                 "media":{"transcodings":[
                   {"url":"${server.url("/transcodings/progressive")}","preset":"mp3_128",
                    "format":{"protocol":"progressive","mime_type":"audio/mpeg"}},
                   {"url":"${server.url("/transcodings/hls")}","preset":"aac_160",
                    "format":{"protocol":"hls","mime_type":"audio/mp4"}}
                 ]}}
            """.trimIndent()
            val playlistBody = """{"url":"${server.url("/playlist.m3u8")}"}"""
            server.enqueue(MockResponse().setBody(trackBody))
            server.enqueue(MockResponse().setBody(playlistBody))
            try {
                val stream = SoundCloudService.resolveStream("42", SoundCloudService.TIER_AAC_160)
                assertNotNull(stream)
                assertTrue(stream!!.url.endsWith("/playlist.m3u8"))
                assertTrue(stream.isHls)
                assertEquals("aac", stream.codec)
                assertEquals(213, stream.durationSec)

                val trackRequest = server.takeRequest()
                assertTrue(trackRequest.path!!.startsWith("/api-v2/tracks/42"))
                assertTrue(trackRequest.path!!.contains("client_id=testClientId0000000000"))
                assertTrue(trackRequest.path!!.contains("app_version=20261101"))
                assertEquals("https://soundcloud.com/", trackRequest.getHeader("Referer"))

                val transcodingRequest = server.takeRequest()
                assertTrue(transcodingRequest.path!!.startsWith("/transcodings/hls"))
                assertTrue(transcodingRequest.path!!.contains("track_authorization=auth-token-1"))
                assertTrue(transcodingRequest.path!!.contains("client_id=testClientId0000000000"))
            } finally {
                SoundCloudService.apiBaseForTest = null
                SoundCloudService.clientIdForTest = null
                SoundCloudService.appVersionForTest = null
                SoundCloudService.forgetCaches()
                server.shutdown()
            }
        }
    }

    @Test
    fun `resolveStream failure is cached and not retried immediately`() {
        kotlinx.coroutines.runBlocking {
            val server = MockWebServer()
            server.start()
            SoundCloudService.apiBaseForTest = server.url("/api-v2").toString().trimEnd('/')
            SoundCloudService.clientIdForTest = "testClientId0000000000"
            SoundCloudService.appVersionForTest = null
            SoundCloudService.forgetCaches()
            server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
            try {
                assertNull(SoundCloudService.resolveStream("43", SoundCloudService.TIER_AAC_160))
                assertNull(SoundCloudService.resolveStream("43", SoundCloudService.TIER_AAC_160))
                assertEquals(1, server.requestCount)
            } finally {
                SoundCloudService.apiBaseForTest = null
                SoundCloudService.clientIdForTest = null
                SoundCloudService.forgetCaches()
                server.shutdown()
            }
        }
    }

    @Test
    fun `an oauth token rides the api requests as an authorization header`() {
        kotlinx.coroutines.runBlocking {
            val server = MockWebServer()
            server.start()
            SoundCloudService.apiBaseForTest = server.url("/api-v2").toString().trimEnd('/')
            SoundCloudService.clientIdForTest = "testClientId0000000000"
            SoundCloudService.appVersionForTest = "20261101"
            SoundCloudService.forgetCaches()
            server.enqueue(
                MockResponse().setBody(
                    """{"collection":[{"kind":"track","id":"7","title":"T",
                       "streamable":true,"policy":"ALLOW",
                       "permalink_url":"https://soundcloud.com/a/t",
                       "duration":120000,"user":{"username":"A"}}]}""",
                ),
            )
            try {
                val rows = SoundCloudService.searchTracks("query", token = "2-123456-789012345-abcdef")
                assertEquals(1, rows.size)
                val request = server.takeRequest()
                assertEquals("OAuth 2-123456-789012345-abcdef", request.getHeader("Authorization"))
                assertTrue(request.path!!.startsWith("/api-v2/search/tracks"))
            } finally {
                SoundCloudService.apiBaseForTest = null
                SoundCloudService.clientIdForTest = null
                SoundCloudService.appVersionForTest = null
                SoundCloudService.forgetCaches()
                server.shutdown()
            }
        }
    }
}
