/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Listen Together player bridge — ported from vivi-music (beta branch),
 * vivi-music's listentogether.ListenTogetherManager (GPL-3.0).
 *
 * PORT-NOTE (Neiro): retitled to com.music.bitchord.data.listentogether and
 * rebuilt against this app's playback shape — there is no PlayerConnection
 * here, so the manager holds a plain `() -> Player?` accessor to the
 * service's live player (attached by PartySync when the service's player
 * exists, detached when it dies) and follows player promotions by watching the
 * accessor's identity, since a crossfade handoff swaps the service's active
 * ExoPlayer under it. Remote tracks resolve through the app's own Song ->
 * MediaItem mapping instead of an innertube watch-queue fetch, host queue
 * sync reads the service player's media items, and the chat history persists
 * through AppSettings. Timber became the app's DebugLog; Hilt is gone (the
 * singleton is held by the facade). Everything else — the event handling,
 * the buffer protocol, the debouncing, the suggestion and chat machinery — is
 * the reference's own logic.
 */



package com.music.bitchord.data.listentogether

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toSong
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manager that bridges the Listen Together WebSocket client with the music player.
 * Handles syncing playback actions between connected users.
 */
class ListenTogetherManager(
    private val client: ListenTogetherClient,
    private val context: Context,
) {
    companion object {
        private const val TAG = "ListenTogetherManager"
        // Debounce threshold for playback syncs - prevents excessive seeking/pausing
        // Increased from 200ms to 1000ms to reduce choppy audio for guests
        private const val SYNC_DEBOUNCE_THRESHOLD_MS = 1000L
        // Position tolerance - only seek if difference exceeds this (prevents micro-adjustments)
        // Increased from 500ms to 2000ms to reduce unnecessary seeks that interrupt playback
        private const val POSITION_TOLERANCE_MS = 2000L
        // Large position tolerance - only seek during playback if difference exceeds this
        // This prevents interrupting active playback for small drifts
        private const val PLAYBACK_POSITION_TOLERANCE_MS = 3000L

        /** Typing indicators stay alive for this long after the last typing event. */
        private const val TYPING_TTL_MS = 4500L

        /** How often a typing client re-announces itself while composing. */
        private const val TYPING_THROTTLE_MS = 2500L

        /** Chat history persisted per local username (survives room switches). */
        private const val MAX_PERSISTED_CHAT_MESSAGES = 150

        /** How often the manager checks whether the service swapped its player. */
        private const val PLAYER_FOLLOW_POLL_MS = 1_000L

        /** How often a host re-reads its queue for SYNC_QUEUE (see the observation's PORT-NOTE). */
        private const val QUEUE_SYNC_POLL_MS = 2_000L
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        initialize()
        observePreferences()
    }

    /**
     * The service's live player, read fresh every time — the service swaps it
     * at a crossfade handoff, and a stale instance would pin a released player.
     * Attached by PartySync when the service's player exists; null before the
     * service comes up and after it dies.
     */
    private var playerAccessor: (() -> Player?)? = null
    private var playerFollowJob: Job? = null
    private var eventCollectorJob: Job? = null
    private var typingExpiryJob: Job? = null
    private var idleDisconnectCollectorJob: Job? = null
    private var roleCollectorJob: Job? = null
    private var queueObserverJob: Job? = null
    private var volumeObserverJob: Job? = null
    private var playerListenerRegistered = false

    private val syncHostVolumeEnabled = MutableStateFlow(false)
    private val smartResyncEnabled = MutableStateFlow(true)
    private var lastSyncedVolume: Float? = null

    private var lastRole: RoomRole = RoomRole.NONE

    // Whether we're currently syncing (to prevent feedback loops)
    @Volatile
    private var isSyncing = false

    // Track the last state we synced to avoid duplicate events
    private var lastSyncedIsPlaying: Boolean? = null
    private var lastSyncedTrackId: String? = null

    // Track last sync action time for debouncing (prevents excessive seeking/pausing)
    private var lastSyncActionTime: Long = 0L

    // Track ID being buffered
    private var bufferingTrackId: String? = null

    // Track active sync job to cancel it if a better update arrives
    private var activeSyncJob: Job? = null

    // Generation ID for track changes - incremented on each new track change
    // Used to prevent old coroutines from overwriting newer track loads
    private var currentTrackGeneration: Int = 0

    // Pending sync to apply after buffering completes for guest
    private var pendingSyncState: SyncStatePayload? = null

    // Track if a buffer-complete arrived before the pending sync was ready
    private var bufferCompleteReceivedForTrack: String? = null

    // Idle disconnect job to save resources
    private var idleDisconnectJob: Job? = null

    // PORT-NOTE (Neiro): the old engine suspended a guest's remote following when
    // another app stole the audio focus (see PartySync.onPlayWhenReadyChanged — the
    // only place a Player's "why" is observable). The ported reference engine
    // applies room state unconditionally, so the flag is restored here and gates
    // every inbound application path below.
    @Volatile
    private var followSuspended = false

    // Expose client state
    val connectionState = client.connectionState
    val roomState = client.roomState
    val role = client.role
    val userId = client.userId
    val pendingJoinRequests = client.pendingJoinRequests
    val bufferingUsers = client.bufferingUsers
    val logs = client.logs
    val events = client.events
    val blockedUsernames = client.blockedUsernames
    val pendingSuggestions = client.pendingSuggestions

    val isInRoom: Boolean get() = client.isInRoom
    val isHost: Boolean get() = client.isHost
    val hasPersistedSession: Boolean get() = client.hasPersistedSession

    // Chat state
    private val _chatMessages = MutableStateFlow<List<ChatMessagePayload>>(emptyList())
    val chatMessages = _chatMessages

    /** Room members currently typing, freshest last; expires via [TYPING_TTL_MS]. */
    private val _typingUsers = MutableStateFlow<List<TypingUser>>(emptyList())
    val typingUsers: kotlinx.coroutines.flow.StateFlow<List<TypingUser>> = _typingUsers

    private var chatPersistJob: Job? = null
    private val chatHistoryJson = Json { ignoreUnknownKeys = true }
    private var lastTypingSentAt = 0L

    private fun hasOtherRoomMembers(): Boolean {
        val myId = userId.value ?: return false
        return (roomState.value?.users?.count { it.userId != myId } ?: 0) > 0
    }

    private val _unreadMessageCount = MutableStateFlow(0)
    val unreadMessageCount: kotlinx.coroutines.flow.StateFlow<Int> = _unreadMessageCount

    fun markChatAsRead() {
        _unreadMessageCount.value = 0
        client.cancelChatNotification()
    }

    /** Tells the client whether the chat screen is on top (suppresses its chat notifications). */
    fun setChatScreenVisible(visible: Boolean) {
        client.setChatScreenVisible(visible)
    }

    // PORT-NOTE: vivi's PlayerConnection exposed play()/pause() wrappers that routed
    // through Cast and enforced Listen-Together guest blocking (bypassed with
    // allowInternalSync during syncs). Neiro's playback has no such wrapper — the
    // service player is driven directly — so the manager replicates vivi's
    // local-player branch verbatim.
    private fun Player.playForSync() {
        if (playbackState == Player.STATE_IDLE) {
            prepare()
        }
        playWhenReady = true
    }

    /** The service's live player right now, or null while the service has none. */
    private fun player(): Player? = playerAccessor?.invoke()

    /** The current item's song, as the app's own model — hosts broadcast from it. */
    private fun currentSong(): Song? = player()?.currentMediaItem?.toSong()

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            try {
                if (isSyncing || !isHost || !isInRoom) return

                val player = player() ?: return

                Log.d(TAG, "Play state changed: $playWhenReady (reason: $reason)")

                // ALWAYS ensure track is synced before play/pause
                val currentTrackId = player.currentMediaItem?.mediaId
                if (currentTrackId != null && currentTrackId != lastSyncedTrackId) {
                    Log.d(TAG, "[SYNC] Sending track change before play state: track = $currentTrackId")
                    currentSong()?.let { song ->
                        sendTrackChangeInternal(song)
                        lastSyncedTrackId = currentTrackId
                        // Reset play state since server resets IsPlaying on track change
                        lastSyncedIsPlaying = false
                    }
                    // ALWAYS send play state after track change if host is playing
                    // Server sets IsPlaying=false on track change, so we must send it
                    if (playWhenReady) {
                        Log.d(TAG, "[SYNC] Host is playing, sending PLAY after track change")
                        lastSyncedIsPlaying = true
                        val position = player.currentPosition
                        client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                    }
                    return
                }

                // Only send play/pause if track is already synced
                sendPlayState(playWhenReady, player)
            } catch (e: Exception) {
                Log.e(TAG, "Error in onPlayWhenReadyChanged")
            }
        }

        private fun sendPlayState(playWhenReady: Boolean, player: Player) {
            try {
                val position = player.currentPosition

                if (playWhenReady) {
                    Log.d(TAG, "Host sending PLAY at position $position")
                    client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                    lastSyncedIsPlaying = true
                } else if (!playWhenReady && (lastSyncedIsPlaying == true)) {
                    Log.d(TAG, "Host sending PAUSE at position $position")
                    client.sendPlaybackAction(PlaybackActions.PAUSE, position = position)
                    lastSyncedIsPlaying = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendPlayState")
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            try {
                if (isSyncing || !isInRoom) return
                if (mediaItem == null) return

                val player = player() ?: return

                val trackId = mediaItem.mediaId

                if (!isHost) {
                    // Guests cannot broadcast playback actions — their local track
                    // change travels to the room as a suggestion instead, so the host
                    // (auto-)approves it and everyone, including the guest, syncs to it.
                    suggestLocalTrackChange(trackId, player)
                    return
                }

                if (trackId == lastSyncedTrackId) return

                lastSyncedTrackId = trackId
                // Reset play state tracking since server resets IsPlaying on track change
                lastSyncedIsPlaying = false

                // Get metadata and send track change
                mediaItem.toSong().let { song ->
                    Log.d(TAG, "Host sending track change: ${song.title}")
                    sendTrackChange(song)

                    // ALWAYS send PLAY after track change if host is currently playing
                    // Server sets IsPlaying=false on track change, so we must re-send it
                    val isPlaying = player.playWhenReady
                    if (isPlaying) {
                        Log.d(TAG, "Host is playing during track change, sending PLAY")
                        lastSyncedIsPlaying = true
                        val position = player.currentPosition
                        client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onMediaItemTransition")
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            try {
                if (isSyncing || !isHost || !isInRoom) return

                // Only send seek if it was a user-initiated seek
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    Log.d(TAG, "Host sending SEEK to ${newPosition.positionMs}")
                    client.sendPlaybackAction(PlaybackActions.SEEK, position = newPosition.positionMs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onPositionDiscontinuity")
            }
        }
    }

    /**
     * Attach the playback service's live player for room sync. [accessor] reads
     * the service's current player every call — a crossfade handoff swaps the
     * instance underneath, and the manager re-attaches its listener when that
     * happens (see [followPlayerPromotions]). Called by PartySync when the
     * service's player comes up; [detachPlayer] when it goes away.
     */
    fun attachPlayer(accessor: () -> Player?) {
        Log.d(TAG, "attachPlayer, isInRoom: $isInRoom")

        try {
            removePlayerListener()
            playerAccessor = accessor
            followPlayerPromotions(accessor)

            // Add listener if in room
            if (isInRoom) {
                try {
                    player()?.addListener(playerListener)
                    playerListenerRegistered = true
                    Log.d(TAG, "Added player listener for room sync")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add player listener")
                    playerListenerRegistered = false
                }
            }

            // Start/stop queue observation based on role
            if (isInRoom && isHost) {
                startQueueSyncObservation()
                startHeartbeat()
                startVolumeSyncObservation()
            } else {
                stopQueueSyncObservation()
                stopHeartbeat()
                stopVolumeSyncObservation()
            }
            updateGuestMuteState()
        } catch (e: Exception) {
            Log.e(TAG, "Error in attachPlayer")
        }
    }

    /** Drops the player and its listener — the playback service is gone. */
    fun detachPlayer() {
        removePlayerListener()
        playerAccessor = null
        playerFollowJob?.cancel()
        playerFollowJob = null
        stopQueueSyncObservation()
        stopHeartbeat()
        stopVolumeSyncObservation()
    }

    /**
     * Suspends applying remote room state to this device's player — called when
     * another app took the audio focus, so the room keeps seeking this listener
     * back into a track whose audio they are no longer entitled to. Their own
     * next local action (see [resumeFollow]) asks to come back.
     */
    fun suspendFollowForFocusLoss() {
        if (followSuspended) return
        followSuspended = true
        Log.i(TAG, "audio focus lost; suspending room following until asked back")
    }

    /** Resumes applying remote room state, catching up to the room's current frame. */
    fun resumeFollow() {
        if (!followSuspended) return
        followSuspended = false
        Log.i(TAG, "resuming room following")
        // Catch up to wherever the room is right now rather than waiting for its
        // next event — the host may have moved on while the listener was away.
        if (isInRoom) requestSync()
    }

    private fun removePlayerListener() {
        if (playerListenerRegistered) {
            try {
                player()?.removeListener(playerListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing old player listener")
            }
            playerListenerRegistered = false
        }
    }

    private fun observePreferences() {
        // PORT-NOTE: vivi read these out of the DataStore; Neiro's AppSettings is
        // already a flow source, so the collectors attach straight to it.
        scope.launch {
            AppSettings.listenTogetherSyncHostVolume
                .distinctUntilChanged()
                .collect { enabled ->
                    syncHostVolumeEnabled.value = enabled
                }
        }
        // A separate coroutine: collect never returns, so a second collector after the first in
        // the same launch was never reached and the smart-resync switch did nothing.
        scope.launch {
            AppSettings.listenTogetherSmartResync
                .distinctUntilChanged()
                .collect { enabled ->
                    smartResyncEnabled.value = enabled
                }
        }
    }

    /**
    * Initialize event collection. Should be called once at app start.
     */
    fun initialize() {
        Log.d(TAG, "Initializing ListenTogetherManager")
        eventCollectorJob?.cancel()
        eventCollectorJob = scope.launch {
            client.events.collect { event ->
                try {
                    Log.d(TAG, "Received event: $event")
                    handleEvent(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling event: $event")
                }
            }
        }

        // Expire stale typing indicators.
        typingExpiryJob?.cancel()
        typingExpiryJob = scope.launch {
            while (true) {
                delay(1000)
                val now = System.currentTimeMillis()
                val fresh = _typingUsers.value.filter { it.expiresAt > now }
                if (fresh.size != _typingUsers.value.size) {
                    _typingUsers.value = fresh
                }
            }
        }

        // Idle Disconnect Timer (15 minutes) - saves resources if user connects but never joins
        idleDisconnectCollectorJob?.cancel()
        idleDisconnectCollectorJob = scope.launch {
            combine(connectionState, client.roomState) { cState, rState ->
                cState to rState
            }.collect { (cState, rState) ->
                idleDisconnectJob?.cancel()
                if (cState == ConnectionState.CONNECTED && rState == null) {
                    idleDisconnectJob = scope.launch {
                        delay(15 * 60 * 1000L) // 15 minutes
                        Log.w(TAG, "Idle disconnect timeout reached (15m without joining a room). Disconnecting...")
                        disconnect()
                    }
                }
            }
        }

        // Role change listener
        roleCollectorJob?.cancel()
        roleCollectorJob = scope.launch {
            role.collect { newRole ->
                try {
                    val previousRole = lastRole
                    lastRole = newRole

                    val wasHost = previousRole == RoomRole.HOST
                    if (newRole == RoomRole.HOST && !wasHost) {
                        if (player() != null) {
                            Log.d(TAG, "Role changed to HOST, starting sync services")
                            startQueueSyncObservation()
                            startHeartbeat()
                            startVolumeSyncObservation()
                            // Re-register listener if needed
                            if (!playerListenerRegistered) {
                                try {
                                    player()?.addListener(playerListener)
                                    playerListenerRegistered = true
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to add player listener on role change")
                                }
                            }
                        }
                    } else if (newRole != RoomRole.HOST && wasHost) {
                        Log.d(TAG, "Role changed from HOST, stopping sync services")
                        stopQueueSyncObservation()
                        stopHeartbeat()
                        stopVolumeSyncObservation()
                    }

                    // Any in-room role needs the player listener: hosts broadcast
                    // playback actions from it, guests turn their local song
                    // changes into room-wide suggestions from it. It is attached
                    // at join time too, but a host-transfer that lands the local
                    // user in the GUEST role re-establishes it here.
                    if (newRole != RoomRole.NONE && newRole != RoomRole.HOST && !playerListenerRegistered) {
                        try {
                            player()?.let { p ->
                                p.addListener(playerListener)
                                playerListenerRegistered = true
                                Log.d(TAG, "Added player listener as guest on role change")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to add player listener on role change")
                        }
                    }
                    updateGuestMuteState()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in role change handler")
                }
            }
        }
    }

    private fun handleEvent(event: ListenTogetherEvent) {
        when (event) {
            is ListenTogetherEvent.Connected -> {
                Log.d(TAG, "Connected to server with userId: ${event.userId}")
            }

            is ListenTogetherEvent.RoomCreated -> {
                Log.d(TAG, "Room created: ${event.roomCode}")
                try {
                    // Register player listener for host
                    val player = player()
                    if (player != null && !playerListenerRegistered) {
                        try {
                            player.addListener(playerListener)
                            playerListenerRegistered = true
                            Log.d(TAG, "Added player listener as host")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to add player listener on room create")
                        }
                    }
                    // Initialize sync state
                    lastSyncedIsPlaying = player?.playWhenReady
                    lastSyncedTrackId = player?.currentMediaItem?.mediaId

                    // If there's already a track loaded, send it to the server
                    player?.currentMediaItem?.toSong()?.let { song ->
                        Log.d(TAG, "Room created with existing track: ${song.title}")
                        // Send track change so server has the current track info
                        sendTrackChangeInternal(song)
                        // If host is already playing, immediately send PLAY with current position
                        val isPlaying = player.playWhenReady
                        if (isPlaying) {
                            lastSyncedIsPlaying = true
                            val position = player.currentPosition
                            Log.d(TAG, "Host already playing on room create, sending PLAY at $position")
                            client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                        }
                    }
                    startQueueSyncObservation()
                    startHeartbeat()
                    startVolumeSyncObservation()
                    broadcastCustomAvatar()
                    // Deliberately NO chat-history restore here: the host is
                    // alone in a freshly created room, and older chats must not
                    // show while the room has a single member. They restore once
                    // someone joins (UserJoined below).
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling RoomCreated event")
                }
            }

            is ListenTogetherEvent.JoinApproved -> {
                Log.d(TAG, "Join approved for room: ${event.roomCode}")

                // Guests need the player listener too: their local song changes
                // travel to the room as suggestions (suggestLocalTrackChange in
                // onMediaItemTransition), which requires observing transitions.
                // The listener is normally attached when the player accessor
                // is set — but that happens long before joining a room, so it
                // must be attached here, mirroring the host's RoomCreated path.
                runCatching {
                    if (!playerListenerRegistered) {
                        player()?.addListener(playerListener)
                        playerListenerRegistered = true
                        Log.d(TAG, "Added player listener as guest")
                    }
                }.onFailure {
                    Log.e(TAG, "Failed to add player listener on join")
                }

                saveMuteStateOnJoin()
                broadcastCustomAvatar()
                restorePersistedChatHistory(
                    otherMembers = event.state.users.filterNot { it.userId == userId.value },
                )

                applyPlaybackState(
                    currentTrack = event.state.currentTrack,
                    isPlaying = event.state.isPlaying,
                    position = event.state.position,
                    queue = event.state.queue
                    // bypassBuffer=false (default) for initial join buffer sync
                )
                applyHostVolumeIfNeeded(event.state.volume)
                updateGuestMuteState()
            }

            is ListenTogetherEvent.PlaybackSync -> {
                Log.d(TAG, "PlaybackSync received: ${event.action.action}")
                // Guests handle all sync actions. Host should also apply queue ops.
                val actionType = event.action.action
                val isQueueOp = actionType == PlaybackActions.QUEUE_ADD ||
                        actionType == PlaybackActions.QUEUE_REMOVE ||
                        actionType == PlaybackActions.QUEUE_CLEAR
                if (!isHost || isQueueOp) {
                    handlePlaybackSync(event.action)
                }
            }

            is ListenTogetherEvent.UserJoined -> {
                Log.d(TAG, "[SYNC] User joined: ${event.username}")
                // When a new user joins, host should send current track immediately

                // Re-share our custom avatar so the newcomer can see it too.
                broadcastCustomAvatar()

                // The newcomer's username is in roomState by the time the event
                // is emitted, so the restore below only brings back the older
                // chats with the people actually present — the newcomer's own
                // history with the local user included.
                restorePersistedChatHistory(
                    otherMembers = (roomState.value?.users ?: emptyList())
                        .filterNot { it.userId == userId.value },
                )

                if (isHost) {
                    try {
                        val player = player()
                        player?.currentMediaItem?.toSong()?.let { song ->
                            Log.d(TAG, "[SYNC] Sending current track to newly joined user: ${song.title}")
                            sendTrackChangeInternal(song)
                            // If host is currently playing, also send PLAY with current position so the guest jumps to the live position
                            if (player.playWhenReady) {
                                val pos = player.currentPosition
                                Log.d(TAG, "[SYNC] Host playing, sending PLAY at $pos for new joiner")
                                client.sendPlaybackAction(PlaybackActions.PLAY, position = pos)
                            }
                            // Don't send play state - let buffering complete first
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling UserJoined event")
                    }
                }
            }

            is ListenTogetherEvent.BufferWait -> {
                Log.d(TAG, "BufferWait: waiting for ${event.waitingFor.size} users")
            }

            is ListenTogetherEvent.BufferComplete -> {
                Log.d(TAG, "BufferComplete for track: ${event.trackId}")
                if (!isHost && bufferingTrackId == event.trackId) {
                    bufferCompleteReceivedForTrack = event.trackId
                    applyPendingSyncIfReady()
                }
            }

            is ListenTogetherEvent.SyncStateReceived -> {
                Log.d(TAG, "SyncStateReceived: playing=${event.state.isPlaying}, pos=${event.state.position}, track=${event.state.currentTrack?.id}")
                if (!isHost) {
                    handleSyncState(event.state)
                }
            }

            is ListenTogetherEvent.Kicked -> {
                Log.d(TAG, "Kicked from room: ${event.reason}")
                cleanup()
            }

            is ListenTogetherEvent.Disconnected -> {
                Log.d(TAG, "Disconnected from server")
                // Don't cleanup on disconnect - we might reconnect
                // cleanup() is called when leaving room intentionally or when kicked
            }

            is ListenTogetherEvent.Reconnecting -> {
                Log.d(TAG, "Reconnecting: attempt ${event.attempt}/${event.maxAttempts}")
            }

            is ListenTogetherEvent.Reconnected -> {
                Log.d(TAG, "Reconnected to room: ${event.roomCode}, isHost: ${event.isHost}")
                restorePersistedChatHistory(
                    otherMembers = event.state.users.filterNot { it.userId == userId.value },
                )
                try {
                    // Re-register player listener
                    val player = player()
                    if (player != null && !playerListenerRegistered) {
                        try {
                            player.addListener(playerListener)
                            playerListenerRegistered = true
                            Log.d(TAG, "Re-added player listener after reconnect")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to re-add player listener after reconnect")
                        }
                    }

                    // Sync state based on role
                    if (event.isHost) {
                        // Host: only send sync if necessary
                        lastSyncedIsPlaying = player?.playWhenReady
                        lastSyncedTrackId = player?.currentMediaItem?.mediaId

                        val currentSong = player?.currentMediaItem?.toSong()
                        if (currentSong != null) {
                            // Check if server already has the right track (from event.state)
                            val serverTrackId = event.state.currentTrack?.id
                            if (serverTrackId != currentSong.videoId) {
                                Log.d(TAG, "Reconnected as host, server track ($serverTrackId) differs from local (${currentSong.videoId}), syncing")
                                sendTrackChangeInternal(currentSong)
                            } else {
                                Log.d(TAG, "Reconnected as host, server already has current track $serverTrackId")
                            }

                            // Small delay before sending play state to let connection stabilize
                            scope.launch {
                                delay(500)
                                try {
                                    val currentPlayer = player()
                                    if (currentPlayer?.playWhenReady == true) {
                                        val pos = currentPlayer.currentPosition
                                        Log.d(TAG, "Reconnected host is playing, sending PLAY at $pos")
                                        client.sendPlaybackAction(PlaybackActions.PLAY, position = pos)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error sending play state after reconnect")
                                }
                            }
                        }
                    } else {
                        // Guest: ALWAYS sync to host's state after reconnection
                        Log.d(TAG, "Reconnected as guest, syncing to host's current state")
                        applyPlaybackState(
                            currentTrack = event.state.currentTrack,
                            isPlaying = event.state.isPlaying,
                            position = event.state.position,
                            queue = event.state.queue,
                            bypassBuffer = true  // Reconnect: bypass buffer protocol
                        )
                        applyHostVolumeIfNeeded(event.state.volume)

                        // Immediately request fresh sync after a short delay to catch live position
                        // but only if smart resync is enabled
                        scope.launch {
                            delay(1000)
                            if (isInRoom && !isHost && smartResyncEnabled.value) {
                                Log.d(TAG, "Requesting fresh sync after reconnect (Smart Resync)")
                                requestSync()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling Reconnected event")
                }
            }

            is ListenTogetherEvent.UserReconnected -> {
                Log.d(TAG, "User reconnected: ${event.username}")
                // No action needed - reconnected user already synced via reconnect state
            }

            is ListenTogetherEvent.UserDisconnected -> {
                Log.d(TAG, "User temporarily disconnected: ${event.username}")
                // User might reconnect, no action needed
            }

            is ListenTogetherEvent.HostChanged -> {
                Log.d(TAG, "Host changed: new host is ${event.newHostName} (${event.newHostId})")
                val wasHost = isHost
                val nowIsHost = event.newHostId == userId.value

                if (wasHost && !nowIsHost) {
                    // Lost host role
                    Log.d(TAG, "Local user lost host role")
                    stopQueueSyncObservation()
                    stopVolumeSyncObservation()
                    if (playerListenerRegistered) {
                        player()?.removeListener(playerListener)
                        playerListenerRegistered = false
                    }
                    // Restore guest mute state since we're now a guest
                    updateGuestMuteState()
                } else if (!wasHost && nowIsHost) {
                    // Gained host role
                    Log.d(TAG, "Local user gained host role")
                    updateGuestMuteState() // This will restore mute state since we're now host

                    // Register player listener
                    val player = player()
                    if (player != null && !playerListenerRegistered) {
                        try {
                            player.addListener(playerListener)
                            playerListenerRegistered = true
                            Log.d(TAG, "Added player listener as new host")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to add player listener on host transfer")
                        }
                    }

                    // Start the queue and volume sync observations now that we're host
                    startQueueSyncObservation()
                    startVolumeSyncObservation()

                    // Send current player state to guests
                    val song = player?.currentMediaItem?.toSong()
                    if (song != null && player != null) {
                        Log.d(TAG, "New host sending current track: ${song.title}")
                        sendTrackChangeInternal(song)

                        // If currently playing, send play state
                        if (player.playWhenReady) {
                            val position = player.currentPosition
                            Log.d(TAG, "New host is playing, sending PLAY at $position")
                            client.sendPlaybackAction(PlaybackActions.PLAY, position = position)
                        }
                    }
                }
            }

            is ListenTogetherEvent.JoinRequestReceived -> {
                Log.d(TAG, "Join request received from ${event.username}")
                // UI already handles this via pendingJoinRequests flow
            }

            is ListenTogetherEvent.LocalSuggestionApproved -> {
                applyApprovedSuggestion(event.payload.trackInfo, event.playImmediately)
            }

            is ListenTogetherEvent.SuggestionRejected -> {
                // Drop the one-shot dedup so the same track can be suggested again: the previous
                // attempt was explicitly turned down, not silently lost.
                lastSuggestedTrackId = null
            }

            is ListenTogetherEvent.ConnectionError -> {
                Log.e(TAG, "Connection error: ${event.error}")
                cleanup()
            }

            is ListenTogetherEvent.ChatMessageReceived -> {
                Log.d(TAG, "Chat message received from ${event.payload.username}")

                // The local user's own message that arrives while they are ALONE
                // in the room is a chat with themselves — flagged on the payload so
                // it never persists (the flag survives restore, unlike a volatile
                // key set, so restored solo messages can never re-enter the store).
                val payload =
                    if (event.payload.userId == userId.value && !hasOtherRoomMembers()) {
                        event.payload.copy(solo = true)
                    } else {
                        event.payload
                    }

                val exists = _chatMessages.value.any { it.timestamp == payload.timestamp && it.userId == payload.userId }
                if (!exists) {
                    _chatMessages.value = _chatMessages.value + payload
                    if (payload.userId != userId.value) {
                        _unreadMessageCount.value++
                    }
                    scheduleChatPersist()
                } else {
                    Log.w(TAG, "Ignoring duplicate chat message from ${payload.username}")
                }
            }

            is ListenTogetherEvent.ChatControlReceived -> {
                applyChatControl(event.userId, event.username, event.event)
            }

            else -> { /* Other events handled by UI */ }
        }
    }

    private fun cleanup() {
        if (lastRole == RoomRole.GUEST) {
            restoreGuestMuteState()
        }
        if (playerListenerRegistered) {
            player()?.removeListener(playerListener)
            playerListenerRegistered = false
        }
        stopQueueSyncObservation()
        stopHeartbeat()
        stopVolumeSyncObservation()
        // Note: Don't clear shouldBlockPlaybackChanges callback - it checks isInRoom dynamically
        // (PORT-NOTE: that callback lived on vivi's PlayerConnection; ArchiveTune has no
        // equivalent hook yet, so there is nothing to clear here.)
        lastSyncedIsPlaying = null
        lastSyncedTrackId = null
        lastSuggestedTrackId = null
        bufferingTrackId = null
        isSyncing = false
        bufferCompleteReceivedForTrack = null
        followSuspended = false
        lastRole = RoomRole.NONE
        lastSyncActionTime = 0L  // Reset sync debouncing
        ++currentTrackGeneration  // Increment to invalidate any pending track-change coroutines
        _chatMessages.value = emptyList() // Clear chat on room leave
        _unreadMessageCount.value = 0
        _typingUsers.value = emptyList()
    }

    // PORT-NOTE: vivi's PlayerConnection/MusicService carried a mute state
    // (isMuted/setMuted) that this manager saved on guest join and restored on
    // leave. ArchiveTune's player has no mute surface, and vivi's own
    // updateGuestMuteState had already degraded to "guests are never muted",
    // so these functions are retained as no-ops to keep the flow structure.

    private fun updateGuestMuteState() {
        // Guests are no longer forced to mute - they can hear the music too
        // (PORT-NOTE: vivi restored a saved mute state here; ArchiveTune's PlayerConnection
        // exposes no mute state, and vivi's own logic had already degraded to a no-op.)
        restoreGuestMuteState()
    }

    /**
     * Save the current mute state when joining a room as guest.
     * This allows us to restore it when leaving the room.
     */
    private fun saveMuteStateOnJoin() {
        // PORT-NOTE: no mute state on ArchiveTune's PlayerConnection - nothing to save.
    }

    /**
     * Restore the mute state that was saved when joining the room.
     * This is called when leaving the room to ensure the user's
     * mute preference is restored to what it was before joining Listen Together.
     */
    private fun restoreGuestMuteState() {
        // PORT-NOTE: no mute state on ArchiveTune's PlayerConnection - nothing to restore.
    }

    private fun applyHostVolumeIfNeeded(volume: Float?) {
        if (!syncHostVolumeEnabled.value || isHost || !isInRoom) return
        val player = player() ?: return
        val target = volume?.coerceIn(0f, 1f) ?: return
        // PORT-NOTE: vivi wrote the service's in-app volume flow; Neiro's player
        // has a plain volume attribute (a scaling factor on the output), so the
        // host's loudness lands as the guest's player volume — their device
        // volume is never touched.
        player.volume = target
    }

    private fun applyPendingSyncIfReady() {
        val pending = pendingSyncState ?: return
        val pendingTrackId = pending.currentTrack?.id ?: bufferingTrackId ?: return
        val completeForTrack = bufferCompleteReceivedForTrack

        if (completeForTrack != pendingTrackId) return

        if (followSuspended && !isHost) {
            Log.d(TAG, "Skipping pending sync - following suspended by focus loss")
            pendingSyncState = null
            bufferingTrackId = null
            bufferCompleteReceivedForTrack = null
            return
        }

        val player = player() ?: return

        Log.d(TAG, "Applying pending sync: track=$pendingTrackId, pos=${pending.position}, play=${pending.isPlaying}")
        isSyncing = true

        val targetPos = pending.position
        val posDiff = kotlin.math.abs(player.currentPosition - targetPos)
        val willPlay = pending.isPlaying

        // PORT-NOTE: vivi toggled connection.allowInternalSync around the seek/play/pause
        // to bypass its PlayerConnection guest gate. Neiro has no such gate.

        // Use appropriate tolerance based on whether we're about to play
        val tolerance = if (willPlay && player.playWhenReady) PLAYBACK_POSITION_TOLERANCE_MS else POSITION_TOLERANCE_MS

        if (posDiff > tolerance) {
            Log.d(TAG, "Applying pending sync: seeking ${player.currentPosition} -> $targetPos (diff ${posDiff}ms > ${tolerance}ms)")
            player.seekTo(targetPos)
        } else {
            Log.d(TAG, "Applying pending sync: skipping seek (diff ${posDiff}ms < ${tolerance}ms)")
        }

        // Apply play/pause state only if it needs to change
        if (willPlay && !player.playWhenReady) {
            Log.d(TAG, "Applying pending sync: starting playback")
            player.playForSync()
        } else if (!willPlay && player.playWhenReady) {
            Log.d(TAG, "Applying pending sync: pausing playback")
            player.pause()
        }

        scope.launch {
            delay(200)
            isSyncing = false
        }

        bufferingTrackId = null
        pendingSyncState = null
        bufferCompleteReceivedForTrack = null
    }

    private fun handlePlaybackSync(action: PlaybackActionPayload) {
        val player = player()
        if (player == null) {
            Log.w(TAG, "Cannot sync playback - no player")
            return
        }

        Log.d(TAG, "Handling playback sync: ${action.action}, position: ${action.position}")

        if (followSuspended && !isHost) {
            Log.d(TAG, "Skipping playback sync - following suspended by focus loss")
            return
        }

        isSyncing = true

        try {
            // PORT-NOTE: vivi set connection.allowInternalSync = true here to bypass its
            // PlayerConnection guest gate during remote syncs. Neiro has no such gate.
            when (action.action) {
                PlaybackActions.PLAY -> {
                    val basePos = action.position ?: 0L
                    val now = System.currentTimeMillis()
                    val adjustedPos = action.serverTime?.let { serverTime ->
                        basePos + kotlin.math.max(0L, now - serverTime)
                    } ?: basePos

                    Log.d(TAG, "Guest: PLAY at position $adjustedPos, currently playing=${player.playWhenReady}")

                    if (bufferingTrackId != null) {
                        pendingSyncState = (pendingSyncState ?: SyncStatePayload(
                            currentTrack = roomState.value?.currentTrack,
                            isPlaying = true,
                            position = adjustedPos,
                            lastUpdate = now
                        )).copy(
                            isPlaying = true,
                            position = adjustedPos,
                            lastUpdate = now
                        )
                        applyPendingSyncIfReady()
                        return
                    }

                    // Debounce PLAY actions when already playing and in sync
                    val posDiff = kotlin.math.abs(player.currentPosition - adjustedPos)
                    val alreadyPlaying = player.playWhenReady

                    if (alreadyPlaying && posDiff < POSITION_TOLERANCE_MS && (now - lastSyncActionTime) < SYNC_DEBOUNCE_THRESHOLD_MS) {
                        Log.d(TAG, "Guest: PLAY debounced - already playing and in sync (diff ${posDiff}ms)")
                        return
                    }

                    // CRITICAL: Only seek during active playback if position is VERY far off
                    // This prevents interrupting the audio for small drifts
                    if (alreadyPlaying) {
                        if (posDiff > PLAYBACK_POSITION_TOLERANCE_MS) {
                            Log.d(TAG, "Guest: PLAY seeking during playback ${player.currentPosition} -> $adjustedPos (diff ${posDiff}ms)")
                            player.seekTo(adjustedPos)
                        } else {
                            Log.d(TAG, "Guest: PLAY skipping seek - already playing, drift acceptable (${posDiff}ms < ${PLAYBACK_POSITION_TOLERANCE_MS}ms)")
                        }
                    } else {
                        // When paused/stopped, we can seek more aggressively
                        if (posDiff > POSITION_TOLERANCE_MS) {
                            Log.d(TAG, "Guest: PLAY seeking while paused ${player.currentPosition} -> $adjustedPos (diff ${posDiff}ms)")
                            player.seekTo(adjustedPos)
                        }
                        // Start playback
                        Log.d(TAG, "Guest: Starting playback")
                        player.playForSync()
                    }
                    lastSyncActionTime = now
                }

                PlaybackActions.PAUSE -> {
                    val pos = action.position ?: 0L
                    val now = System.currentTimeMillis()

                    Log.d(TAG, "Guest: PAUSE at position $pos, currently playing=${player.playWhenReady}")

                    if (bufferingTrackId != null) {
                        pendingSyncState = (pendingSyncState ?: SyncStatePayload(
                            currentTrack = roomState.value?.currentTrack,
                            isPlaying = false,
                            position = pos,
                            lastUpdate = now
                        )).copy(
                            isPlaying = false,
                            position = pos,
                            lastUpdate = now
                        )
                        applyPendingSyncIfReady()
                        return
                    }

                    // Debounce PAUSE actions when already paused and in sync
                    val posDiff = kotlin.math.abs(player.currentPosition - pos)
                    val alreadyPaused = !player.playWhenReady

                    if (alreadyPaused && posDiff < POSITION_TOLERANCE_MS && (now - lastSyncActionTime) < SYNC_DEBOUNCE_THRESHOLD_MS) {
                        Log.d(TAG, "Guest: PAUSE debounced - already paused and in sync (diff ${posDiff}ms)")
                        return
                    }

                    // Pause playback first
                    if (player.playWhenReady) {
                        Log.d(TAG, "Guest: Pausing playback")
                        player.pause()
                    }

                    // Only seek if position difference is significant
                    if (posDiff > POSITION_TOLERANCE_MS) {
                        Log.d(TAG, "Guest: PAUSE seeking ${player.currentPosition} -> $pos (diff ${posDiff}ms)")
                        player.seekTo(pos)
                    } else {
                        Log.d(TAG, "Guest: PAUSE skipping seek (diff ${posDiff}ms < ${POSITION_TOLERANCE_MS}ms)")
                    }
                    lastSyncActionTime = now
                }

                PlaybackActions.SEEK -> {
                    val pos = action.position ?: 0L
                    val now = System.currentTimeMillis()

                    // Debounce SEEK actions - don't seek if one just happened
                    if (now - lastSyncActionTime < SYNC_DEBOUNCE_THRESHOLD_MS) {
                        Log.d(TAG, "Guest: SEEK debounced (only ${now - lastSyncActionTime}ms since last sync)")
                        return
                    }

                    // Use larger position tolerance
                    if (kotlin.math.abs(player.currentPosition - pos) > POSITION_TOLERANCE_MS) {
                        Log.d(TAG, "Guest: SEEK to $pos from ${player.currentPosition} (diff > ${POSITION_TOLERANCE_MS}ms)")
                        player.seekTo(pos)
                        lastSyncActionTime = now
                    } else {
                        Log.d(TAG, "Guest: SEEK ignored (position diff < ${POSITION_TOLERANCE_MS}ms)")
                    }
                }

                PlaybackActions.CHANGE_TRACK -> {
                    action.trackInfo?.let { track ->
                        Log.d(TAG, "Guest: CHANGE_TRACK to ${track.title}, queue size=${action.queue?.size}")

                        // Reset sync debounce timer on track change - this is a fresh sync cycle
                        lastSyncActionTime = 0L
                        // The room moved on from whatever we last suggested, so the local one-shot
                        // dedup has to move on with it — otherwise picking the same song twice in a
                        // row never re-suggests it.
                        lastSuggestedTrackId = null

                        // If we have a queue, use it! This is the "smart" sync path.
                        if (action.queue != null && action.queue.isNotEmpty()) {
                            val queueTitle = action.queueTitle
                            applyPlaybackState(
                                currentTrack = track,
                                isPlaying = false, // Will be updated by subsequent PLAY or pending sync
                                position = 0,
                                queue = action.queue,
                                queueTitle = queueTitle
                            )
                        } else {
                            // Fallback to old behavior (network fetch) if no queue provided
                            bufferingTrackId = track.id
                            syncToTrack(track, false, 0)
                        }
                    }
                }

                PlaybackActions.SKIP_NEXT -> {
                    Log.d(TAG, "Guest: SKIP_NEXT")
                    player.seekToNextMediaItem()
                }

                PlaybackActions.SKIP_PREV -> {
                    Log.d(TAG, "Guest: SKIP_PREV")
                    player.seekToPreviousMediaItem()
                }

                PlaybackActions.QUEUE_ADD -> {
                    val track = action.trackInfo
                    if (track == null) {
                        Log.w(TAG, "QUEUE_ADD missing trackInfo")
                    } else {
                        Log.d(TAG, "Guest: QUEUE_ADD ${track.title}, insertNext=${action.insertNext == true}")
                        // PORT-NOTE: no watch-queue fetch here — the app's queue
                        // items carry the song's identity and resolve their stream
                        // when the player reaches them, so the wire's TrackInfo maps
                        // straight onto a Song and a MediaItem.
                        scope.launch(Dispatchers.Main) {
                            val playerNow = player() ?: return@launch
                            val mediaItem = track.toSong().toMediaItem()
                            if (action.insertNext == true) {
                                playerNow.addMediaItems(playerNow.currentMediaItemIndex + 1, listOf(mediaItem))
                            } else {
                                playerNow.addMediaItems(listOf(mediaItem))
                            }
                        }
                    }
                }

                PlaybackActions.QUEUE_REMOVE -> {
                    val removeId = action.trackId
                    if (removeId.isNullOrEmpty()) {
                        Log.w(TAG, "QUEUE_REMOVE missing trackId")
                    } else {
                        // Find first queue item with matching mediaId after current index
                        val startIndex = player.currentMediaItemIndex + 1
                        var removeIndex = -1
                        val total = player.mediaItemCount
                        for (i in startIndex until total) {
                            val id = player.getMediaItemAt(i).mediaId
                            if (id == removeId) { removeIndex = i; break }
                        }
                        if (removeIndex >= 0) {
                            Log.d(TAG, "Guest: QUEUE_REMOVE index=$removeIndex id=$removeId")
                            player.removeMediaItem(removeIndex)
                        } else {
                            Log.w(TAG, "QUEUE_REMOVE id not found in queue: $removeId")
                        }
                    }
                }

                PlaybackActions.QUEUE_CLEAR -> {
                    val currentIndex = player.currentMediaItemIndex
                    val count = player.mediaItemCount
                    val itemsAfter = count - (currentIndex + 1)
                    if (itemsAfter > 0) {
                        Log.d(TAG, "Guest: QUEUE_CLEAR removing $itemsAfter items after current")
                        player.removeMediaItems(currentIndex + 1, count - (currentIndex + 1))
                    }
                }

                PlaybackActions.SET_VOLUME -> {
                    applyHostVolumeIfNeeded(action.volume)
                }

                PlaybackActions.SYNC_QUEUE -> {
                    val queue = action.queue
                    if (queue != null) {
                        Log.d(TAG, "Guest: SYNC_QUEUE size=${queue.size}")
                        // Cancel any pending "smart" sync in favour of this authoritative queue
                        activeSyncJob?.cancel()

                        scope.launch(Dispatchers.Main) {
                            val playerNow = player() ?: return@launch

                            // Map TrackInfo to MediaItems
                            val mediaItems = queue.map { track ->
                                track.toSong().toMediaItem()
                            }

                            // Try to find current track in new queue to preserve playback state
                            val currentId = playerNow.currentMediaItem?.mediaId
                            var newIndex = -1
                            if (currentId != null) {
                                newIndex = mediaItems.indexOfFirst { it.mediaId == currentId }
                            }

                            val currentPos = playerNow.currentPosition
                            val wasPlaying = playerNow.isPlaying

                            if (newIndex != -1) {
                                playerNow.setMediaItems(mediaItems, newIndex, currentPos)
                            } else {
                                playerNow.setMediaItems(mediaItems)
                            }

                            // Restore playing state if needed
                            if (wasPlaying && !playerNow.isPlaying) {
                                playerNow.playForSync()
                            }

                            // PORT-NOTE: no queue-title surface to write here — the
                            // app draws "Playing from" from its own queue metadata.
                        }
                    }
                }
            }
        } finally {
            // PORT-NOTE: vivi reset connection.allowInternalSync here; no such flag exists
            // on Neiro's player.
            // Minimal delay to prevent feedback loops
            scope.launch {
                delay(200)
                isSyncing = false
            }
        }
    }

    private fun handleSyncState(state: SyncStatePayload) {
        val now = System.currentTimeMillis()
        val adjustedPos = if (state.isPlaying) {
            state.position + kotlin.math.max(0L, now - state.lastUpdate)
        } else {
            state.position
        }

        Log.d(TAG, "handleSyncState: playing=${state.isPlaying}, pos=${state.position} -> adj=$adjustedPos, track=${state.currentTrack?.id}")

        applyPlaybackState(
            currentTrack = state.currentTrack,
            isPlaying = state.isPlaying,
            position = adjustedPos,
            queue = state.queue,
            bypassBuffer = true  // Manual sync: bypass buffer
        )
        applyHostVolumeIfNeeded(state.volume)
    }

    private fun applyPlaybackState(
        currentTrack: TrackInfo?,
        isPlaying: Boolean,
        position: Long,
        queue: List<TrackInfo>?,
        queueTitle: String? = null,  // Kept for signature parity; unused (PORT-NOTE above)
        bypassBuffer: Boolean = false
    ) {
        if (followSuspended && !isHost) {
            Log.d(TAG, "Skipping playback state - following suspended by focus loss")
            return
        }
        val player = player()
        if (player == null) {
            Log.w(TAG, "Cannot apply playback state - no player")
            return
        }

        Log.d(TAG, "Applying playback state: track=${currentTrack?.id}, pos=$position, queue=${queue?.size}, bypassBuffer=$bypassBuffer")

        // Cancel any pending sync job
        activeSyncJob?.cancel()

        // If no track, just pause and clear/set queue
        if (currentTrack == null) {
            Log.d(TAG, "No track in state, pausing")
            val generation = ++currentTrackGeneration
            scope.launch(Dispatchers.Main) {
                // Verify we're still on the same track generation (no newer track change arrived)
                if (currentTrackGeneration != generation) {
                    Log.d(TAG, "Skipping stale track generation: $generation vs current $currentTrackGeneration")
                    return@launch
                }

                val playerNow = player() ?: return@launch
                isSyncing = true
                if (queue != null && queue.isNotEmpty()) {
                    val mediaItems = queue.map { it.toSong().toMediaItem() }
                    playerNow.setMediaItems(mediaItems)
                } else if (queue != null) {
                    playerNow.clearMediaItems()
                }
                playerNow.pause()
                isSyncing = false
            }
            return
        }

        bufferingTrackId = currentTrack.id
        val generation = ++currentTrackGeneration

        scope.launch(Dispatchers.Main) {
            // Verify we're still on the same track generation (no newer track change arrived)
            if (currentTrackGeneration != generation) {
                Log.d(TAG, "Skipping stale track generation: $generation vs current $currentTrackGeneration (track ${currentTrack.id})")
                return@launch
            }

            val playerNow = player() ?: return@launch
            isSyncing = true

            try {
                // Re-verify generation before applying media items (critical section)
                if (currentTrackGeneration != generation) {
                    Log.d(TAG, "Stale generation detected before setMediaItems: $generation vs $currentTrackGeneration")
                    return@launch
                }

                // Apply queue/media (same)
                if (queue != null && queue.isNotEmpty()) {
                    val mediaItems = queue.map { it.toSong().toMediaItem() }

                    // Find index of current track
                    var startIndex = mediaItems.indexOfFirst { it.mediaId == currentTrack.id }
                    if (startIndex == -1) {
                        Log.w(TAG, "Current track ${currentTrack.id} not found in queue, defaulting to 0")
                        val singleItem = currentTrack.toSong().toMediaItem()
                        // Prepend or fallback? Let's just play the track alone if not in queue
                        playerNow.setMediaItems(listOf(singleItem), 0, position)
                    } else {
                        playerNow.setMediaItems(mediaItems, startIndex, position)
                    }
                } else {
                    // No queue provided, fallback to loading just the track (or radio) via syncToTrack logic
                    // But we want to avoid double loading.
                    // If queue is null, we might be in a state where we should fetch radio?
                    // But here we assume authoritative state.
                    Log.d(TAG, "No queue in state, loading single track")
                    // Construct single item
                    val item = currentTrack.toSong().toMediaItem()
                    playerNow.setMediaItems(listOf(item), 0, position)
                }

                playerNow.seekTo(position)  // Always seek immediately to target pos

                if (bypassBuffer) {
                    // Manual sync/reconnect: apply play/pause immediately, no buffer protocol
                    Log.d(TAG, "Bypass buffer: immediately applying play=$isPlaying at pos=$position")

                    // Wait for player to be ready before seek/play
                    var attempts = 0
                    while (playerNow.playbackState != Player.STATE_READY && attempts < 100) {
                        delay(50)
                        attempts++
                    }
                    if (playerNow.playbackState == Player.STATE_READY) {
                        Log.d(TAG, "Player ready after ${attempts * 50}ms, seeking to $position")
                        playerNow.seekTo(position)
                        if (isPlaying) {
                            playerNow.playForSync()
                            Log.d(TAG, "Bypass: PLAY issued")
                        } else {
                            playerNow.pause()
                            Log.d(TAG, "Bypass: PAUSE issued")
                        }
                    } else {
                        Log.w(TAG, "Player not ready after 5s timeout during bypass sync")
                    }

                    // Clear sync state
                    pendingSyncState = null
                    bufferingTrackId = null
                    bufferCompleteReceivedForTrack = null
                } else {
                    // Normal sync: pause, store pending, send buffer_ready
                    playerNow.pause()
                    pendingSyncState = SyncStatePayload(
                        currentTrack = currentTrack,
                        isPlaying = isPlaying,
                        position = position,
                        lastUpdate = System.currentTimeMillis()
                    )
                    applyPendingSyncIfReady()
                    client.sendBufferReady(currentTrack.id)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error applying playback state")
            } finally {
                delay(200)
                isSyncing = false
            }
        }
    }

    private fun syncToTrack(track: TrackInfo, shouldPlay: Boolean, position: Long) {
        Log.d(TAG, "syncToTrack: ${track.title}, play: $shouldPlay, pos: $position")

        // Track which buffer-complete we expect for this load
        bufferingTrackId = track.id
        val generation = currentTrackGeneration

        activeSyncJob?.cancel()
        activeSyncJob = scope.launch(Dispatchers.Main) {
            try {
                // Check if a newer track change arrived - skip this load if stale
                if (currentTrackGeneration != generation) {
                    Log.d(TAG, "Skipping stale syncToTrack for ${track.id} (generation $generation vs $currentTrackGeneration)")
                    isSyncing = false
                    return@launch
                }

                // PORT-NOTE: vivi fetched a watch queue (the track's radio) here;
                // Neiro plays the one track every member of the room has — the
                // queue arrives separately on the next SYNC_QUEUE — so the load
                // is a single media item built straight from the wire's TrackInfo.
                val player = player() ?: run {
                    isSyncing = false
                    return@launch
                }
                isSyncing = true
                player.setMediaItems(listOf(track.toSong().toMediaItem()), 0, 0L)
                player.prepare()

                // Wait for player to be ready - monitor actual player state
                var waitCount = 0
                while (waitCount < 40) { // Max 2 seconds (40 * 50ms)
                    // Check generation again while waiting
                    if (currentTrackGeneration != generation) {
                        Log.d(TAG, "Generation changed while waiting for player ready - aborting sync for ${track.id}")
                        isSyncing = false
                        return@launch
                    }
                    try {
                        if (player.playbackState == Player.STATE_READY) {
                            Log.d(TAG, "Player ready after ${waitCount * 50}ms")
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking player state")
                        break
                    }
                    delay(50)
                    waitCount++
                }

                // Do NOT seek here; defer the exact seek until after the server signals buffer-complete
                // Ensure paused state before signaling ready
                player.pause()

                // Store pending sync (guest will apply seek + play/pause after BufferComplete)
                pendingSyncState = SyncStatePayload(
                    currentTrack = track,
                    isPlaying = shouldPlay,
                    position = position,
                    lastUpdate = System.currentTimeMillis()
                )

                // Apply immediately if buffer-complete already arrived
                applyPendingSyncIfReady()

                // Signal we're ready to play
                client.sendBufferReady(track.id)
                Log.d(TAG, "Sent buffer ready for ${track.id}, pending sync stored: pos=$position, play=$shouldPlay")

                // Minimal delay before accepting sync commands
                delay(100)
                isSyncing = false
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing to track")
                isSyncing = false
            }
        }
    }

    // Public API for host actions

    /**
     * Connect to the Listen Together server
     */
    fun connect() {
        Log.d(TAG, "Connecting to server")
        client.connect()
    }

    /**
     * Drops the held player when the playback service is destroyed. The manager
     * deliberately keeps it across MainActivity's onStop (the accessor follows the
     * live service, so room sync keeps working in the background), but once the
     * service itself is gone the accessor only pins a released player until the
     * next launch. Called from PartySync's stop().
     */
    fun onMusicServiceDestroyed() {
        detachPlayer()
    }

    /**
     * Keeps [playerListener] on the service's live player. A crossfade handoff
     * swaps the session player and releases the old one; the listener used to
     * stay on the released player, so after the first blend a host stopped
     * broadcasting play/pause/seek/track changes and a guest stopped
     * suggesting songs, with nothing in the UI to show it.
     *
     * PORT-NOTE: vivi's PlayerConnection followed a playerFlow the service
     * pushed promotions onto; Neiro's service has no such flow, so the accessor's
     * identity is polled on a slow cadence — a promotion is rare, and a second
     * of latency in re-attaching the listener costs one missed host event at
     * worst.
     */
    private fun followPlayerPromotions(accessor: () -> Player?) {
        playerFollowJob?.cancel()
        playerFollowJob = null
        playerFollowJob =
            scope.launch {
                var previous: Player? = accessor()
                while (isActive) {
                    delay(PLAYER_FOLLOW_POLL_MS)
                    val newPlayer = accessor()
                    if (newPlayer === previous) continue
                    if (playerListenerRegistered && previous != null) {
                        runCatching { previous.removeListener(playerListener) }
                        runCatching {
                            // Remove first so a listener an event handler already put on the
                            // new player is never registered twice.
                            newPlayer?.removeListener(playerListener)
                            newPlayer?.addListener(playerListener)
                        }.onFailure { Log.e(TAG, "Failed to move player listener after promotion") }
                    }
                    previous = newPlayer
                }
            }
    }

    /**
     * Disconnect from the server
     */
    fun disconnect() {
        Log.d(TAG, "Disconnecting from server")
        cleanup()
        client.disconnect()
    }

    /**
     * Create a new room
     */
    fun createRoom(username: String) {
        Log.d(TAG, "Creating room with username: $username")
        client.createRoom(username)
    }

    /**
     * Join an existing room. False when the request was not sent because another room request is
     * still pending.
     */
    fun joinRoom(roomCode: String, username: String): Boolean {
        Log.d(TAG, "Joining room $roomCode as $username")
        return client.joinRoom(roomCode, username)
    }

    /**
     * Leave the current room
     */
    fun leaveRoom() {
        Log.d(TAG, "Leaving room")
        cleanup()
        client.leaveRoom()
    }

    /**
     * Approve a join request
     */
    fun approveJoin(userId: String) = client.approveJoin(userId)

    /**
     * Reject a join request
     */
    fun rejectJoin(userId: String, reason: String? = null) = client.rejectJoin(userId, reason)

    /**
     * Kick a user
     */
    fun kickUser(userId: String, reason: String? = null) = client.kickUser(userId, reason)

    /**
     * Block a user permanently (internal list)
     */
    fun blockUser(username: String) = client.blockUser(username)

    /**
     * Unblock a previously blocked user
     */
    fun unblockUser(username: String) = client.unblockUser(username)

    /**
     * Get all currently blocked usernames
     */
    fun getBlockedUsernames(): Set<String> = blockedUsernames.value

    /**
     * Transfer host role to another user
     */
    fun transferHost(newHostId: String) = client.transferHost(newHostId)

    /**
     * Send track change (host only) - called when host changes track
     */
    fun sendTrackChange(song: Song) {
        if (!isHost || isSyncing) return
        sendTrackChangeInternal(song)
    }

    /**
     * Internal track change - bypasses isSyncing check for initial state sync
     */
    private fun sendTrackChangeInternal(song: Song) {
        if (!isHost) return

        val trackInfo = song.toTrackInfo()

        Log.d(TAG, "Sending track change: ${trackInfo.title}, duration: ${trackInfo.duration}")

        // Also grab current queue to send along with track change
        val currentQueue = try {
            player()?.let { p -> (0 until p.mediaItemCount).map { p.getMediaItemAt(it).toSong().toTrackInfo() } }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get current queue")
            null
        }

        client.sendPlaybackAction(
            PlaybackActions.CHANGE_TRACK,
            queueTitle = null,
            trackInfo = trackInfo,
            queue = currentQueue
        )
    }

    private fun startQueueSyncObservation() {
        if (queueObserverJob?.isActive == true) return

        Log.d(TAG, "Starting queue sync observation")
        // PORT-NOTE: vivi observed the PlayerConnection's queue-windows flow; the
        // service player here exposes no such flow, so the host's queue is polled
        // on a slow cadence and SYNC_QUEUE fires when the item ids actually move —
        // a queue that has not changed costs one cheap read of media ids.
        queueObserverJob = scope.launch {
            var lastIds: List<String> = emptyList()
            while (isActive) {
                val p = player()
                if (p != null) {
                    val ids = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }
                    if (ids != lastIds && !isSyncing) {
                        lastIds = ids
                        if (isHost && isInRoom) {
                            delay(500) // Debounce rapid playlist manipulations
                            val pNow = player()
                            if (pNow != null && isHost && isInRoom && !isSyncing) {
                                val tracks = (0 until pNow.mediaItemCount)
                                    .map { pNow.getMediaItemAt(it).toSong().toTrackInfo() }
                                Log.d(TAG, "Sending SYNC_QUEUE with ${tracks.size} items")
                                client.sendPlaybackAction(
                                    PlaybackActions.SYNC_QUEUE,
                                    queueTitle = null,
                                    queue = tracks,
                                )
                            }
                        }
                    } else {
                        lastIds = ids
                    }
                }
                delay(QUEUE_SYNC_POLL_MS)
            }
        }
    }

    private fun startVolumeSyncObservation() {
        if (volumeObserverJob?.isActive == true) return

        Log.d(TAG, "Starting volume sync observation")
        // PORT-NOTE: vivi watched the service's in-app player-volume flow. Neiro's
        // playback has no in-app volume surface — its player volume attribute is
        // the crossfade automation's own — so the host broadcasts the device's
        // music-stream volume as a ratio of its maximum, watched through the
        // system settings' volume tree the way the app's own zero-volume
        // observer does. Guests apply it as their player's volume (see
        // [applyHostVolumeIfNeeded]); nobody's device volume is ever written.
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager == null) return
        val observer = object : android.database.ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                if (!isHost || !isInRoom || !syncHostVolumeEnabled.value) return
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val normalized = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    .coerceIn(0, max)
                    .toFloat() / max
                val last = lastSyncedVolume
                if (last != null && kotlin.math.abs(last - normalized) < 0.01f) return
                lastSyncedVolume = normalized
                client.sendPlaybackAction(PlaybackActions.SET_VOLUME, volume = normalized)
            }
        }
        context.contentResolver.registerContentObserver(
            android.provider.Settings.System.CONTENT_URI,
            true,
            observer,
        )
        volumeObserverJob = scope.launch {
            try {
                awaitCancellation()
            } finally {
                runCatching {
                    context.contentResolver.unregisterContentObserver(observer)
                }
            }
        }
    }

    private fun stopVolumeSyncObservation() {
        volumeObserverJob?.cancel()
        volumeObserverJob = null
        lastSyncedVolume = null
    }

    private var lastSuggestedTrackId: String? = null

    /**
     * Shares the locally picked custom profile picture with the room (only when the
     * avatar index says we actually use one). Piggybacks on the chat relay.
     */
    fun broadcastCustomAvatar() {
        try {
            if (!isInRoom) return
            if (AppSettings.listenTogetherAvatarIndex.value != ListenTogetherAvatar.CUSTOM_AVATAR_INDEX) return
            val bytes = ListenTogetherAvatar.loadCustomAvatarBytes(context) ?: return
            scope.launch(Dispatchers.IO) {
                client.sendCustomAvatar(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting custom avatar")
        }
    }

    /** Custom profile pictures received from room members, keyed by user id. */
    val customAvatars: kotlinx.coroutines.flow.StateFlow<Map<String, ByteArray>> get() = client.customAvatars

    /** Custom profile picture for a member: our own file for ourselves, the received broadcast otherwise. */
    fun customAvatarFor(userId: String?): ByteArray? {
        val selfId = this.userId.value
        val isSelf = userId == null || userId == selfId
        if (isSelf) {
            if (AppSettings.listenTogetherAvatarIndex.value != ListenTogetherAvatar.CUSTOM_AVATAR_INDEX) return null
            return ListenTogetherAvatar.loadCustomAvatarBytes(context)
        }
        return client.customAvatars.value[userId]
    }

    private fun suggestLocalTrackChange(trackId: String, player: Player) {
        try {
            if (trackId == lastSuggestedTrackId) return
            val roomTrackId = roomState.value?.currentTrack?.id
            if (trackId == roomTrackId) return

            val song = player.currentMediaItem?.toSong() ?: return
            lastSuggestedTrackId = trackId
            val trackInfo = song.toTrackInfo()
            Log.d(TAG, "Guest track change sent as suggestion: ${song.title}")
            client.suggestTrack(trackInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error suggesting local track change")
        }
    }

    private fun stopQueueSyncObservation() {
        queueObserverJob?.cancel()
        queueObserverJob = null
    }

    /**
     * The wire's track as this app's own [Song] — enough identity for the queue
     * to hold it, and the stream resolves when the player reaches it.
     */
    internal fun TrackInfo.toSong(): Song = Song(
        videoId = id,
        title = title,
        artist = artist,
        thumbnailUrl = thumbnail,
        albumName = album,
        durationText = duration.takeIf { it > 0L }?.let { ms ->
            val total = ms / 1000
            "%d:%02d".format(total / 60, total % 60)
        },
    )

    /**
     * A local [Song] as the wire's track: what the host broadcasts and what a
     * guest suggests, built the same way on every device.
     */
    internal fun Song.toTrackInfo(): TrackInfo {
        val durationMs = TrackMatcher.secondsOf(durationText)?.let { it * 1000L } ?: 180000L
        return TrackInfo(
            id = videoId,
            title = title,
            artist = artist,
            album = albumName,
            duration = durationMs,
            thumbnail = thumbnailUrl,
        )
    }

    /** The engine's client — the facade and screens read the room's socket state through it. */
    fun client(): ListenTogetherClient = client

    /**
     * Appends [tracks] to the host's own queue. The queue observer notices the
     * ids move and broadcasts the next SYNC_QUEUE, so this is the host half of
     * the facade's [com.music.bitchord.data.listentogether.ListenTogether.queueAdd]
     * — the room reconciles every member from the one broadcast.
     */
    fun hostQueueAdd(tracks: List<TrackInfo>, playNext: Boolean = false) {
        if (tracks.isEmpty()) return
        if (!isHost) return
        scope.launch(Dispatchers.Main) {
            val player = player() ?: return@launch
            val items = tracks.map { it.toSong().toMediaItem() }
            if (playNext) {
                player.addMediaItems(player.currentMediaItemIndex + 1, items)
            } else {
                player.addMediaItems(items)
            }
        }
    }

    /**
     * Request sync state from server (for guests to re-sync)
     * Call this when a guest presses play/pause to sync with host
     */
    fun requestSync() {
        if (!isInRoom || isHost) {
            Log.d(TAG, "requestSync: not applicable (isInRoom=$isInRoom, isHost=$isHost)")
            return
        }
        Log.d(TAG, "Requesting sync from server")
        client.requestSync()
    }

    /**
     * Clear logs
     */
    fun clearLogs() = client.clearLogs()

    /** The username this device joined/created the room with (chat bookkeeping). */
    val currentUsername: String? get() = client.currentUsername

    // Suggestions API

    /**
     * Suggest the given track to the host (guest only)
     */
    fun suggestTrack(track: TrackInfo) = client.suggestTrack(track)

    /**
     * The track the local player is on right now (host-side source for sharing the current song
     * into the chat when the room state's currentTrack is stale or absent).
     */
    fun currentLocalTrack(): TrackInfo? {
        if (!isInRoom) return null
        return try {
            player()?.currentMediaItem?.toSong()?.toTrackInfo()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read the current local track", e)
            null
        }
    }

    /**
     * Approve a suggestion (host only)
     */
    fun approveSuggestion(suggestionId: String) {
        if (!isHost) return
        // Send approval; server will insert-next and broadcast once
        client.approveSuggestion(suggestionId)
    }

    /**
     * Reject a suggestion (host only)
     */
    fun rejectSuggestion(suggestionId: String, reason: String? = null) = client.rejectSuggestion(suggestionId, reason)

    /**
     * Force reconnection to server (for manual recovery)
     */
    fun forceReconnect() {
        Log.d(TAG, "Forcing reconnection")
        client.forceReconnect()
    }

    /**
     * Get persisted room code if available
     */
    fun getPersistedRoomCode(): String? = client.getPersistedRoomCode()

    /**
     * Get current session age
     */
    fun getSessionAge(): Long = client.getSessionAge()

    // Heartbeat timer
    private var heartbeatJob: Job? = null

    private fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (heartbeatJob?.isActive == true && isInRoom && isHost) {
                delay(10000L) // 10 seconds (increased frequency from 15s to 10s)
                player()?.let { player ->
                    if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                        val pos = player.currentPosition
                        Log.d(TAG, "Host heartbeat: sending PLAY at pos $pos")
                        client.sendPlaybackAction(PlaybackActions.PLAY, position = pos)
                    }
                }
            }
        }
        Log.d(TAG, "Host heartbeat started (10s interval)")
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.d(TAG, "Host heartbeat stopped")
    }

    /**
     * Applies an approved suggestion (or a locally shared song on the host) through the full
     * manual-skip path: playNext + prepareForManualSkip so an in-flight crossfade never keeps
     * streaming the previous song.
     */
    private fun applyApprovedSuggestion(trackInfo: TrackInfo, playImmediately: Boolean) {
        try {
            val player = player()
            if (player == null) {
                Log.w(TAG, "Cannot apply approved suggestion - no player")
                return
            }
            val mediaItem = trackInfo.toSong().toMediaItem()
            player.addMediaItems(player.currentMediaItemIndex + 1, listOf(mediaItem))
            if (playImmediately) {
                val nextIndex = player.currentMediaItemIndex + 1
                if (nextIndex < player.mediaItemCount &&
                    player.getMediaItemAt(nextIndex).mediaId == mediaItem.mediaId
                ) {
                    // Full manual-skip semantics: an in-flight crossfade (or its
                    // pauseAtEnd handoff) would otherwise keep the OLD song's
                    // audio playing through the secondary player while the
                    // queue already moved on, so cancel it first — exactly what
                    // a tap on the skip button does.
                    //
                    // PORT-NOTE: vivi called the service's prepareForManualSkip here;
                    // Neiro's crossfade has no public cancel hook — but a party keeps
                    // the room deterministic another way: the app's queue is the
                    // room's while connected, and blends arm from the queue moving
                    // on, so the same skip that starts the suggestion also settles
                    // the blend.
                    //
                    // Force playback on: an auto-approved suggestion starts the
                    // room's FIRST song while the host is still idle, so
                    // restoring the pre-suggestion playWhenReady (false) left
                    // the room paused until someone tapped play — and the
                    // follow-up CHANGE_TRACK then paused the guests too.
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.playWhenReady = true
                } else {
                    Log.w(TAG, "Approved suggestion not adjacent after queue insert; leaving it queued")
                }
            }
            Log.d(TAG, "Approved suggestion applied: ${trackInfo.title} (playImmediately=$playImmediately)")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying approved suggestion")
        }
    }

    fun sendChatMessage(message: String, replyTo: RepliedMessage? = null) {
        if (message.isBlank()) return
        client.sendChatMessage(message, replyTo)
    }

    /**
     * Shares a song into the chat as a rich tappable card (sent as an [LTS:] envelope on the chat
     * relay, optionally with a caption).
     */
    fun shareTrackToChat(track: TrackInfo, caption: String = "") {
        client.sendChatMessage(caption.trim(), null, sharedTrack = track)
    }

    /**
     * Plays a song shared in the chat: the host applies it directly through the
     * approved-suggestion path; a guest suggests it (auto-approved by default) so the song starts
     * in the room for everyone.
     */
    fun playSharedTrack(track: TrackInfo) {
        if (!isInRoom) return
        if (isHost) {
            applyApprovedSuggestion(track, playImmediately = true)
        } else {
            suggestTrack(track)
        }
    }

    /**
     * Applies a reactions/edit/delete/pin/typing control event to the local chat
     * state. All applications are idempotent, so the sender can apply locally and
     * again through its own server echo without harm. Typing echoes from self are
     * ignored so the composer never shows their own indicator.
     */
    private fun applyChatControl(fromUserId: String, fromUsername: String, control: ChatControlEvent) {
        when (control.action) {
            ChatControlEvent.ACTION_TYPING -> {
                if (fromUserId == userId.value) return
                val now = System.currentTimeMillis()
                _typingUsers.value =
                    _typingUsers.value.filter { it.userId != fromUserId } +
                        TypingUser(userId = fromUserId, username = fromUsername, expiresAt = now + TYPING_TTL_MS)
            }

            ChatControlEvent.ACTION_REACT,
            ChatControlEvent.ACTION_UNREACT -> {
                val emoji = control.emoji ?: return
                val targetTimestamp = control.targetTimestamp ?: return
                val targetUserId = control.targetUserId ?: return
                updateChatMessage(targetUserId, targetTimestamp) { message ->
                    val current = message.reactions[emoji].orEmpty()
                    val updated =
                        if (control.action == ChatControlEvent.ACTION_REACT) {
                            if (current.contains(fromUsername)) current else current + fromUsername
                        } else {
                            current - fromUsername
                        }
                    val newReactions =
                        if (updated.isEmpty()) message.reactions - emoji
                        else message.reactions + (emoji to updated)
                    message.copy(reactions = newReactions)
                }
            }

            ChatControlEvent.ACTION_EDIT -> {
                val targetTimestamp = control.targetTimestamp ?: return
                val targetUserId = control.targetUserId ?: return
                if (targetUserId != fromUserId) return // only one's own messages
                val newText = control.text?.trim()?.takeIf { it.isNotEmpty() } ?: return
                updateChatMessage(targetUserId, targetTimestamp) { message ->
                    message.copy(message = newText, edited = true)
                }
            }

            ChatControlEvent.ACTION_DELETE -> {
                val targetTimestamp = control.targetTimestamp ?: return
                val targetUserId = control.targetUserId ?: return
                // Authors delete their own messages; the room host may
                // additionally delete anyone's (moderation).
                val senderIsRoomHost = roomState.value?.hostId == fromUserId
                if (targetUserId != fromUserId && !senderIsRoomHost) return
                // Tombstone, not removal: everyone (and the persisted history)
                // keeps seeing that a message existed and was deleted.
                updateChatMessage(targetUserId, targetTimestamp) { message ->
                    message.copy(deleted = true, message = "", sharedTrack = null)
                }
            }

            ChatControlEvent.ACTION_PIN,
            ChatControlEvent.ACTION_UNPIN -> {
                val targetTimestamp = control.targetTimestamp ?: return
                val targetUserId = control.targetUserId ?: return
                val pinned = control.action == ChatControlEvent.ACTION_PIN
                updateChatMessage(targetUserId, targetTimestamp) { message ->
                    // The stamp orders the pinned carousel latest-pin-first;
                    // it is reset to zero when unpinned.
                    if (pinned) {
                        message.copy(pinned = true, pinnedAt = System.currentTimeMillis())
                    } else {
                        message.copy(pinned = false, pinnedAt = 0L)
                    }
                }
            }
        }
    }

    private inline fun updateChatMessage(
        targetUserId: String,
        targetTimestamp: Long,
        transform: (ChatMessagePayload) -> ChatMessagePayload,
    ) {
        val messages = _chatMessages.value
        val index = messages.indexOfFirst { it.userId == targetUserId && it.timestamp == targetTimestamp }
        if (index == -1) return
        val updated = messages.toMutableList()
        updated[index] = transform(updated[index])
        _chatMessages.value = updated
        scheduleChatPersist()
    }

    /** Toggles the local user's emoji reaction on a message, room-wide. */
    fun toggleReaction(message: ChatMessagePayload, emoji: String) {
        val me = client.currentUsername ?: return
        val action =
            if (message.reactions[emoji]?.contains(me) == true) ChatControlEvent.ACTION_UNREACT
            else ChatControlEvent.ACTION_REACT
        val control = ChatControlEvent(
            action = action,
            targetTimestamp = message.timestamp,
            targetUserId = message.userId,
            emoji = emoji,
        )
        client.sendChatControl(control)
        applyChatControl(userId.value ?: "", me, control)
    }

    /** Pins or unpins a message for everyone in the room. */
    fun setPinned(message: ChatMessagePayload, pinned: Boolean) {
        val control = ChatControlEvent(
            action = if (pinned) ChatControlEvent.ACTION_PIN else ChatControlEvent.ACTION_UNPIN,
            targetTimestamp = message.timestamp,
            targetUserId = message.userId,
        )
        client.sendChatControl(control)
        applyChatControl(userId.value ?: "", client.currentUsername ?: "", control)
    }

    /** Edits one of the local user's own messages, room-wide. */
    fun editMessage(message: ChatMessagePayload, newText: String) {
        if (newText.isBlank() || message.userId != userId.value) return
        val control = ChatControlEvent(
            action = ChatControlEvent.ACTION_EDIT,
            targetTimestamp = message.timestamp,
            targetUserId = message.userId,
            text = newText,
        )
        client.sendChatControl(control)
        applyChatControl(userId.value ?: "", client.currentUsername ?: "", control)
    }

    /** Deletes a message for everyone in the room. Members may delete their own
     * messages; the host may additionally delete anyone's (moderation). */
    fun deleteMessageForEveryone(message: ChatMessagePayload) {
        if (message.userId != userId.value && !isHost) return
        val control = ChatControlEvent(
            action = ChatControlEvent.ACTION_DELETE,
            targetTimestamp = message.timestamp,
            targetUserId = message.userId,
        )
        client.sendChatControl(control)
        applyChatControl(userId.value ?: "", client.currentUsername ?: "", control)
    }

    /** Hides a message from the local user's view only. Nothing is broadcast —
     * other members keep seeing the message — and the persisted history is
     * rewritten without it, so it does not come back on restore. */
    fun deleteMessageForMe(message: ChatMessagePayload) {
        val remaining =
            _chatMessages.value.filterNot {
                it.userId == message.userId && it.timestamp == message.timestamp
            }
        _chatMessages.value = remaining

        // The post-removal snapshot is written directly rather than through
        // the debounced persist: a delayed write would race the leave-room
        // wipe of the live list (which must never erase the stored
        // conversation). Removing the last kept message clears the store
        // instead of silently leaving it stale.
        chatPersistJob?.cancel()
        scope.launch(Dispatchers.IO) {
            val username = client.currentUsername ?: return@launch
            val trimmed = remaining.takeLast(MAX_PERSISTED_CHAT_MESSAGES).filterNot { it.solo }
            // PORT-NOTE: the history rides AppSettings' raw string slot — one
            // JSON blob, written off the main thread the way the DataStore was.
            runCatching {
                if (trimmed.isEmpty()) {
                    AppSettings.setListenTogetherChatHistory(null)
                } else {
                    AppSettings.setListenTogetherChatHistory(
                        chatHistoryJson.encodeToString(
                            PersistedChatHistory.serializer(),
                            PersistedChatHistory(username = username, messages = trimmed),
                        )
                    )
                }
            }.onFailure { Log.e(TAG, "Failed to persist chat history") }
        }
    }

    /** Re-announces that the local user is composing, throttled to one frame per 2.5s. */
    fun notifyTyping() {
        if (!isInRoom) return
        val now = System.currentTimeMillis()
        if (now - lastTypingSentAt < TYPING_THROTTLE_MS) return
        lastTypingSentAt = now
        client.sendChatControl(ChatControlEvent(action = ChatControlEvent.ACTION_TYPING))
    }

    /** Debounced persistence of the chat list, keyed by the local username. Only
     * messages exchanged with other members are written — messages flagged
     * [ChatMessagePayload.solo] (the user talking to an empty room) are
     * deliberately dropped. When nothing worth keeping remains, the stored
     * history is CLEARED instead of silently left stale. */
    private fun scheduleChatPersist() {
        chatPersistJob?.cancel()
        chatPersistJob = scope.launch(Dispatchers.IO) {
            delay(600)
            val username = client.currentUsername ?: return@launch
            // Leaving the room wipes the live list before this debounced job
            // runs — that must never erase a previously stored conversation.
            if (_chatMessages.value.isEmpty()) return@launch
            val trimmed =
                _chatMessages.value
                    .takeLast(MAX_PERSISTED_CHAT_MESSAGES)
                    .filterNot { it.solo }
            runCatching {
                if (trimmed.isEmpty()) {
                    AppSettings.setListenTogetherChatHistory(null)
                } else {
                    AppSettings.setListenTogetherChatHistory(
                        chatHistoryJson.encodeToString(
                            PersistedChatHistory.serializer(),
                            PersistedChatHistory(username = username, messages = trimmed),
                        )
                    )
                }
            }.onFailure { Log.e(TAG, "Failed to persist chat history") }
        }
    }

    /**
     * Restores the per-username chat history, scoped to the members that are
     * actually in the room right now:
     *  - nothing restores while the local user is alone (an empty room has no
     *    conversation to continue), and
     *  - only messages between the local user and the given members come back —
     *    history with people who are not present stays hidden until they join.
     *
     * Restored messages carry [ChatMessagePayload.restored] so the chat list can
     * draw the "older messages" divider where history ends, and their user ids
     * are remapped to the CURRENT session ids, so everything keyed by user id
     * downstream — own-message alignment (right side), avatar lookup, reactions,
     * pins, edits and deletes — works on them exactly as on live messages.
     *
     * Histories written by an older persistence scheme (before solo messages
     * were flagged) are discarded once — they may contain the user's alone-room
     * chatter, which must never come back.
     */
    private fun restorePersistedChatHistory(otherMembers: List<UserInfo>) {
        if (otherMembers.isEmpty()) return
        val username = client.currentUsername
        if (username.isNullOrBlank()) return
        val myId = userId.value
        val memberIdByName = otherMembers.associate { it.username to it.userId }
        scope.launch(Dispatchers.IO) {
            val raw = runCatching { AppSettings.listenTogetherChatHistory() }.getOrNull()
                ?: return@launch
            val stored = runCatching {
                chatHistoryJson.decodeFromString(PersistedChatHistory.serializer(), raw)
            }.getOrNull() ?: return@launch
            if (stored.username != username) return@launch
            if (stored.version != PersistedChatHistory.CURRENT_VERSION) {
                Log.d(TAG, "Discarding chat history from an older persistence scheme")
                runCatching {
                    AppSettings.setListenTogetherChatHistory(null)
                }
                return@launch
            }

            val memberNames = otherMembers.map { it.username }.toSet()
            val restored = stored.messages
                .filter { it.username == username || it.username in memberNames }
                .map { message ->
                    val currentId =
                        if (message.username == username) myId
                        else memberIdByName[message.username]
                    if (currentId != null && currentId != message.userId) {
                        message.copy(userId = currentId, restored = true)
                    } else {
                        message.copy(restored = true)
                    }
                }
            if (restored.isEmpty()) return@launch

            withContext(Dispatchers.Main) {
                val existing = _chatMessages.value
                val existingKeys = existing.map { it.username to it.timestamp }.toHashSet()
                val fresh = restored.filterNot { (it.username to it.timestamp) in existingKeys }
                if (fresh.isEmpty()) return@withContext
                // Timestamp-ordered merge: restored history is older than the
                // live session's messages, but a mid-session restore (someone
                // joins after the local user already chatted) must still land in
                // the right position, not blindly on top.
                _chatMessages.value = (fresh + existing).sortedBy { it.timestamp }
                Log.d(TAG,
                    "Restored ${fresh.size} chat messages for $username with ${otherMembers.map { it.username }}")
            }
        }
    }
}

/** A room member that is currently typing; expires after [ListenTogetherManager]'s typing TTL. */
@kotlinx.serialization.Serializable
data class TypingUser(
    val userId: String,
    val username: String,
    val expiresAt: Long,
)

/** Locally persisted chat history, keyed by the username that produced it.
 * [version] gates the persistence scheme: bump it whenever the message filter
 * semantics change, so histories written by older builds (which may contain
 * entries the new rules would never store) are discarded instead of restored. */
@kotlinx.serialization.Serializable
data class PersistedChatHistory(
    val version: Int = CURRENT_VERSION,
    val username: String,
    val messages: List<ChatMessagePayload>,
) {
    companion object {
        /** 1: unfiltered history. 2: solo messages never persisted. */
        const val CURRENT_VERSION = 2
    }
}
