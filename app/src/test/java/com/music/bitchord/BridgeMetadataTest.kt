package com.music.bitchord

import moe.rukamori.archivetune.bridge.BridgeMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeMetadataTest {
    @Test
    fun mapsIdTitleArtistsAndArtwork() {
        val m =
            BridgeMetadata.build(
                mediaId = "dQw4w9WgXcQ",
                title = "Never Gonna Give You Up",
                artist = "Rick Astley, Stock Aitken Waterman",
                artistId = "UCuAXFkgsw1L7xaCfnd5JJOw",
                artworkUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                albumTitle = "Whenever You Need Somebody",
                durationText = "3:33",
                isMusicVideo = true,
            )
        assertEquals("dQw4w9WgXcQ", m.id)
        assertEquals("Never Gonna Give You Up", m.title)
        assertEquals(listOf("Rick Astley", "Stock Aitken Waterman"), m.artists.map { it.name })
        assertEquals("UCuAXFkgsw1L7xaCfnd5JJOw", m.artists[0].id)
        assertNull(m.artists[1].id)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", m.thumbnailUrl)
        assertEquals("Whenever You Need Somebody", m.album?.title)
        assertEquals(213, m.duration)
        assertTrue(m.isMusicVideo)
    }

    @Test
    fun durationMsWinsOverTextAndUnknownIsMinusOne() {
        assertEquals(61, BridgeMetadata.build("a", "t", "x", durationMs = 61_500, durationText = "9:99").duration)
        assertEquals(-1, BridgeMetadata.build("a", "t", "x").duration)
        assertEquals(3723, BridgeMetadata.parseDuration("1:02:03"))
        assertNull(BridgeMetadata.parseDuration("n/a"))
    }

    @Test
    fun blankFieldsBecomeAbsent() {
        val m = BridgeMetadata.build("a", null, null, artworkUrl = "", albumTitle = " ")
        assertEquals("", m.title)
        assertTrue(m.artists.isEmpty())
        assertNull(m.thumbnailUrl)
        assertNull(m.album)
        assertFalse(m.isMusicVideo)
    }
}
