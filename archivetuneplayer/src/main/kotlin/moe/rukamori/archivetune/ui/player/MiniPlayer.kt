/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.MiniPlayerCompactHeight
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.constants.SwipeSensitivityKey
import moe.rukamori.archivetune.ui.component.CompactControlGap
import moe.rukamori.archivetune.ui.component.CompactControlSize
import moe.rukamori.archivetune.ui.component.LocalNavigationBarBackdrop
import moe.rukamori.archivetune.ui.component.rememberPreSFrostedBitmap
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.isLowEndDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun MiniPlayer(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
    isPairedWithNavigation: Boolean = false,
    compactFractionState: State<Float> = remember { mutableStateOf(0f) },
    compactHorizontalPadding: Dp = 16.dp,
    compactReserveEndControl: Boolean = true,
    onArtworkSlotPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val compactFraction = compactFractionState.value

    val compactStartInset = compactHorizontalPadding + CompactControlSize + CompactControlGap

    val compactEndInset =
        if (compactReserveEndControl) {
            compactHorizontalPadding + CompactControlSize + CompactControlGap
        } else {
            compactHorizontalPadding
        }
    val compactModifier =
        modifier.padding(
            start = lerp(0.dp, compactStartInset, compactFraction),
            end = lerp(0.dp, compactEndInset, compactFraction),
        )
    NewMiniPlayer(
        positionProvider = positionProvider,
        durationProvider = durationProvider,
        modifier = compactModifier,
        pureBlack = pureBlack,
        isPairedWithNavigation = isPairedWithNavigation,
        compactFraction = compactFraction,

        compactShowTransportControls = !compactReserveEndControl,
        onArtworkSlotPositioned = onArtworkSlotPositioned,
    )
}

@Composable
private fun NewMiniPlayer(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
    isPairedWithNavigation: Boolean,
    compactFraction: Float = 0f,
    compactShowTransportControls: Boolean = false,
    onArtworkSlotPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val swipeSensitivity by rememberPreference(SwipeSensitivityKey, 0.73f)
    val swipeThumbnail by rememberPreference(moe.rukamori.archivetune.constants.SwipeThumbnailKey, true)

    // The frosted pill's content, on the theme's own colours.
    val colorScheme = MaterialTheme.colorScheme
    val contentColors =
        remember(colorScheme) {
            MiniPlayerContentColors(
                title = colorScheme.onSurface,
                secondary = colorScheme.onSurfaceVariant,
                progress = colorScheme.primary,
                progressTrack = colorScheme.outline.copy(alpha = 0.18f),
                artworkContainer = colorScheme.surfaceVariant,
                artworkBorder = colorScheme.outline.copy(alpha = 0.2f),
                primaryButtonContainer = colorScheme.primary,
                primaryButtonIcon = colorScheme.onPrimary,
                secondaryButtonContainer = colorScheme.surfaceContainerHighest,
                buttonIcon = colorScheme.onSurface,
                disabledButtonIcon = colorScheme.onSurface.copy(alpha = 0.38f),
            )
        }
    val miniPlayerShape =
        remember(isPairedWithNavigation, compactFraction) {
            if (compactFraction > 0.5f) {
                RoundedCornerShape(percent = 50)
            } else if (isPairedWithNavigation) {
                RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp,
                )
            } else {
                null
            }
        } ?: MaterialTheme.shapes.extraLarge

    SwipeableMiniPlayerBox(
        modifier = modifier,
        contentMaxWidth = if (isPairedWithNavigation) NavigationBarMaxWidth else null,
        swipeSensitivity = swipeSensitivity,
        swipeThumbnail = swipeThumbnail,
        playerConnection = playerConnection,
        layoutDirection = layoutDirection,
        coroutineScope = coroutineScope,
        pureBlack = pureBlack,
        useLegacyBackground = false,
        compactFraction = compactFraction,
    ) { offsetX ->
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(lerp(MiniPlayerHeight, MiniPlayerCompactHeight, compactFraction))

                    .graphicsLayer {
                        translationX = offsetX
                    }
                    .clip(miniPlayerShape),
        ) {
            MiniPlayerBackground(
                modifier = Modifier.fillMaxSize(),
            )
            NewMiniPlayerContent(
                positionProvider = positionProvider,
                durationProvider = durationProvider,
                playerConnection = playerConnection,
                colors = contentColors,
                compactFraction = compactFraction,
                compactShowTransportControls = compactShowTransportControls,
                onArtworkSlotPositioned = onArtworkSlotPositioned,
            )
        }
    }
}

private const val FrostedMiniPlayerBlurRadiusPx = 60f
private const val FrostedMiniPlayerOverlayAlpha = 0.30f

/**
 * The mini player's surface: the frosted pill — the navigation bar's backdrop
 * blurred behind the bar's own colour, or that colour plain wherever there is
 * no backdrop to blur (and a pre-S bitmap path where the layer can't run).
 */
@Composable
private fun MiniPlayerBackground(
    modifier: Modifier = Modifier,
) {
    val backdrop = LocalNavigationBarBackdrop.current
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    if (backdrop == null) {
        Box(modifier = modifier.background(baseColor))
    } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        val positionInRootState = remember { mutableStateOf(Offset.Zero) }
        val miniPlayerSizeState = remember { mutableStateOf(IntSize.Zero) }
        val positionInRoot by positionInRootState
        val miniPlayerSize by miniPlayerSizeState
        val blurredBitmap = rememberPreSFrostedBitmap(
            backdrop = backdrop,
            barPositionInRoot = positionInRoot,
            barSize = miniPlayerSize,
            blurRadiusPx = FrostedMiniPlayerBlurRadiusPx,
            updateIntervalMs = if (LocalContext.current.isLowEndDevice()) 160L else 80L,
        )
        Box(
            modifier =
                modifier
                    .onGloballyPositioned(

                        remember(positionInRootState, miniPlayerSizeState) {
                            { coordinates ->
                                positionInRootState.value = coordinates.positionInRoot()
                                miniPlayerSizeState.value = coordinates.size
                            }
                        },
                    )
                    .background(baseColor),
        ) {
            if (blurredBitmap != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = FrostedMiniPlayerOverlayAlpha
                                clip = true
                            }.drawBehind {
                                drawImage(blurredBitmap)
                            },
                )
            }
        }
    } else {
        val positionInRootState = remember { mutableStateOf(Offset.Zero) }
        val positionInRoot by positionInRootState
        Box(
            modifier =
                modifier
                    .onGloballyPositioned(
                        remember(positionInRootState) {
                            { coordinates -> positionInRootState.value = coordinates.positionInRoot() }
                        },
                    )
                    .background(baseColor),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            renderEffect =
                                BlurEffect(
                                    radiusX = FrostedMiniPlayerBlurRadiusPx,
                                    radiusY = FrostedMiniPlayerBlurRadiusPx,
                                    edgeTreatment = TileMode.Clamp,
                                )
                            alpha = FrostedMiniPlayerOverlayAlpha
                            clip = true
                        }.drawBehind {
                            val offset = backdrop.contentOffsetInRoot - positionInRoot
                            translate(offset.x, offset.y) {
                                runCatching { drawLayer(backdrop.layer) }
                            }
                        },
            )
        }
    }
}
