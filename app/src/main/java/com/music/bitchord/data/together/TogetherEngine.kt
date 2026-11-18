/*
 * Neiro (2026)
 * © 4nx3b — github.com/4nx3b
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package com.music.bitchord.data.together

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import com.music.bitchord.BuildConfig
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import com.music.bitchord.data.DebugLog as Log
import com.music.bitchord.data.model.QueueTier
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.playback.fromAutoplay
import com.music.bitchord.playback.toMediaItem
import com.music.bitchord.playback.toSong
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object TogetherEngine {
    const val HOST_PARTICIPANT_ID = "host"

    private const val TAG = "TogetherEngine"
    private const val TOGETHER_HOST_INACTIVITY_TIMEOUT_MS = 10L * 60 * 1000L
    private const val HEARTBEAT_INTERVAL_MS = 2_000L
    private const val GUEST_CONTROL_TIMEOUT_LAN_MS = 2_000L
    private const val GUEST_CONTROL_TIMEOUT_ONLINE_MS = 5_000L
    private const val GUEST_CONTROL_DEDUPE_MS = 350L
    private const val SEEK_REPORT_FLOOR_MS = 1_000L
    private const val MAX_GUEST_ADD_REQUESTS = 5
    private const val ROOM_QUEUE_PAST_WINDOW = 10
    private const val ROOM_QUEUE_UPCOMING_WINDOW = 25
    private const val MAX_CHAT_MESSAGES = 200

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO +
                CoroutineExceptionHandler { _, t ->
                    Log.w(TAG, "session job failed: ${t.message}")
                },
        )

    private var appContext: Context? = null

    @Volatile
    private var playerAccessor: (() -> Player?)? = null

    private val _sessionState = MutableStateFlow<TogetherSessionState>(TogetherSessionState.Idle)
    val sessionState: StateFlow<TogetherSessionState> = _sessionState.asStateFlow()

    private val _roomState = MutableStateFlow<TogetherRoomState?>(null)
    val roomState: StateFlow<TogetherRoomState?> = _roomState.asStateFlow()

    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chat: StateFlow<List<ChatMessage>> = _chat.asStateFlow()

    private val _clockSnapshot = MutableStateFlow<TogetherClockSnapshot?>(null)
    val clockSnapshot: StateFlow<TogetherClockSnapshot?> = _clockSnapshot.asStateFlow()

    private val _clockSynced = MutableStateFlow(false)
    val clockSynced: StateFlow<Boolean> = _clockSynced.asStateFlow()

    private val _autoplayTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val autoplayTrackIds: StateFlow<Set<String>> = _autoplayTrackIds.asStateFlow()

    val isApplyingRemote: Boolean get() = applyingRemote

    @Volatile
    private var applyingRemote: Boolean = false

    @Volatile
    private var focusLost: Boolean = false

    @Volatile
    private var locallyPaused: Boolean = false

    private var server: TogetherServer? = null
    private var onlineHost: TogetherOnlineHost? = null
    private var client: TogetherClient? = null
    private var broadcastJob: Job? = null
    private var onlineConnectJob: Job? = null
    private var clientEventsJob: Job? = null
    private var heartbeatJob: Job? = null
    private var inactivityJob: Job? = null
    private var inactivityEndSession: (suspend () -> Unit)? = null
    private var deferredPlayFallbackJob: Job? = null
    private var clock: TogetherClock? = null
    private var selfParticipantId: String? = null
    private var authorityParticipantId: String? = null
    private var isOnlineSession: Boolean = false
    private var lastAppliedSentAtElapsedMs: Long = 0L
    private var lastSentControlAtElapsedMs: Long = 0L
    private var lastSentControlAction: ControlAction? = null
    private var pendingGuestControl: PendingGuestControl? = null
    private var pendingGuestAddTrack: PendingGuestAddTrack? = null
    private val participantNames = ConcurrentHashMap<String, String>()
    private var lastNoticeKey: String? = null
    private var lastNoticeAtElapsedMs: Long = 0L
    private var pendingSessionId: String? = null

    private data class PendingGuestControl(
        val desiredIsPlaying: Boolean? = null,
        val desiredIndex: Int? = null,
        val desiredTrackId: String? = null,
        val requestedAtElapsedMs: Long,
        val expiresAtElapsedMs: Long,
    )

    private data class PendingGuestAddTrack(
        val trackId: String,
        val expiresAtElapsedMs: Long,
    )

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun attachPlayer(accessor: () -> Player?) {
        playerAccessor = accessor
    }

    fun detachPlayer() {
        playerAccessor = null
        if (_sessionState.value !is TogetherSessionState.Idle) {
            leave()
        }
    }

    fun isPlayerAttached(): Boolean = playerAccessor != null

    private suspend fun <T> onPlayer(block: suspend (Player) -> T): T? {
        val accessor = playerAccessor ?: return null
        return withContext(Dispatchers.Main) {
            val player = accessor() ?: return@withContext null
            block(player)
        }
    }

    private fun displayNameOrDefault(fallbackRes: Int): String {
        val stored = AppSettings.togetherDisplayName.value.trim()
        if (stored.isNotBlank()) return stored
        val context = appContext ?: return ""
        return Build.MODEL?.takeIf { it.isNotBlank() } ?: context.getString(fallbackRes)
    }

    fun startHosting(
        displayName: String,
        port: Int,
        settings: TogetherRoomSettings,
    ) {
        scope.launch {
            stopInternal()
            isOnlineSession = false
            _sessionState.value = TogetherSessionState.Idle

            val localIp = getLocalIpv4Address()
            val sessionId = UUID.randomUUID().toString()
            val sessionKey = UUID.randomUUID().toString()
            val joinInfo =
                TogetherJoinInfo(
                    host = localIp ?: "127.0.0.1",
                    port = port,
                    sessionId = sessionId,
                    sessionKey = sessionKey,
                )
            val joinLink = TogetherLink.encode(joinInfo)

            val hostName = displayName.trim().ifBlank { appContext?.getString(R.string.app_name).orEmpty() }
            val newServer =
                TogetherServer(
                    scope = scope,
                    sessionId = sessionId,
                    sessionKey = sessionKey,
                    hostDisplayName = hostName,
                    initialSettings = settings,
                    hostParticipantId = HOST_PARTICIPANT_ID,
                )

            newServer.onEvent = { event ->
                scope.launch { handleHostEvent(event) { newServer.currentSettings() } }
            }

            newServer.start(port)
            server = newServer
            scheduleHostInactivityTimeout(sessionId)

            _sessionState.value =
                TogetherSessionState.Hosting(
                    sessionId = sessionId,
                    joinLink = joinLink,
                    localAddressHint = localIp,
                    port = port,
                    settings = settings,
                    roomState = null,
                )
            Log.i(TAG, "hosting together session $sessionId on port $port")

            broadcastJob =
                scope.launch {
                    while (server === newServer) {
                        if (authorityParticipantId == null || authorityParticipantId == HOST_PARTICIPANT_ID) {
                            val state = buildRoomState(sessionId = sessionId, hostId = HOST_PARTICIPANT_ID)
                            if (state != null) {
                                newServer.broadcastRoomState(state)
                                val hosting = _sessionState.value as? TogetherSessionState.Hosting
                                if (hosting?.sessionId == sessionId) {
                                    _sessionState.value =
                                        hosting.copy(
                                            settings = newServer.currentSettings(),
                                            roomState =
                                                state.copy(
                                                    participants = newServer.currentParticipants(),
                                                    settings = newServer.currentSettings(),
                                                ),
                                        )
                                }
                                _roomState.value =
                                    state.copy(
                                        participants = newServer.currentParticipants(),
                                        settings = newServer.currentSettings(),
                                    )
                            }
                        }
                        delay(TogetherPlaybackSync.BroadcastIntervalMs)
                    }
                }
        }
    }

    fun startHostingOnline(
        displayName: String,
        settings: TogetherRoomSettings,
    ) {
        scope.launch {
            stopInternal()
            isOnlineSession = true
            _sessionState.value = TogetherSessionState.Idle

            val baseUrl = TogetherOnlineEndpoint.baseUrlOrNull()
            if (baseUrl == null) {
                _sessionState.value =
                    TogetherSessionState.Error(
                        message = appContext?.getString(R.string.together_online_not_configured).orEmpty(),
                        recoverable = true,
                    )
                return@launch
            }

            val togetherToken =
                BuildConfig.TOGETHER_BEARER_TOKEN
                    .trim()
                    .takeIf { it.isNotBlank() }
            if (togetherToken == null) {
                _sessionState.value =
                    TogetherSessionState.Error(
                        message = appContext?.getString(R.string.together_token_missing).orEmpty(),
                        recoverable = true,
                    )
                return@launch
            }

            val api = TogetherOnlineApi(baseUrl = baseUrl, bearerToken = togetherToken)
            val hostName = displayName.trim().ifBlank { appContext?.getString(R.string.app_name).orEmpty() }

            val created =
                runCatching { api.createSession(hostDisplayName = hostName, settings = settings) }
                    .getOrElse { t ->
                        _sessionState.value =
                            TogetherSessionState.Error(
                                message = onlineErrorMessage(t),
                                recoverable = true,
                            )
                        return@launch
                    }

            val newOnlineHost =
                TogetherOnlineHost(
                    externalScope = scope,
                    sessionId = created.sessionId,
                    sessionKey = created.hostKey,
                    hostId = HOST_PARTICIPANT_ID,
                    hostDisplayName = hostName,
                    initialSettings = created.settings,
                    clientId = AppSettings.togetherClientId(),
                    bearerToken = togetherToken,
                )

            newOnlineHost.onEvent = { event ->
                scope.launch { handleHostEvent(event) { newOnlineHost.currentSettings() } }
            }

            onlineHost = newOnlineHost
            scheduleHostInactivityTimeout(created.sessionId) {
                api.endSession(
                    sessionId = created.sessionId,
                    hostKey = created.hostKey,
                )
            }

            _sessionState.value =
                TogetherSessionState.HostingOnline(
                    sessionId = created.sessionId,
                    code = created.code,
                    settings = created.settings,
                    roomState = null,
                )
            Log.i(TAG, "hosting online together session ${created.sessionId} code ${created.code}")

            val wsUrl =
                TogetherOnlineEndpoint.onlineWebSocketUrlOrNull(
                    rawWsUrl = created.wsUrl,
                    baseUrl = baseUrl,
                )
            if (wsUrl == null) {
                _sessionState.value =
                    TogetherSessionState.Error(
                        message = appContext?.getString(R.string.together_invalid_ws_url).orEmpty(),
                        recoverable = true,
                    )
                stopInternal()
                return@launch
            }

            onlineConnectJob?.cancel()
            onlineConnectJob =
                scope.launch {
                    newOnlineHost.connect(wsUrl)
                }

            broadcastJob =
                scope.launch {
                    while (onlineHost === newOnlineHost) {
                        val state =
                            if (authorityParticipantId == null || authorityParticipantId == HOST_PARTICIPANT_ID) {
                                buildRoomState(sessionId = created.sessionId, hostId = HOST_PARTICIPANT_ID)
                            } else {
                                null
                            }
                        if (state != null) {
                            newOnlineHost.broadcastRoomState(state)
                            val hosting = _sessionState.value as? TogetherSessionState.HostingOnline
                            if (hosting?.sessionId == created.sessionId) {
                                val currentSettings = newOnlineHost.currentSettings()
                                _sessionState.value =
                                    hosting.copy(
                                        settings = currentSettings,
                                        roomState =
                                            state.copy(
                                                participants = newOnlineHost.currentParticipants(),
                                                settings = currentSettings,
                                            ),
                                    )
                            }
                            _roomState.value =
                                state.copy(
                                    participants = newOnlineHost.currentParticipants(),
                                    settings = newOnlineHost.currentSettings(),
                                )
                        }
                        delay(TogetherPlaybackSync.BroadcastIntervalMs)
                    }
                }
        }
    }

    fun joinByLink(
        rawLink: String,
        displayName: String,
    ) {
        val joinInfo = TogetherLink.decode(rawLink)
        if (joinInfo == null) {
            _sessionState.value =
                TogetherSessionState.Error(
                    message = appContext?.getString(R.string.invalid_link).orEmpty(),
                    recoverable = true,
                )
            return
        }

        _sessionState.value = TogetherSessionState.Joining(joinInfo.toDeepLink())

        scope.launch {
            stopInternal()
            isOnlineSession = false
            startGuestClient(
                sessionId = joinInfo.sessionId,
                connect = { guestClient ->
                    guestClient.connect(joinInfo, displayName.trim().ifBlank { appContext?.getString(R.string.together_role_guest).orEmpty() })
                },
            )
        }
    }

    fun joinByCode(
        code: String,
        displayName: String,
    ) {
        val trimmedCode = code.trim()
        if (trimmedCode.isBlank()) {
            _sessionState.value =
                TogetherSessionState.Error(
                    message = appContext?.getString(R.string.invalid_code).orEmpty(),
                    recoverable = true,
                )
            return
        }

        _sessionState.value = TogetherSessionState.JoiningOnline(trimmedCode)

        scope.launch {
            stopInternal()
            isOnlineSession = true

            val baseUrl = TogetherOnlineEndpoint.baseUrlOrNull()
            if (baseUrl == null) {
                _sessionState.value =
                    TogetherSessionState.Error(
                        message = appContext?.getString(R.string.together_online_not_configured).orEmpty(),
                        recoverable = true,
                    )
                return@launch
            }

            val togetherToken =
                BuildConfig.TOGETHER_BEARER_TOKEN
                    .trim()
                    .takeIf { it.isNotBlank() }
            if (togetherToken == null) {
                _sessionState.value =
                    TogetherSessionState.Error(
                        message = appContext?.getString(R.string.together_token_missing).orEmpty(),
                        recoverable = true,
                    )
                return@launch
            }

            val api = TogetherOnlineApi(baseUrl = baseUrl, bearerToken = togetherToken)
            val resolved =
                runCatching { api.resolveCode(trimmedCode) }
                    .getOrElse { t ->
                        _sessionState.value =
                            TogetherSessionState.Error(
                                message = onlineErrorMessage(t),
                                recoverable = true,
                            )
                        return@launch
                    }

            val wsUrl =
                TogetherOnlineEndpoint.onlineWebSocketUrlOrNull(
                    rawWsUrl = resolved.wsUrl,
                    baseUrl = baseUrl,
                )
            if (wsUrl == null) {
                _sessionState.value =
                    TogetherSessionState.Error(
                        message = appContext?.getString(R.string.together_invalid_ws_url).orEmpty(),
                        recoverable = true,
                    )
                stopInternal()
                return@launch
            }

            startGuestClient(
                sessionId = resolved.sessionId,
                connect = { guestClient ->
                    guestClient.connect(
                        wsUrl = wsUrl,
                        sessionId = resolved.sessionId,
                        sessionKey = resolved.guestKey,
                        displayName = displayName.trim().ifBlank { appContext?.getString(R.string.together_role_guest).orEmpty() },
                    )
                },
            )
        }
    }

    private fun startGuestClient(
        sessionId: String,
        connect: (TogetherClient) -> Unit,
    ) {
        val guestClient =
            TogetherClient(
                scope,
                clientId = AppSettings.togetherClientId(),
                bearerToken = BuildConfig.TOGETHER_BEARER_TOKEN.trim().takeIf { it.isNotBlank() },
            )
        client = guestClient
        clock = TogetherClock()
        _clockSynced.value = false
        selfParticipantId = null
        pendingSessionId = sessionId

        clientEventsJob?.cancel()
        clientEventsJob =
            scope.launch {
                guestClient.events.collect { event -> handleClientEvent(event, displayName = { displayNameOrDefault(R.string.together_role_guest) }) }
            }

        connect(guestClient)
    }

    fun leave() {
        _sessionState.value = TogetherSessionState.Idle
        scope.launch { stopInternal() }
    }

    fun updateSettings(settings: TogetherRoomSettings) {
        val currentServer = server
        val currentOnlineHost = onlineHost
        if (currentServer == null && currentOnlineHost == null) return
        scope.launch {
            currentServer?.updateSettings(settings)
            currentOnlineHost?.updateSettings(settings)
        }
    }

    fun approveParticipant(
        participantId: String,
        approved: Boolean,
    ) {
        val currentServer = server
        val currentOnlineHost = onlineHost
        if (currentServer == null && currentOnlineHost == null) return
        scope.launch {
            currentServer?.approveParticipant(participantId, approved)
            currentOnlineHost?.approveParticipant(participantId, approved)
        }
    }

    fun kickParticipant(participantId: String) {
        val currentOnlineHost = onlineHost ?: return
        scope.launch {
            currentOnlineHost.kickParticipant(participantId, null)
        }
    }

    fun banParticipant(participantId: String) {
        val currentOnlineHost = onlineHost ?: return
        scope.launch {
            currentOnlineHost.banParticipant(participantId, null)
        }
    }

    fun transferHostOwnership(participantId: String) {
        val targetId = participantId.trim()
        if (targetId.isBlank() || targetId == HOST_PARTICIPANT_ID || targetId == selfParticipantId) return
        val currentServer = server
        val currentOnlineHost = onlineHost
        val currentClient = client
        val joined = _sessionState.value as? TogetherSessionState.Joined
        scope.launch {
            when {
                currentServer != null -> currentServer.transferHostOwnership(targetId)
                currentOnlineHost != null -> currentOnlineHost.transferHostOwnership(targetId)
                joined?.role is TogetherRole.Host && currentClient != null ->
                    currentClient.transferHostOwnership(joined.sessionId, targetId)
            }
        }
    }

    fun sendChat(text: String) {
        val trimmed = text.trim().take(500)
        if (trimmed.isBlank()) return
        val session = _sessionState.value
        val sessionId = session.sessionIdOrNull ?: return

        val currentServer = server
        val currentOnlineHost = onlineHost
        val currentClient = client
        if (currentServer != null) {
            val message =
                ChatMessage(
                    sessionId = sessionId,
                    participantId = HOST_PARTICIPANT_ID,
                    displayName = displayNameOrDefault(R.string.app_name),
                    text = trimmed,
                    sentAtMs = SystemClock.elapsedRealtime(),
                )
            scope.launch {
                currentServer.broadcastChat(message)
                appendChat(message)
            }
        } else if (currentOnlineHost != null) {
            val message =
                ChatMessage(
                    sessionId = sessionId,
                    participantId = selfParticipantId ?: HOST_PARTICIPANT_ID,
                    displayName = displayNameOrDefault(R.string.app_name),
                    text = trimmed,
                    sentAtMs = SystemClock.elapsedRealtime(),
                )
            scope.launch {
                currentOnlineHost.sendChat(message)
                appendChat(message)
            }
        } else if (currentClient != null) {
            currentClient.sendChat(sessionId, trimmed)
        }
    }

    fun requestGuestControl(action: ControlAction) {
        val guestClient = client ?: return
        val session = _sessionState.value as? TogetherSessionState.Joined ?: return
        if (session.role !is TogetherRole.Guest) return
        if (!session.roomState.settings.allowGuestsToControlPlayback) return

        val now = SystemClock.elapsedRealtime()
        val lastAction = lastSentControlAction
        val lastAt = lastSentControlAtElapsedMs
        if (lastAction == action && now - lastAt < GUEST_CONTROL_DEDUPE_MS) return
        lastSentControlAction = action
        lastSentControlAtElapsedMs = now

        val timeout = if (isOnlineSession) GUEST_CONTROL_TIMEOUT_ONLINE_MS else GUEST_CONTROL_TIMEOUT_LAN_MS
        pendingGuestControl =
            when (action) {
                ControlAction.Play -> {
                    PendingGuestControl(desiredIsPlaying = true, requestedAtElapsedMs = now, expiresAtElapsedMs = now + timeout)
                }

                ControlAction.Pause -> {
                    PendingGuestControl(desiredIsPlaying = false, requestedAtElapsedMs = now, expiresAtElapsedMs = now + timeout)
                }

                is ControlAction.SeekToIndex -> {
                    PendingGuestControl(
                        desiredIndex = action.index.coerceAtLeast(0),
                        requestedAtElapsedMs = now,
                        expiresAtElapsedMs = now + timeout,
                    )
                }

                is ControlAction.SeekToTrack -> {
                    PendingGuestControl(
                        desiredTrackId = action.trackId.trim().ifBlank { null },
                        requestedAtElapsedMs = now,
                        expiresAtElapsedMs = now + timeout,
                    )
                }

                else -> {
                    pendingGuestControl
                }
            }
        guestClient.requestControl(session.sessionId, action)
    }

    fun requestGuestPlay() {
        requestGuestControl(ControlAction.Play)
        scheduleDeferredPlayFallback()
    }

    private fun scheduleDeferredPlayFallback() {
        deferredPlayFallbackJob?.cancel()
        deferredPlayFallbackJob =
            scope.launch {
                delay(if (isOnlineSession) GUEST_CONTROL_TIMEOUT_ONLINE_MS else GUEST_CONTROL_TIMEOUT_LAN_MS)
                val pending = pendingGuestControl ?: return@launch
                if (pending.desiredIsPlaying != true) return@launch
                val roomNow = _roomState.value
                if (roomNow?.isPlaying == true) return@launch
                pendingGuestControl = null
                onPlayer { exo ->
                    if (!exo.playWhenReady) {
                        Log.w(TAG, "host never acknowledged the resume; starting locally")
                        exo.prepare()
                        exo.play()
                    }
                }
            }
    }

    fun publishGuestIntent() {
        val session = _sessionState.value
        if (session !is TogetherSessionState.Joined) return
        if (session.role !is TogetherRole.Guest) return
        val room = _roomState.value ?: return
        if (!room.settings.allowGuestsToControlPlayback) return

        scope.launch {
            val currentId = onPlayer { it.currentMediaItem?.mediaId }
            val position = onPlayer { it.currentPosition.coerceAtLeast(0L) } ?: return@launch
            val wantsPlaying = onPlayer { it.playWhenReady } ?: return@launch
            if (currentId == null) return@launch

            val roomTrackId = room.queue.getOrNull(room.currentIndex.coerceAtLeast(0))?.id
            if (currentId != roomTrackId) {
                val track =
                    onPlayer { exo ->
                        val song = exo.currentMediaItem?.toSong()
                        if (song == null || isDeviceFile(song)) {
                            null
                        } else {
                            song.toTogetherTrack(exo.duration)
                        }
                    } ?: return@launch
                val ops =
                    TogetherGuestPlaybackPlanner.planPlayTrackNow(
                        roomState = room,
                        track = track,
                        positionMs = position,
                        playWhenReady = wantsPlaying,
                    )
                ops.forEach { op ->
                    when (op) {
                        is TogetherGuestOp.Control -> requestGuestControl(op.action)
                        is TogetherGuestOp.AddTrack -> guestAddTrack(op.track, op.mode)
                    }
                }
                return@launch
            }

            if (wantsPlaying != room.isPlaying) {
                requestGuestControl(if (wantsPlaying) ControlAction.Play else ControlAction.Pause)
                return@launch
            }

            val now = SystemClock.elapsedRealtime()
            val target =
                TogetherPlaybackSync.targetPositionMs(
                    state = room,
                    isOnlineSession = isOnlineSession,
                    clockSnapshot = if (isOnlineSession) null else clock?.snapshot(),
                    nowElapsedRealtimeMs = now,
                )
            if (kotlin.math.abs(position - target) > SEEK_REPORT_FLOOR_MS) {
                requestGuestControl(ControlAction.SeekTo(position))
            }
        }
    }

    fun guestAddTrack(
        track: TogetherTrack,
        mode: AddTrackMode,
    ) {
        val guestClient = client ?: return
        val session = _sessionState.value as? TogetherSessionState.Joined ?: return
        if (session.role !is TogetherRole.Guest) return
        if (!session.roomState.settings.allowGuestsToAddTracks) return
        val now = SystemClock.elapsedRealtime()
        val timeout = if (isOnlineSession) GUEST_CONTROL_TIMEOUT_ONLINE_MS else GUEST_CONTROL_TIMEOUT_LAN_MS
        pendingGuestAddTrack = PendingGuestAddTrack(trackId = track.id, expiresAtElapsedMs = now + timeout)
        guestClient.requestAddTrack(session.sessionId, track, mode)
    }

    fun publishGuestQueueAdds() {
        val session = _sessionState.value
        if (session !is TogetherSessionState.Joined) return
        if (session.role !is TogetherRole.Guest) return
        val room = _roomState.value ?: return
        if (!room.settings.allowGuestsToAddTracks) return

        scope.launch {
            val snapshot =
                onPlayer { exo ->
                    val currentIndex = exo.currentMediaItemIndex
                    val upcoming =
                        (currentIndex + 1 until exo.mediaItemCount)
                            .map { exo.getMediaItemAt(it) }
                            .filterNot { it.mediaId.startsWith("content://") || it.mediaId.startsWith("file://") }
                    Triple(currentIndex, exo.currentMediaItemIndex, upcoming)
                } ?: return@launch
            val upcoming = snapshot.third
            if (upcoming.isEmpty()) return@launch
            val roomIds = room.queue.map { it.id }.toSet()
            val extras = upcoming.filter { it.mediaId !in roomIds }
            if (extras.isEmpty()) return@launch
            val currentSeenIds = mutableSetOf<String>()
            extras.take(MAX_GUEST_ADD_REQUESTS).forEach { item ->
                if (!currentSeenIds.add(item.mediaId)) return@forEach
                val song = item.toSong()
                val mode = if (item.mediaId == upcoming.firstOrNull()?.mediaId) AddTrackMode.PLAY_NEXT else AddTrackMode.ADD_TO_QUEUE
                guestAddTrack(song.toTogetherTrack(0L), mode)
            }
        }
    }

    fun addTracksToQueue(tracks: List<TogetherTrack>, autoplay: Boolean) {
        if (tracks.isEmpty()) return
        scope.launch {
            val items =
                withContext(Dispatchers.Default) {
                    tracks.map { track ->
                        track.toSong()
                            .let { song ->
                                if (autoplay) song.copy(queueTier = QueueTier.AUTOPLAY) else song
                            }
                            .toMediaItem()
                    }
                }
            onPlayer { exo ->
                exo.addMediaItems(items)
            }
        }
    }

    fun markFocusLost() {
        focusLost = true
        pendingGuestControl = null
        deferredPlayFallbackJob?.cancel()
        deferredPlayFallbackJob = null
    }

    fun clearFocusLost() {
        focusLost = false
    }

    fun markLocallyPaused() {
        locallyPaused = true
    }

    fun clearLocallyPaused() {
        locallyPaused = false
    }

    fun inSession(): Boolean =
        _sessionState.value !is TogetherSessionState.Idle &&
            _sessionState.value !is TogetherSessionState.Error

    /** Whether the live session rides the online relay rather than the LAN server. */
    val onlineRoom: Boolean get() = isOnlineSession

    fun controlsLockedForSelf(): Boolean {
        val session = _sessionState.value as? TogetherSessionState.Joined ?: return false
        return session.role is TogetherRole.Guest &&
            !session.roomState.settings.allowGuestsToControlPlayback
    }

    fun isGuestControlSession(): Boolean {
        val session = _sessionState.value as? TogetherSessionState.Joined ?: return false
        return session.role is TogetherRole.Guest &&
            session.roomState.settings.allowGuestsToControlPlayback
    }

    fun isGuestAddSession(): Boolean {
        val session = _sessionState.value as? TogetherSessionState.Joined ?: return false
        return session.role is TogetherRole.Guest &&
            session.roomState.settings.allowGuestsToAddTracks
    }

    fun guestTargetPositionMs(): Long? {
        val room = _roomState.value ?: return null
        return TogetherPlaybackSync.targetPositionMs(
            state = room,
            isOnlineSession = isOnlineSession,
            clockSnapshot = if (isOnlineSession) null else clock?.snapshot(),
            nowElapsedRealtimeMs = SystemClock.elapsedRealtime(),
        )
    }

    private fun clearLocalPauseIfFreed(room: TogetherRoomState) {
        if (!locallyPaused) return
        val session = _sessionState.value as? TogetherSessionState.Joined ?: return
        val controlsLocked = session.role is TogetherRole.Guest && !room.settings.allowGuestsToControlPlayback
        if (!controlsLocked) locallyPaused = false
    }

    private fun onlineErrorMessage(t: Throwable): String {
        val context = appContext ?: return t.message.orEmpty()
        if (t is TogetherOnlineApiException) {
            val code = t.statusCode
            return when {
                code == 404 -> context.getString(R.string.together_session_not_found)
                code != null && code in 500..599 -> context.getString(R.string.together_server_error)
                else -> t.message ?: context.getString(R.string.network_unavailable)
            }
        }
        val root = generateSequence(t) { it.cause }.lastOrNull() ?: t
        return when (root) {
            is java.net.UnknownHostException -> context.getString(R.string.together_server_unreachable)
            is java.net.ConnectException -> context.getString(R.string.together_server_unreachable)
            is java.net.SocketTimeoutException -> context.getString(R.string.together_connection_timed_out)
            is javax.net.ssl.SSLHandshakeException -> context.getString(R.string.together_server_unreachable)
            else -> context.getString(R.string.network_unavailable)
        }
    }

    private suspend fun handleHostEvent(
        event: TogetherServerEvent,
        currentSettings: suspend () -> TogetherRoomSettings,
    ) {
        when (event) {
            is TogetherServerEvent.ControlRequested -> {
                val settings = currentSettings()
                if (!settings.allowGuestsToControlPlayback) return
                applyHostControl(event.request.action)
            }

            is TogetherServerEvent.AddTrackRequested -> {
                val settings = currentSettings()
                if (!settings.allowGuestsToAddTracks) return
                applyHostAddTrack(event.request.track, event.request.mode)
            }

            is TogetherServerEvent.ParticipantJoined -> {
                val participant = event.participant
                if (!participant.isHost && !participant.isPending) {
                    participantNames[participant.id] = participant.name
                    cancelHostInactivityTimeout()
                    showParticipantNotification(participant.name, joined = true)
                }
            }

            is TogetherServerEvent.ParticipantLeft -> {
                val participantName =
                    participantNames.remove(event.participantId)
                        ?: return
                showParticipantNotification(participantName, joined = false)
                if (participantNames.isEmpty()) {
                    val sessionId = _sessionState.value.sessionIdOrNull
                    if (sessionId != null) {
                        scheduleHostInactivityTimeout(sessionId)
                    }
                }
            }

            is TogetherServerEvent.ChatReceived -> {
                appendChat(event.message)
            }

            is TogetherServerEvent.HostTransferred -> {
                val sessionId = _sessionState.value.sessionIdOrNull
                if (event.participantId == HOST_PARTICIPANT_ID &&
                    participantNames.isEmpty() &&
                    sessionId != null
                ) {
                    scheduleHostInactivityTimeout(sessionId)
                } else {
                    cancelHostInactivityTimeout()
                    inactivityEndSession = null
                }
                handleHostTransferred(event.participantId)
            }

            is TogetherServerEvent.RoomStateReceived -> {
                if (event.state.hostId != HOST_PARTICIPANT_ID) {
                    selfParticipantId = HOST_PARTICIPANT_ID
                    applyRemoteRoomState(event.state, force = true)
                }
            }

            is TogetherServerEvent.Error -> {
                val current = _sessionState.value
                if (current is TogetherSessionState.Idle) return
                if (event.message.startsWith("Chat rate limit")) {
                    showNotice(event.message)
                    return
                }
                _sessionState.value =
                    TogetherSessionState.Error(
                        message = event.message,
                        recoverable = true,
                    )
                stopInternal()
            }

            else -> {
                Unit
            }
        }
    }

    private fun handleClientEvent(
        event: TogetherClientEvent,
        displayName: () -> String,
    ) {
        when (event) {
            is TogetherClientEvent.Welcome -> {
                selfParticipantId = event.welcome.participantId
                val state = _sessionState.value
                if (state is TogetherSessionState.Joining || state is TogetherSessionState.JoiningOnline) {
                    val sessionId = pendingSessionId ?: state.sessionIdOrNull.orEmpty()
                    val initial =
                        TogetherRoomState(
                            sessionId = sessionId,
                            hostId = HOST_PARTICIPANT_ID,
                            participants =
                                listOf(
                                    TogetherParticipant(
                                        id = event.welcome.participantId,
                                        name = displayName(),
                                        isHost = false,
                                        isPending = event.welcome.isPending,
                                        isConnected = true,
                                    ),
                                ),
                            settings = event.welcome.settings,
                        )
                    _sessionState.value =
                        TogetherSessionState.Joined(
                            role = TogetherRole.Guest,
                            sessionId = sessionId,
                            selfParticipantId = event.welcome.participantId,
                            roomState = initial,
                        )
                    _roomState.value = initial
                    lastAppliedSentAtElapsedMs = 0L
                    val guestClient = client ?: return
                    startHeartbeat(sessionId, guestClient)
                }
            }

            is TogetherClientEvent.RoomState -> {
                scope.launch { applyRemoteRoomState(event.state) }
            }

            is TogetherClientEvent.HostTransferred -> {
                handleHostTransferred(event.transfer.participantId)
                val currentClient = client ?: return
                if (event.transfer.participantId == selfParticipantId) {
                    startAuthorityBroadcast(event.transfer.sessionId, event.transfer.participantId, currentClient)
                }
            }

            is TogetherClientEvent.ControlRequested -> {
                val joined = _sessionState.value as? TogetherSessionState.Joined
                if (authorityParticipantId == selfParticipantId &&
                    joined?.roomState?.settings?.allowGuestsToControlPlayback == true
                ) {
                    scope.launch { applyHostControl(event.request.action) }
                }
            }

            is TogetherClientEvent.AddTrackRequested -> {
                val joined = _sessionState.value as? TogetherSessionState.Joined
                if (authorityParticipantId == selfParticipantId &&
                    joined?.roomState?.settings?.allowGuestsToAddTracks == true
                ) {
                    scope.launch { applyHostAddTrack(event.request.track, event.request.mode) }
                }
            }

            is TogetherClientEvent.ChatReceived -> {
                appendChat(event.message)
            }

            is TogetherClientEvent.JoinDecision -> {
                if (!event.decision.approved) {
                    _sessionState.value =
                        TogetherSessionState.Error(
                            message = appContext?.getString(R.string.not_allowed).orEmpty(),
                            recoverable = true,
                        )
                    scope.launch { stopInternal() }
                }
            }

            is TogetherClientEvent.ServerIssue -> {
                Log.w(TAG, "server issue code=${event.code.orEmpty()} message=${event.message}")
                when (event.code) {
                    "GUEST_CONTROL_DISABLED" -> {
                        showNotice(event.message)
                        val joined = _sessionState.value as? TogetherSessionState.Joined
                        if (joined?.role is TogetherRole.Guest) {
                            pendingGuestControl = null
                            lastSentControlAction = null
                            restoreGuestPlayback()
                        }
                    }

                    "GUEST_ADD_DISABLED" -> {
                        showNotice(event.message)
                    }

                    "HOST_OFFLINE" -> {
                        showNotice(event.message)
                    }

                    else -> {
                        _sessionState.value =
                            TogetherSessionState.Error(
                                message = event.message,
                                recoverable = true,
                            )
                        scope.launch { stopInternal() }
                    }
                }
            }

            is TogetherClientEvent.HeartbeatPong -> {
                val currentClock = clock ?: return
                val snapshot =
                    currentClock.onPong(
                        sentAtElapsedMs = event.pong.clientElapsedRealtimeMs,
                        receivedAtElapsedMs = event.receivedAtElapsedRealtimeMs,
                        serverElapsedMs = event.pong.serverElapsedRealtimeMs,
                    )
                _clockSnapshot.value = snapshot
                _clockSynced.value = true
            }

            is TogetherClientEvent.Error -> {
                val current = _sessionState.value
                if (current is TogetherSessionState.Idle) return
                _sessionState.value =
                    TogetherSessionState.Error(
                        message = event.message,
                        recoverable = true,
                    )
                scope.launch { stopInternal() }
            }

            TogetherClientEvent.Disconnected -> {
                val current = _sessionState.value
                if (current is TogetherSessionState.Idle) return
                _sessionState.value =
                    TogetherSessionState.Error(
                        message =
                            if (current is TogetherSessionState.Joined && current.role is TogetherRole.Guest) {
                                appContext?.getString(R.string.together_host_left_session).orEmpty()
                            } else {
                                appContext?.getString(R.string.network_unavailable).orEmpty()
                            },
                        recoverable = true,
                    )
                scope.launch { stopInternal() }
            }
        }
    }

    private suspend fun applyHostControl(action: ControlAction) {
        onPlayer { exo ->
            when (action) {
                ControlAction.Play -> {
                    if (!exo.playWhenReady) {
                        exo.prepare()
                        exo.play()
                    }
                }

                ControlAction.Pause -> {
                    if (exo.playWhenReady) {
                        exo.pause()
                    }
                }

                is ControlAction.SeekTo -> {
                    exo.seekTo(action.positionMs.coerceAtLeast(0L))
                    exo.prepare()
                }

                ControlAction.SkipNext -> {
                    if (exo.hasNextMediaItem()) {
                        exo.seekToNext()
                        exo.prepare()
                        exo.play()
                    }
                }

                ControlAction.SkipPrevious -> {
                    if (exo.hasPreviousMediaItem()) {
                        exo.seekToPrevious()
                        exo.prepare()
                        exo.play()
                    }
                }

                is ControlAction.SeekToTrack -> {
                    val trackId = action.trackId.trim()
                    if (trackId.isNotBlank()) {
                        val idx =
                            (0 until exo.mediaItemCount)
                                .indexOfFirst { exo.getMediaItemAt(it).mediaId == trackId }
                        if (idx >= 0 && idx < exo.mediaItemCount) {
                            exo.seekTo(idx, action.positionMs.coerceAtLeast(0L))
                            exo.prepare()
                        }
                    }
                }

                is ControlAction.SeekToIndex -> {
                    val idx = action.index.coerceAtLeast(0)
                    if (idx < exo.mediaItemCount) {
                        exo.seekTo(idx, action.positionMs.coerceAtLeast(0L))
                        exo.prepare()
                    }
                }

                is ControlAction.SetRepeatMode -> {
                    if (exo.repeatMode != action.repeatMode) {
                        exo.repeatMode = action.repeatMode
                    }
                }

                is ControlAction.SetShuffleEnabled -> {
                    if (exo.shuffleModeEnabled != action.shuffleEnabled) {
                        exo.shuffleModeEnabled = action.shuffleEnabled
                    }
                }
            }
        }
    }

    private suspend fun applyHostAddTrack(
        track: TogetherTrack,
        mode: AddTrackMode,
    ) {
        val item = track.toSong().toMediaItem()
        onPlayer { exo ->
            when (mode) {
                AddTrackMode.PLAY_NEXT -> exo.addMediaItems(exo.currentMediaItemIndex + 1, listOf(item))
                AddTrackMode.ADD_TO_QUEUE -> exo.addMediaItems(listOf(item))
            }
        }
    }

    private suspend fun buildRoomState(
        sessionId: String,
        hostId: String,
    ): TogetherRoomState? =
        onPlayer { exo ->
            val items = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it) }
            val filtered = items.filterNot { it.mediaId.startsWith("content://") || it.mediaId.startsWith("file://") }
            val currentId = exo.currentMediaItem?.mediaId
            val currentSong = exo.currentMediaItem?.toSong()
            val indexInFiltered = filtered.indexOfFirst { it.mediaId == currentId }
            val currentDurationMs = exo.duration

            val autoplayIds = mutableSetOf<String>()
            val tracks = mutableListOf<TogetherTrack>()
            var roomIndex = 0

            if (indexInFiltered >= 0 && currentSong != null && !isDeviceFile(currentSong)) {
                val windowStart = (indexInFiltered - ROOM_QUEUE_PAST_WINDOW).coerceAtLeast(0)
                val windowEnd = (indexInFiltered + 1 + ROOM_QUEUE_UPCOMING_WINDOW).coerceAtMost(filtered.size)
                for (i in windowStart until windowEnd) {
                    val item = filtered[i]
                    val song = item.toSong()
                    if (item.fromAutoplay) autoplayIds.add(song.videoId)
                    tracks.add(song.toTogetherTrack(if (i == indexInFiltered) currentDurationMs else 0L))
                }
                roomIndex = indexInFiltered - windowStart
            }

            _autoplayTrackIds.value = autoplayIds

            TogetherRoomState(
                sessionId = sessionId,
                hostId = hostId,
                participants = emptyList(),
                settings = TogetherRoomSettings(),
                queue = tracks,
                queueHash = md5(tracks.joinToString(separator = "|") { it.id }),
                currentIndex = roomIndex,
                isPlaying = exo.playWhenReady && exo.playbackState != Player.STATE_ENDED,
                positionMs = exo.currentPosition.coerceAtLeast(0L),
                repeatMode = exo.repeatMode,
                shuffleEnabled = exo.shuffleModeEnabled,
                sentAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            )
        }

    private fun handleHostTransferred(participantId: String) {
        authorityParticipantId = participantId
        if (participantId != HOST_PARTICIPANT_ID) {
            selfParticipantId = HOST_PARTICIPANT_ID
        }
        when (val current = _sessionState.value) {
            is TogetherSessionState.Hosting -> {
                val roomState = current.roomState?.let { markHostParticipant(it, participantId) }
                _sessionState.value =
                    TogetherSessionState.Joined(
                        role =
                            if (participantId == HOST_PARTICIPANT_ID) {
                                TogetherRole.Host
                            } else {
                                TogetherRole.Guest
                            },
                        sessionId = current.sessionId,
                        selfParticipantId = HOST_PARTICIPANT_ID,
                        roomState =
                            roomState
                                ?: TogetherRoomState(
                                    sessionId = current.sessionId,
                                    hostId = participantId,
                                ),
                    )
            }

            is TogetherSessionState.HostingOnline -> {
                val roomState = current.roomState?.let { markHostParticipant(it, participantId) }
                _sessionState.value =
                    TogetherSessionState.Joined(
                        role =
                            if (participantId == HOST_PARTICIPANT_ID) {
                                TogetherRole.Host
                            } else {
                                TogetherRole.Guest
                            },
                        sessionId = current.sessionId,
                        selfParticipantId = HOST_PARTICIPANT_ID,
                        roomState =
                            roomState
                                ?: TogetherRoomState(
                                    sessionId = current.sessionId,
                                    hostId = participantId,
                                ),
                    )
            }

            is TogetherSessionState.Joined -> {
                _sessionState.value =
                    current.copy(
                        role =
                            if (current.selfParticipantId == participantId) {
                                TogetherRole.Host
                            } else {
                                TogetherRole.Guest
                            },
                        roomState = markHostParticipant(current.roomState, participantId),
                    )
            }

            else -> {
                Unit
            }
        }
    }

    private fun markHostParticipant(
        state: TogetherRoomState,
        hostId: String,
    ): TogetherRoomState =
        state.copy(
            hostId = hostId,
            participants =
                state.participants.map { participant ->
                    participant.copy(isHost = participant.id == hostId)
                },
        )

    private fun startAuthorityBroadcast(
        sessionId: String,
        participantId: String,
        guestClient: TogetherClient,
    ) {
        broadcastJob?.cancel()
        broadcastJob =
            scope.launch {
                while (client === guestClient && authorityParticipantId == participantId) {
                    val state = buildRoomState(sessionId = sessionId, hostId = participantId)
                    if (state != null) {
                        guestClient.sendRoomState(state)
                        _roomState.value = state
                        val joined = _sessionState.value as? TogetherSessionState.Joined
                        if (joined?.sessionId == sessionId && joined.selfParticipantId == participantId) {
                            _sessionState.value = joined.copy(roomState = state)
                        }
                    }
                    delay(TogetherPlaybackSync.BroadcastIntervalMs)
                }
            }
    }

    private fun restoreGuestPlayback() {
        scope.launch {
            val joined = _sessionState.value as? TogetherSessionState.Joined
            if (joined?.role is TogetherRole.Guest && !applyingRemote) {
                applyRemoteRoomState(joined.roomState, force = true)
            }
        }
    }

    private suspend fun applyRemoteRoomState(
        state: TogetherRoomState,
        force: Boolean = false,
    ) = withContext(Dispatchers.Main) {
        val pid = selfParticipantId ?: return@withContext
        val now = SystemClock.elapsedRealtime()

        clearLocalPauseIfFreed(state)
        if (focusLost) return@withContext

        val pending = pendingGuestControl
        if (force) {
            pendingGuestControl = null
        } else if (pending != null) {
            val currentTrackId = state.queue.getOrNull(state.currentIndex.coerceAtLeast(0))?.id
            val mismatch =
                (pending.desiredIsPlaying != null && state.isPlaying != pending.desiredIsPlaying) ||
                    (pending.desiredIndex != null && state.currentIndex != pending.desiredIndex) ||
                    (pending.desiredTrackId != null && currentTrackId != pending.desiredTrackId)
            if (now >= pending.expiresAtElapsedMs) {
                if ((pending.desiredIndex != null || pending.desiredTrackId != null) &&
                    now - pending.requestedAtElapsedMs >= 1200L &&
                    mismatch
                ) {
                    showNotice(appContext?.getString(R.string.together_song_change_failed).orEmpty())
                }
                pendingGuestControl = null
            } else {
                if (mismatch) return@withContext
                pendingGuestControl = null
            }
        }

        if (!force && pendingGuestAddTrack != null) {
            val pendingAdd = pendingGuestAddTrack
            if (pendingAdd != null) {
                if (state.queue.any { it.id == pendingAdd.trackId }) {
                    pendingGuestAddTrack = null
                } else if (now >= pendingAdd.expiresAtElapsedMs) {
                    pendingGuestAddTrack = null
                } else {
                    return@withContext
                }
            }
        }

        val sentAt = state.sentAtElapsedRealtimeMs
        if (TogetherPlaybackSync.isStaleRoomState(
                sentAtElapsedRealtimeMs = sentAt,
                lastAppliedSentAtElapsedRealtimeMs = lastAppliedSentAtElapsedMs,
                force = force,
            )
        ) {
            return@withContext
        }

        val targetPos =
            TogetherPlaybackSync.targetPositionMs(
                state = state,
                isOnlineSession = isOnlineSession,
                clockSnapshot = if (isOnlineSession) null else clock?.snapshot(),
                nowElapsedRealtimeMs = now,
            )

        applyingRemote = true
        try {
            val exo = playerAccessor?.invoke() ?: return@withContext
            val desiredIds = state.queue.map { it.id }
            val desiredHash = state.queueHash
            val localIds = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }
            val localHash = if (localIds.isEmpty()) "" else md5(localIds.joinToString(separator = "|"))
            val needsRebuild =
                TogetherPlaybackSync.needsQueueRebuild(
                    desiredHash = desiredHash,
                    desiredIds = desiredIds,
                    localHash = localHash,
                    localIds = localIds,
                )

            if (desiredIds.isNotEmpty() && needsRebuild) {
                val items =
                    withContext(Dispatchers.Default) {
                        state.queue.map { it.toSong().toMediaItem() }
                    }
                val startIndex = state.currentIndex.coerceIn(0, desiredIds.lastIndex)
                exo.setMediaItems(items, startIndex, targetPos)
                exo.prepare()
                if (exo.repeatMode != state.repeatMode) exo.repeatMode = state.repeatMode
                if (exo.shuffleModeEnabled != state.shuffleEnabled) exo.shuffleModeEnabled = state.shuffleEnabled
                if (!locallyPaused) exo.playWhenReady = state.isPlaying
            } else {
                val index =
                    if (exo.mediaItemCount > 0) {
                        state.currentIndex.coerceIn(0, exo.mediaItemCount - 1)
                    } else {
                        0
                    }
                val indexChanged = exo.mediaItemCount > 0 && index != exo.currentMediaItemIndex

                if (indexChanged) {
                    if (exo.repeatMode != state.repeatMode) exo.repeatMode = state.repeatMode
                    if (exo.shuffleModeEnabled != state.shuffleEnabled) exo.shuffleModeEnabled = state.shuffleEnabled
                    exo.seekTo(index, targetPos)
                    exo.prepare()
                    if (!locallyPaused) exo.playWhenReady = state.isPlaying
                } else {
                    val playbackStateChanged = exo.playWhenReady != state.isPlaying
                    if (exo.repeatMode != state.repeatMode) exo.repeatMode = state.repeatMode
                    if (exo.shuffleModeEnabled != state.shuffleEnabled) exo.shuffleModeEnabled = state.shuffleEnabled
                    if (!locallyPaused && playbackStateChanged) exo.playWhenReady = state.isPlaying
                    val shouldSeekForDrift =
                        TogetherPlaybackSync.shouldSeekForDrift(
                            currentPositionMs = exo.currentPosition,
                            targetPositionMs = targetPos,
                            isPlaying = state.isPlaying,
                            isOnlineSession = isOnlineSession,
                        )
                    if (!locallyPaused && (shouldSeekForDrift || (playbackStateChanged && !state.isPlaying))) {
                        exo.seekTo(targetPos)
                        exo.prepare()
                    }
                }
            }
            lastAppliedSentAtElapsedMs = sentAt

            _roomState.value = state
            val joined = _sessionState.value as? TogetherSessionState.Joined
            if (joined != null && joined.sessionId == state.sessionId && joined.selfParticipantId == pid) {
                _sessionState.value = joined.copy(roomState = state)
            } else if (joined == null) {
                _sessionState.value =
                    TogetherSessionState.Joined(
                        role = TogetherRole.Guest,
                        sessionId = state.sessionId,
                        selfParticipantId = pid,
                        roomState = state,
                    )
            }
        } finally {
            applyingRemote = false
        }
    }

    private fun startHeartbeat(
        sessionId: String,
        guestClient: TogetherClient,
    ) {
        heartbeatJob?.cancel()
        heartbeatJob =
            scope.launch {
                var pingId = 0L
                while (client === guestClient) {
                    val now = SystemClock.elapsedRealtime()
                    guestClient.sendHeartbeat(sessionId = sessionId, pingId = pingId++, clientElapsedRealtimeMs = now)
                    delay(HEARTBEAT_INTERVAL_MS)
                }
            }
    }

    private fun cancelHostInactivityTimeout() {
        inactivityJob?.cancel()
        inactivityJob = null
    }

    private fun scheduleHostInactivityTimeout(
        sessionId: String,
        endOnlineSession: (suspend () -> Unit)? = inactivityEndSession,
    ) {
        cancelHostInactivityTimeout()
        inactivityEndSession = endOnlineSession
        inactivityJob =
            scope.launch {
                delay(TOGETHER_HOST_INACTIVITY_TIMEOUT_MS)

                val currentState = _sessionState.value
                val isCurrentHostSession =
                    when (currentState) {
                        is TogetherSessionState.Hosting -> currentState.sessionId == sessionId
                        is TogetherSessionState.HostingOnline -> currentState.sessionId == sessionId
                        is TogetherSessionState.Joined ->
                            currentState.sessionId == sessionId &&
                                currentState.role is TogetherRole.Host
                        else -> false
                    }
                val isLocalAuthority =
                    authorityParticipantId == null ||
                        authorityParticipantId == HOST_PARTICIPANT_ID
                val participants =
                    server?.currentParticipants()
                        ?: onlineHost?.currentParticipants()
                        ?: emptyList()
                val hasConnectedGuest =
                    participants.any { participant ->
                        participant.id != HOST_PARTICIPANT_ID &&
                            participant.isConnected &&
                            !participant.isPending
                    }
                if (!isCurrentHostSession ||
                    !isLocalAuthority ||
                    participantNames.isNotEmpty() ||
                    hasConnectedGuest
                ) {
                    inactivityJob = null
                    return@launch
                }

                inactivityJob = null
                runCatching { endOnlineSession?.invoke() }
                    .onFailure { error ->
                        Log.w(TAG, "unable to end inactive online room: ${error.message}")
                    }
                stopInternal()
                _sessionState.value = TogetherSessionState.Idle
                showRoomClosedNotification()
            }
    }

    private suspend fun stopInternal() {
        cancelHostInactivityTimeout()
        inactivityEndSession = null

        broadcastJob?.cancel()
        broadcastJob = null

        onlineConnectJob?.cancel()
        onlineConnectJob = null

        clientEventsJob?.cancel()
        clientEventsJob = null

        heartbeatJob?.cancel()
        heartbeatJob = null

        deferredPlayFallbackJob?.cancel()
        deferredPlayFallbackJob = null

        clock = null
        selfParticipantId = null
        authorityParticipantId = null
        participantNames.clear()
        pendingSessionId = null
        isOnlineSession = false
        applyingRemote = false
        lastAppliedSentAtElapsedMs = 0L
        lastSentControlAtElapsedMs = 0L
        lastSentControlAction = null
        pendingGuestControl = null
        pendingGuestAddTrack = null
        focusLost = false
        locallyPaused = false
        _clockSynced.value = false
        _clockSnapshot.value = null
        _autoplayTrackIds.value = emptySet()
        _roomState.value = null

        try {
            client?.disconnect()
        } catch (_: Exception) {
        }
        client = null

        try {
            onlineHost?.disconnect()
        } catch (_: Exception) {
        }
        onlineHost = null

        try {
            server?.stop()
        } catch (_: Exception) {
        }
        server = null
    }

    private fun appendChat(message: ChatMessage) {
        val current = _chat.value.toMutableList()
        current.add(message)
        while (current.size > MAX_CHAT_MESSAGES) {
            current.removeAt(0)
        }
        _chat.value = current
    }

    private fun showNotice(message: String) {
        val text = message.trim()
        if (text.isBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (lastNoticeKey == text && now - lastNoticeAtElapsedMs < 1200L) return
        lastNoticeKey = text
        lastNoticeAtElapsedMs = now
        val context = appContext ?: return
        scope.launch(Dispatchers.Main) {
            runCatching {
                android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showParticipantNotification(
        participantName: String,
        joined: Boolean,
    ) {
        val context = appContext ?: return
        val normalizedName = participantName.trim().ifBlank { context.getString(R.string.together_unknown_participant) }
        val contentText =
            context.getString(
                if (joined) {
                    R.string.together_participant_joined_notification
                } else {
                    R.string.together_participant_left_notification
                },
                normalizedName,
            )
        val notification = buildEventNotification(context, contentText)
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(TOGETHER_PARTICIPANT_NOTIFICATION_ID, notification)
        }.onFailure { error ->
            Log.w(TAG, "unable to show participant notification: ${error.message}")
        }
    }

    private fun showRoomClosedNotification() {
        val context = appContext ?: return
        val notification =
            buildEventNotification(
                context,
                context.getString(R.string.together_room_closed_inactivity_notification),
            )
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(TOGETHER_ROOM_CLOSED_NOTIFICATION_ID, notification)
        }.onFailure { error ->
            Log.w(TAG, "unable to show room closed notification: ${error.message}")
        }
    }

    private fun buildEventNotification(
        context: Context,
        contentText: String,
    ): android.app.Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(
                TOGETHER_NOTIFICATION_CHANNEL_ID,
                context.getString(R.string.together_room_events_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.together_room_events_channel_description)
                setShowBadge(false)
            },
        )
        val contentIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(context, TOGETHER_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setContentTitle(context.getString(R.string.listen_together))
            .setContentText(contentText)
            .setContentIntent(contentIntent)
            .setCategory(android.app.Notification.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
    }

    private fun getLocalIpv4Address(): String? =
        runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { it is java.net.Inet4Address }
                ?.hostAddress
        }.getOrNull()

    private fun md5(input: String): String = MessageDigest.getInstance("MD5")
        .digest(input.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private const val TOGETHER_NOTIFICATION_CHANNEL_ID = "together_room_events"
    private const val TOGETHER_PARTICIPANT_NOTIFICATION_ID = 891
    private const val TOGETHER_ROOM_CLOSED_NOTIFICATION_ID = 892
}

private fun isDeviceFile(song: Song): Boolean =
    song.videoId.startsWith("content://") || song.videoId.startsWith("file://")

internal fun Song.toTogetherTrack(playerDurationMs: Long): TogetherTrack = TogetherTrack(
    id = videoId,
    title = title,
    artists = listOfNotNull(artist.takeIf { it.isNotBlank() }),
    durationSec = when {
        playerDurationMs > 0L -> (playerDurationMs / 1000L).toInt()
        else -> TrackMatcher.secondsOf(durationText) ?: -1
    },
    thumbnailUrl = thumbnailUrl,
)

internal fun TogetherTrack.toSong(): Song = Song(
    videoId = id,
    title = title,
    artist = artists.joinToString(", ").ifBlank { title },
    thumbnailUrl = thumbnailUrl,
    durationText = durationSec.takeIf { it >= 0 }?.let { sec ->
        "%d:%02d".format(sec / 60, sec % 60)
    },
    queueTier = QueueTier.USER_QUEUE,
)
