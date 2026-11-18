/*
 * ArchiveTune (2026)
 * © vossgraves — github.com/vossgraves
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Listen Together chat — ported from vivi-music (beta branch)
 * ui/screens/ListenTogetherChatComponents.kt and ui/screens/CommentTogether.kt
 * (GPL-3.0), via ArchiveTune's port of both: avatars, typing indicators,
 * message bubbles with swipe-to-reply, reactions, the Instagram-style anchored
 * action popup, the full emoji picker, and the chat page around them.
 *
 * PORT-NOTE (Neiro): the manager is reached through this app's
 * [ListenTogether] facade rather than a CompositionLocal; the anchored
 * action popup always takes its non-glass look (the app has no chat-local
 * liquid-glass layer to sample, and the reference's fallback look is the same
 * chrome); the typing dots animate regardless of lifecycle state; and the
 * song picker searches the app's own active sources instead of YouTube only.
 */

package com.music.bitchord.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.music.bitchord.R
import com.music.bitchord.data.listentogether.ChatMessagePayload
import com.music.bitchord.data.listentogether.ListenTogether
import com.music.bitchord.data.listentogether.ListenTogetherProtocol
import com.music.bitchord.data.listentogether.ListenTogetherServers
import com.music.bitchord.data.listentogether.RepliedMessage
import com.music.bitchord.data.listentogether.RoomState
import com.music.bitchord.data.listentogether.TrackInfo
import com.music.bitchord.data.listentogether.TypingUser
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

/** Quick-reaction strip shown first in the anchored popup. */
internal val QuickReactionEmojis = listOf("❤️", "👍", "😂", "😮", "😢", "🔥")

/**
 * The chat page: pinned banner, message list, composer. It is its own page
 * with its own back, full-screen — [onBack] is what the header's arrow calls.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListenTogetherChatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val manager = ListenTogether.manager ?: run {
        // Nothing to chat with before the engine exists — the page stays but
        // says so, the same way the main screen's not-configured content does.
        Box(modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.listen_together_not_configured),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
        return
    }
    val messages by manager.chatMessages.collectAsState()
    val userId by manager.userId.collectAsState()
    val roomState by manager.roomState.collectAsState()
    val typingUsers by manager.typingUsers.collectAsState()

    var textInput by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessagePayload?>(null) }
    var editingMessage by remember { mutableStateOf<ChatMessagePayload?>(null) }
    var actionTarget by remember { mutableStateOf<MessageActionTarget?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showSongPicker by remember { mutableStateOf(false) }
    var jumpTargetKey by remember { mutableStateOf<String?>(null) }

    // The protobuf server has no chat relay at all — its protocol carries no
    // chat message type — so the composer is replaced by an explanatory notice.
    val serverUrl by AppSettings.listenTogetherServerUrl.collectAsState()
    val chatSupported by remember(serverUrl) {
        mutableStateOf(ListenTogetherServers.findByUrl(serverUrl)?.protocol != ListenTogetherProtocol.PROTOBUF)
    }

    val lazyListState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // While the chat screen is on top, the client suppresses chat-message
    // notifications (and the shade conversation is cancelled via markChatAsRead).
    DisposableEffect(Unit) {
        manager.setChatScreenVisible(true)
        onDispose { manager.setChatScreenVisible(false) }
    }

    // Auto-follow the newest messages only when the reader is already at (or
    // near) the bottom — yanking the list down while someone reads pinned
    // history would fight both them and the pinned jump.
    val atBottom by remember {
        derivedStateOf {
            val info = lazyListState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total == 0 || last >= total - 2
        }
    }

    LaunchedEffect(messages.size) {
        manager.markChatAsRead()
        if (messages.isNotEmpty() && atBottom) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    // The chat must always OPEN on the most recent message: the animated
    // auto-follow above raced the first layout pass and lost, so long
    // histories started stuck at the top. Wait for the first real layout,
    // give late-arriving persisted history one settle beat, then snap.
    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.layoutInfo.totalItemsCount }
            .filter { it > 0 }
            .first()
        delay(150)
        val total = lazyListState.layoutInfo.totalItemsCount
        if (total > 0) {
            lazyListState.scrollToItem(total - 1)
        }
    }

    // Clear the jump highlight shortly after it lands.
    LaunchedEffect(jumpTargetKey) {
        if (jumpTargetKey == null) return@LaunchedEffect
        delay(1400)
        jumpTargetKey = null
    }

    val pinnedMessages = remember(messages) { messages.filter { it.pinned } }

    // Host role from the live room state (recomposes on host transfer) — drives
    // the "delete for everyone" moderation action on other people's messages.
    val iAmHost = roomState?.hostId != null && roomState?.hostId == userId

    // Own-message detection: session user id first, username as the fallback —
    // restored history from a previous session carries the OLD session's user
    // ids, and those messages must still land on the right side.
    val myUsername = manager.currentUsername
    fun isOwnMessage(message: ChatMessagePayload): Boolean =
        message.userId == userId || (myUsername != null && message.username == myUsername)

    // Where the restored (persisted) history ends — the divider between the
    // older messages and this session's live conversation sits right after it.
    val lastRestoredIndex = remember(messages) { messages.indexOfLast { it.restored } }

    fun sendMessage() {
        if (textInput.isBlank()) return
        when {
            editingMessage != null -> {
                manager.editMessage(editingMessage!!, textInput.trim())
                editingMessage = null
                textInput = ""
                focusManager.clearFocus()
            }
            replyingTo != null -> {
                val replyData = RepliedMessage(replyingTo!!.username, replyingTo!!.message)
                manager.sendChatMessage(textInput.trim(), replyData)
                replyingTo = null
                textInput = ""
                focusManager.clearFocus()
            }
            else -> {
                manager.sendChatMessage(textInput.trim())
                textInput = ""
                focusManager.clearFocus()
            }
        }
    }

    fun shareCurrentTrack() {
        // Room state first (guests are synced from the host); the local player
        // window covers hosts whose room state may lag its own track changes.
        val track = roomState?.currentTrack ?: manager.currentLocalTrack()
        if (track == null || track.id.isBlank() || track.id == "unknown") {
            Toast.makeText(context, R.string.listen_together_chat_nothing_playing, Toast.LENGTH_SHORT).show()
            return
        }
        manager.shareTrackToChat(track)
    }

    fun playSharedTrack(track: TrackInfo) {
        manager.playSharedTrack(track)
        Toast.makeText(context, R.string.listen_together_chat_play_song, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // The page's own header: back, the chat's name, the room it belongs to.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
            }
            Column {
                Text(
                    text = stringResource(R.string.comments),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                roomState?.roomCode?.let { code ->
                    Text(
                        text = "${stringResource(R.string.room_code)}: $code",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (pinnedMessages.isNotEmpty()) {
                PinnedMessagesStack(
                    messages = pinnedMessages,
                    onUnpin = { pinned -> manager.setPinned(pinned, false) },
                    onJumpTo = { pinned ->
                        val index = messages.indexOfFirst {
                            it.timestamp == pinned.timestamp && it.userId == pinned.userId
                        }
                        if (index >= 0) {
                            jumpTargetKey = "${pinned.userId}:${pinned.timestamp}"
                            coroutineScope.launch {
                                // Instant (not animated) so long histories snap
                                // straight to the pinned message; the highlight
                                // flash below marks the target row.
                                lazyListState.scrollToItem(index)
                            }
                        }
                    },
                )
            }

            if (messages.isEmpty() && typingUsers.isEmpty()) {
                EmptyChatPlaceholder()
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    messages.forEachIndexed { index, message ->
                        item(key = message.timestamp.toString() + message.userId) {
                            MessageItem(
                                message = message,
                                isMe = isOwnMessage(message),
                                myUsername = myUsername,
                                onReply = { replyingTo = it },
                                onLongPress = { pressed, bounds ->
                                    actionTarget = MessageActionTarget(pressed, bounds, isOwnMessage(pressed), iAmHost)
                                },
                                onToggleReaction = { msg, emoji ->
                                    manager.toggleReaction(msg, emoji)
                                },
                                onPlayTrack = ::playSharedTrack,
                                highlighted = jumpTargetKey == "${message.userId}:${message.timestamp}",
                            )
                        }
                        if (index == lastRestoredIndex) {
                            item(key = "older_messages_divider") {
                                OlderMessagesDivider()
                            }
                        }
                    }
                }
            }
        }

        // The composer half — its own column so the IME resize (imePadding
        // above) moves it with the keyboard.
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            // Reply / edit preview strip
            AnimatedVisibility(
                visible = replyingTo != null || editingMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val replyMsg = replyingTo
                val editMsg = editingMessage
                Surface(
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (editMsg != null) MaterialTheme.colorScheme.tertiary
                                    else MaterialTheme.colorScheme.primary,
                                ),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp),
                        ) {
                            Text(
                                text = if (editMsg != null) {
                                    stringResource(R.string.listen_together_chat_edit_message)
                                } else {
                                    replyMsg?.username.orEmpty()
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (editMsg != null) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = editMsg?.message ?: replyMsg?.message.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            if (editingMessage != null) {
                                editingMessage = null
                                textInput = ""
                            }
                            replyingTo = null
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // Typing indicator with layered avatars
            AnimatedVisibility(
                visible = typingUsers.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                TypingIndicatorRow(typingUsers = typingUsers)
            }

            if (chatSupported) {
                ChatInputArea(
                    text = textInput,
                    onTextChange = { newText ->
                        textInput = newText
                        if (newText.isNotBlank()) manager.notifyTyping()
                    },
                    onSend = ::sendMessage,
                    onShareTrack = { showSongPicker = true },
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.listen_together_chat_unsupported_server),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // Anchored Instagram-style action popup over a plain dark menu chrome.
    actionTarget?.let { target ->
        MessageActionsPopup(
            target = target,
            myUsername = manager.currentUsername,
            onReact = { emoji -> manager.toggleReaction(target.message, emoji) },
            onOpenEmojiPicker = { showEmojiPicker = true },
            onReply = { replyingTo = target.message },
            onCopy = {
                clipboardManager.setText(AnnotatedString(target.message.message))
                Toast.makeText(
                    context,
                    context.getString(R.string.listen_together_chat_message_copied),
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onEdit = {
                editingMessage = target.message
                replyingTo = null
                textInput = target.message.message
            },
            onPinToggle = { manager.setPinned(target.message, !target.message.pinned) },
            onDeleteForMe = { manager.deleteMessageForMe(target.message) },
            onDeleteForEveryone = { manager.deleteMessageForEveryone(target.message) },
            onDismiss = { actionTarget = null },
        )
    }

    // Full emoji picker for reactions with any emoji.
    if (showEmojiPicker) {
        EmojiPickerSheet(
            onPick = { emoji ->
                showEmojiPicker = false
                actionTarget?.let { target ->
                    manager.toggleReaction(target.message, emoji)
                }
                actionTarget = null
            },
            onDismiss = { showEmojiPicker = false },
        )
    }

    // Song picker opened from the composer's music-note button: search the
    // app's own sources and share any result as a rich tappable card, with the
    // room's current song offered as a quick action on top.
    if (showSongPicker) {
        ShareSongPickerSheet(
            currentTrack = roomState?.currentTrack ?: manager.currentLocalTrack(),
            onShareTrack = { track ->
                manager.shareTrackToChat(track)
                showSongPicker = false
            },
            onShareCurrent = {
                shareCurrentTrack()
                showSongPicker = false
            },
            onDismiss = { showSongPicker = false },
        )
    }
}

/**
 * The in-app chat popup: the newest message over a quick-reply box, shown while
 * the app is in the foreground with a room open but the chat page closed.
 *
 * PORT-NOTE (Neiro): the reference surfaced in-app messages through the
 * system's shade conversation only; this app also carries an in-app
 * notifications preference, and this popup is what it gates. It appears for
 * unread messages, hides itself again once they are read, and opens the chat
 * page on a tap.
 */
@Composable
fun ListenTogetherChatPopup(
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manager = ListenTogether.manager ?: return
    val inAppNotifications by AppSettings.listenTogetherInAppNotifications.collectAsState()
    val messages by manager.chatMessages.collectAsState()
    val unread by manager.unreadMessageCount.collectAsState()
    val connectionState by manager.connectionState.collectAsState()

    var replyText by remember { mutableStateOf("") }
    // The popup drops again once the unread count clears, so a read room never
    // keeps a banner up over the tab the listener is reading.
    val visible = inAppNotifications && unread > 0 && manager.isInRoom &&
        connectionState == com.music.bitchord.data.listentogether.ConnectionState.CONNECTED
    if (!visible) {
        replyText = ""
        return
    }
    val latest = messages.lastOrNull { !it.deleted } ?: messages.lastOrNull() ?: return

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable(onClick = onOpenChat),
    ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChatAvatar(
                        userId = latest.userId,
                        fallbackName = latest.username,
                        size = 24.dp,
                    )
                    Text(
                        text = latest.username,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (unread > 1) {
                        Text(
                            text = unread.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    IconButton(
                        onClick = { manager.markChatAsRead() },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = latest.message.ifBlank {
                        latest.sharedTrack?.let { "${it.title} — ${it.artist}" }.orEmpty()
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // The quick reply: sends without leaving the page the listener
                // is on — the field consumes its own taps, so only the banner
                // around it opens the chat.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text(stringResource(R.string.listen_together_chat_reply_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            if (replyText.isNotBlank()) {
                                manager.sendChatMessage(replyText.trim())
                                replyText = ""
                                manager.markChatAsRead()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(R.string.send),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

@Composable
private fun ChatInputArea(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onShareTrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onShareTrack) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = stringResource(R.string.listen_together_chat_pick_song_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text(stringResource(R.string.type_message)) },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                ),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
            )

            FloatingActionButton(
                onClick = onSend,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp),
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = stringResource(R.string.send),
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyChatPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.listen_together_chat_empty),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.listen_together_chat_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Circular room-member avatar for the chat: custom broadcast picture first,
 * then the picked avatar-index glyph, then the username initial.
 */
@Composable
internal fun ChatAvatar(
    userId: String,
    fallbackName: String,
    size: Dp,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
) {
    val manager = ListenTogether.manager
    val roomState by (manager?.roomState ?: MutableStateFlow<RoomState?>(null)).collectAsState()
    val currentUserId by (manager?.userId ?: MutableStateFlow<String?>(null)).collectAsState()
    val customAvatars by (manager?.customAvatars ?: MutableStateFlow<Map<String, ByteArray>>(emptyMap()))
        .collectAsState()

    // The avatar preference participates in the key so picking a new custom
    // profile picture refreshes the self avatar live (and falling back from a
    // custom picture to an index asset does too).
    val selfAvatarPref by AppSettings.listenTogetherAvatarIndex.collectAsState()

    // Restored history from a previous session carries that session's user
    // ids, so identity falls back to the username: a message is "mine" when
    // its username matches the local user's, and another member's avatar
    // resolves through the room-state entry with their username.
    val myUsername = manager?.currentUsername
    val isSelf = userId == currentUserId || (myUsername != null && fallbackName == myUsername)

    val user =
        roomState?.users?.find { it.userId == userId }
            ?: roomState?.users?.find { it.username == fallbackName }

    val bytes =
        when {
            manager == null -> null
            isSelf -> remember(manager, currentUserId, selfAvatarPref) {
                manager.customAvatarFor(currentUserId)
            }
            // Direct hit first; when the message's user id is from an older
            // session, route through the member's CURRENT id so the avatar
            // broadcast they sent at join time still applies.
            else -> customAvatars[userId] ?: user?.let { customAvatars[it.userId] }
        }
    val bitmap: Bitmap? =
        produceState<Bitmap?>(initialValue = null, bytes) {
            value = bytes?.let { encoded ->
                withContext(Dispatchers.Default) {
                    runCatching { BitmapFactory.decodeByteArray(encoded, 0, encoded.size) }.getOrNull()
                }
            }
        }.value

    val avatarIndex = user?.avatarIndex ?: 0
    val isHost = user?.isHost == true
    val initial = (user?.username?.takeIf { it.isNotBlank() } ?: fallbackName).take(1).uppercase()

    Box(
        modifier =
            modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    when {
                        isHost -> MaterialTheme.colorScheme.primary
                        isSelf -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    },
                )
                .then(
                    if (outlined) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.surface,
                            shape = CircleShape,
                        )
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            avatarIndex == 0 -> {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color =
                        when {
                            isHost -> MaterialTheme.colorScheme.onPrimary
                            isSelf -> MaterialTheme.colorScheme.onSecondary
                            else -> MaterialTheme.colorScheme.onTertiaryContainer
                        },
                )
            }
            else -> {
                Text(
                    text = AvatarGlyphs.getOrElse(avatarIndex - 1) { "👤" },
                    fontSize = (size.value * 0.62f).sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Typing indicator: the typing members' avatars layered on top of each other
 * (later members draw above earlier ones, separated by a surface outline),
 * followed by bouncing dots and the member names.
 */
@Composable
internal fun TypingIndicatorRow(
    typingUsers: List<TypingUser>,
    modifier: Modifier = Modifier,
) {
    if (typingUsers.isEmpty()) return
    val transition = rememberInfiniteTransition(label = "typing-dots")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Restart),
        label = "typing-phase",
    )

    Row(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(((typingUsers.size - 1) * 13 + 20).dp).height(20.dp)) {
            typingUsers.take(5).forEachIndexed { index, typing ->
                ChatAvatar(
                    userId = typing.userId,
                    fallbackName = typing.username,
                    size = 20.dp,
                    outlined = index > 0,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .offset(x = (index * 13).dp)
                            .zIndex(index.toFloat()),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { i ->
                val step = (phase + i * 0.33f) % 1f
                val bounce = sin(step * Math.PI).toFloat()
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 2.dp)
                            .offset(y = (-bounce * 3).dp)
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.65f * bounce)),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        val names =
            when {
                typingUsers.size == 1 -> typingUsers.first().username
                typingUsers.size == 2 -> "${typingUsers[0].username} & ${typingUsers[1].username}"
                else -> "${typingUsers.take(2).joinToString { it.username }}…"
            }
        Text(
            text = stringResource(R.string.listen_together_chat_typing),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = names,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Data of the message whose anchored action popup is open. [isHost] is the
 * LOCAL user's host status (drives "delete for everyone" availability), as
 * opposed to [isMe] which is about the pressed message's author.
 */
internal data class MessageActionTarget(
    val message: ChatMessagePayload,
    val bounds: Rect,
    val isMe: Boolean,
    val isHost: Boolean = false,
)

/**
 * Instagram-style action popup anchored to a long-pressed message bubble:
 * quick reactions, the full-emoji entry and reply/copy/edit/pin/delete, opening
 * with the lyrics-popup morph (spring scale + fade from the bubble edge).
 *
 * PORT-NOTE (Neiro): the reference sampled a chat-local liquid-glass layer for
 * the popup's surface; this app has no such layer at the page level, so the
 * popup always takes the reference's fallback look — a clean elevated dark
 * menu over a scrim — which is the same rounded chrome either way.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MessageActionsPopup(
    target: MessageActionTarget,
    myUsername: String?,
    onReact: (String) -> Unit,
    onOpenEmojiPicker: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onPinToggle: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var dismissed by remember { mutableStateOf(false) }
    val scaleAnim = remember { Animatable(0.35f) }
    val alphaAnim = remember { Animatable(0f) }

    var overlayWidthPx by remember { mutableIntStateOf(0) }
    var overlayHeightPx by remember { mutableIntStateOf(0) }
    var popupWidthPx by remember { mutableIntStateOf(0) }
    var popupHeightPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        if (dismissed) return@LaunchedEffect
        val scaleJob = scope.launch {
            scaleAnim.animateTo(1f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))
        }
        val alphaJob = scope.launch { alphaAnim.animateTo(1f, tween(180)) }
        scaleJob.join()
        alphaJob.join()
    }

    LaunchedEffect(dismissed) {
        if (!dismissed) return@LaunchedEffect
        val scaleJob = scope.launch {
            scaleAnim.animateTo(0.35f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium))
        }
        val alphaJob = scope.launch { alphaAnim.animateTo(0f, tween(160)) }
        scaleJob.join()
        alphaJob.join()
        onDismiss()
    }

    fun popupPlacement(): Pair<IntOffset, Boolean> {
        val marginPx = with(density) { 12.dp.toPx() }.toInt()
        val estimatedHeight =
            if (popupHeightPx > 0) popupHeightPx else with(density) { 112.dp.toPx() }.toInt()
        val estimatedWidth =
            if (popupWidthPx > 0) popupWidthPx else with(density) { 300.dp.toPx() }.toInt()
        val screenWidthPx = if (overlayWidthPx > 0) overlayWidthPx else estimatedWidth + 2 * marginPx
        val x =
            if (target.isMe) {
                (target.bounds.right.toInt() - estimatedWidth - 8).coerceAtLeast(marginPx)
            } else {
                (target.bounds.left.toInt() + 8).coerceAtMost(screenWidthPx - estimatedWidth - marginPx)
            }.coerceIn(marginPx, (screenWidthPx - estimatedWidth - marginPx).coerceAtLeast(marginPx))
        val below = target.bounds.bottom + 6 < overlayHeightPx - estimatedHeight - marginPx
        val y =
            if (below) {
                target.bounds.bottom.toInt() + 6
            } else {
                (target.bounds.top.toInt() - estimatedHeight - 6).coerceAtLeast(marginPx)
            }
        return IntOffset(x, y.coerceAtLeast(marginPx)) to below
    }

    val scrimAlpha = 0.25f * alphaAnim.value

    // The popup's chrome: an 18dp rounded sheet with divider rules between the
    // reaction strip and the action row — a clean elevated dark menu.
    val popupShape = RoundedCornerShape(18.dp)
    val frostedModifier =
        Modifier
            .background(Color(0xF226262B), popupShape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), popupShape)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    overlayWidthPx = size.width
                    overlayHeightPx = size.height
                }
                .background(Color.Black.copy(alpha = scrimAlpha))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (!dismissed) dismissed = true },
                ),
    ) {
        val (offset, opensBelow) = popupPlacement()

        Column(
            modifier =
                Modifier
                    .offset { offset }
                    .onSizeChanged { size ->
                        popupWidthPx = size.width
                        popupHeightPx = size.height
                    }
                    .widthIn(max = 320.dp)
                    .graphicsLayer {
                        this.alpha = alphaAnim.value
                        this.scaleX = scaleAnim.value
                        this.scaleY = scaleAnim.value
                        this.transformOrigin =
                            TransformOrigin(
                                if (target.isMe) 0.92f else 0.08f,
                                if (opensBelow) 0f else 1f,
                            )
                        this.shadowElevation = 18.dp.toPx()
                        this.shape = popupShape
                        this.clip = false
                    }
                    .clip(popupShape)
                    .then(frostedModifier)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            // Quick reactions row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                QuickReactionEmojis.forEach { emoji ->
                    Box(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        onReact(emoji)
                                        if (!dismissed) dismissed = true
                                    },
                                )
                                .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 22.sp)
                    }
                }
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOpenEmojiPicker,
                            )
                            .padding(6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.listen_together_chat_all_emojis),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(
                modifier =
                    Modifier
                        .padding(vertical = 6.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.14f)),
            )

            // Action row — a FlowRow so the extra delete chips wrap onto a
            // second line instead of overflowing the 320dp-wide popup.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PopupActionChip(icon = Icons.AutoMirrored.Rounded.Reply, label = stringResource(R.string.listen_together_chat_reply_label)) {
                    onReply()
                    if (!dismissed) dismissed = true
                }
                PopupActionChip(icon = Icons.Rounded.ContentCopy, label = stringResource(R.string.listen_together_chat_copy)) {
                    onCopy()
                    if (!dismissed) dismissed = true
                }
                if (target.isMe) {
                    PopupActionChip(icon = Icons.Rounded.Edit, label = stringResource(R.string.edit)) {
                        onEdit()
                        if (!dismissed) dismissed = true
                    }
                }
                PopupActionChip(
                    icon = Icons.Rounded.PushPin,
                    label = stringResource(
                        if (target.message.pinned) R.string.listen_together_chat_unpin
                        else R.string.listen_together_chat_pin
                    ),
                ) {
                    onPinToggle()
                    if (!dismissed) dismissed = true
                }
                if (target.isMe) {
                    // One's own messages only ever delete room-wide.
                    PopupActionChip(icon = Icons.Rounded.Delete, label = stringResource(R.string.delete)) {
                        onDeleteForEveryone()
                        if (!dismissed) dismissed = true
                    }
                } else {
                    // Other people's messages: a private hide plus, for the
                    // host, a room-wide moderation delete.
                    PopupActionChip(icon = Icons.Rounded.Delete, label = stringResource(R.string.delete)) {
                        onDeleteForMe()
                        if (!dismissed) dismissed = true
                    }
                    if (target.isHost) {
                        PopupActionChip(
                            icon = Icons.Rounded.DeleteForever,
                            label = stringResource(R.string.listen_together_chat_delete_for_everyone),
                        ) {
                            onDeleteForEveryone()
                            if (!dismissed) dismissed = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.10f),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Full emoji picker sheet opened from the anchored popup's "+" chip: every
 * emoji an Android keyboard offers, grouped with headers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EmojiPickerSheet(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(44.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = screenHeight * 0.72f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            EmojiCatalog.forEach { (category, emojis) ->
                item(key = "header_$category") {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
                    )
                }
                gridItems(
                    items = emojis,
                    key = { emoji -> "$category$emoji" },
                ) { emoji ->
                    Box(
                        modifier =
                            Modifier
                                .padding(4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { onPick(emoji) },
                                )
                                .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

/** Horizontal chip row of the reactions already applied to a message. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReactionsRow(
    message: ChatMessagePayload,
    myUsername: String?,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (message.reactions.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        message.reactions.forEach { (emoji, users) ->
            val mine = myUsername != null && users.contains(myUsername)
            Surface(
                onClick = { onToggle(emoji) },
                shape = RoundedCornerShape(999.dp),
                color =
                    if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor =
                    if (mine) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                border =
                    BorderStroke(
                        width = 1.dp,
                        color =
                            if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = emoji, fontSize = 14.sp)
                    if (users.size > 1) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = users.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

/**
 * One chat message: the sender's avatar before their name (incoming) and the
 * local user's own avatar at the trailing edge (own messages — their custom
 * profile picture is visible to themselves, exactly as others' are), a
 * swipe-to-reply bubble with a long-press action popup trigger, reply preview,
 * shared-song cards, edited/pinned marks, deleted tombstones, the reaction
 * chips and a highlight flash when jumped to from the pinned banner.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageItem(
    message: ChatMessagePayload,
    isMe: Boolean,
    myUsername: String?,
    onReply: (ChatMessagePayload) -> Unit,
    onLongPress: (ChatMessagePayload, Rect) -> Unit,
    onToggleReaction: (ChatMessagePayload, String) -> Unit,
    onPlayTrack: (TrackInfo) -> Unit,
    highlighted: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val bubbleBounds = remember { mutableStateOf(Rect.Zero) }
    val swipeOffset = remember { Animatable(0f) }
    val replyThresholdPx = with(density) { 72.dp.toPx() }
    val maxSwipePx = with(density) { 130.dp.toPx() }

    val highlightAlpha by animateFloatAsState(
        targetValue = if (highlighted) 0.35f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "chat-jump-highlight",
    )

    val bubbleColor =
        if (isMe) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    val textColor =
        if (isMe) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    val replyBgColor =
        if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
    ) {
        if (!isMe) {
            ChatAvatar(
                userId = message.userId,
                fallbackName = message.username,
                size = 28.dp,
                modifier = Modifier.align(Alignment.Top),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            if (!isMe) {
                Text(
                    text = message.username,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp, bottom = 3.dp),
                )
            }

            Box {
                // Reply hint revealed behind the bubble while swiping.
                val swipeProgress = (swipeOffset.value / replyThresholdPx).coerceIn(0f, 1f)
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f + 0.75f * swipeProgress),
                    modifier =
                        Modifier
                            .align(if (isMe) Alignment.CenterEnd else Alignment.CenterStart)
                            .offset {
                                val padPx = with(this) { 6.dp.toPx() }
                                IntOffset(
                                    x = if (isMe) (swipeOffset.value + padPx).toInt() else -(swipeOffset.value + padPx).toInt(),
                                    y = 0,
                                )
                            }
                            .size(20.dp)
                            .graphicsLayer { alpha = swipeProgress },
                )

                Surface(
                    color = bubbleColor,
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 16.dp,
                    ),
                    tonalElevation = 2.dp,
                    modifier =
                        Modifier
                            .onGloballyPositioned { coordinates ->
                                bubbleBounds.value = coordinates.boundsInRoot()
                            }
                            .graphicsLayer {
                                translationX = if (isMe) -swipeOffset.value else swipeOffset.value
                            }
                            .pointerInput(isMe, message.deleted) {
                                if (message.deleted) return@pointerInput
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        val target =
                                            (swipeOffset.value + if (isMe) -dragAmount else dragAmount)
                                                .coerceIn(0f, maxSwipePx)
                                        scope.launch { swipeOffset.snapTo(target) }
                                    },
                                    onDragEnd = {
                                        scope.launch {
                                            if (swipeOffset.value > replyThresholdPx) {
                                                onReply(message)
                                            }
                                            swipeOffset.animateTo(
                                                0f,
                                                spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMedium),
                                            )
                                        }
                                    },
                                )
                            }
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                                enabled = !message.deleted,
                                onLongClick = {
                                    if (!message.deleted) {
                                        onLongPress(message, bubbleBounds.value)
                                    }
                                },
                            ),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        message.replyTo?.let { reply ->
                            RepliedMessagePreview(reply, replyBgColor, textColor)
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (message.deleted) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = textColor.copy(alpha = 0.45f),
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = stringResource(R.string.listen_together_chat_message_deleted),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic,
                                    color = textColor.copy(alpha = 0.5f),
                                )
                            }
                        } else {
                            message.sharedTrack?.let { track ->
                                SharedTrackCard(
                                    track = track,
                                    bubbleColor = bubbleColor,
                                    onPlay = { onPlayTrack(track) },
                                )
                                if (message.message.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }

                            if (message.message.isNotBlank()) {
                                Text(
                                    text = formatMessageWithLinks(message.message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textColor,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .align(Alignment.End)
                                    .padding(top = 2.dp),
                        ) {
                            if (message.pinned) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(11.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            if (message.edited) {
                                Text(
                                    text = stringResource(R.string.listen_together_chat_edited),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = textColor.copy(alpha = 0.55f),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = formatTime(message.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.6f),
                            )
                        }
                    }
                }

                // Jump-to-message highlight flash (from the pinned banner).
                if (highlightAlpha > 0f) {
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = highlightAlpha }
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                    )
                }
            }

            if (!message.deleted) {
                ReactionsRow(
                    message = message,
                    myUsername = myUsername,
                    onToggle = { emoji -> onToggleReaction(message, emoji) },
                    modifier =
                        Modifier.padding(
                            top = 3.dp,
                            start = if (isMe) 0.dp else 4.dp,
                            end = if (isMe) 4.dp else 0.dp,
                        ),
                )
            }
        }

        if (isMe) {
            Spacer(modifier = Modifier.width(8.dp))
            ChatAvatar(
                userId = message.userId,
                fallbackName = message.username,
                size = 28.dp,
                modifier = Modifier.align(Alignment.Top),
            )
        }
    }
}

/**
 * Instagram-style shared-song card inside a chat bubble: thumbnail, title,
 * artist and duration on a rounded tile; tapping it starts the song in the room
 * (host applies it directly, guests suggest it).
 */
@Composable
internal fun SharedTrackCard(
    track: TrackInfo,
    bubbleColor: Color,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(12.dp),
        color = bubbleColor.copy(alpha = 0.65f),
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatTrackDuration(track.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.listen_together_chat_play_song),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

/** mm:ss for a track duration in milliseconds (blank when unknown). */
internal fun formatTrackDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalSeconds = durationMs / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return if (minutes >= 60L) {
        val hours = minutes / 60L
        "%d:%02d:%02d".format(java.util.Locale.ROOT, hours, minutes % 60L, seconds)
    } else {
        "%d:%02d".format(java.util.Locale.ROOT, minutes, seconds)
    }
}

/**
 * Marker drawn where the restored (persisted) history ends and the live
 * conversation begins, so it is always clear which messages are older.
 */
@Composable
internal fun OlderMessagesDivider(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
        Icon(
            imageVector = Icons.Rounded.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.listen_together_chat_older_messages),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        )
    }
}

@Composable
internal fun RepliedMessagePreview(
    reply: RepliedMessage,
    replyBgColor: Color,
    textColor: Color,
) {
    Surface(
        color = replyBgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(8.dp)) {
            Box(
                modifier =
                    Modifier
                        .width(3.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(MaterialTheme.colorScheme.primary),
            )
            Column(modifier = Modifier.padding(start = 10.dp)) {
                Text(
                    text = reply.username,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = reply.message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    color = textColor.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Banner bar over the chat list showing the pinned messages as a stacked
 * carousel — ONE constant-height row no matter how many messages are pinned.
 * The latest pin always displays first; tapping the bar steps to the pin
 * before it (wrapping around to the latest at the end) and jumps the chat to
 * that message, while swiping horizontally browses older/newer pins without
 * scrolling the list. The trailing button unpins the message on display.
 */
@Composable
internal fun PinnedMessagesStack(
    messages: List<ChatMessagePayload>,
    onUnpin: (ChatMessagePayload) -> Unit,
    onJumpTo: (ChatMessagePayload) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 48.dp.toPx() }
    val maxSwipePx = with(density) { 96.dp.toPx() }
    val dragOffset = remember { Animatable(0f) }

    // Latest pin first: recency of the pin itself, falling back to the message
    // timestamp for history pinned before the stamp existed.
    val ordered = remember(messages) {
        messages.sortedByDescending { if (it.pinnedAt > 0) it.pinnedAt else it.timestamp }
    }

    var displayedKey by remember { mutableStateOf<String?>(null) }
    var previousKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var slideDirection by remember { mutableIntStateOf(1) }

    // Follow the pinned set: a freshly pinned message takes over the bar (the
    // "latest pin displays first" rule), the shown pin is kept while it stays
    // pinned, and a shown pin that vanishes falls back to the latest.
    LaunchedEffect(ordered) {
        val keys = ordered.map { pinnedKeyOf(it) }
        val added = keys.toSet() - previousKeys
        when {
            displayedKey == null -> displayedKey = keys.firstOrNull()
            added.isNotEmpty() -> displayedKey = keys.firstOrNull()
            displayedKey !in keys -> displayedKey = keys.firstOrNull()
        }
        previousKeys = keys.toSet()
    }

    val index = ordered.indexOfFirst { pinnedKeyOf(it) == displayedKey }.takeIf { it >= 0 } ?: 0
    val displayed = ordered.getOrNull(index) ?: ordered.first()

    fun showOlder() {
        if (ordered.size < 2) return
        slideDirection = 1
        displayedKey = pinnedKeyOf(ordered[(index + 1) % ordered.size])
    }

    fun showNewer() {
        if (ordered.size < 2) return
        slideDirection = -1
        displayedKey = pinnedKeyOf(ordered[(index - 1 + ordered.size) % ordered.size])
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    // Keyed on the displayed pin as well: browsing via tap changes
                    // the shown message without changing the pinned set, and a
                    // stale closure would then swipe-navigate from an old index.
                    .pointerInput(ordered, displayedKey) {
                        detectHorizontalDragGestures(
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    dragOffset.snapTo(
                                        (dragOffset.value + dragAmount).coerceIn(-maxSwipePx, maxSwipePx),
                                    )
                                }
                            },
                            onDragEnd = {
                                scope.launch {
                                    if (dragOffset.value <= -swipeThresholdPx) {
                                        showOlder()
                                    } else if (dragOffset.value >= swipeThresholdPx) {
                                        showNewer()
                                    }
                                    dragOffset.snapTo(0f)
                                }
                            },
                        )
                    }
                    .clickable {
                        if (ordered.size > 1) {
                            // Tapping walks back through the pin history — and
                            // lands the chat on the message it lands on.
                            slideDirection = 1
                            val next = ordered[(index + 1) % ordered.size]
                            displayedKey = pinnedKeyOf(next)
                            onJumpTo(next)
                        } else {
                            onJumpTo(displayed)
                        }
                    }
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))

            if (ordered.size > 1) {
                Text(
                    text = stringResource(
                        R.string.listen_together_chat_pinned_position,
                        index + 1,
                        ordered.size,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier =
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // The carousel slot: exactly one preview at a time, sliding in the
            // direction of travel and dragged along with the finger mid-gesture.
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = displayedKey,
                    transitionSpec = {
                        if (slideDirection >= 0) {
                            (slideInHorizontally(tween(220)) { it / 2 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(220)) { -it / 2 } + fadeOut(tween(160)))
                        } else {
                            (slideInHorizontally(tween(220)) { -it / 2 } + fadeIn(tween(220))) togetherWith
                                (slideOutHorizontally(tween(220)) { it / 2 } + fadeOut(tween(160)))
                        }
                    },
                    label = "pinned-carousel",
                    modifier =
                        Modifier.graphicsLayer {
                            translationX = dragOffset.value
                        },
                ) { key ->
                    val pinned = ordered.firstOrNull { pinnedKeyOf(it) == key } ?: displayed
                    Column {
                        Text(
                            text = pinned.username,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        val preview =
                            when {
                                pinned.deleted -> stringResource(R.string.listen_together_chat_message_deleted)
                                pinned.sharedTrack != null -> "♪ ${pinned.sharedTrack.title} • ${pinned.sharedTrack.artist}"
                                else -> pinned.message
                            }
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            IconButton(onClick = { onUnpin(displayed) }) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.listen_together_chat_unpin),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Stable identity of a chat message across list updates. */
private fun pinnedKeyOf(message: ChatMessagePayload): String = "${message.userId}:${message.timestamp}"

@Composable
internal fun formatMessageWithLinks(text: String): AnnotatedString {
    val context = LocalContext.current
    // Any http(s) link is tappable. YouTube Music song links open in-app; every
    // other URL opens directly through the system's default handler.
    val urlRegex = Regex("(https?://[^\\s<>\\\"]+)")
    val matches = urlRegex.findAll(text)

    if (matches.none()) return AnnotatedString(text)

    return buildAnnotatedString {
        var lastIdx = 0
        for (match in matches) {
            append(text.substring(lastIdx, match.range.first))

            val url = match.value
            val linkAnnotation = LinkAnnotation.Url(
                url = url,
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold,
                    ),
                ),
                linkInteractionListener = {
                    try {
                        if (url.contains("music.youtube.com")) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                `package` = context.packageName
                            }
                            context.startActivity(intent)
                        } else {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    } catch (e: Exception) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e2: Exception) {
                            Toast.makeText(context, url, Toast.LENGTH_SHORT).show()
                        }
                    }
                },
            )

            withLink(linkAnnotation) {
                append(url)
            }

            lastIdx = match.range.last + 1
        }
        if (lastIdx < text.length) {
            append(text.substring(lastIdx))
        }
    }
}

internal fun formatTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

/**
 * Bottom sheet to pick a song for the chat: a search box over the app's own
 * sources plus a "now playing" quick-share row. Tapping any row shares the
 * song as a rich card into the room chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShareSongPickerSheet(
    currentTrack: TrackInfo?,
    onShareTrack: (TrackInfo) -> Unit,
    onShareCurrent: () -> Unit,
    onDismiss: () -> Unit,
) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<com.music.bitchord.data.model.Song>>(emptyList()) }
        var searching by remember { mutableStateOf(false) }
        var searched by remember { mutableStateOf(false) }

        // Debounced search — only the freshest query may land its rows, so a
        // slow source behind a newer keystroke is dropped.
        LaunchedEffect(query) {
            if (query.isBlank()) {
                results = emptyList()
                searching = false
                searched = false
                return@LaunchedEffect
            }
            searching = true
            val searchedFor = query
            delay(400L)
            val found = searchSongs(searchedFor)
            if (searchedFor == query) {
                results = found
                searching = false
                searched = true
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(max = screenHeight * 0.82f),
        ) {
            Text(
                text = stringResource(R.string.listen_together_chat_pick_song_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.listen_together_chat_pick_song_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                modifier = Modifier.fillMaxWidth(),
            )

            currentTrack?.let { track ->
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .clickable(onClick = onShareCurrent)
                                .padding(8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier =
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.listen_together_chat_now_playing),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            if (searching) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(32.dp),
                )
            } else if (searched && results.isEmpty()) {
                Text(
                    text = stringResource(R.string.listen_together_chat_pick_song_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .heightIn(max = screenHeight * 0.5f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    results.take(8).forEach { song ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onShareTrack(song.toSuggestedTrack()) }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                        ) {
                            song.thumbnailUrl?.let { thumb ->
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
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
