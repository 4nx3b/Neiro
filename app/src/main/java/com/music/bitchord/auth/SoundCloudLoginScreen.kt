package com.music.bitchord.auth

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.music.bitchord.R
import com.music.bitchord.data.soundcloud.SoundCloudAccountStore
import com.music.bitchord.data.soundcloud.SoundCloudService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val LOGIN_URL = "https://soundcloud.com/signin"

private val COOKIE_ORIGINS = listOf(
    "https://soundcloud.com",
    "https://www.soundcloud.com",
    "https://m.soundcloud.com",
    "https://secure.soundcloud.com",
    "https://api-v2.soundcloud.com",
)

private val CAPTURE_DELAYS_MS = listOf(0L, 300L, 800L, 1_600L, 3_000L, 5_000L)

private const val STORAGE_READ_SCRIPT = """
    (function() {
        var rows = [];
        function dumpStorage(label, storage) {
            try {
                for (var i = 0; i < storage.length; i++) {
                    var key = storage.key(i);
                    var value = storage.getItem(key);
                    if (key && value) {
                        rows.push(label + ':' + key + '=' + value);
                    }
                }
            } catch (error) {}
        }
        dumpStorage('localStorage', window.localStorage);
        dumpStorage('sessionStorage', window.sessionStorage);
        try {
            var sc = window.SC;
            if (sc && sc.connector && typeof sc.connector.oauth_token === 'string') {
                rows.push('runtime:oauth_token=' + sc.connector.oauth_token);
            }
        } catch (error) {}
        try {
            var accessToken = window.__sc_token;
            if (typeof accessToken === 'string') rows.push('runtime:access_token=' + accessToken);
        } catch (error) {}
        return rows.join('\n');
    })()
"""

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SoundCloudLoginScreen(
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handled = remember { AtomicBoolean(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var manualToken by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun finishLogin(token: String) {
        if (verifying) return
        verifying = true
        scope.launch {
            val username = withContext(Dispatchers.IO) { SoundCloudService.verifyToken(token) }
            if (username == null) {
                handled.set(false)
                verifying = false
                toast(context.getString(R.string.soundcloud_token_invalid))
                return@launch
            }
            SoundCloudAccountStore.adopt(
                SoundCloudAccountStore.Account(token = token, username = username),
            )
            toast(context.getString(R.string.soundcloud_login_success, username))
            onConnected()
        }
    }

    fun captureNow(view: WebView?) {
        view ?: return
        val cookieInputs = COOKIE_ORIGINS.mapNotNull { CookieManager.getInstance().getCookie(it) }
        cookieInputs.firstNotNullOfOrNull { SoundCloudService.normalizeAuthToken(it) }
            ?.let { token ->
                if (handled.compareAndSet(false, true)) finishLogin(token)
                return
            }
        view.evaluateJavascript(STORAGE_READ_SCRIPT) { result ->
            val storage = decodeJsString(result) ?: return@evaluateJavascript
            (cookieInputs + storage).firstNotNullOfOrNull { SoundCloudService.normalizeAuthToken(it) }
                ?.let { token ->
                    if (handled.compareAndSet(false, true)) finishLogin(token)
                }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = SoundCloudService.BROWSER_USER_AGENT
                    setBackgroundColor(0xFF121212.toInt())

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView,
                            url: String?,
                            favicon: android.graphics.Bitmap?,
                        ) {
                            super.onPageStarted(view, url, favicon)
                            canGoBack = view.canGoBack()
                            handler.postDelayed({ captureNow(view) }, 150L)
                        }

                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            super.onPageFinished(view, url)
                            canGoBack = view.canGoBack()
                            CookieManager.getInstance().flush()
                            CAPTURE_DELAYS_MS.forEach { delay ->
                                handler.postDelayed({ captureNow(view) }, delay)
                            }
                        }
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    loadUrl(LOGIN_URL)
                    webView = this
                }
            },
            onRelease = {
                handler.removeCallbacksAndMessages(null)
                it.destroy()
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = manualToken,
                onValueChange = { manualToken = it },
                label = { Text(stringResource(R.string.soundcloud_token_paste)) },
                supportingText = { Text(stringResource(R.string.soundcloud_token_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val token = SoundCloudService.normalizeAuthToken(manualToken)
                    if (token == null) {
                        toast(context.getString(R.string.soundcloud_token_invalid))
                    } else {
                        finishLogin(token)
                    }
                },
                enabled = !verifying && manualToken.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (verifying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.soundcloud_token_apply))
            }
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}

private fun decodeJsString(result: String?): String? =
    result
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it != "null" }
        ?.trim('"')
        ?.replace("\\\"", "\"")
        ?.replace("\\\\", "\\")
        ?.replace("\\n", "\n")
        ?.replace("\\r", "\r")
        ?.replace("\\u003d", "=")
        ?.replace("\\u003D", "=")
        ?.replace("\\u003b", ";")
        ?.replace("\\u003B", ";")
        ?.takeIf { it.isNotBlank() }
