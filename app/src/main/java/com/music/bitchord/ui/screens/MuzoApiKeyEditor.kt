package com.music.bitchord.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.sources.MuzoApiCheck
import com.music.bitchord.data.sources.MuzoService
import com.music.bitchord.ui.components.AddonEditorAlert
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The Muzo Data API key, on the card an addon's address is added with.
 *
 * **Mounted from [MainActivity][com.music.bitchord.MainActivity], not from the
 * settings sheet, and it has to stay that way** — same reason as
 * [SignatureApiEditor][com.music.bitchord.ui.screens.SignatureApiEditor]: the
 * card is a haze effect that must sample a layer it is not part of, and its
 * scrim has to cover the tab bar and the mini player.
 *
 * Test asks the service's own usage question with the key as typed, so a key
 * is only stored once it has actually answered — and the answer it gives is
 * the quota line, which is more use than a bare "connected". Saving a blank
 * key forgets it, which is the same affordance the signature API's editor
 * gives its address.
 */
@Composable
fun MuzoApiKeyEditor(hazeState: HazeState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val stored by AppSettings.muzoApiKey.collectAsStateWithLifecycle()

    // A draft of the stored key, seeded once: re-seeding it from the store
    // would overwrite typing the first time anything else touched it.
    var input by remember { mutableStateOf(stored) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var busy by remember { mutableStateOf(false) }

    // Resolved here rather than in the coroutine, because a composable's
    // strings can only be asked for in composition — see the same note on
    // [SignatureApiEditor][com.music.bitchord.ui.screens.SignatureApiEditor].
    val rejected = stringResource(R.string.muzo_api_rejected)
    val unreachable = stringResource(R.string.source_unreachable)
    val unlimited = stringResource(R.string.muzo_streaming_unlimited)

    /**
     * The usage question with the key as typed, then the verdict — and the
     * verdict is the quota line rather than a plain "connected", because it
     * is the one thing a keyholder checking a key actually wants to read.
     */
    val test: (onGood: () -> Unit) -> Unit = { onGood ->
        val key = input.trim()
        if (key.isBlank()) {
            status = rejected to false
        } else {
            busy = true
            status = null
            scope.launch {
                val check = withContext(Dispatchers.IO) {
                    runCatching { MuzoService.checkApi(key) }
                        .getOrElse { MuzoApiCheck.Unreachable(it.message ?: "Failed") }
                }
                busy = false
                status = when (check) {
                    is MuzoApiCheck.Ok -> context.getString(
                        R.string.muzo_api_ok,
                        check.usageToday,
                        check.dailyLimit,
                        check.streamLimit
                            ?.takeIf { it.isNotBlank() && !it.equals("unlimited", ignoreCase = true) }
                            ?: unlimited,
                    ) to true
                    is MuzoApiCheck.Rejected -> rejected.format(check.reason) to false
                    is MuzoApiCheck.Unreachable -> unreachable.format(check.reason) to false
                }
                if (check is MuzoApiCheck.Ok) onGood()
            }
        }
    }

    val remove: () -> Unit = {
        AppSettings.setMuzoApiKey("")
        onDismiss()
    }

    AddonEditorAlert(
        hazeState = hazeState,
        title = stringResource(R.string.muzo_api_key),
        description = stringResource(R.string.muzo_api_key_subtitle),
        urlValue = input,
        // A result describes the key it was run against, so editing the key
        // clears it.
        onUrlChange = {
            input = it
            status = null
        },
        urlPlaceholder = stringResource(R.string.muzo_api_key_hint),
        status = status?.first,
        statusIsGood = status?.second == true,
        testing = busy,
        canSubmit = input.isNotBlank(),
        onTest = { test {} },
        onSave = {
            test {
                AppSettings.setMuzoApiKey(input.trim())
                onDismiss()
            }
        },
        onRemove = remove.takeIf { stored.isNotBlank() },
        removeLabel = stringResource(R.string.remove),
        onDismiss = onDismiss,
    )
}
