/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.autoRateDisplay
import moe.rukamori.archivetune.db.entities.containerLabel
import moe.rukamori.archivetune.db.entities.formattedFileSize
import moe.rukamori.archivetune.models.ActiveOutputDevice
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.ActionPromptDialog
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.bottomSheetDraggable
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun CurrentSongHeader(
    sheetState: BottomSheetState,
    mediaMetadata: MediaMetadata?,
    liked: Boolean,
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleModeEnabled: Boolean,
    locked: Boolean,
    songCount: Int,
    queueDuration: Int,
    infiniteQueueEnabled: Boolean,
    infiniteQueueLoading: Boolean,
    backgroundColor: Color,
    onBackgroundColor: Color,
    onToggleLike: () -> Unit,
    onMenuClick: () -> Unit,
    onClearQueueClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onLockClick: () -> Unit,
    onInfiniteQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)

    val stableTopInset = LocalStableSystemBarsTopPadding.current
    val infiniteQueueAvailable = mediaMetadata?.isPodcast != true
    val queueItemCountText =
        if (mediaMetadata?.isPodcast == true) {
            pluralStringResource(R.plurals.n_episode, songCount, songCount)
        } else {
            pluralStringResource(R.plurals.n_song, songCount, songCount)
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(top = stableTopInset)
                .bottomSheetDraggable(sheetState)
                .padding(horizontal = 16.dp)
                .padding(top = 20.dp, bottom = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(48.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(onBackgroundColor.copy(alpha = 0.4f)),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AsyncImage(
                model = mediaMetadata?.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(onBackgroundColor.copy(alpha = 0.06f)),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = mediaMetadata?.title ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onBackgroundColor,
                )
                Text(
                    text = mediaMetadata?.artists?.joinToString(", ") { it.name } ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = onBackgroundColor.copy(alpha = 0.6f),
                )
            }

            IconButton(
                onClick = onToggleLike,
                modifier = Modifier.size(44.dp),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        contentColor =
                            if (liked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                onBackgroundColor
                            },
                    ),
            ) {
                Icon(
                    painter =
                        painterResource(
                            if (liked) {
                                R.drawable.favorite
                            } else {
                                R.drawable.favorite_border
                            },
                        ),
                    contentDescription = stringResource(R.string.action_like),
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(onBackgroundColor.copy(alpha = 0.06f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                IconButton(
                    onClick = onLockClick,
                    modifier = Modifier.size(40.dp),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            contentColor = onBackgroundColor.copy(alpha = 0.7f),
                        ),
                ) {
                    Icon(
                        painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(40.dp),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            contentColor = onBackgroundColor.copy(alpha = 0.7f),
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onClearQueueClick,
                    modifier = Modifier.size(40.dp),
                    colors =
                        IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.delete),
                        contentDescription = stringResource(R.string.clear),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Text(
                text =
                    queueItemCountText +
                        "  •  " + makeTimeString(queueDuration * 1000L),
                style = MaterialTheme.typography.labelMedium,
                color = onBackgroundColor.copy(alpha = 0.55f),
                modifier = Modifier.padding(end = 14.dp),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val uncheckedColors =
                ToggleButtonDefaults.toggleButtonColors(
                    containerColor = onBackgroundColor.copy(alpha = 0.12f),
                    contentColor = onBackgroundColor,
                )
            val checkedColors =
                ToggleButtonDefaults.toggleButtonColors(
                    checkedContainerColor = onBackgroundColor.copy(alpha = 0.22f),
                    checkedContentColor = onBackgroundColor,
                )
            val infiniteCheckedColors =
                ToggleButtonDefaults.toggleButtonColors(
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = onBackgroundColor.copy(alpha = 0.12f),
                    contentColor = onBackgroundColor.copy(alpha = 0.5f),
                )

            ToggleButton(
                checked = shuffleModeEnabled,
                onCheckedChange = {
                    if (enableHapticFeedback) {
                        view.performHapticFeedback(
                            android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                        )
                    }
                    onShuffleClick()
                },
                modifier = Modifier.weight(1f).size(48.dp),
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                colors = if (shuffleModeEnabled) checkedColors else uncheckedColors,
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = stringResource(R.string.action_shuffle_on),
                    modifier = Modifier.size(22.dp),
                )
            }

            ToggleButton(
                checked = repeatMode != Player.REPEAT_MODE_OFF,
                onCheckedChange = {
                    if (enableHapticFeedback) {
                        view.performHapticFeedback(
                            android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                        )
                    }
                    onRepeatClick()
                },
                modifier = Modifier.weight(1f).size(48.dp),
                shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                colors = if (repeatMode != Player.REPEAT_MODE_OFF) checkedColors else uncheckedColors,
            ) {
                Icon(
                    painter =
                        painterResource(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                                Player.REPEAT_MODE_ALL -> R.drawable.repeat_on
                                else -> R.drawable.repeat
                            },
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }

            ToggleButton(
                checked = infiniteQueueAvailable && infiniteQueueEnabled,
                onCheckedChange = { onInfiniteQueueClick() },
                modifier = Modifier.weight(1f).size(48.dp),
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                colors = infiniteCheckedColors,
                enabled = infiniteQueueAvailable && !infiniteQueueLoading,
            ) {
                AnimatedContent(
                    targetState = infiniteQueueLoading,
                    label = "InfiniteQueueLoading",
                ) { loading ->
                    if (loading) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = LocalContentColor.current,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.all_inclusive),
                            contentDescription = stringResource(R.string.similar_content),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(R.string.queue_continue_playing),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = onBackgroundColor,
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = stringResource(R.string.queue_autoplaying_similar),
            style = MaterialTheme.typography.bodySmall,
            color = onBackgroundColor.copy(alpha = 0.5f),
        )

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalDivider(
            color = onBackgroundColor.copy(alpha = 0.08f),
            thickness = 1.dp,
        )
    }
}

@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    onEndOfSong: () -> Unit,
    initialValue: Float = 30f,
) {
    var sleepTimerValue by remember { mutableFloatStateOf(initialValue) }

    ActionPromptDialog(
        titleBar = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.sleep_timer),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        },
        onDismiss = onDismiss,
        onConfirm = {
            onConfirm(sleepTimerValue.roundToInt())
        },
        onCancel = onDismiss,
        onReset = {
            sleepTimerValue = 30f
        },
        content = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text =
                        pluralStringResource(
                            R.plurals.minute,
                            sleepTimerValue.roundToInt(),
                            sleepTimerValue.roundToInt(),
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                )

                Spacer(Modifier.height(16.dp))

                Slider(
                    value = sleepTimerValue,
                    onValueChange = { sleepTimerValue = it },
                    valueRange = 5f..120f,
                    steps = (120 - 5) / 5 - 1,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                OutlinedButton(onClick = onEndOfSong, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.end_of_song))
                }
            }
        },
    )
}

@Composable
fun CodecInfoRow(
    codec: String,
    bitrate: String?,
    fileSize: String,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 30.dp, end = 30.dp, top = 6.dp, bottom = 2.dp),
    ) {
        Text(
            text =
                buildString {
                    append(codec)
                    if (!bitrate.isNullOrBlank()) {
                        append(" • ")
                        append(bitrate)
                    }
                    if (fileSize.isNotEmpty()) {
                        append(" • ")
                        append(fileSize)
                    }
                },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun QueueCollapsedContentV9(
    showCodecOnPlayer: Boolean,
    currentFormat: FormatEntity?,
    textBackgroundColor: Color,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    onShuffleClick: () -> Unit,
    onRepeatModeClick: () -> Unit,
    onMenuClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val railContainerColor = textBackgroundColor.copy(alpha = 0.14f)
    val buttonContainerColor = textBackgroundColor.copy(alpha = 0.08f)
    val selectedButtonContainerColor = textBackgroundColor.copy(alpha = 0.18f)
    val uncheckedColors =
        ToggleButtonDefaults.toggleButtonColors(
            containerColor = buttonContainerColor,
            contentColor = textBackgroundColor.copy(alpha = 0.76f),
        )
    val checkedColors =
        ToggleButtonDefaults.toggleButtonColors(
            checkedContainerColor = selectedButtonContainerColor,
            checkedContentColor = textBackgroundColor,
            containerColor = buttonContainerColor,
            contentColor = textBackgroundColor.copy(alpha = 0.76f),
        )

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                ).padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showCodecOnPlayer && currentFormat != null) {
            val container = currentFormat.containerLabel()
            val bitrate = currentFormat.autoRateDisplay()
            val fileSize = currentFormat.formattedFileSize()

            CodecInfoRow(
                codec = container,
                bitrate = bitrate,
                fileSize = fileSize,
                textColor = textBackgroundColor.copy(alpha = 0.6f),
            )
        }

        if (sleepTimerEnabled) {
            Surface(
                onClick = onSleepTimerClick,
                shape = RoundedCornerShape(18.dp),
                color = textBackgroundColor.copy(alpha = 0.08f),
                modifier =
                    Modifier
                        .padding(bottom = 8.dp)
                        .height(34.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.bedtime),
                        contentDescription = stringResource(R.string.sleep_timer),
                        tint = textBackgroundColor,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = makeTimeString(sleepTimerTimeLeft.coerceAtLeast(0L)),
                        style = MaterialTheme.typography.labelMedium,
                        color = textBackgroundColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(42.dp),
            color = railContainerColor,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 52.dp)
                    .height(72.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                ToggleButton(
                    checked = shuffleModeEnabled,
                    onCheckedChange = {
                        if (enableHapticFeedback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        onShuffleClick()
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    colors = if (shuffleModeEnabled) checkedColors else uncheckedColors,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription =
                            stringResource(
                                if (shuffleModeEnabled) R.string.action_shuffle_on else R.string.action_shuffle_off,
                            ),
                        modifier = Modifier.size(26.dp),
                    )
                }

                ToggleButton(
                    checked = repeatMode != Player.REPEAT_MODE_OFF,
                    onCheckedChange = {
                        if (enableHapticFeedback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        onRepeatModeClick()
                    },
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                    colors = if (repeatMode != Player.REPEAT_MODE_OFF) checkedColors else uncheckedColors,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                    else -> R.drawable.repeat
                                },
                            ),
                        contentDescription =
                            stringResource(
                                when (repeatMode) {
                                    Player.REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                    Player.REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                    else -> R.string.repeat_mode_off
                                },
                            ),
                        modifier = Modifier.size(26.dp),
                    )
                }

                Surface(
                    onClick = {
                        if (enableHapticFeedback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        onMenuClick()
                    },
                    shape =
                        RoundedCornerShape(
                            topStart = 12.dp,
                            bottomStart = 12.dp,
                            topEnd = 34.dp,
                            bottomEnd = 34.dp,
                        ),
                    color = buttonContainerColor,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(56.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = stringResource(R.string.more_options),
                            tint = textBackgroundColor,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}

