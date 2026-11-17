package com.music.bitchord.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.launch

/**
 * The app's alert, as the same glass popup every menu is: it grows out of the
 * centre of the screen (a dialog has no button of its own to grow out of)
 * along the popup spring, its contents blurring in sharp in the second half
 * of the growth, and goes back the same way before it is gone. The window
 * behind is blurred by the system (Android 12+), so the panel frosts over the
 * page. Same parameters as material3's AlertDialog for the ones the app uses.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val reduceMotion by AppSettings.reduceAnimation.collectAsStateWithLifecycle()
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                progress.animateTo(
                    0f,
                    tween(if (reduceMotion) 100 else MORPH_CLOSE_MS, easing = FastOutSlowInEasing),
                )
                onDismissRequest()
            }
        }
    }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            if (reduceMotion) tween(120, easing = LinearEasing) else spring(MORPH_DAMPING, MORPH_STIFFNESS),
        )
    }
    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                it.setDimAmount(0.18f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    it.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    it.attributes = it.attributes.apply { blurBehindRadius = 48 }
                }
            }
        }
        var panelBox by remember { mutableStateOf<MorphBox?>(null) }
        var screenBox by remember { mutableStateOf<MorphBox?>(null) }
        Box(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { screenBox = MorphBox(0f, 0f, it.size.width.toFloat(), it.size.height.toFloat()) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = close,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val colors = iosMenuColors()
            Column(
                modifier
                    .padding(horizontal = 32.dp)
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned {
                        val p = it.positionInRoot()
                        panelBox = MorphBox(p.x, p.y, it.size.width.toFloat(), it.size.height.toFloat())
                    }
                    .graphicsLayer {
                        val p = progress.value
                        val to = panelBox
                        val screen = screenBox
                        if (reduceMotion || to == null || screen == null) {
                            alpha = p.coerceIn(0f, 1f)
                        } else {
                            val frame = morphFrame(p, centreSource(screen, 48.dp.toPx()), to)
                            alpha = (p * 6f).coerceIn(0f, 1f)
                            transformOrigin = TransformOrigin(0f, 0f)
                            scaleX = frame.scaleX
                            scaleY = frame.scaleY
                            translationX = frame.translationX
                            translationY = frame.translationY
                        }
                    }
                    .iosMenuPanel(colors = colors, fillOverride = colors.fill.copy(alpha = if (colors.dark) 0.78f else 0.84f))
                    // Taps on the panel stay on it.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(horizontal = 22.dp, vertical = 20.dp),
            ) {
                Column(Modifier.morphContent(progress::value, reduceMotion)) {
                    CompositionLocalProvider(LocalContentColor provides colors.label) {
                        title?.let {
                            ProvideTextStyle(MaterialTheme.typography.titleLarge) { it() }
                        }
                        text?.let {
                            Box(Modifier.padding(top = if (title != null) 12.dp else 0.dp)) {
                                ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() }
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 18.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            dismissButton?.invoke()
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}
