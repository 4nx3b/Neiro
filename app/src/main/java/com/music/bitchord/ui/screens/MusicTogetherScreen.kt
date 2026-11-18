/*
 * Neiro (2026)
 * © 4nx3b — github.com/4nx3b
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */
package com.music.bitchord.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.together.TogetherDeepLink
import com.music.bitchord.data.together.TogetherEngine
import com.music.bitchord.data.together.TogetherLink
import com.music.bitchord.data.together.TogetherParticipant
import com.music.bitchord.data.together.TogetherRole
import com.music.bitchord.data.together.TogetherRoomSettings
import com.music.bitchord.data.together.TogetherRoomState
import com.music.bitchord.data.together.TogetherSessionState
import com.music.bitchord.data.together.isConnectedToSession
import com.music.bitchord.data.together.roomStateOrNull
import com.music.bitchord.data.together.sessionIdOrNull
import com.music.bitchord.ui.components.GlassAlertDialog
import com.music.bitchord.ui.components.IosSwitch
import com.music.bitchord.ui.icons.SfIcons
import java.util.Date

/**
 * Music Together: host a session on the LAN or through the online relay, join
 * one from a link or a code, and watch the room — participants, playback, the
 * activity log, and the chat everyone in the session shares.
 *
 * State is held here the way this app's screens hold it: the engine's flows
 * collected with `collectAsStateWithLifecycle`, the screen-local choices
 * (LAN vs online, the open dialog) as rememberable state. The session machine
 * itself lives in [TogetherEngine] and in the playback service, so leaving
 * this screen changes nothing about a session's lifetime.
 */
@Composable
fun MusicTogetherScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val sessionState by TogetherEngine.sessionState.collectAsStateWithLifecycle()
    val roomState by TogetherEngine.roomState.collectAsStateWithLifecycle()
    val chat by TogetherEngine.chat.collectAsStateWithLifecycle()
    val displayName by AppSettings.togetherDisplayName.collectAsStateWithLifecycle()
    val port by AppSettings.togetherDefaultPort.collectAsStateWithLifecycle()
    val allowAdd by AppSettings.togetherAllowGuestsToAddTracks.collectAsStateWithLifecycle()
    val allowControl by AppSettings.togetherAllowGuestsControlPlayback.collectAsStateWithLifecycle()
    val requireApproval by AppSettings.togetherRequireHostApproval.collectAsStateWithLifecycle()
    val lastJoinLink by AppSettings.togetherLastJoinLink.collectAsStateWithLifecycle()
    val welcomeShown by AppSettings.togetherWelcomeShown.collectAsStateWithLifecycle()

    var hostModeOnline by rememberSaveable { mutableStateOf(false) }
    var joinModeOnline by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<TogetherDialog>(TogetherDialog.None) }
    var welcomeDismissedThisSession by remember { mutableStateOf(false) }
    var welcomeDontShowAgain by remember { mutableStateOf(true) }
    var chatInput by remember { mutableStateOf("") }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val activityLog = remember { TogetherActivityLog() }
    LaunchedEffect(sessionState) {
        activityLog.onSessionState(sessionState)
    }

    LaunchedEffect(toastMessage) {
        val text = toastMessage ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        toastMessage = null
    }

    val pendingInvite by TogetherDeepLink.pending.collectAsStateWithLifecycle()
    LaunchedEffect(pendingInvite) {
        val invite = pendingInvite ?: return@LaunchedEffect
        if (sessionState is TogetherSessionState.Idle || sessionState is TogetherSessionState.Error) {
            val link = invite.toDeepLink()
            AppSettings.setTogetherLastJoinLink(link)
            TogetherEngine.joinByLink(link, displayName)
            TogetherDeepLink.handled()
        } else {
            TogetherDeepLink.handled()
        }
    }

    val isHostRole =
        when (sessionState) {
            is TogetherSessionState.Hosting,
            is TogetherSessionState.HostingOnline,
            -> true

            is TogetherSessionState.Joined -> (sessionState as TogetherSessionState.Joined).role is TogetherRole.Host
            else -> false
        }
    val isJoinedAsGuest = sessionState is TogetherSessionState.Joined &&
        (sessionState as TogetherSessionState.Joined).role is TogetherRole.Guest
    val isWaitingApproval =
        isJoinedAsGuest &&
            (sessionState as TogetherSessionState.Joined).roomState.participants
                .firstOrNull { it.id == (sessionState as TogetherSessionState.Joined).selfParticipantId }
                ?.isPending == true
    val isCreatingSessionLoading =
        when (sessionState) {
            is TogetherSessionState.Hosting -> (sessionState as TogetherSessionState.Hosting).roomState == null
            is TogetherSessionState.HostingOnline -> (sessionState as TogetherSessionState.HostingOnline).roomState == null
            else -> false
        }
    val isJoining =
        sessionState is TogetherSessionState.Joining || sessionState is TogetherSessionState.JoiningOnline
    val active = sessionState.isConnectedToSession
    val isJoinedAsAcceptedGuest = isJoinedAsGuest && !isWaitingApproval
    val disableJoinUi = isHostRole || isCreatingSessionLoading || isJoinedAsGuest

    val roomSettings = TogetherRoomSettings(
        allowGuestsToAddTracks = allowAdd,
        allowGuestsToControlPlayback = allowControl,
        requireHostApprovalToJoin = requireApproval,
    )

    val pushSettingsToActiveSession: () -> Unit = {
        if (active && !isJoinedAsGuest) {
            TogetherEngine.updateSettings(roomSettings)
        }
    }

    val joinInput = lastJoinLink
    val canJoin =
        if (joinModeOnline) {
            joinInput.trim().isNotBlank()
        } else {
            TogetherLink.decode(joinInput) != null
        }

    val startSession: () -> Unit = {
        val mayStart = !isCreatingSessionLoading && !isJoining && !isHostRole && sessionState !is TogetherSessionState.Joined
        if (mayStart) {
            val name = displayName.trim().ifBlank { android.os.Build.MODEL ?: context.getString(R.string.app_name) }
            if (hostModeOnline) {
                TogetherEngine.startHostingOnline(displayName = name, settings = roomSettings)
            } else {
                TogetherEngine.startHosting(displayName = name, port = port, settings = roomSettings)
            }
        }
    }

    val submitJoinInput: (String) -> Unit = { raw ->
        val trimmed = raw.trim()
        if (trimmed.isNotBlank()) {
            AppSettings.setTogetherLastJoinLink(trimmed)
            val name = displayName.trim().ifBlank { android.os.Build.MODEL ?: context.getString(R.string.app_name) }
            if (joinModeOnline) {
                TogetherEngine.joinByCode(trimmed, name)
            } else {
                TogetherEngine.joinByLink(trimmed, name)
            }
        }
    }

    val sessionShare: Pair<Int, String>? =
        when (sessionState) {
            is TogetherSessionState.Hosting -> R.string.session_link to (sessionState as TogetherSessionState.Hosting).joinLink
            is TogetherSessionState.HostingOnline -> R.string.session_code to (sessionState as TogetherSessionState.HostingOnline).code
            else -> null
        }

    val selfId =
        when (sessionState) {
            is TogetherSessionState.Hosting,
            is TogetherSessionState.HostingOnline,
            -> TogetherEngine.HOST_PARTICIPANT_ID

            is TogetherSessionState.Joined -> (sessionState as TogetherSessionState.Joined).selfParticipantId
            else -> null
        }
    val onlineRoom = sessionState is TogetherSessionState.HostingOnline || TogetherEngine.onlineRoom

    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(contentPadding)) {
        val wide = maxWidth >= WIDE_BREAKPOINT.dp
        if (wide) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.25f)
                        .fillMaxHeight()
                        .widthIn(max = 720.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    SettingsPageTitle(stringResource(R.string.listen_together))
                    StatusCard(
                        sessionState = sessionState,
                        sessionShare = sessionShare,
                        active = active,
                        isJoining = isJoining,
                        onCopy = { label, value ->
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString(value))
                            toastMessage = "${context.getString(R.string.copied)} · ${context.getString(label)}"
                        },
                        onShare = { value ->
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, value)
                                    },
                                    null,
                                ),
                            )
                        },
                        onLeave = { TogetherEngine.leave() },
                    )
                    PlaybackCard(roomState = roomState)
                    if (!isJoinedAsGuest) {
                        HostControlsCard(
                            onlineMode = hostModeOnline,
                            displayName = displayName,
                            port = port,
                            allowAdd = allowAdd,
                            allowControl = allowControl,
                            requireApproval = requireApproval,
                            startEnabled = !isCreatingSessionLoading && !isJoining && !isHostRole && sessionState !is TogetherSessionState.Joined,
                            loading = isCreatingSessionLoading,
                            onSetHostModeOnline = { hostModeOnline = it },
                            onEditDisplayName = { dialog = TogetherDialog.DisplayName(displayName) },
                            onEditPort = { dialog = TogetherDialog.Port(port.toString()) },
                            onSetAllowAdd = {
                                AppSettings.setTogetherAllowGuestsToAddTracks(it)
                                pushSettingsToActiveSession()
                            },
                            onSetAllowControl = {
                                AppSettings.setTogetherAllowGuestsControlPlayback(it)
                                pushSettingsToActiveSession()
                            },
                            onSetRequireApproval = {
                                AppSettings.setTogetherRequireHostApproval(it)
                                pushSettingsToActiveSession()
                            },
                            onStart = { startSession() },
                        )
                    }
                    if (!active) {
                        JoinControlsCard(
                            onlineMode = joinModeOnline,
                            input = joinInput,
                            canJoin = canJoin && !disableJoinUi && !isJoining && !isJoinedAsAcceptedGuest && !isWaitingApproval,
                            disabled = disableJoinUi,
                            joined = isJoinedAsAcceptedGuest,
                            waitingApproval = isWaitingApproval,
                            joining = isJoining,
                            onSetJoinModeOnline = { if (!disableJoinUi) joinModeOnline = it },
                            onEditInput = { dialog = TogetherDialog.Join(joinInput) },
                            onJoin = { submitJoinInput(joinInput) },
                        )
                    }
                    ChatCard(
                        chat = chat,
                        selfId = selfId,
                        connected = active,
                        input = chatInput,
                        onInputChange = { chatInput = it },
                        onSend = {
                            TogetherEngine.sendChat(chatInput)
                            chatInput = ""
                        },
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .widthIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(28.dp))
                    ParticipantsCard(
                        roomState = roomState,
                        isHostRole = isHostRole,
                        onlineRoom = onlineRoom,
                        onApprove = { TogetherEngine.approveParticipant(it, true) },
                        onReject = { TogetherEngine.approveParticipant(it, false) },
                        onTransferHost = { participant ->
                            dialog = TogetherDialog.TransferHost(participant.id, participant.name)
                        },
                        onKick = { participant ->
                            dialog = TogetherDialog.KickParticipant(participant.id, participant.name)
                        },
                        onBan = { participant ->
                            dialog = TogetherDialog.BanParticipant(participant.id, participant.name)
                        },
                    )
                    ActivityLogCard(entries = activityLog.entries)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                SettingsPageTitle(stringResource(R.string.listen_together))
                StatusCard(
                    sessionState = sessionState,
                    sessionShare = sessionShare,
                    active = active,
                    isJoining = isJoining,
                    onCopy = { label, value ->
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(value))
                        toastMessage = "${context.getString(R.string.copied)} · ${context.getString(label)}"
                    },
                    onShare = { value ->
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, value)
                                },
                                null,
                            ),
                        )
                    },
                    onLeave = { TogetherEngine.leave() },
                )
                PlaybackCard(roomState = roomState)
                if (!isJoinedAsGuest) {
                    HostControlsCard(
                        onlineMode = hostModeOnline,
                        displayName = displayName,
                        port = port,
                        allowAdd = allowAdd,
                        allowControl = allowControl,
                        requireApproval = requireApproval,
                        startEnabled = !isCreatingSessionLoading && !isJoining && !isHostRole && sessionState !is TogetherSessionState.Joined,
                        loading = isCreatingSessionLoading,
                        onSetHostModeOnline = { hostModeOnline = it },
                        onEditDisplayName = { dialog = TogetherDialog.DisplayName(displayName) },
                        onEditPort = { dialog = TogetherDialog.Port(port.toString()) },
                        onSetAllowAdd = {
                            AppSettings.setTogetherAllowGuestsToAddTracks(it)
                            pushSettingsToActiveSession()
                        },
                        onSetAllowControl = {
                            AppSettings.setTogetherAllowGuestsControlPlayback(it)
                            pushSettingsToActiveSession()
                        },
                        onSetRequireApproval = {
                            AppSettings.setTogetherRequireHostApproval(it)
                            pushSettingsToActiveSession()
                        },
                        onStart = { startSession() },
                    )
                }
                if (!active) {
                    JoinControlsCard(
                        onlineMode = joinModeOnline,
                        input = joinInput,
                        canJoin = canJoin && !disableJoinUi && !isJoining && !isJoinedAsAcceptedGuest && !isWaitingApproval,
                        disabled = disableJoinUi,
                        joined = isJoinedAsAcceptedGuest,
                        waitingApproval = isWaitingApproval,
                        joining = isJoining,
                        onSetJoinModeOnline = { if (!disableJoinUi) joinModeOnline = it },
                        onEditInput = { dialog = TogetherDialog.Join(joinInput) },
                        onJoin = { submitJoinInput(joinInput) },
                    )
                }
                ParticipantsCard(
                    roomState = roomState,
                    isHostRole = isHostRole,
                    onlineRoom = onlineRoom,
                    onApprove = { TogetherEngine.approveParticipant(it, true) },
                    onReject = { TogetherEngine.approveParticipant(it, false) },
                    onTransferHost = { participant ->
                        dialog = TogetherDialog.TransferHost(participant.id, participant.name)
                    },
                    onKick = { participant ->
                        dialog = TogetherDialog.KickParticipant(participant.id, participant.name)
                    },
                    onBan = { participant ->
                        dialog = TogetherDialog.BanParticipant(participant.id, participant.name)
                    },
                )
                ChatCard(
                    chat = chat,
                    selfId = selfId,
                    connected = active,
                    input = chatInput,
                    onInputChange = { chatInput = it },
                    onSend = {
                        TogetherEngine.sendChat(chatInput)
                        chatInput = ""
                    },
                )
                ActivityLogCard(entries = activityLog.entries)
                Spacer(Modifier.height(24.dp))
            }
        }

        TogetherDialogs(
            dialog = dialog,
            welcomeVisible = !welcomeShown && !welcomeDismissedThisSession,
            welcomeDontShowAgain = welcomeDontShowAgain,
            hostOnline = hostModeOnline,
            joinOnline = joinModeOnline,
            onWelcomeDontShowAgainChange = { welcomeDontShowAgain = it },
            onWelcomeGotIt = {
                welcomeDismissedThisSession = true
                if (welcomeDontShowAgain) AppSettings.setTogetherWelcomeShown(true)
            },
            onDismissDialog = { dialog = TogetherDialog.None },
            onSubmitDisplayName = {
                if (it.isNotBlank()) AppSettings.setTogetherDisplayName(it)
            },
            onSubmitPort = { value ->
                value.toIntOrNull()?.takeIf { it in 1..65535 }?.let(AppSettings::setTogetherDefaultPort)
            },
            onSubmitJoin = submitJoinInput,
            onConfirmKick = { TogetherEngine.kickParticipant(it) },
            onConfirmBan = { TogetherEngine.banParticipant(it) },
            onConfirmTransferHost = { TogetherEngine.transferHostOwnership(it) },
        )
    }
}

@Composable
private fun StatusCard(
    sessionState: TogetherSessionState,
    sessionShare: Pair<Int, String>?,
    active: Boolean,
    isJoining: Boolean,
    onCopy: (Int, String) -> Unit,
    onShare: (String) -> Unit,
    onLeave: () -> Unit,
) {
    val accent =
        when {
            sessionState is TogetherSessionState.Error -> MaterialTheme.colorScheme.error
            active -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val stateLabel =
        when (sessionState) {
            TogetherSessionState.Idle -> stringResource(R.string.together_idle)
            is TogetherSessionState.Hosting,
            is TogetherSessionState.HostingOnline,
            -> stringResource(R.string.together_hosting)

            is TogetherSessionState.Joining,
            is TogetherSessionState.JoiningOnline,
            -> stringResource(R.string.together_joining)

            is TogetherSessionState.Joined ->
                if (isWaitingApprovalState(sessionState)) {
                    stringResource(R.string.together_waiting_approval)
                } else {
                    stringResource(R.string.together_connected)
                }

            is TogetherSessionState.Error -> stringResource(R.string.together_error_state)
        }
    val canLeave = active || isJoining
    SettingsGroup {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (sessionState is TogetherSessionState.Error) SfIcons.ErrorOutline else SfIcons.Groups,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.together_status),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                )
                Text(
                    text = stateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            if (active) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
        val errorMessage = (sessionState as? TogetherSessionState.Error)?.message
        if (!errorMessage.isNullOrBlank()) {
            RowDivider()
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        if (sessionShare != null) {
            RowDivider()
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = stringResource(sessionShare.first),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = sessionShare.second,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onCopy(sessionShare.first, sessionShare.second) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = SfIcons.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(R.string.copy_link))
                    }
                    TextButton(
                        onClick = { onShare(sessionShare.second) },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = SfIcons.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = stringResource(R.string.share))
                    }
                }
            }
        }
        if (canLeave) {
            RowDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onLeave,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = SfIcons.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = stringResource(R.string.leave))
                }
            }
        }
    }
}

private fun isWaitingApprovalState(session: TogetherSessionState.Joined): Boolean =
    session.roomState.participants
        .firstOrNull { it.id == session.selfParticipantId }
        ?.isPending == true

@Composable
private fun PlaybackCard(roomState: TogetherRoomState?) {
    val track = roomState?.queue?.getOrNull(roomState.currentIndex.coerceAtLeast(0))
    SettingsGroup(header = stringResource(R.string.together_current_playback)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = SfIcons.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = track?.title ?: stringResource(R.string.together_playback_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val artists = track?.artists?.joinToString()?.takeIf { it.isNotBlank() }
                if (artists != null) {
                    Text(
                        text = artists,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        RowDivider()
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoPill(
                text = when {
                    roomState == null -> stringResource(R.string.together_playback_empty)
                    roomState.isPlaying -> stringResource(R.string.together_playback_playing)
                    else -> stringResource(R.string.together_playback_paused)
                },
            )
            InfoPill(text = stringResource(R.string.together_queue_count, roomState?.queue?.size ?: 0))
            if (roomState != null && roomState.queue.isNotEmpty()) {
                InfoPill(
                    text = stringResource(
                        R.string.together_queue_position,
                        roomState.currentIndex.coerceAtLeast(0) + 1,
                        roomState.queue.size,
                    ),
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoPill(
                text = if (roomState?.shuffleEnabled == true) {
                    stringResource(R.string.together_shuffle_on)
                } else {
                    stringResource(R.string.together_shuffle_off)
                },
            )
            InfoPill(text = stringResource(R.string.together_repeat_mode, roomState?.repeatMode ?: 0))
        }
    }
}

@Composable
private fun HostControlsCard(
    onlineMode: Boolean,
    displayName: String,
    port: Int,
    allowAdd: Boolean,
    allowControl: Boolean,
    requireApproval: Boolean,
    startEnabled: Boolean,
    loading: Boolean,
    onSetHostModeOnline: (Boolean) -> Unit,
    onEditDisplayName: () -> Unit,
    onEditPort: () -> Unit,
    onSetAllowAdd: (Boolean) -> Unit,
    onSetAllowControl: (Boolean) -> Unit,
    onSetRequireApproval: (Boolean) -> Unit,
    onStart: () -> Unit,
) {
    SettingsGroup(header = stringResource(R.string.together_host_section)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SegmentedControl(
                options = listOf(stringResource(R.string.together_lan), stringResource(R.string.together_online)),
                selectedIndex = if (onlineMode) 1 else 0,
                onSelect = { onSetHostModeOnline(it == 1) },
            )
        }
        SettingsRow(
            icon = SfIcons.Person,
            title = stringResource(R.string.together_display_name),
            subtitle = displayName.trim().ifBlank { stringResource(R.string.together_display_name_placeholder) },
            onClick = onEditDisplayName,
        )
        if (!onlineMode) {
            RowDivider()
            SettingsRow(
                icon = SfIcons.Dns,
                title = stringResource(R.string.together_port),
                value = port.toString(),
                onClick = onEditPort,
            )
        }
        RowDivider()
        ToggleRow(
            icon = SfIcons.PlaylistPlay,
            title = stringResource(R.string.together_allow_guests_add),
            checked = allowAdd,
            onCheckedChange = onSetAllowAdd,
        )
        RowDivider()
        ToggleRow(
            icon = SfIcons.PlayCircle,
            title = stringResource(R.string.together_allow_guests_control),
            checked = allowControl,
            onCheckedChange = onSetAllowControl,
        )
        RowDivider()
        ToggleRow(
            icon = SfIcons.Lock,
            title = stringResource(R.string.together_require_approval),
            checked = requireApproval,
            onCheckedChange = onSetRequireApproval,
        )
        RowDivider()
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                enabled = startEnabled,
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.loading))
                } else {
                    Icon(
                        imageVector = SfIcons.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = stringResource(R.string.start_session))
                }
            }
        }
    }
}

@Composable
private fun JoinControlsCard(
    onlineMode: Boolean,
    input: String,
    canJoin: Boolean,
    disabled: Boolean,
    joined: Boolean,
    waitingApproval: Boolean,
    joining: Boolean,
    onSetJoinModeOnline: (Boolean) -> Unit,
    onEditInput: () -> Unit,
    onJoin: () -> Unit,
) {
    SettingsGroup(header = stringResource(R.string.together_join_section)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SegmentedControl(
                options = listOf(stringResource(R.string.together_join_link), stringResource(R.string.together_join_code)),
                selectedIndex = if (onlineMode) 1 else 0,
                onSelect = { onSetJoinModeOnline(it == 1) },
                enabled = !disabled,
            )
        }
        SettingsRow(
            icon = SfIcons.Groups,
            title = stringResource(R.string.join_session),
            subtitle = input.trim().ifBlank {
                stringResource(if (onlineMode) R.string.together_join_code_hint else R.string.together_join_link_hint)
            },
            enabled = !disabled,
            onClick = if (!disabled && !joining && !joined && !waitingApproval) onEditInput else null,
        )
        RowDivider()
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            FilledTonalButton(
                enabled = canJoin,
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                when {
                    joining -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.connecting))
                    }

                    waitingApproval -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.together_waiting_approval))
                    }

                    joined -> {
                        Icon(
                            imageVector = SfIcons.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.joined))
                    }

                    else -> {
                        Icon(
                            imageVector = SfIcons.Login,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(R.string.join))
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantsCard(
    roomState: TogetherRoomState?,
    isHostRole: Boolean,
    onlineRoom: Boolean,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onTransferHost: (TogetherParticipant) -> Unit,
    onKick: (TogetherParticipant) -> Unit,
    onBan: (TogetherParticipant) -> Unit,
) {
    val participants = roomState?.participants.orEmpty()
    SettingsGroup(
        header = stringResource(R.string.together_participants),
        footer = null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.together_connected_count, participants.count { it.isConnected && !it.isPending }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (participants.isEmpty()) {
            EmptyPanel(
                icon = SfIcons.Person,
                title = stringResource(R.string.together_participants_empty),
                body = stringResource(R.string.together_participants_empty_body),
            )
        } else {
            participants.forEachIndexed { index, participant ->
                if (index > 0) RowDivider()
                ParticipantRow(
                    participant = participant,
                    showApproveActions = isHostRole && participant.isPending &&
                        roomState?.settings?.requireHostApprovalToJoin == true,
                    showModerationActions = isHostRole && onlineRoom && !participant.isHost,
                    showTransferHostAction = isHostRole && !participant.isHost && !participant.isPending,
                    onApprove = { onApprove(participant.id) },
                    onReject = { onReject(participant.id) },
                    onTransferHost = { onTransferHost(participant) },
                    onKick = { onKick(participant) },
                    onBan = { onBan(participant) },
                )
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: TogetherParticipant,
    showApproveActions: Boolean,
    showModerationActions: Boolean,
    showTransferHostAction: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onTransferHost: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (participant.isHost) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = participant.name.trim().take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.labelLarge,
                color = if (participant.isHost) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = participant.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    participant.isHost -> stringResource(R.string.together_role_host)
                    participant.isPending -> stringResource(R.string.together_pending_approval)
                    else -> stringResource(R.string.together_role_guest)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (showApproveActions) {
                IconButton(onClick = onApprove) {
                    Icon(
                        imageVector = SfIcons.Check,
                        contentDescription = stringResource(R.string.together_approve),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onReject) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.together_reject),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showTransferHostAction) {
                IconButton(onClick = onTransferHost) {
                    Icon(
                        imageVector = SfIcons.SwitchAccount,
                        contentDescription = stringResource(R.string.together_transfer_host),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showModerationActions) {
                IconButton(onClick = onKick) {
                    Icon(
                        imageVector = Icons.Rounded.Logout,
                        contentDescription = stringResource(R.string.together_kick),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onBan) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = stringResource(R.string.together_ban),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatCard(
    chat: List<com.music.bitchord.data.together.ChatMessage>,
    selfId: String?,
    connected: Boolean,
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    SettingsGroup(header = stringResource(R.string.together_chat)) {
        if (chat.isEmpty()) {
            EmptyPanel(
                icon = SfIcons.Notes,
                title = stringResource(R.string.together_chat_empty),
                body = stringResource(R.string.together_chat_empty_body),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                reverseLayout = true,
            ) {
                items(chat.size) { index ->
                    val message = chat[chat.size - 1 - index]
                    ChatMessageRow(message = message, isSelf = message.participantId == selfId)
                }
            }
        }
        RowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = input,
                onValueChange = { if (it.length <= 500) onInputChange(it) },
                singleLine = true,
                enabled = connected,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (input.isNotBlank()) onSend() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(Modifier.heightIn(min = 32.dp), contentAlignment = Alignment.CenterStart) {
                        if (input.isEmpty()) {
                            Text(
                                text = stringResource(R.string.together_chat_input_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            IconButton(
                onClick = { if (input.isNotBlank()) onSend() },
                enabled = connected && input.isNotBlank(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Send,
                    contentDescription = stringResource(R.string.together_chat_send),
                    tint = if (connected && input.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun ChatMessageRow(
    message: com.music.bitchord.data.together.ChatMessage,
    isSelf: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (isSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = message.displayName.trim().take(1).uppercase().ifBlank { "?" },
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelf) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelf) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(Date(message.sentAtMs)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun ActivityLogCard(entries: List<TogetherActivityLog.Entry>) {
    SettingsGroup(header = stringResource(R.string.together_activity_log)) {
        Text(
            text = stringResource(R.string.together_activity_log_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (entries.isEmpty()) {
            EmptyPanel(
                icon = SfIcons.History,
                title = stringResource(R.string.together_activity_log_empty),
                body = stringResource(R.string.together_activity_log_empty_body),
            )
        } else {
            entries.take(MaxVisibleActivityLog).forEachIndexed { index, entry ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = when (entry.args.size) {
                                0 -> stringResource(entry.messageRes)
                                1 -> stringResource(entry.messageRes, entry.args[0])
                                else -> stringResource(entry.messageRes, entry.args[0], entry.args[1])
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = entry.timestamp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        IosSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoPill(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmptyPanel(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private sealed interface TogetherDialog {
    data object None : TogetherDialog

    data class DisplayName(
        val initialValue: String,
    ) : TogetherDialog

    data class Port(
        val initialValue: String,
    ) : TogetherDialog

    data class Join(
        val initialValue: String,
    ) : TogetherDialog

    data class KickParticipant(
        val participantId: String,
        val participantName: String,
    ) : TogetherDialog

    data class BanParticipant(
        val participantId: String,
        val participantName: String,
    ) : TogetherDialog

    data class TransferHost(
        val participantId: String,
        val participantName: String,
    ) : TogetherDialog
}

@Composable
private fun TogetherDialogs(
    dialog: TogetherDialog,
    welcomeVisible: Boolean,
    welcomeDontShowAgain: Boolean,
    hostOnline: Boolean,
    joinOnline: Boolean,
    onWelcomeDontShowAgainChange: (Boolean) -> Unit,
    onWelcomeGotIt: () -> Unit,
    onDismissDialog: () -> Unit,
    onSubmitDisplayName: (String) -> Unit,
    onSubmitPort: (String) -> Unit,
    onSubmitJoin: (String) -> Unit,
    onConfirmKick: (String) -> Unit,
    onConfirmBan: (String) -> Unit,
    onConfirmTransferHost: (String) -> Unit,
) {
    if (welcomeVisible) {
        WelcomeDialog(
            dontShowAgain = welcomeDontShowAgain,
            onDontShowAgainChange = onWelcomeDontShowAgainChange,
            onGotIt = onWelcomeGotIt,
        )
    }

    when (val current = dialog) {
        TogetherDialog.None -> Unit

        is TogetherDialog.DisplayName -> {
            TogetherInputDialog(
                title = stringResource(R.string.together_display_name),
                initialValue = current.initialValue,
                placeholder = stringResource(R.string.together_display_name_placeholder),
                isValid = { it.trim().isNotBlank() },
                onDone = onSubmitDisplayName,
                onDismiss = onDismissDialog,
            )
        }

        is TogetherDialog.Port -> {
            TogetherInputDialog(
                title = stringResource(R.string.together_port),
                initialValue = current.initialValue,
                placeholder = current.initialValue,
                isValid = { it.trim().toIntOrNull() in 1..65535 },
                keyboardType = KeyboardType.Number,
                onDone = onSubmitPort,
                onDismiss = onDismissDialog,
            )
        }

        is TogetherDialog.Join -> {
            TogetherInputDialog(
                title = stringResource(R.string.join_session),
                initialValue = current.initialValue,
                placeholder = stringResource(if (joinOnline) R.string.together_join_code_hint else R.string.together_join_link_hint),
                isValid = {
                    if (joinOnline) {
                        it.trim().isNotBlank()
                    } else {
                        TogetherLink.decode(it) != null
                    }
                },
                multiLine = true,
                onDone = onSubmitJoin,
                onDismiss = onDismissDialog,
            )
        }

        is TogetherDialog.KickParticipant -> {
            ConfirmParticipantDialog(
                title = stringResource(R.string.together_kick),
                body = stringResource(R.string.together_kick_confirm, current.participantName),
                confirm = stringResource(R.string.together_kick),
                destructive = true,
                onConfirm = { onConfirmKick(current.participantId) },
                onDismiss = onDismissDialog,
            )
        }

        is TogetherDialog.BanParticipant -> {
            ConfirmParticipantDialog(
                title = stringResource(R.string.together_ban),
                body = stringResource(R.string.together_ban_confirm, current.participantName),
                confirm = stringResource(R.string.together_ban),
                destructive = true,
                onConfirm = { onConfirmBan(current.participantId) },
                onDismiss = onDismissDialog,
            )
        }

        is TogetherDialog.TransferHost -> {
            ConfirmParticipantDialog(
                title = stringResource(R.string.together_transfer_host),
                body = stringResource(R.string.together_transfer_host_confirm, current.participantName),
                confirm = stringResource(R.string.together_transfer_host),
                destructive = false,
                onConfirm = { onConfirmTransferHost(current.participantId) },
                onDismiss = onDismissDialog,
            )
        }
    }
}

@Composable
private fun TogetherInputDialog(
    title: String,
    initialValue: String,
    placeholder: String,
    isValid: (String) -> Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    multiLine: Boolean = false,
    onDone: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initialValue) {
        mutableStateOf(TextFieldValue(initialValue, TextRange(initialValue.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = isValid(value.text),
                onClick = {
                    onDone(value.text)
                    onDismiss()
                },
            ) {
                Text(text = stringResource(R.string.got_it))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dismiss))
            }
        },
        title = { Text(text = title) },
        text = {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = !multiLine,
                maxLines = if (multiLine) 8 else 1,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = if (multiLine) ImeAction.Done else ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box(Modifier.heightIn(min = 32.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
        },
    )
}

@Composable
private fun ConfirmParticipantDialog(
    title: String,
    body: String,
    confirm: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(
                    text = confirm,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.dismiss))
            }
        },
        title = { Text(text = title) },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun WelcomeDialog(
    dontShowAgain: Boolean,
    onDontShowAgainChange: (Boolean) -> Unit,
    onGotIt: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onGotIt,
        confirmButton = {
            Button(onClick = onGotIt) {
                Icon(
                    imageVector = SfIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(text = stringResource(R.string.got_it))
            }
        },
        title = { Text(text = stringResource(R.string.together_welcome_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.together_welcome_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InstructionRow(
                    icon = SfIcons.Wifi,
                    title = stringResource(R.string.together_welcome_host_title),
                    body = stringResource(R.string.together_welcome_host_body),
                )
                InstructionRow(
                    icon = SfIcons.Login,
                    title = stringResource(R.string.together_welcome_join_title),
                    body = stringResource(R.string.together_welcome_join_body),
                )
                InstructionRow(
                    icon = SfIcons.Lock,
                    title = stringResource(R.string.together_welcome_permissions_title),
                    body = stringResource(R.string.together_welcome_permissions_body),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IosSwitch(
                        checked = dontShowAgain,
                        onCheckedChange = onDontShowAgainChange,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.together_dont_show_again),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
    )
}

@Composable
private fun InstructionRow(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private class TogetherActivityLog {
    data class Entry(
        val id: Long,
        val icon: ImageVector,
        val messageRes: Int,
        val args: List<String>,
        val timestamp: String,
    )

    val entries = androidx.compose.runtime.mutableStateListOf<Entry>()

    private var nextId = 0L
    private var lastSessionId: String? = null
    private var lastRoomState: TogetherRoomState? = null
    private var lastStateWasError = false

    fun onSessionState(sessionState: TogetherSessionState) {
        val timestamp = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(Date())
        if (sessionState is TogetherSessionState.Idle) {
            lastSessionId = null
            lastRoomState = null
            lastStateWasError = false
            entries.clear()
            return
        }

        val sessionId = sessionState.sessionIdOrNull ?: lastSessionId
        if (sessionId == null) return

        if (lastSessionId != sessionId) {
            lastSessionId = sessionId
            lastRoomState = null
            lastStateWasError = false
            entries.clear()
            add(
                icon = SfIcons.Groups,
                messageRes =
                    if (sessionState is TogetherSessionState.Joining || sessionState is TogetherSessionState.JoiningOnline) {
                        R.string.together_activity_joining_session
                    } else {
                        R.string.together_activity_session_started
                    },
                args = emptyList(),
                timestamp = timestamp,
            )
        }

        if (sessionState is TogetherSessionState.Error && !lastStateWasError) {
            add(
                icon = SfIcons.ErrorOutline,
                messageRes = R.string.together_activity_error,
                args = listOf(sessionState.message.ifBlank { "" }),
                timestamp = timestamp,
            )
            lastStateWasError = true
        } else if (sessionState !is TogetherSessionState.Error) {
            lastStateWasError = false
        }

        val currentRoomState = sessionState.roomStateOrNull
        if (currentRoomState != null) {
            val previousRoomState = lastRoomState
            if (previousRoomState != null) {
                appendRoomStateDiff(previousRoomState, currentRoomState, timestamp)
            }
            lastRoomState = currentRoomState
        }
    }

    private fun appendRoomStateDiff(
        previous: TogetherRoomState,
        current: TogetherRoomState,
        timestamp: String,
    ) {
        val previousParticipants = previous.participants.associateBy { it.id }
        val currentParticipants = current.participants.associateBy { it.id }
        current.participants.forEach { participant ->
            if (previousParticipants[participant.id] == null && !participant.isHost) {
                add(
                    icon = SfIcons.Person,
                    messageRes =
                        if (participant.isPending) {
                            R.string.together_activity_join_requested
                        } else {
                            R.string.together_participant_joined_notification
                        },
                    args = listOf(participant.name),
                    timestamp = timestamp,
                )
            }
        }
        previous.participants.forEach { participant ->
            if (currentParticipants[participant.id] == null && !participant.isHost) {
                add(
                    icon = SfIcons.Logout,
                    messageRes = R.string.together_participant_left_notification,
                    args = listOf(participant.name),
                    timestamp = timestamp,
                )
            }
        }

        if (previous.hostId != current.hostId) {
            add(
                icon = SfIcons.SwitchAccount,
                messageRes = R.string.together_activity_host_transferred,
                args = listOf(current.actorName(current.hostId)),
                timestamp = timestamp,
            )
        }

        val actor = current.actorName(current.hostId)
        if (previous.isPlaying != current.isPlaying) {
            add(
                icon = if (current.isPlaying) SfIcons.PlayCircle else Icons.Rounded.Pause,
                messageRes = if (current.isPlaying) {
                    R.string.together_activity_playback_resumed
                } else {
                    R.string.together_activity_playback_paused
                },
                args = listOf(actor),
                timestamp = timestamp,
            )
        }

        val previousTrack = previous.queue.getOrNull(previous.currentIndex)
        val currentTrack = current.queue.getOrNull(current.currentIndex)
        if (previousTrack?.id != currentTrack?.id && currentTrack != null) {
            add(
                icon = SfIcons.MusicNote,
                messageRes = R.string.together_activity_song_changed,
                args = listOf(actor, currentTrack.title),
                timestamp = timestamp,
            )
        } else if (previous.currentIndex != current.currentIndex && currentTrack != null) {
            add(
                icon = Icons.Rounded.SkipNext,
                messageRes = R.string.together_activity_song_skipped,
                args = listOf(actor, currentTrack.title),
                timestamp = timestamp,
            )
        }

        if (previous.queueHash != current.queueHash || previous.queue.size != current.queue.size) {
            add(
                icon = SfIcons.PlaylistPlay,
                messageRes = R.string.together_activity_queue_updated,
                args = listOf(actor),
                timestamp = timestamp,
            )
        }

        if (previous.shuffleEnabled != current.shuffleEnabled || previous.repeatMode != current.repeatMode) {
            add(
                icon = SfIcons.Settings,
                messageRes = R.string.together_activity_playback_state_changed,
                args = listOf(actor),
                timestamp = timestamp,
            )
        }
    }

    private fun TogetherRoomState.actorName(participantId: String): String =
        participants.firstOrNull { it.id == participantId }?.name
            ?: participants.firstOrNull { it.isHost }?.name
            ?: ""

    private fun add(
        icon: ImageVector,
        messageRes: Int,
        args: List<String>,
        timestamp: String,
    ) {
        entries.add(0, Entry(nextId++, icon, messageRes, args, timestamp))
        while (entries.size > MaxActivityLogSize) {
            entries.removeAt(entries.size - 1)
        }
    }
}

private const val MaxActivityLogSize = 80
private const val MaxVisibleActivityLog = 80
private const val WIDE_BREAKPOINT = 840
