package com.music.bitchord.data.listentogether

import android.content.Context
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The compatibility facade over Listen Together.
 *
 * The feature itself lives in the ported vivi engine —
 * [ListenTogetherManager] (the player bridge), [ListenTogetherClient] (the
 * room WebSocket) and the files around them — and every new surface speaks to
 * the manager directly. What remains here is the [State] shape the rest of the
 * app already reads: the player surfaces, the playback service's AutoPlay
 * election, the mini player's party pill and the surfaces that gate
 * local-only behaviour on "in a party" all predate the port, and they keep
 * working unchanged through this mapping:
 *
 *  - [inParty] — this device is hosting or has joined a room.
 *  - [controlsLocked] — a guest, which in this room model is everyone but the
 *    host: the vivi protocol has no guest-control switch, guests cannot
 *    broadcast playback actions at all (their picks travel as suggestions),
 *    so the room reads as host-only to the app.
 *  - [awaitingStart] — a connection is still being established.
 *  - [nickname] — the username the room sees.
 *
 * PORT-NOTE (Neiro): the reference engine keeps no AutoPlay tier on the wire,
 * so the host's own adds through [queueAdd] are flagged locally in
 * [autoplayTrackIds] — the same trick the old engine used to keep the
 * AutoPlay section boundary of the queue readable on the host.
 */
object ListenTogether {

    enum class Connection { OFFLINE, CONNECTING, LIVE }

    data class State(
        val code: String? = null,
        val you: PartyMember? = null,
        val members: List<PartyMember> = emptyList(),
        val maxMembers: Int = 5,
        /** @see controlsLocked */
        val hostOnlyControl: Boolean = false,
        val playback: PartyPlayback = PartyPlayback(),
        val queue: PartyQueue = PartyQueue(),
        val connection: Connection = Connection.OFFLINE,
        /** False until the first round trip; the playhead is a guess until then. */
        val clockSynced: Boolean = false,
        val roundTripMs: Long = 0,
        /** The last thing that went wrong, for the screen to show. */
        val error: String? = null,
    ) {
        val inParty: Boolean get() = code != null
        val isFull: Boolean get() = members.size >= maxMembers

        /**
         * Whether this device may not drive the music.
         *
         * True only for a guest in a room whose host has taken control of it —
         * the host is never locked out of their own room, and a device that is
         * not in one is not in this feature's business at all. The room's own
         * permission gate enforces the same rule, so this is what the app shows
         * rather than what makes it true.
         *
         * Freed automatically when the host hands control back or transfers
         * hosting to this device. Everything reading it has to follow.
         */
        val controlsLocked: Boolean
            get() = inParty && hostOnlyControl && you?.isHost != true
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The engine itself, once [init] has built it. Screens read this; null before startup finishes. */
    var manager: ListenTogetherManager? = null
        private set

    /**
     * The last error the room surface should show — a failed connect, a server
     * refusal or a rejected join. The engine's own event stream is the source;
     * this is the one-slot memory of it the facade shape promises.
     */
    private val lastError = MutableStateFlow<String?>(null)

    /**
     * Track ids this host queued through [queueAdd] — the wire's room queue
     * carries no AutoPlay tier, so the boundary is remembered here and stamped
     * onto the queue when the facade builds [State].
     */
    private val autoplayTrackIds = MutableStateFlow<Set<String>>(emptySet())

    private val facadeState = MutableStateFlow(State())

    /** @see manager */
    val client: ListenTogetherClient? get() = manager?.client()

    val state: StateFlow<State> = facadeState.asStateFlow()

    /** True while a connection is still being established; the transport draws this as "it is coming". */
    val awaitingStart: StateFlow<Boolean> =
        facadeState
            .map { it.connection == Connection.CONNECTING && !it.inParty }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /** The session display name — what every other device in the room sees. */
    fun nickname(): String = AppSettings.listenTogetherUsername.value

    fun setNickname(value: String) {
        AppSettings.setListenTogetherUsername(value)
    }

    /**
     * AutoPlay's own preference while connected. The room has no party-wide
     * setting: the host is the only supplier (the wire refuses guest playback
     * actions outright), so this is exactly the local switch on the host and a
     * guest never runs AutoPlay — their queue is the room's.
     */
    fun setAutoplay(enabled: Boolean) {
        AppSettings.setAutoplay(enabled)
    }

    /**
     * Adds tracks to the session's queue on behalf of AutoPlay.
     *
     * On the host this is the local append the queue observer then broadcasts;
     * a guest never reaches it (their AutoPlay is off through [State.playback]),
     * but the guest path exists anyway and offers the tracks to the host the
     * way a guest's own picks do — as suggestions.
     */
    fun queueAdd(tracks: List<PartyTrack>, playNext: Boolean = false) {
        if (tracks.isEmpty()) return
        val engine = manager ?: return
        val ids = tracks.map { it.videoId }.toSet()
        if (engine.isHost) {
            engine.hostQueueAdd(tracks.map { it.toTrackInfo() }, playNext)
            autoplayTrackIds.value = autoplayTrackIds.value + ids
        } else {
            tracks.forEach { track -> engine.suggestTrack(track.toTrackInfo()) }
        }
    }

    fun init(context: Context) {
        if (manager != null) return
        val appContext = context.applicationContext
        val engineClient = ListenTogetherClient(appContext)
        val engine = ListenTogetherManager(engineClient, appContext)
        manager = engine

        // The facade's own State is a projection of the engine's flows, pushed
        // rather than combined at read time: the object exists before the
        // engine does, and everything reading `state.value` before init gets
        // the blank party it always got.
        scope.launch {
            combine(
                engine.roomState,
                engine.connectionState,
                engine.userId,
                engine.role,
                AppSettings.autoplay,
                autoplayTrackIds,
                lastError,
            ) { room, connection, userId, role, autoplay, autoplayIds, error ->
                Inputs(room, connection, userId, role, autoplay, autoplayIds, error)
            }.collect { inputs -> facadeState.value = buildState(inputs) }
        }
        // The one-slot error memory: the last connection failure or refusal
        // stays until the room is healthy again.
        scope.launch {
            engine.events.collect { event ->
                when (event) {
                    is ListenTogetherEvent.ConnectionError -> lastError.value = event.error
                    is ListenTogetherEvent.ServerError -> lastError.value = event.message
                    is ListenTogetherEvent.JoinRejected -> lastError.value = event.reason
                    is ListenTogetherEvent.Connected,
                    is ListenTogetherEvent.RoomCreated,
                    is ListenTogetherEvent.JoinApproved,
                    is ListenTogetherEvent.Reconnected,
                    -> lastError.value = null
                    else -> Unit
                }
            }
        }
        // A fresh party means a fresh AutoPlay section; ids carried over from
        // the previous room would mark the new queue's copies as suggestions.
        scope.launch {
            engine.roomState.collect { room ->
                if (room == null) autoplayTrackIds.value = emptySet()
            }
        }
    }

    private data class Inputs(
        val room: RoomState?,
        val connection: ConnectionState,
        val userId: String?,
        val role: RoomRole,
        val autoplay: Boolean,
        val autoplayIds: Set<String>,
        val error: String?,
    )

    private fun buildState(inputs: Inputs): State {
        val room = inputs.room
        if (room == null) {
            return State(
                connection = when (inputs.connection) {
                    ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Connection.CONNECTING
                    ConnectionState.CONNECTED -> Connection.LIVE
                    else -> Connection.OFFLINE
                },
                error = inputs.error,
            )
        }

        val userId = inputs.userId
        val isHost = inputs.role == RoomRole.HOST || room.hostId == userId
        val members =
            room.users.map { user ->
                PartyMember(
                    memberId = user.userId,
                    userId = user.userId,
                    displayName = user.cleanUsername,
                    isHost = user.isHost,
                    connected = user.isConnected,
                )
            }
        val queueItems = room.queue.map { it.toFacadeTrack(inputs.autoplayIds) }
        val currentTrack = room.currentTrack
        val queueIndex =
            if (currentTrack == null) {
                -1
            } else {
                queueItems.indexOfFirst { it.videoId == currentTrack.id }
            }
        val now = System.currentTimeMillis()
        val effective =
            if (room.isPlaying) room.position + (now - room.lastUpdate).coerceAtLeast(0L) else room.position
        return State(
            code = room.roomCode,
            you = members.firstOrNull { it.memberId == userId },
            members = members,
            // The vivi room model has no guest-control switch: guests cannot
            // broadcast playback actions at all, so every room reads as
            // host-only and [State.controlsLocked] settles it for the app.
            hostOnlyControl = true,
            playback = PartyPlayback(
                track = currentTrack?.toFacadeTrack(inputs.autoplayIds),
                queueLength = queueItems.size,
                queueIndex = queueIndex,
                isPlaying = room.isPlaying,
                positionMs = room.position,
                // The server stamps its own clock when it sends the frame; this
                // is the wall-clock anchor the reader adds elapsed time to.
                anchorMs = room.lastUpdate,
                effectivePositionMs = effective,
                autoplayEnabled = isHost && inputs.autoplay,
            ),
            queue = PartyQueue(
                index = queueIndex,
                items = queueItems,
            ),
            connection = when (inputs.connection) {
                ConnectionState.CONNECTED -> Connection.LIVE
                ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Connection.CONNECTING
                else -> Connection.OFFLINE
            },
            // PORT-NOTE: the reference engine has no NTP-style offset exchange —
            // positions ride the server's wall clock, which every device shares
            // well enough; "synced" here means the room is live and its frames
            // have started arriving.
            clockSynced = inputs.connection == ConnectionState.CONNECTED,
            roundTripMs = 0,
            error = inputs.error,
        )
    }

    /** The wire's track as the facade's shared track, with the local AutoPlay tier stamped on. */
    private fun TrackInfo.toFacadeTrack(autoplayIds: Set<String>): PartyTrack =
        PartyTrack(
            videoId = id,
            title = title,
            artist = artist,
            thumbnailUrl = thumbnail,
            durationMs = duration.takeIf { it > 0L },
            fromAutoplay = id in autoplayIds,
        )

    /** The facade's shared track as the wire's track, for suggestions and host-side adds. */
    private fun PartyTrack.toTrackInfo(): TrackInfo =
        TrackInfo(
            id = videoId,
            title = title,
            artist = artist,
            duration = durationMs ?: 0L,
            thumbnail = thumbnailUrl,
        )
}
