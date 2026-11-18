package com.music.bitchord.playback

import androidx.media3.common.Player
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Binds playback to a Listen Together room, when there is one.
 *
 * The room machinery itself — the WebSocket client, the event handling, the
 * host's queue observation and heartbeat, applying the room's frames to the
 * player — lives in the ported engine
 * ([ListenTogetherManager][com.music.bitchord.data.listentogether.ListenTogetherManager]
 * behind the [ListenTogether] facade). What stays here is the half that cannot:
 * the doors [PlaybackService] already calls, which exist because a room has to
 * survive the app being backgrounded and the screen going off, and because only
 * this service-side adapter can tell the player the two directions apart.
 *
 * ## What the doors do now
 *
 * The ported engine has no guest control protocol: guests cannot broadcast
 * playback actions at all (their picks travel as suggestions, applied by the
 * host), so [onLocalIntent]'s old publish step is gone — what it still does is
 * the one thing a socket cannot express: a listener whose audio focus was
 * stolen stops *following* the room until their own next action asks to come
 * back (see [onPlayWhenReadyChanged] and the manager's follow suspension).
 *
 * Inbound never passes through this class at all: the engine applies room
 * state to the raw player (the same "below the wrapper" rule as before), so
 * its own corrections never echo as user intents.
 *
 * One deliberate consequence, unchanged from the party era: a pause this app
 * did not ask for — audio focus lost to another app, a call — goes directly
 * to the player and is *not* published. One person taking a call does not stop
 * the music for everyone else.
 *
 * ## The queue stash
 *
 * Joining a room replaces the player's queue with the room's, so the
 * listener's own is stashed first and restored on leaving —
 * [PartyPersonalQueueStash] — surviving process death in between.
 */
class PartySync(
    private val scope: CoroutineScope,
    /** Read fresh every time: the service swaps players at a crossfade. */
    private val player: () -> Player?,
) {

    private val jobs = mutableListOf<Job>()
    private var lastInRoom = false

    /** A queue row started dragging in the UI. See [onLocalIntent]. */
    private var queueDragActive = false

    /** Whether a move landed while [queueDragActive] was true, awaiting [endQueueDrag]. */
    private var queueDragDirty = false

    fun start() {
        // The engine holds the service's live player through this accessor —
        // a crossfade handoff swaps the instance underneath, and the manager
        // re-attaches its listener when that happens. Detached again by
        // [stop] when the service dies.
        ListenTogether.manager?.attachPlayer(player)
        jobs += scope.launch {
            ListenTogether.state
                .map { it.inParty }
                .distinctUntilChanged()
                .collect { inRoom ->
                    if (inRoom && !lastInRoom) {
                        onEnteredRoom()
                    } else if (!inRoom && lastInRoom) {
                        onLeftRoom()
                    }
                    lastInRoom = inRoom
                }
        }
    }

    fun stop() {
        jobs.forEach(Job::cancel)
        jobs.clear()
        // The service itself is going away: dropping the player through the
        // manager's own door rather than a bare detachPlayer keeps its
        // bookkeeping (heartbeat, queue observation) in step.
        ListenTogether.manager?.onMusicServiceDestroyed()
    }

    /**
     * The user did something to playback on this device.
     *
     * The engine cannot broadcast a guest's transport actions (the wire has no
     * guest control; their song picks travel as suggestions from the manager's
     * own listener), so what this door is left with is the one thing that must
     * happen on a local action: a listener who had dropped out of following the
     * room — audio focus stolen — asks to come back by acting again.
     *
     * Debounced across a queue-row drag, because a drag is many calls and the
     * ask belongs at the end of it.
     */
    fun onLocalIntent() {
        val manager = ListenTogether.manager ?: return
        if (!manager.isInRoom) return
        if (queueDragActive) {
            queueDragDirty = true
            return
        }
        manager.resumeFollow()
    }

    /** A queue row started dragging in the UI. See [onLocalIntent]. */
    fun beginQueueDrag() {
        queueDragActive = true
    }

    /** The row was dropped (or the drag cancelled). Flushes anything parked by [onLocalIntent]. */
    fun endQueueDrag() {
        queueDragActive = false
        if (queueDragDirty) {
            queueDragDirty = false
            onLocalIntent()
        }
    }

    /**
     * The player's `playWhenReady` moved, and why.
     *
     * The *why* is the whole reason this exists and is only available here:
     * [Player] has no getter for it, so a pause caused by another app taking
     * the audio is indistinguishable, a tick later, from any other pause. It
     * has to be caught as it happens. A guest silenced this way stops
     * following the room until their own next action asks to come back —
     * otherwise the engine would keep seeking them back into place and take
     * the audio straight off whatever the listener had just started.
     */
    fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        val manager = ListenTogether.manager ?: return
        if (playWhenReady) {
            // Resuming is as good as acting: the listener is asking to hear it.
            manager.resumeFollow()
            return
        }
        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS && manager.isInRoom) {
            Log.i(TAG, "another app took the audio; dropping out of the room until asked back")
            manager.suspendFollowForFocusLoss()
        }
    }

    /**
     * Play or pause for a guest whose room is locked to its host.
     *
     * Returns true when it has handled it, which it has whenever the room is
     * locked to somebody else: the press moves this device and nothing else,
     * publishes nothing, and is never deferred to a room that is not waiting on
     * this device for anything.
     *
     * Resuming rejoins wherever the room has *got to* rather than where this
     * listener left off — the radio model, and the only thing that makes sense
     * when the music never stopped for anybody else. That seek is the one this
     * device performs on its own behalf while locked.
     */
    fun onLockedTransport(playing: Boolean): Boolean {
        val party = ListenTogether.state.value
        if (!party.controlsLocked) return false
        val exo = player() ?: return false
        if (playing) {
            ListenTogether.manager?.resumeFollow()
            if (!party.playback.isPlaying) return true
            exo.seekTo(party.playback.effectivePositionMs)
            exo.play()
        } else {
            // Staying quiet is the listener's own business: a local pause that
            // the room must not see and nothing downstream should undo.
            exo.pause()
        }
        return true
    }

    /**
     * Whether a `play()` from this device should be held back for the room.
     *
     * The ported engine has no guest play request — the wire refuses guest
     * playback actions outright and the host's own play state is what the room
     * syncs to — so there is nothing to wait for and this always answers false:
     * play behaves exactly as it does outside this feature. A locked guest never
     * reaches here anyway; [onLockedTransport] handled their press first.
     */
    fun shouldDeferPlay(): Boolean = false

    private fun onEnteredRoom() {
        val exo = player() ?: return
        Log.i(TAG, "entered listen together room, stashing personal queue")
        PartyPersonalQueueStash.stashFromPlayer(exo)
    }

    private fun onLeftRoom() {
        Log.i(TAG, "left listen together room, restoring personal queue if stashed")
        val stashed = PartyPersonalQueueStash.load()
        if (stashed != null) {
            val exo = player() ?: return
            val items = stashed.songs.map { it.toMediaItem() }
            exo.setMediaItems(items, stashed.index, stashed.positionMs)
            exo.prepare()
            if (stashed.wasPlaying) {
                exo.play()
            } else {
                exo.pause()
            }
            PartyPersonalQueueStash.clear()
        }
    }

    private companion object {
        const val TAG = "PartySync"
    }
}

/**
 * What a room needs to know about a track: enough to name it and show it.
 *
 * Everything else about how this device is playing it — which source answered,
 * at what quality, from the network or from a download — stays here, which is
 * what lets two people in a room be on different sources and still be in the
 * same place in the same song.
 */
internal fun Song.toPartyTrack(playerDurationMs: Long): PartyTrack = PartyTrack(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    // The player's own figure when it has one, since it comes from the decoder;
    // otherwise what the row that queued the track claimed.
    durationMs = playerDurationMs.takeIf { it > 0L }
        ?: TrackMatcher.secondsOf(durationText)?.let { it * 1000L },
    fromAutoplay = fromAutoplay,
)

internal fun PartyTrack.toSong(): Song = Song(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    // Not cosmetic: this is what a cross-source match is made on, so a device
    // whose sources differ from the sender's needs it to find the same
    // recording. See [Song.matchQuery].
    durationText = durationMs?.let { ms ->
        val total = ms / 1000
        "%d:%02d".format(total / 60, total % 60)
    },
    queueTier = if (fromAutoplay) QueueTier.AUTOPLAY else QueueTier.USER_QUEUE,
)
