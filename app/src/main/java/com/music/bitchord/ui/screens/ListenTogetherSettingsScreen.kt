/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Ported from vivi-music (beta branch)
 * ui/screens/settings/integrations/ListenTogetherSettings.kt (GPL-3.0), via
 * ArchiveTune's port of it.
 *
 * PORT-NOTE (Neiro): rebuilt with this app's own settings building blocks
 * ([SettingsGroup], [SettingsRow], [IosSwitch], [GlassAlertDialog]) so the
 * page reads as every other settings page does; preferences are AppSettings
 * flows, the engine state comes through the [ListenTogether] facade, and the
 * reference's drawable avatar picker became the app's emoji avatar table (the
 * custom gallery picture path is unchanged). The "in-app notifications" row is
 * this app's own addition — it gates the in-app chat popup the MainActivity
 * mounts.
 */

package com.music.bitchord.ui.screens

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.ListenTogetherAvatar
import com.music.bitchord.data.listentogether.ListenTogetherServer
import com.music.bitchord.data.listentogether.ListenTogetherServers
import com.music.bitchord.data.listentogether.LogEntry
import com.music.bitchord.data.listentogether.LogLevel
import com.music.bitchord.data.listentogether.RoomRole
import com.music.bitchord.data.listentogether.RoomState
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.ui.components.GlassAlertDialog
import com.music.bitchord.ui.components.IosSwitch
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The Listen Together settings page: identity (username, avatar, custom
 * picture), the room's server, the blocked list, the host-side automation
 * switches, notifications and the playback-following switches, and the
 * connection log viewer.
 */
@Composable
fun ListenTogetherSettingsScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = ListenTogether.manager

    val roomState by (manager?.roomState ?: MutableStateFlow<RoomState?>(null)).collectAsState()
    val role by (manager?.role ?: MutableStateFlow(RoomRole.NONE)).collectAsState()
    val logs by (manager?.logs ?: MutableStateFlow<List<LogEntry>>(emptyList())).collectAsState()
    val blockedUsernames by (manager?.blockedUsernames ?: MutableStateFlow<Set<String>>(emptySet()))
        .collectAsState()

    val username by AppSettings.listenTogetherUsername.collectAsState()
    val serverUrl by AppSettings.listenTogetherServerUrl.collectAsState()
    val avatarIndex by AppSettings.listenTogetherAvatarIndex.collectAsState()
    val autoApproval by AppSettings.listenTogetherAutoApproval.collectAsState()
    val suggestionAutoApprove by AppSettings.listenTogetherSuggestionAutoApprove.collectAsState()
    val chatNotifications by AppSettings.listenTogetherChatNotifications.collectAsState()
    val inAppNotifications by AppSettings.listenTogetherInAppNotifications.collectAsState()
    val syncHostVolume by AppSettings.listenTogetherSyncHostVolume.collectAsState()
    val smartResync by AppSettings.listenTogetherSmartResync.collectAsState()

    var showServerUrlDialog by rememberSaveable { mutableStateOf(false) }
    var showUsernameDialog by rememberSaveable { mutableStateOf(false) }
    var showAvatarPicker by rememberSaveable { mutableStateOf(false) }
    var showLogsDialog by rememberSaveable { mutableStateOf(false) }
    var showBlockedUsersDialog by rememberSaveable { mutableStateOf(false) }

    val servers = remember { ListenTogetherServers.servers }
    val selectedServer = remember(serverUrl) { ListenTogetherServers.findByUrl(serverUrl) }

    // Identity and the server only change outside a room: the wire carries the
    // join-time username and avatar, and the client cannot hop servers without
    // dropping the session it is in.
    val identityLocked = roomState != null
    val inRoomAsGuest = role == RoomRole.GUEST

    // Dialogs
    if (showServerUrlDialog) {
        ServerChooserDialog(
            servers = servers,
            currentUrl = serverUrl,
            onSelect = { server ->
                AppSettings.setListenTogetherServerUrl(server.url)
                showServerUrlDialog = false
            },
            onUseCustom = { customUrl ->
                AppSettings.setListenTogetherServerUrl(customUrl)
                showServerUrlDialog = false
            },
            onDismiss = { showServerUrlDialog = false },
        )
    }

    if (showUsernameDialog) {
        UsernameDialog(
            current = username,
            locked = identityLocked,
            onSaved = { saved ->
                AppSettings.setListenTogetherUsername(saved)
                showUsernameDialog = false
            },
            onReset = {
                AppSettings.setListenTogetherUsername("")
                showUsernameDialog = false
            },
            onDismiss = { showUsernameDialog = false },
        )
    }

    if (showAvatarPicker) {
        AvatarPickerDialog(
            selectedAvatarIndex = avatarIndex,
            onAvatarSelected = { index ->
                AppSettings.setListenTogetherAvatarIndex(index)
                showAvatarPicker = false
            },
            onDismiss = { showAvatarPicker = false },
        )
    }

    if (showLogsDialog) {
        LogsDialog(
            logs = logs,
            onClear = { manager?.clearLogs() },
            onDismiss = { showLogsDialog = false },
        )
    }

    if (showBlockedUsersDialog) {
        BlockedUsersDialog(
            blockedUsernames = blockedUsernames,
            onUnblock = { unblocked -> manager?.unblockUser(unblocked) },
            onDismiss = { showBlockedUsersDialog = false },
        )
    }

    val customAvatarPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                if (ListenTogetherAvatar.saveCustomAvatar(context, uri)) {
                    AppSettings.setListenTogetherAvatarIndex(ListenTogetherAvatar.CUSTOM_AVATAR_INDEX)
                    // Re-broadcast immediately so members of the current room see
                    // the new profile picture without waiting for a rejoin.
                    manager?.broadcastCustomAvatar()
                } else {
                    Toast.makeText(
                        context,
                        context.getString(R.string.listen_together_custom_avatar_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        SettingsPageTitle(stringResource(R.string.listen_together))

        // ---- Identity ----
        SettingsGroup(topSpacing = 0.dp) {
            val usernameTitle = stringResource(R.string.listen_together_username)
            SettingsRow(
                iconContent = {
                    AvatarBadge(avatarIndex = avatarIndex, fallbackName = username)
                },
                title = usernameTitle,
                value = username.ifEmpty { stringResource(R.string.not_set) },
                enabled = !identityLocked,
                onClick = {
                    if (identityLocked) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.listen_together_cannot_edit_username_in_room),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        showUsernameDialog = true
                    }
                },
            )
            RowDivider()
            val serverTitle = stringResource(R.string.listen_together_server_url)
            SettingsRow(
                icon = Icons.Rounded.Cloud,
                title = serverTitle,
                value = selectedServer?.let { server -> "${server.name} · ${server.location}" } ?: serverUrl,
                onClick = { showServerUrlDialog = true },
            )
            RowDivider()
            val avatarTitle = stringResource(R.string.listen_together_custom_avatar)
            SettingsRow(
                icon = Icons.Rounded.Image,
                title = avatarTitle,
                subtitle = stringResource(R.string.listen_together_custom_avatar_desc),
                enabled = !identityLocked,
                onClick = {
                    if (identityLocked) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.listen_together_cannot_edit_username_in_room),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        customAvatarPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }
                },
            )
            RowDivider()
            val blockedTitle = stringResource(R.string.listen_together_blocked_users)
            SettingsRow(
                icon = Icons.Rounded.Block,
                title = blockedTitle,
                value = if (blockedUsernames.isNotEmpty()) {
                    stringResource(R.string.listen_together_blocked_users_count, blockedUsernames.size)
                } else {
                    stringResource(R.string.listen_together_no_blocked_users)
                },
                enabled = blockedUsernames.isNotEmpty(),
                onClick = if (blockedUsernames.isNotEmpty()) {
                    { showBlockedUsersDialog = true }
                } else {
                    null
                },
            )
        }

        // ---- Rooms ----
        SettingsGroup(header = stringResource(R.string.listen_together_settings_rooms_header)) {
            val autoApprovalTitle = stringResource(R.string.listen_together_auto_approval)
            SettingsRow(
                icon = Icons.Rounded.Check,
                title = autoApprovalTitle,
                subtitle = stringResource(R.string.listen_together_auto_approval_desc),
                enabled = !inRoomAsGuest,
                trailing = {
                    IosSwitch(
                        checked = autoApproval,
                        onCheckedChange = { AppSettings.setListenTogetherAutoApproval(it) },
                        enabled = !inRoomAsGuest,
                    )
                },
                onClick = { if (!inRoomAsGuest) AppSettings.setListenTogetherAutoApproval(!autoApproval) },
            )
            RowDivider()
            val suggestionTitle = stringResource(R.string.listen_together_suggestion_auto_approve)
            SettingsRow(
                icon = Icons.Rounded.QueueMusic,
                title = suggestionTitle,
                subtitle = stringResource(R.string.listen_together_suggestion_auto_approve_desc),
                enabled = !inRoomAsGuest,
                trailing = {
                    IosSwitch(
                        checked = suggestionAutoApprove,
                        onCheckedChange = { AppSettings.setListenTogetherSuggestionAutoApprove(it) },
                        enabled = !inRoomAsGuest,
                    )
                },
                onClick = {
                    if (!inRoomAsGuest) {
                        AppSettings.setListenTogetherSuggestionAutoApprove(!suggestionAutoApprove)
                    }
                },
            )
        }

        // ---- Notifications ----
        SettingsGroup(header = stringResource(R.string.listen_together_settings_notifications_header)) {
            val chatNotificationsTitle = stringResource(R.string.listen_together_chat_notifications)
            SettingsRow(
                icon = Icons.Rounded.ChatBubble,
                title = chatNotificationsTitle,
                subtitle = stringResource(R.string.listen_together_chat_notifications_desc),
                trailing = {
                    IosSwitch(
                        checked = chatNotifications,
                        onCheckedChange = { AppSettings.setListenTogetherChatNotifications(it) },
                    )
                },
                onClick = { AppSettings.setListenTogetherChatNotifications(!chatNotifications) },
            )
            RowDivider()
            val inAppTitle = stringResource(R.string.listen_together_in_app_notifications)
            SettingsRow(
                icon = Icons.Rounded.Groups,
                title = inAppTitle,
                subtitle = stringResource(R.string.listen_together_in_app_notifications_desc),
                trailing = {
                    IosSwitch(
                        checked = inAppNotifications,
                        onCheckedChange = { AppSettings.setListenTogetherInAppNotifications(it) },
                    )
                },
                onClick = { AppSettings.setListenTogetherInAppNotifications(!inAppNotifications) },
            )
        }

        // ---- Playback ----
        SettingsGroup(header = stringResource(R.string.listen_together_settings_playback_header)) {
            val syncVolumeTitle = stringResource(R.string.listen_together_sync_volume)
            SettingsRow(
                icon = Icons.Rounded.VolumeUp,
                title = syncVolumeTitle,
                subtitle = stringResource(R.string.listen_together_sync_volume_desc),
                trailing = {
                    IosSwitch(
                        checked = syncHostVolume,
                        onCheckedChange = { AppSettings.setListenTogetherSyncHostVolume(it) },
                    )
                },
                onClick = { AppSettings.setListenTogetherSyncHostVolume(!syncHostVolume) },
            )
            RowDivider()
            val smartResyncTitle = stringResource(R.string.listen_together_smart_resync)
            SettingsRow(
                icon = Icons.Rounded.Sync,
                title = smartResyncTitle,
                subtitle = stringResource(R.string.listen_together_smart_resync_desc),
                trailing = {
                    IosSwitch(
                        checked = smartResync,
                        onCheckedChange = { AppSettings.setListenTogetherSmartResync(it) },
                    )
                },
                onClick = { AppSettings.setListenTogetherSmartResync(!smartResync) },
            )
        }

        // ---- Diagnostics ----
        SettingsGroup(header = stringResource(R.string.listen_together_settings_diagnostics_header)) {
            val logsTitle = stringResource(R.string.listen_together_view_logs)
            SettingsRow(
                icon = Icons.Rounded.BugReport,
                title = logsTitle,
                subtitle = stringResource(R.string.listen_together_view_logs_desc),
                onClick = { showLogsDialog = true },
            )
        }

        // The page's plain-language footer, the description the feature itself
        // gives on its own screen.
        Row(
            modifier = Modifier.padding(top = 16.dp, start = 20.dp, end = 20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text = stringResource(R.string.listen_together_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}

/** The avatar this device joins rooms with, as a small round badge. */
@Composable
private fun AvatarBadge(avatarIndex: Int, fallbackName: String) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val customAvatarBitmap = remember(avatarIndex) {
            if (avatarIndex == ListenTogetherAvatar.CUSTOM_AVATAR_INDEX) {
                ListenTogetherAvatar.loadCustomAvatarBytes(context)
                    ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } else {
                null
            }
        }
        when {
            customAvatarBitmap != null -> {
                Image(
                    bitmap = customAvatarBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop,
                )
            }
            avatarIndex == 0 -> Text(
                text = fallbackName.take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            else -> Text(
                text = AvatarGlyphs.getOrElse(avatarIndex - 1) { "👤" },
                fontSize = 13.sp,
            )
        }
    }
}

/** Edits the stored username — reset clears it back to "Not set". */
@Composable
private fun UsernameDialog(
    current: String,
    locked: Boolean,
    onSaved: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tempUsername by rememberSaveable(current) { mutableStateOf(current) }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSaved(tempUsername.trim()) }, enabled = tempUsername.isNotBlank()) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset, enabled = !locked) {
                    Text(stringResource(R.string.reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        title = { Text(stringResource(R.string.listen_together_username)) },
    ) {
        OutlinedTextField(
            value = tempUsername,
            onValueChange = { tempUsername = it },
            label = { Text(stringResource(R.string.enter_username)) },
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            trailingIcon = {
                if (tempUsername.isNotBlank()) {
                    IconButton(onClick = { tempUsername = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The server chooser: the known servers with name, location, operator and URL
 * (the selected one checked), plus the custom-server section with its own URL
 * field and "Use custom server" button.
 */
@Composable
private fun ServerChooserDialog(
    servers: List<ListenTogetherServer>,
    currentUrl: String,
    onSelect: (ListenTogetherServer) -> Unit,
    onUseCustom: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var customUrl by rememberSaveable(currentUrl) { mutableStateOf(currentUrl) }
    val trimmedCustomUrl = customUrl.trim()

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.listen_together_choose_server)) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            servers.forEach { server ->
                val isSelected = server.url == currentUrl
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(server) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = server.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${server.location} · ${server.operator}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = server.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.listen_together_custom_server),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = customUrl,
                onValueChange = { customUrl = it },
                label = { Text(stringResource(R.string.listen_together_server_url)) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onUseCustom(trimmedCustomUrl) },
                enabled = trimmedCustomUrl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.listen_together_use_custom_server))
            }
        }
    }
}

/**
 * The avatar picker: the initial (index 0), the emoji table, and the custom
 * gallery picture as its own entry at the end.
 */
@Composable
private fun AvatarPickerDialog(
    selectedAvatarIndex: Int,
    onAvatarSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        title = { Text(stringResource(R.string.listen_together_custom_avatar)) },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AvatarOption(
                    label = stringResource(R.string.you_label),
                    glyph = null,
                    selected = selectedAvatarIndex == 0,
                    onClick = { onAvatarSelected(0) },
                )
                AvatarGlyphs.forEachIndexed { index, glyph ->
                    AvatarOption(
                        label = null,
                        glyph = glyph,
                        selected = selectedAvatarIndex == index + 1,
                        onClick = { onAvatarSelected(index + 1) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarOption(
    label: String?,
    glyph: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            border = if (selected) {
                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                null
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (glyph != null) {
                    Text(text = glyph, fontSize = 20.sp)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = label,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The connection log viewer: every entry the client recorded, freshest last,
 * copyable and clearable.
 */
@Composable
private fun LogsDialog(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Follow the tail so a live connection keeps the newest entry on screen.
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    GlassAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        val textToCopy = logs.joinToString("\n") { log ->
                            buildString {
                                append(log.timestamp)
                                append(" [")
                                append(log.level.name)
                                append("] ")
                                append(log.message)
                                log.details?.let { d -> append(" -- $d") }
                            }
                        }
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("ListenTogetherLogs", textToCopy)
                        cm.setPrimaryClip(clip)
                        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
                    },
                    enabled = logs.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.copy))
                }
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.clear))
                }
            }
        },
        title = { Text(stringResource(R.string.listen_together_logs)) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp),
        ) {
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.listen_together_no_logs),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(logs) { log ->
                        LogEntryItem(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(log: LogEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = log.timestamp,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = when (log.level) {
                    LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
                    LogLevel.WARNING -> androidx.compose.ui.graphics.Color(0xFFFFF3CD)
                    LogLevel.DEBUG -> MaterialTheme.colorScheme.surfaceVariant
                    LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer
                },
            ) {
                Text(
                    text = log.level.name,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    color = when (log.level) {
                        LogLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        LogLevel.WARNING -> androidx.compose.ui.graphics.Color(0xFF856404)
                        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
                        LogLevel.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }
        }

        Text(
            text = log.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        log.details?.let { details ->
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BlockedUsersDialog(
    blockedUsernames: Set<String>,
    onUnblock: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
        title = { Text(stringResource(R.string.listen_together_blocked_users)) },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        ) {
            if (blockedUsernames.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.listen_together_no_blocked_users),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(blockedUsernames.toList()) { username ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = username,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            TextButton(
                                onClick = { onUnblock(username) },
                            ) {
                                Text(stringResource(R.string.unblock))
                            }
                        }
                    }
                }
            }
        }
    }
}
