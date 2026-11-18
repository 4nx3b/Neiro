/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from vivi-music (beta branch) ui/screens/ListenTogetherScreen.kt (GPL-3.0),
 * via ArchiveTune's port of it.
 *
 * PORT-NOTE (Neiro): the screen reaches the engine through this app's own
 * [ListenTogether] facade rather than a CompositionLocal-injected Hilt manager,
 * the reference's rememberPreference reads are AppSettings flows, the
 * room-creation clipboard copy keeps its behaviour minus the nav-specific
 * scrollToTop saved-state handle, and the "settings" link at the bottom is the
 * [onOpenSettings] callback this app's navigator hands the screen. The
 * reference's drawable avatars became a small emoji table — this app ships no
 * person-asset drawables — and song search goes through the app's own active
 * [SourceRegistry][com.music.bitchord.data.sources.SourceRegistry] sources
 * instead of a YouTube-only innertube call.
 */

package com.music.bitchord.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.listentogether.ConnectionState
import com.music.bitchord.data.listentogether.JoinRequestPayload
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.ListenTogetherAvatar
import com.music.bitchord.data.listentogether.ListenTogetherEvent
import com.music.bitchord.data.listentogether.ListenTogetherInvite
import com.music.bitchord.data.listentogether.ListenTogetherManager
import com.music.bitchord.data.listentogether.ListenTogetherServers
import com.music.bitchord.data.listentogether.SuggestionReceivedPayload
import com.music.bitchord.data.listentogether.TrackInfo
import com.music.bitchord.data.listentogether.UserInfo
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.TrackMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PORT-NOTE (Neiro): the reference held these in a Hilt ViewModel; this app has
 * no per-screen viewmodels, so the same fields and event translation live in
 * this plain holder the screen owns, with its scope cancelled on leave.
 */
private class ListenTogetherScreenModel(
    private val manager: ListenTogetherManager,
) {
    var roomCodeInput by mutableStateOf("")
    var usernameInput by mutableStateOf("")
    var isCreatingRoom by mutableStateOf(false)
    var isJoiningRoom by mutableStateOf(false)
    var joinErrorMessage by mutableStateOf<String?>(null)

    var selectedUserForMenu by mutableStateOf<String?>(null)
    var selectedUsername by mutableStateOf<String?>(null)

    /**
     * The room-code box accepts a code handed over from outside — an invite
     * link, in practice — exactly as if it had been typed, and [joinRoom] is
     * what a link's tap ultimately drives. The reference's ViewModel had the
     * same reachability through its own fields.
     */
    fun offerRoomCode(code: String) {
        if (roomCodeInput.isBlank()) roomCodeInput = code.take(8).uppercase()
    }

    suspend fun collectEvents() {
        manager.events.collect { event ->
            event.listenTogetherRequestFailureMessage()?.let { message ->
                joinErrorMessage = message
                isJoiningRoom = false
                isCreatingRoom = false
            }
            when (event) {
                is ListenTogetherEvent.JoinRejected -> {
                    val reason = event.reason
                    joinErrorMessage = when {
                        reason.isNullOrBlank() -> "Join request denied"
                        reason.contains("invalid", ignoreCase = true) -> "Invalid room code"
                        else -> "Join request denied: $reason"
                    }
                    isJoiningRoom = false
                    isCreatingRoom = false
                }
                is ListenTogetherEvent.JoinApproved -> {
                    isJoiningRoom = false
                    isCreatingRoom = false
                    joinErrorMessage = null
                }
                is ListenTogetherEvent.RoomCreated -> {
                    isCreatingRoom = false
                    isJoiningRoom = false
                }
                is ListenTogetherEvent.Disconnected -> {
                    isJoiningRoom = false
                    isCreatingRoom = false
                }
                else -> {}
            }
        }
    }

    suspend fun collectConnection() {
        manager.connectionState.collect { state ->
            if (state == ConnectionState.ERROR || state == ConnectionState.DISCONNECTED) {
                isJoiningRoom = false
                isCreatingRoom = false
            }
        }
    }

    fun createRoom(username: String) {
        if (isCreatingRoom || isJoiningRoom) return
        isCreatingRoom = true
        isJoiningRoom = false
        joinErrorMessage = null
        manager.createRoom(username)
    }

    fun joinRoom(roomCode: String, username: String) {
        if (isCreatingRoom || isJoiningRoom) return
        isJoiningRoom = true
        isCreatingRoom = false
        joinErrorMessage = null
        manager.joinRoom(roomCode, username)
    }
}

/**
 * The request-failure text of an event, as the reference's top-level
 * `listenTogetherRequestFailure` helper produced it — null when the event is
 * not a failure the join flow should surface.
 */
private fun ListenTogetherEvent.listenTogetherRequestFailureMessage(): String? = when (this) {
    is ListenTogetherEvent.ConnectionError -> error
    is ListenTogetherEvent.ServerError -> message
    is ListenTogetherEvent.JoinRejected -> reason
    is ListenTogetherEvent.Kicked -> reason
    else -> null
}

/**
 * The Listen Together page: the connection card, the join/create room section
 * with its 8-box room code, and — once in a room — the members, the host's
 * pending join requests and song suggestions, the room queue and the guest's
 * suggest-a-song card. A single scrolling list that fills the page; the
 * [contentPadding] it is handed keeps the first and last rows clear of the
 * app's floating bars.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListenTogetherScreen(
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val manager = ListenTogether.manager

    if (manager == null) {
        NotConfiguredContent(modifier)
        return
    }

    val connectionState by manager.connectionState.collectAsState()
    val roomState by manager.roomState.collectAsState()
    val userId by manager.userId.collectAsState()
    val pendingJoinRequests by manager.pendingJoinRequests.collectAsState()
    val pendingSuggestions by manager.pendingSuggestions.collectAsState()
    val unreadMessageCount by manager.unreadMessageCount.collectAsState()

    val savedUsername by AppSettings.listenTogetherUsername.collectAsState()
    val pendingInviteCode by ListenTogetherInvite.pending.collectAsState()

    val model = remember(manager) { ListenTogetherScreenModel(manager) }
    val scope = rememberCoroutineScope()
    DisposableEffect(manager) {
        val jobs = listOf(
            scope.launch { model.collectEvents() },
            scope.launch { model.collectConnection() },
        )
        onDispose { jobs.forEach { it.cancel() } }
    }

    LaunchedEffect(savedUsername) {
        if (model.usernameInput.isBlank() && savedUsername.isNotBlank()) {
            model.usernameInput = savedUsername
        }
    }

    // An invite link's code lands in the room-code box as if typed, then joins
    // with whatever name the room already knows this listener by — the
    // reference's deep link path, kept but routed through the screen so the
    // join shows up in the same waiting-for-approval flow a manual join does.
    LaunchedEffect(pendingInviteCode) {
        val code = pendingInviteCode ?: return@LaunchedEffect
        if (!manager.isInRoom) {
            model.offerRoomCode(code)
            val username = model.usernameInput.takeIf { it.isNotBlank() } ?: savedUsername
            val finalUsername = username.trim().ifBlank { "Guest" }
            Toast.makeText(context, context.getString(R.string.joining_room, code), Toast.LENGTH_SHORT).show()
            model.joinRoom(code, finalUsername)
        }
        ListenTogetherInvite.handled()
    }

    LaunchedEffect(manager) {
        manager.events.collect { event ->
            if (event is ListenTogetherEvent.RoomCreated) {
                // The code is on screen in a moment; copying it on arrival is one
                // fewer tap on the way to sharing it with the first listener.
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("ListenTogetherRoom", event.roomCode)
                clipboard.setPrimaryClip(clip)
            }
        }
    }

    val isInRoom = manager.isInRoom
    val isHost = roomState?.hostId == userId

    if (model.selectedUserForMenu != null && model.selectedUsername != null) {
        UserActionDialog(
            username = model.selectedUsername ?: "",
            onKick = {
                model.selectedUserForMenu?.let { manager.kickUser(it, "Removed by host") }
                model.selectedUserForMenu = null
                model.selectedUsername = null
            },
            onPermanentKick = {
                model.selectedUserForMenu?.let { blockedId ->
                    model.selectedUsername?.let { username ->
                        manager.blockUser(username)
                        manager.kickUser(blockedId, context.getString(R.string.user_blocked_by_host))
                    }
                }
                model.selectedUserForMenu = null
                model.selectedUsername = null
            },
            onTransferOwnership = {
                model.selectedUserForMenu?.let { manager.transferHost(it) }
                model.selectedUserForMenu = null
                model.selectedUsername = null
            },
            onDismiss = {
                model.selectedUserForMenu = null
                model.selectedUsername = null
            },
        )
    }

    var showSuggestSearch by rememberSaveable { mutableStateOf(false) }

    if (showSuggestSearch) {
        SuggestSongDialog(
            onDismiss = { showSuggestSearch = false },
            onSuggest = { track ->
                showSuggestSearch = false
                manager.suggestTrack(track)
            },
        )
    }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header (always visible)
        item { HeaderSection() }

        item {
            ConnectionStatusCard(
                connectionState = connectionState,
                onConnect = { manager.connect() },
                onDisconnect = { manager.disconnect() },
                onReconnect = { manager.forceReconnect() },
                errorMessage = model.joinErrorMessage,
            )
        }

        if (connectionState == ConnectionState.CONNECTED) {
            item {
                Text(
                    text = stringResource(R.string.listen_together_background_disconnect_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Join/Create section (always visible, morphs based on state)
        item {
            JoinCreateRoomSection(
                usernameInput = model.usernameInput,
                onUsernameChange = { model.usernameInput = it },
                roomCodeInput = model.roomCodeInput,
                onRoomCodeChange = { model.roomCodeInput = it },
                savedUsername = savedUsername,
                isJoiningRoom = model.isJoiningRoom,
                joinErrorMessage = model.joinErrorMessage,
                bringIntoViewRequester = bringIntoViewRequester,
                isInRoom = isInRoom,
                activeRoomCode = roomState?.roomCode ?: "",
                onCreateRoom = {
                    val username = model.usernameInput.takeIf { it.isNotBlank() } ?: savedUsername
                    val finalUsername = username.trim()
                    if (finalUsername.isNotBlank()) {
                        AppSettings.setListenTogetherUsername(finalUsername)
                        Toast.makeText(context, R.string.creating_room, Toast.LENGTH_SHORT).show()
                        model.createRoom(finalUsername)
                    } else {
                        Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                    }
                },
                onJoinRoom = {
                    val username = model.usernameInput.takeIf { it.isNotBlank() } ?: savedUsername
                    val finalUsername = username.trim()
                    if (finalUsername.isNotBlank()) {
                        AppSettings.setListenTogetherUsername(finalUsername)
                        Toast.makeText(
                            context,
                            context.getString(R.string.joining_room, model.roomCodeInput),
                            Toast.LENGTH_SHORT,
                        ).show()
                        model.joinRoom(model.roomCodeInput, finalUsername)
                    } else {
                        Toast.makeText(context, R.string.error_username_empty, Toast.LENGTH_SHORT).show()
                    }
                },
                onLeaveRoom = { manager.leaveRoom() },
                onCancelJoin = {
                    if (model.isJoiningRoom) {
                        model.isJoiningRoom = false
                        model.joinErrorMessage = null
                        manager.leaveRoom()
                    }
                },
                onFieldFocused = {
                    scope.launch { bringIntoViewRequester.bringIntoView() }
                },
            )
        }

        // Room details (visible when in a room)
        if (isInRoom) {
            roomState?.let { room ->
                // Room status (copy/share/chat actions)
                item {
                    RoomStatusCard(
                        roomCode = room.roomCode,
                        isHost = isHost,
                        context = context,
                        onOpenChat = onOpenChat,
                        unreadMessageCount = unreadMessageCount,
                    )
                }

                // Connected users
                val connectedUsers = room.users.filter { it.isConnected }
                val currentUserIdValue = userId ?: ""
                item {
                    ConnectedUsersSection(
                        users = connectedUsers,
                        isHost = isHost,
                        currentUserId = currentUserIdValue,
                        onUserClick = { clickedUserId, username ->
                            if (isHost && clickedUserId != currentUserIdValue) {
                                model.selectedUserForMenu = clickedUserId
                                model.selectedUsername = username
                            }
                        },
                    )
                }

                // Pending join requests (host only)
                if (isHost && pendingJoinRequests.isNotEmpty()) {
                    item {
                        PendingJoinRequestsSection(
                            requests = pendingJoinRequests,
                            onApprove = { manager.approveJoin(it) },
                            onReject = { manager.rejectJoin(it, "Rejected by host") },
                        )
                    }
                }

                // Pending suggestions (host only)
                if (isHost && pendingSuggestions.isNotEmpty()) {
                    item {
                        PendingSuggestionsSection(
                            suggestions = pendingSuggestions,
                            onApprove = { manager.approveSuggestion(it) },
                            onReject = { manager.rejectSuggestion(it, "Rejected by host") },
                        )
                    }
                }

                item {
                    RoomQueueSection(
                        queue = room.queue,
                        currentTrackId = room.currentTrack?.id,
                    )
                }

                if (!isHost) {
                    item {
                        SuggestSongCard(
                            onOpenSearch = { showSuggestSearch = true },
                        )
                    }
                }
            }
        }

        // Settings link
        item {
            SettingsGroup(topSpacing = 0.dp) {
                val settingsTitle = stringResource(R.string.settings)
                SettingsRow(
                    icon = Icons.Rounded.Settings,
                    title = settingsTitle,
                    subtitle = stringResource(R.string.listen_together_settings_desc),
                    onClick = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun NotConfiguredContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.listen_together),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.listen_together_not_configured),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeaderSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left side: large icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Groups,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(48.dp),
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Right side: title + subtitle
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.listen_together),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.listen_together_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onReconnect: () -> Unit,
    errorMessage: String?,
) {
    val iconTint = when (connectionState) {
        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.tertiary
        ConnectionState.ERROR -> MaterialTheme.colorScheme.error
        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
            .background(
                if (connectionState == ConnectionState.ERROR) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top area: Text on left, Icon on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (connectionState) {
                            ConnectionState.CONNECTED -> stringResource(R.string.listen_together_connected)
                            ConnectionState.CONNECTING -> stringResource(R.string.listen_together_connecting)
                            ConnectionState.RECONNECTING -> stringResource(R.string.listen_together_reconnecting)
                            ConnectionState.ERROR -> stringResource(R.string.listen_together_error)
                            ConnectionState.DISCONNECTED -> stringResource(R.string.listen_together_disconnected)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = iconTint,
                    )
                    if ((connectionState == ConnectionState.ERROR || connectionState == ConnectionState.DISCONNECTED) &&
                        !errorMessage.isNullOrBlank()
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (connectionState == ConnectionState.ERROR) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    if (connectionState == ConnectionState.CONNECTING ||
                        connectionState == ConnectionState.RECONNECTING
                    ) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .clip(RoundedCornerShape(8.dp)),
                            color = iconTint,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconTint.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when (connectionState) {
                            ConnectionState.CONNECTED -> Icons.Rounded.Cloud
                            ConnectionState.ERROR -> Icons.Rounded.ErrorOutline
                            ConnectionState.DISCONNECTED -> Icons.Rounded.CloudOff
                            else -> Icons.Rounded.CloudOff
                        },
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // Bottom Actions
            if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
                Surface(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.connect),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        onClick = onDisconnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.disconnect),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Surface(
                        onClick = onReconnect,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.listen_together_reconnect),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomStatusCard(
    roomCode: String,
    context: Context,
    onOpenChat: () -> Unit,
    unreadMessageCount: Int,
) {
    // Action Row without the bulky card background
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val serverUrl by AppSettings.listenTogetherServerUrl.collectAsState()

        // The invite link must point at the server the room actually lives on —
        // only the JSON (vivi) servers serve a web client at /listen. The
        // protobuf server has no web client, so the link button is hidden there.
        val webInviteSupported =
            remember(serverUrl) {
                ListenTogetherServers.findByUrl(serverUrl)?.protocol !=
                    com.music.bitchord.data.listentogether.ListenTogetherProtocol.PROTOBUF
            }

        // The protobuf server has no chat relay at all — its protocol carries
        // no chat message type — so the chat entry point is hidden entirely
        // instead of leading to a dead composer.
        val chatSupported =
            remember(serverUrl) {
                ListenTogetherServers.findByUrl(serverUrl)?.protocol !=
                    com.music.bitchord.data.listentogether.ListenTogetherProtocol.PROTOBUF
            }
        val inviteLink =
            remember(roomCode, serverUrl) {
                "https://${ListenTogetherServers.inviteHost(serverUrl)}/listen?code=$roomCode"
            }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (chatSupported) {
                FilledTonalButton(
                    onClick = onOpenChat,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadMessageCount > 0) {
                                Badge { Text(unreadMessageCount.toString()) }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubble,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.comments),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (webInviteSupported) {
                FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Listen Together Link", inviteLink)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.copy_link),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            FilledTonalButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Room Code", roomCode)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.copy_code),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The room-member avatar table the wire's `avatar_index` selects.
 *
 * PORT-NOTE (Neiro): the reference resolved these to drawable assets this app
 * does not ship, so every indexed avatar is a distinct person emoji instead —
 * index 0 stays the username initial and index [ListenTogetherAvatar.CUSTOM_AVATAR_INDEX]
 * the broadcast custom picture, both of which the wire keeps as they were.
 */
internal val AvatarGlyphs = listOf(
    "👨", "👩", "🧑", "🧔", "🧑‍💻", "🕵️", "👨‍🚀", "🦸",
    "🧙", "👩‍🎨", "🧝", "🤵", "👸",
)

/** Draws the avatar [avatarIndex] names: custom picture, indexed glyph, or the initial. */
@Composable
private fun AvatarFace(
    avatarIndex: Int,
    userId: String?,
    fallbackName: String,
    contentColor: androidx.compose.ui.graphics.Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
) {
    val manager = ListenTogether.manager
    val currentUserId by (manager?.userId ?: MutableStateFlow<String?>(null)).collectAsState()
    val customAvatars by (manager?.customAvatars ?: MutableStateFlow<Map<String, ByteArray>>(emptyMap()))
        .collectAsState()
    val selfBytes = remember(manager, currentUserId) { manager?.customAvatarFor(currentUserId) }
    val bytes = if (userId == null || userId == currentUserId) selfBytes else customAvatars[userId]
    val bitmap: Bitmap? = produceState<Bitmap?>(initialValue = null, bytes) {
        value = bytes?.let { encoded ->
            withContext(Dispatchers.Default) {
                runCatching { BitmapFactory.decodeByteArray(encoded, 0, encoded.size) }.getOrNull()
            }
        }
    }.value

    when {
        bitmap != null -> {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
        avatarIndex == 0 -> {
            Text(
                text = fallbackName.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
        else -> {
            Text(
                text = AvatarGlyphs.getOrElse(avatarIndex - 1) { "👤" },
                fontSize = fontSize,
                modifier = Modifier.fillMaxSize(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ConnectedUsersSection(
    users: List<UserInfo>,
    isHost: Boolean,
    currentUserId: String,
    onUserClick: (String, String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = "${stringResource(R.string.connected_users)} (${users.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                users.forEach { user ->
                    UserAvatar(
                        user = user,
                        isCurrentUser = user.userId == currentUserId,
                        isClickable = isHost && user.userId != currentUserId,
                        onClick = { onUserClick(user.userId, user.cleanUsername) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UserAvatar(
    user: UserInfo,
    isCurrentUser: Boolean,
    isClickable: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(enabled = isClickable, onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = when {
                    user.isHost -> MaterialTheme.colorScheme.primary
                    isCurrentUser -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.tertiaryContainer
                },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AvatarFace(
                        avatarIndex = user.avatarIndex,
                        userId = user.userId,
                        fallbackName = user.cleanUsername,
                        contentColor = when {
                            user.isHost -> MaterialTheme.colorScheme.onPrimary
                            isCurrentUser -> MaterialTheme.colorScheme.onSecondary
                            else -> MaterialTheme.colorScheme.onTertiaryContainer
                        },
                        fontSize = 28.sp,
                    )
                }
            }

            if (user.isHost || isCurrentUser) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(20.dp),
                    shape = CircleShape,
                    color = if (user.isHost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = if (user.isHost) Icons.Rounded.Star else Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = user.cleanUsername,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Medium,
            color = if (user.isHost) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        if (user.isHost) {
            Text(
                text = stringResource(R.string.host_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            )
        } else if (isCurrentUser) {
            Text(
                text = stringResource(R.string.you_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun PendingJoinRequestsSection(
    requests: List<JoinRequestPayload>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.listen_together_join_requests),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            requests.forEach { request ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary,
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            AvatarFace(
                                avatarIndex = request.avatarIndex,
                                userId = request.userId,
                                fallbackName = request.cleanUsername,
                                contentColor = MaterialTheme.colorScheme.onSecondary,
                                fontSize = 18.sp,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = request.cleanUsername,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onApprove(request.userId) }) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.approve),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(onClick = { onReject(request.userId) }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.reject),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingSuggestionsSection(
    suggestions: List<SuggestionReceivedPayload>,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.pending_suggestions),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            suggestions.forEach { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = suggestion.trackInfo.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = suggestion.fromUsername,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onApprove(suggestion.suggestionId) }) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.approve),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    IconButton(onClick = { onReject(suggestion.suggestionId) }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.reject),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JoinCreateRoomSection(
    usernameInput: String,
    onUsernameChange: (String) -> Unit,
    roomCodeInput: String,
    onRoomCodeChange: (String) -> Unit,
    savedUsername: String,
    isJoiningRoom: Boolean,
    joinErrorMessage: String?,
    bringIntoViewRequester: BringIntoViewRequester,
    isInRoom: Boolean = false,
    activeRoomCode: String = "",
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onLeaveRoom: () -> Unit = {},
    onCancelJoin: () -> Unit = {},
    onFieldFocused: () -> Unit = {},
) {
    val avatarIndex by AppSettings.listenTogetherAvatarIndex.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val usernameBorderColor by animateColorAsState(
                targetValue = if (isInRoom) {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "usernameBorderColor",
            )
            val usernameContainerColor by animateColorAsState(
                targetValue = if (isInRoom) {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "usernameContainerColor",
            )
            val usernameIconTint by animateColorAsState(
                targetValue = if (isInRoom) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "usernameIconTint",
            )

            // Username input — read-only when in room
            OutlinedTextField(
                value = if (isInRoom) (usernameInput.takeIf { it.isNotBlank() } ?: savedUsername) else usernameInput,
                onValueChange = if (isInRoom) { _ -> } else onUsernameChange,
                readOnly = isInRoom,
                label = { Text(stringResource(R.string.username)) },
                placeholder = { Text(stringResource(R.string.enter_username)) },
                leadingIcon = {
                    AvatarFace(
                        avatarIndex = avatarIndex,
                        userId = null,
                        fallbackName = (usernameInput.takeIf { it.isNotBlank() } ?: savedUsername),
                        contentColor = usernameIconTint,
                        fontSize = 16.sp,
                    )
                },
                trailingIcon = {
                    AnimatedContent(
                        targetState = isInRoom,
                        label = "usernameTrailingIcon",
                    ) { inRoom ->
                        if (inRoom) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        } else if (usernameInput.isNotBlank()) {
                            IconButton(onClick = { onUsernameChange("") }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                )
                            }
                        } else {
                            Spacer(Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = usernameBorderColor,
                    focusedContainerColor = usernameContainerColor,
                    unfocusedContainerColor = usernameContainerColor,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { if (it.isFocused && !isInRoom) onFieldFocused() },
            )

            // Room code label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Start),
            ) {
                Icon(
                    imageVector = if (isInRoom) Icons.Rounded.Link else Icons.Rounded.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.room_code),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // OTP boxes — editable when not in room, read-only display when in room
            if (isInRoom) {
                // Staggered one-by-one reveal animation when room code arrives.
                // isFirstFrame detects an already-connected state entering
                // instantly, silencing the animation.
                val isFirstFrame = remember { mutableStateOf(true) }
                DisposableEffect(Unit) {
                    isFirstFrame.value = false
                    onDispose { }
                }

                var revealedCount by rememberSaveable(activeRoomCode) {
                    mutableStateOf(if (isFirstFrame.value && activeRoomCode.isNotBlank()) activeRoomCode.length else 0)
                }

                LaunchedEffect(activeRoomCode) {
                    if (activeRoomCode.isNotBlank() && revealedCount < activeRoomCode.length) {
                        repeat(activeRoomCode.length - revealedCount) {
                            delay(100L)
                            revealedCount++
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    repeat(8) { index ->
                        val isRevealed = index < revealedCount
                        val char = if (isRevealed) activeRoomCode.getOrNull(index)?.toString() ?: "" else ""
                        val charScale by animateFloatAsState(
                            targetValue = if (isRevealed) 1f else 0.4f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            label = "charScale_$index",
                        )
                        val charAlpha by animateFloatAsState(
                            targetValue = if (isRevealed) 1f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "charAlpha_$index",
                        )
                        val boxColor by animateColorAsState(
                            targetValue = if (isRevealed) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "boxColor_$index",
                        )
                        val borderColor by animateColorAsState(
                            targetValue = if (isRevealed) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            },
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "borderColor_$index",
                        )
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = boxColor,
                            border = BorderStroke(1.dp, borderColor),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = char,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.graphicsLayer(
                                        scaleX = charScale,
                                        scaleY = charScale,
                                        alpha = charAlpha,
                                    ),
                                )
                            }
                        }
                    }
                }
            } else {
                // Editable OTP input
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BasicTextField(
                        value = roomCodeInput,
                        onValueChange = { if (it.length <= 8) onRoomCodeChange(it.uppercase()) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .onFocusChanged { if (it.isFocused) onFieldFocused() },
                        decorationBox = { innerTextField ->
                            Box {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    repeat(8) { index ->
                                        val char = roomCodeInput.getOrNull(index)?.toString() ?: ""
                                        val isFocused = roomCodeInput.length == index
                                        val hasChar = char.isNotEmpty()
                                        val scale by animateFloatAsState(
                                            targetValue = if (hasChar) 1f else 0.85f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium,
                                            ),
                                            label = "inputCharScale_$index",
                                        )
                                        val boxColor by animateColorAsState(
                                            targetValue = if (hasChar) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                            label = "inputBoxColor_$index",
                                        )
                                        val textColor by animateColorAsState(
                                            targetValue = if (hasChar) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                            label = "inputTextColor_$index",
                                        )
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .graphicsLayer(scaleX = scale, scaleY = scale),
                                            shape = RoundedCornerShape(8.dp),
                                            color = boxColor,
                                            border = if (isFocused) {
                                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                            } else if (hasChar) {
                                                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                            } else {
                                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                            },
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = char,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textColor,
                                                )
                                            }
                                        }
                                    }
                                }
                                Box(modifier = Modifier.matchParentSize().alpha(0.01f)) {
                                    innerTextField()
                                }
                            }
                        },
                    )

                    AnimatedVisibility(visible = roomCodeInput.isNotBlank()) {
                        androidx.compose.material3.FilledIconButton(
                            onClick = {
                                onRoomCodeChange("")
                                onCancelJoin()
                            },
                            modifier = Modifier.padding(start = 8.dp),
                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(Icons.Rounded.Close, null)
                        }
                    }
                }
            }

            // Waiting for approval indicator (only when not in room)
            AnimatedVisibility(
                visible = isJoiningRoom && !isInRoom,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.waiting_for_approval),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = joinErrorMessage != null && !isInRoom,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = joinErrorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            // Action buttons — morph between Create/Join and Leave Room
            if (isInRoom) {
                Button(
                    onClick = onLeaveRoom,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Login,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.leave_room), fontWeight = FontWeight.SemiBold)
                }
            } else {
                val hasUsername = usernameInput.trim().isNotBlank() || savedUsername.isNotBlank()
                val hasRoomCode = roomCodeInput.length == 8

                // Morphing Create/Join Button
                AnimatedVisibility(visible = hasUsername) {
                    val containerColor by animateColorAsState(
                        targetValue = if (hasRoomCode) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "buttonColorAnim",
                    )
                    Button(
                        onClick = if (hasRoomCode) onJoinRoom else onCreateRoom,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = hasUsername,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = containerColor,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        AnimatedContent(
                            targetState = hasRoomCode,
                            transitionSpec = {
                                tween<Float>(200).let {
                                    fadeIn(it) togetherWith fadeOut(it)
                                }
                            },
                            label = "JoinCreateButtonAnim",
                        ) { isJoin ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isJoin) {
                                    Icon(
                                        imageVector = Icons.Rounded.Group,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.join_room), fontWeight = FontWeight.SemiBold)
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.create_room), fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserActionDialog(
    username: String,
    onKick: () -> Unit,
    onPermanentKick: () -> Unit,
    onTransferOwnership: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.music.bitchord.ui.components.GlassAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.manage_user),
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onKick),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.kick_user),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = stringResource(R.string.kick_user_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPermanentKick),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.permanently_kick_user),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.permanently_kick_user_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTransferOwnership),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.transfer_ownership),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.transfer_ownership_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomQueueSection(
    queue: List<TrackInfo>,
    currentTrackId: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.listen_together_room_queue),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (queue.isEmpty()) {
                Text(
                    text = stringResource(R.string.listen_together_queue_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                queue.take(12).forEachIndexed { index, track ->
                    val isCurrent = track.id == currentTrackId
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .alpha(if (isCurrent) 1f else 0.75f),
                    ) {
                        Text(
                            text = if (isCurrent) "▶" else "${index + 1}.",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.width(28.dp),
                        )
                        track.thumbnail?.let { thumb ->
                            AsyncImage(
                                model = thumb,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (queue.size > 12) {
                    Text(
                        text = stringResource(R.string.listen_together_queue_more, queue.size - 12),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestSongCard(
    onOpenSearch: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSearch),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.listen_together_suggest_song),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.listen_together_suggest_song_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** The app's own search sources, one query — the first rows that answer. */
internal suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
    val found = mutableListOf<Song>()
    for (source in com.music.bitchord.data.sources.SourceRegistry.active()) {
        val results = runCatching { source.search(query, limit = 12) }.getOrNull().orEmpty()
        found += results.filter { it.videoId.isNotBlank() }
        if (found.size >= 12) break
    }
    found
}

/**
 * The wire's track as this screen suggests it — the same shape the engine's
 * own Song-to-wire mapping produces.
 */
internal fun Song.toSuggestedTrack(): TrackInfo = TrackInfo(
    id = videoId,
    title = title,
    artist = artist,
    album = albumName,
    duration = TrackMatcher.secondsOf(durationText)?.let { it * 1000L } ?: 0L,
    thumbnail = thumbnailUrl,
)

/**
 * Search-and-suggest dialog: the app's own sources queried, rows tapped to
 * send the pick to the host as a suggestion.
 */
@Composable
private fun SuggestSongDialog(
    onDismiss: () -> Unit,
    onSuggest: (TrackInfo) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Song>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    // Debounced search over the app's own sources — only the freshest query
    // may land its rows, so a slow source behind a newer keystroke is dropped.
    LaunchedEffect(query) {
        if (query.length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(350L)
        searching = true
        val searchedFor = query
        val found = searchSongs(searchedFor)
        if (searchedFor == query) {
            results = found
            searching = false
        }
    }

    com.music.bitchord.ui.components.GlassAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.listen_together_suggest_song),
                fontWeight = FontWeight.Bold,
            )
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (searching) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(32.dp),
                )
            } else if (query.length >= 2 && results.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_results_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    results.take(8).forEach { song ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSuggest(song.toSuggestedTrack()) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            song.thumbnailUrl?.let { thumb ->
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
