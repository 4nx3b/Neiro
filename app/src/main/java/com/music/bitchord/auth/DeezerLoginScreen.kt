package com.music.bitchord.auth

import android.annotation.SuppressLint
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
import com.music.bitchord.data.deezer.DeezerAccountStore
import com.music.bitchord.data.deezer.DeezerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val LOGIN_URL = "https://www.deezer.com/login"
private const val COOKIE_ORIGIN = "https://www.deezer.com"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeezerLoginScreen(
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handled = remember { AtomicBoolean(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var manualArl by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun finishLogin(arl: String) {
        if (verifying) return
        verifying = true
        scope.launch {
            val info = withContext(Dispatchers.IO) { DeezerService.verifyArl(arl) }
            if (info == null) {
                handled.set(false)
                verifying = false
                toast(context.getString(R.string.deezer_arl_invalid))
                return@launch
            }
            DeezerAccountStore.adopt(
                DeezerAccountStore.Account(arl = arl, name = info.name, lossless = info.lossless),
            )
            toast(context.getString(R.string.deezer_login_success, info.name))
            onConnected()
        }
    }

    fun readArl(): String? =
        CookieManager.getInstance().getCookie(COOKIE_ORIGIN)
            ?.split(';')
            ?.firstNotNullOfOrNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size != 2) return@firstNotNullOfOrNull null
                pieces[1].trim().takeIf {
                    pieces[0].trim().equals("arl", ignoreCase = true) && it.isNotEmpty()
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
                    setBackgroundColor(0xFF121212.toInt())

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(
                            view: WebView,
                            url: String?,
                        ) {
                            canGoBack = view.canGoBack()
                            val arl = readArl() ?: return
                            if (!handled.compareAndSet(false, true)) return
                            finishLogin(arl)
                        }
                    }

                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    loadUrl(LOGIN_URL)
                    webView = this
                }
            },
            onRelease = { it.destroy() },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = manualArl,
                onValueChange = { manualArl = it },
                label = { Text(stringResource(R.string.deezer_arl_paste)) },
                supportingText = { Text(stringResource(R.string.deezer_arl_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val arl = manualArl.trim()
                    if (arl.length < 20) {
                        toast(context.getString(R.string.deezer_arl_invalid))
                    } else {
                        finishLogin(arl)
                    }
                },
                enabled = !verifying && manualArl.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (verifying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(stringResource(R.string.deezer_arl_apply))
            }
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}
