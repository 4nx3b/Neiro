package com.music.bitchord.playback

import androidx.media3.common.Player
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.listentogether.PartyTrack
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.data.together.TogetherEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Binds playback to a Music Together session, when there is one.
 *
 * The session machinery itself — the embedded LAN server, the online relay
 * host, the guest socket, the room-state broadcasts — lives in
 * [TogetherEngine], an object every surface can read. What stays here is the
 * half that cannot: the doors [PlaybackService] already calls, which exist
 * because a session has to survive the app being backgrounded and the screen
 * going off, and because only this service-side adapter can tell the player
 * the two directions apart.
 *
 * ## Telling the two directions apart
 *
 * Outbound is [onLocalIntent], reached only from the service's `SessionPlayer`
 * — the [androidx.media3.common.ForwardingPlayer] every external surface knocks
 * on — plus the automatic-advance listener, both of which mean "this device's
 * user did something". On a guest, that diff is turned into the room's
 * `ControlRequest` / `AddTrackRequest` messages. The host needs nothing here:
 * its player is the room's authority and [TogetherEngine] publishes it every
 * 500 ms regardless of who moved it.
 *
 * Inbound never passes through this class at all: the engine applies room
 * state to the raw player (the same "below the wrapper" rule as before), so
 * its own corrections never echo as user intents. What this class adds around
 * that is the two behaviours a socket cannot express — a guest whose audio
 * focus was stolen stops *following* until they ask to come back, and a guest
 * locked out of the controls gets a pause that stays local.
 *
 * One deliberate consequence, unchanged from the party era: a pause this app
 * did not ask for — audio focus lost to another app, a call — goes directly
 * to the player and is *not* published. One person taking a call does not stop
 * the music for everyone else.
 */
class PartySync(
    private val scope: CoroutineScope,
    /** Read fresh every time: the service swaps players at a crossfade. */
    private val player: () -> Player?,
) {

    private val jobs = mutableListOf<Job>()
    private var publishJob: Job? = null
    private var lastInSession = false

    /** A queue row started dragging in the UI. See [onLocalIntent]. */
    private var queueDragActive = false

    /** Whether a move landed while [queueDragActive] was true, awaiting [endQueueDrag]. */
    private var queueDragDirty = false

    fun start() {
        TogetherEngine.attachPlayer(player)
        jobs += scope.launch {
            TogetherEngine.sessionState
                .map { it.isConnectedToSession }
                .distinctUntilChanged()
                .collect { inSession ->
                    if (inSession && !lastInSession) {
                        onEnteredSession()
                    } else if (!inSession && lastInSession) {
                        onLeftSession()
                    }
                    lastInSession = inSession
                }
        }
    }

    fun stop() {
        jobs.forEach(Job::cancel)
        jobs.clear()
        publishJob?.cancel()
        TogetherEngine.detachPlayer()
    }

    /**
     * The user did something to playback on this device — or the queue moved on
     * by itself, which is the same thing as far as a room is concerned.
     *
     * Debounced, because one gesture is several calls, and publishing each
     * would put several control requests on the wire for one tap.
     */
    fun onLocalIntent() {
        if (!TogetherEngine.inSession()) return
        if (TogetherEngine.isApplyingRemote) return
        if (!TogetherEngine.isGuestControlSession() && !TogetherEngine.isGuestAddSession()) return
        TogetherEngine.clearFocusLost()
        if (queueDragActive) {
            queueDragDirty = true
            publishJob?.cancel()
            return
        }
        publishJob?.cancel()
        publishJob = scope.launch {
            delay(PUBLISH_DEBOUNCE_MS)
            TogetherEngine.publishGuestIntent()
            TogetherEngine.publishGuestQueueAdds()
        }
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
        if (playWhenReady) {
            TogetherEngine.clearFocusLost()
            return
        }
        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS &&
            TogetherEngine.inSession()
        ) {
            Log.i(TAG, "another app took the audio; dropping out of the session until asked back")
            TogetherEngine.markFocusLost()
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
        if (!TogetherEngine.controlsLockedForSelf()) return false
        val exo = player() ?: return false
        if (playing) {
            TogetherEngine.clearLocallyPaused()
            val room = TogetherEngine.roomState.value ?: return true
            if (!room.isPlaying) return true
            TogetherEngine.guestTargetPositionMs()
                ?.let(exo::seekTo)
            exo.play()
        } else {
            TogetherEngine.markLocallyPaused()
            exo.pause()
        }
        return true
    }

    /**
     * Whether a `play()` from this device should be held back for the room.
     *
     * True means the caller must *not* start the player: the engine sends the
     * guest's `Play` request to the host, the host applies it, and the next
     * room state starts this device with everybody else. If the host never
     * answers, the engine's fallback starts the player anyway — the press is
     * never simply swallowed.
     *
     * False whenever the room could not be asked anything — no session, a
     * locked room, a player that never stopped — in which case play behaves
     * exactly as it does outside this feature.
     */
    fun shouldDeferPlay(): Boolean {
        if (!TogetherEngine.isGuestControlSession()) return false
        val exo = player() ?: return false
        if (exo.playWhenReady) return false
        TogetherEngine.requestGuestPlay()
        return true
    }

    private fun onEnteredSession() {
        val exo = player() ?: return
        Log.i(TAG, "entered together session, stashing personal queue")
        PartyPersonalQueueStash.stashFromPlayer(exo)
    }

    private fun onLeftSession() {
        Log.i(TAG, "left together session, restoring personal queue if stashed")
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

        /**
         * Long enough to coalesce the burst one tap makes — choosing a track
         * is `setMediaItems`, `prepare`, `play` within a few milliseconds —
         * and no longer, because a resume is held until this has elapsed and
         * every millisecond here is silence after the button was pressed.
         */
        const val PUBLISH_DEBOUNCE_MS = 120L
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

internal data class QueueMoveDelta(
    val fromIndex: Int,
    val toIndex: Int,
    val videoId: String,
)

/**
 * Detects if [newList] is the result of moving exactly one item in [oldList].
 * If so, returns the from and to indices (offset by [baseOffset]) and the item's id.
 * Returns null if the lists cannot be explained by a single move.
 */
internal fun detectSingleMove(
    oldList: List<String>,
    newList: List<String>,
    baseOffset: Int = 0,
): QueueMoveDelta? {
    if (oldList.size != newList.size || oldList == newList || oldList.isEmpty()) return null
    if (oldList.groupingBy { it }.eachCount() != newList.groupingBy { it }.eachCount()) return null

    for (from in oldList.indices) {
        val item = oldList[from]
        val withoutItem = oldList.toMutableList().apply { removeAt(from) }
        for (to in oldList.indices) {
            if (from == to) continue
            val simulated = withoutItem.toMutableList().apply { add(to, item) }
            if (simulated == newList) {
                return QueueMoveDelta(
                    fromIndex = baseOffset + from,
                    toIndex = baseOffset + to,
                    videoId = item,
                )
            }
        }
    }
    return null
}
