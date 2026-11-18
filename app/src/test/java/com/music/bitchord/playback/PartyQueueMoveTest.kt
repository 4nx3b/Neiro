package com.music.bitchord.playback

import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The wire contracts the party surfaces still lean on. The single-move
 * detection these tests used to sit beside went with the old engine — the
 * ported protocol has one authority (the host), so a guest's reorder never
 * travels as a move.
 */
class PartyQueueMoveTest {

    @Test
    fun partyTrackKeepsAutoplaySectionMetadataOnTheWire() {
        val encoded = Json.encodeToString(PartyTrack.serializer(), PartyTrack("A", fromAutoplay = true))
        val decoded = Json.decodeFromString(PartyTrack.serializer(), encoded)

        assertEquals(true, decoded.fromAutoplay)
    }

    @Test
    fun testPartyPlaybackSerializationPreservesSeparateSequences() {
        val playback = com.music.bitchord.data.listentogether.PartyPlayback(
            seq = 10,
            queueSeq = 25,
            queueIndex = 2,
            isPlaying = true,
            positionMs = 5000L,
            anchorMs = 100000L,
        )
        val encoded = Json.encodeToString(com.music.bitchord.data.listentogether.PartyPlayback.serializer(), playback)
        val decoded = Json.decodeFromString(com.music.bitchord.data.listentogether.PartyPlayback.serializer(), encoded)

        assertEquals(10L, decoded.seq)
        assertEquals(25L, decoded.queueSeq)
        assertEquals(true, decoded.isPlaying)
        assertEquals(5000L, decoded.positionMs)
        assertEquals(100000L, decoded.anchorMs)
    }

    @Test
    fun testQueueOperationDoesNotAdvancePlaybackSeqContract() {
        val initialPlayback = com.music.bitchord.data.listentogether.PartyPlayback(
            seq = 5,
            queueSeq = 1,
            isPlaying = true,
            positionMs = 12000L,
            anchorMs = 50000L,
        )
        // A queue move arrives: queueSeq advances, playback seq must remain 5
        val updatedPlayback = initialPlayback.copy(
            queueSeq = initialPlayback.queueSeq + 1,
        )

        assertEquals(5L, updatedPlayback.seq)
        assertEquals(2L, updatedPlayback.queueSeq)
        // Playback anchor and position are untouched
        assertEquals(initialPlayback.anchorMs, updatedPlayback.anchorMs)
        assertEquals(initialPlayback.positionMs, updatedPlayback.positionMs)
    }

    @Test
    fun testPartyTrackToSongMapsTiersCorrectly() {
        val manualTrack = PartyTrack("manual", title = "Manual", fromAutoplay = false)
        val autoTrack = PartyTrack("auto", title = "Auto", fromAutoplay = true)

        val manualSong = manualTrack.toSong()
        val autoSong = autoTrack.toSong()

        assertEquals(QueueTier.USER_QUEUE, manualSong.queueTier)
        assertEquals(QueueTier.AUTOPLAY, autoSong.queueTier)
    }

    @Test
    fun testSongToPartyTrackMapsAutoplayFlagCorrectly() {
        val autoSong = Song(videoId = "1", title = "1", artist = "Artist 1", thumbnailUrl = null, queueTier = QueueTier.AUTOPLAY)
        val userSong = Song(videoId = "2", title = "2", artist = "Artist 2", thumbnailUrl = null, queueTier = QueueTier.USER_QUEUE)
        val contextSong = Song(videoId = "3", title = "3", artist = "Artist 3", thumbnailUrl = null, queueTier = QueueTier.CONTEXT)

        assertEquals(true, autoSong.toPartyTrack(1000L).fromAutoplay)
        assertEquals(false, userSong.toPartyTrack(1000L).fromAutoplay)
        assertEquals(false, contextSong.toPartyTrack(1000L).fromAutoplay)
    }
}
