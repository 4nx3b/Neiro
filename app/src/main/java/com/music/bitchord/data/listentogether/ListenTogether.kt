package com.music.bitchord.data.listentogether

import android.content.Context
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.together.TogetherClockSnapshot
import com.music.bitchord.data.together.TogetherEngine
import com.music.bitchord.data.together.TogetherRole
import com.music.bitchord.data.together.TogetherRoomState
import com.music.bitchord.data.together.TogetherSessionState
import com.music.bitchord.data.together.isConnectedToSession
import com.music.bitchord.data.together.sessionIdOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The compatibility facade over Music Together.
 *
 * The feature itself lives in [TogetherEngine][com.music.bitchord.data.together.TogetherEngine]
 * — the embedded LAN server, the online relay host, the guest socket and the
 * room-state protocol — and everything new speaks to that object directly.
 * What remains here is the [State] shape the rest of the app already reads:
 * the player surfaces, the playback service's AutoPlay election, the mini
 * player's party pill and the surfaces that gate local-only behaviour on
 * "in a party" all predate the port, and they keep working unchanged through
 * this mapping:
 *
 *  - [inParty] — this device is hosting or has joined a session.
 *  - [controlsLocked] — a guest in a room whose host disabled guest control.
 *  - [awaitingStart] — a join is still in flight.
 *  - [nickname] — the session display name.
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

    val state: StateFlow<State> =
        combine(
            combine(
                TogetherEngine.sessionState,
                TogetherEngine.roomState,
                TogetherEngine.autoplayTrackIds,
                TogetherEngine.clockSynced,
                TogetherEngine.clockSnapshot,
            ) { session, room, autoplayIds, clockSynced, clock ->
                SessionInputs(session, room, autoplayIds, clockSynced, clock)
            },
            combine(
                AppSettings.autoplay,
                AppSettings.togetherAllowGuestsControlPlayback,
            ) { autoplay, allowControl ->
                SettingInputs(autoplay, allowControl)
            },
        ) { inputs, settings ->
            buildState(inputs, settings)
        }.stateIn(scope, SharingStarted.Eagerly, State())

    private data class SessionInputs(
        val session: TogetherSessionState,
        val room: TogetherRoomState?,
        val autoplayIds: Set<String>,
        val clockSynced: Boolean,
        val clock: TogetherClockSnapshot?,
    )

    private data class SettingInputs(
        val autoplay: Boolean,
        val allowControl: Boolean,
    )

    private fun buildState(
        inputs: SessionInputs,
        settings: SettingInputs,
    ): State {
        val session = inputs.session
        val room = inputs.room
        val autoplayIds = inputs.autoplayIds
        val autoplay = settings.autoplay
        val allowControl = settings.allowControl
        val selfId =
            when (session) {
                is TogetherSessionState.Hosting,
                is TogetherSessionState.HostingOnline,
                -> TogetherEngine.HOST_PARTICIPANT_ID

                is TogetherSessionState.Joined -> session.selfParticipantId
                else -> null
            }
        val isHostRole =
            when (session) {
                is TogetherSessionState.Hosting,
                is TogetherSessionState.HostingOnline,
                -> true

                is TogetherSessionState.Joined -> session.role is TogetherRole.Host
                else -> false
            }
        val inSession = session.isConnectedToSession
        val code = session.sessionIdOrNull.takeIf { inSession || session is TogetherSessionState.Joined }
        val members =
            room?.participants?.map { participant ->
                PartyMember(
                    memberId = participant.id,
                    displayName = participant.name,
                    isHost = participant.isHost,
                    connected = participant.isConnected,
                )
            } ?: emptyList()
        val currentTrack = room?.queue?.getOrNull(room.currentIndex.coerceAtLeast(0))
        return State(
            code = code,
            you = members.firstOrNull { it.memberId == selfId },
            members = members,
            hostOnlyControl = !(room?.settings?.allowGuestsToControlPlayback ?: allowControl),
            playback = PartyPlayback(
                track = currentTrack?.toFacadeTrack(autoplayIds),
                queueLength = room?.queue?.size ?: 0,
                queueIndex = room?.currentIndex ?: -1,
                isPlaying = room?.isPlaying ?: false,
                positionMs = room?.positionMs ?: 0L,
                anchorMs = room?.sentAtElapsedRealtimeMs ?: 0L,
                effectivePositionMs = room?.positionMs ?: 0L,
                autoplayEnabled = isHostRole && autoplay,
            ),
            queue = PartyQueue(
                index = room?.currentIndex ?: -1,
                items = room?.queue?.map { it.toFacadeTrack(autoplayIds) } ?: emptyList(),
            ),
            connection =
                when {
                    session is TogetherSessionState.Joining ||
                        session is TogetherSessionState.JoiningOnline -> Connection.CONNECTING
                    inSession -> Connection.LIVE
                    else -> Connection.OFFLINE
                },
            clockSynced = inputs.clockSynced || isHostRole,
            roundTripMs = inputs.clock?.estimatedRttMs ?: 0,
            error = (session as? TogetherSessionState.Error)?.message,
        )
    }

    /** True while a join is still in flight; the transport draws this as "it is coming". */
    val awaitingStart: StateFlow<Boolean> =
        TogetherEngine.sessionState
            .map { it is TogetherSessionState.Joining || it is TogetherSessionState.JoiningOnline }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /** The session display name — what every other device in the room sees. */
    fun nickname(): String = AppSettings.togetherDisplayName.value

    fun setNickname(value: String) {
        AppSettings.setTogetherDisplayName(value)
    }

    /**
     * AutoPlay's own preference while connected. The room has no party-wide
     * setting any more: on the host this is exactly the local switch (and the
     * room broadcasts whatever it queues), and a guest never runs AutoPlay —
     * their queue is the room's.
     */
    fun setAutoplay(enabled: Boolean) {
        AppSettings.setAutoplay(enabled)
    }

    /**
     * Adds tracks to the session's queue on behalf of AutoPlay.
     *
     * On the host this is the local append the room then broadcasts; a guest
     * never reaches it (their AutoPlay is off through [State.playback]), but
     * the guest path exists anyway and asks the host the way a guest's own
     * adds do.
     */
    fun queueAdd(tracks: List<PartyTrack>, playNext: Boolean = false) {
        if (tracks.isEmpty()) return
        val togetherTracks =
            tracks.map { track ->
                com.music.bitchord.data.together.TogetherTrack(
                    id = track.videoId,
                    title = track.title,
                    artists = listOfNotNull(track.artist.takeIf { it.isNotBlank() }),
                    durationSec = track.durationMs?.let { (it / 1000L).toInt() } ?: -1,
                    thumbnailUrl = track.thumbnailUrl,
                )
            }
        if (TogetherEngine.isGuestAddSession()) {
            togetherTracks.forEach { track ->
                TogetherEngine.guestAddTrack(
                    track,
                    if (playNext) com.music.bitchord.data.together.AddTrackMode.PLAY_NEXT
                    else com.music.bitchord.data.together.AddTrackMode.ADD_TO_QUEUE,
                )
            }
        } else {
            TogetherEngine.addTracksToQueue(togetherTracks, autoplay = true)
        }
    }

    fun init(context: Context) {
        TogetherEngine.init(context)
        val legacy =
            context.applicationContext
                .getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .getString(LEGACY_KEY_NICKNAME, null)
                ?.trim()
        if (!legacy.isNullOrBlank() && AppSettings.togetherDisplayName.value.isBlank()) {
            AppSettings.setTogetherDisplayName(legacy)
        }
    }

    private fun com.music.bitchord.data.together.TogetherTrack.toFacadeTrack(
        autoplayIds: Set<String>,
    ): PartyTrack =
        PartyTrack(
            videoId = id,
            title = title,
            artist = artists.joinToString(", "),
            thumbnailUrl = thumbnailUrl,
            durationMs = durationSec.takeIf { it >= 0 }?.let { it * 1000L },
            fromAutoplay = id in autoplayIds,
        )

    private const val LEGACY_PREFS = "bitchord_listen_together"
    private const val LEGACY_KEY_NICKNAME = "party_nickname"
}
