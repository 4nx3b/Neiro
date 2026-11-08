package com.music.bitchord.auth

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
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
import com.music.bitchord.data.qobuz.QobuzAccountStore
import com.music.bitchord.data.qobuz.QobuzService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val WEB_PLAYER_URL = "https://play.qobuz.com/login"

private val LOGIN_HOST_SUFFIXES = listOf("qobuz.com")

private val HOOK_JS = """
    javascript:(function(){
      if(window.__neiroQobuzHook)return;window.__neiroQobuzHook=1;
      var tok=null,app=null;
      function pushCreds(){try{if(tok&&app){NeiroQobuzAuth.onCredentials(tok,app);}}catch(e){}}
      function scanHeaders(h){try{if(!h)return;
        var t=h.get?h.get('X-User-Auth-Token'):(h['X-User-Auth-Token']||h['x-user-auth-token']);
        var a=h.get?h.get('X-App-Id'):(h['X-App-Id']||h['x-app-id']);
        if(t&&t.length>20){tok=t;} if(a&&a.length>3){app=a;} pushCreds();
      }catch(e){}}
      try{var of=window.fetch;if(of){window.fetch=function(){try{var a=arguments[1];if(a&&a.headers){scanHeaders(a.headers);}}catch(e){}return of.apply(this,arguments);};}}catch(e){}
      try{var os=XMLHttpRequest.prototype.setRequestHeader;XMLHttpRequest.prototype.setRequestHeader=function(k,v){try{var kk=String(k).toLowerCase();if(kk==='x-user-auth-token'&&v&&v.length>20){tok=v;}if(kk==='x-app-id'&&v){app=v;}pushCreds();}catch(e){}return os.apply(this,arguments);};}catch(e){}
      try{
        var scripts=document.querySelectorAll('script[src]');
        var scraped=false;
        function scanBundle(js){
          if(scraped)return;
          var matches=js.match(/[^a-fA-F0-9]([a-f0-9]{32})[^a-fA-F0-9]/g);
          if(!matches)return;
          var seen={};
          for(var i=0;i<matches.length;i++){
            var m=matches[i].replace(/[^a-f0-9]/g,'');
            if(m.length===32&&!seen[m]){seen[m]=1;
              if(m==='d41d8cd98f00b204e9800998ecf8427e')continue;
              scraped=true;
              try{NeiroQobuzAuth.onSecret(m);}catch(e){}
              return;
            }
          }
        }
        for(var i=0;i<scripts.length;i++){
          (function(src){
            fetch(src).then(function(r){return r.text();}).then(scanBundle).catch(function(){});
          })(scripts[i].src);
        }
      }catch(e){}
    })()
""".trimIndent()

private fun isLoginHost(url: android.net.Uri?): Boolean {
    if (url?.scheme != "https") return false
    val host = url.host?.lowercase() ?: return false
    return LOGIN_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun QobuzLoginScreen(
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handled = remember { AtomicBoolean(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf<Pair<String, String>?>(null) }
    var scrapedSecret by remember { mutableStateOf<String?>(null) }
    var secretInput by remember { mutableStateOf("") }
    var verifying by remember { mutableStateOf(false) }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun saveAccount(token: String, appId: String, appSecret: String) {
        if (verifying) return
        verifying = true
        scope.launch {
            val candidate = QobuzAccountStore.Account(
                userAuthToken = token,
                appId = appId,
                appSecret = appSecret,
                label = "Web login",
                premium = false,
            )
            val standing = withContext(Dispatchers.IO) { QobuzService.accountStanding(candidate) }
            if (standing == null) {
                verifying = false
                handled.set(false)
                toast(context.getString(R.string.qobuz_credentials_invalid))
                return@launch
            }
            QobuzAccountStore.adopt(candidate.copy(premium = standing.premium))
            toast(context.getString(R.string.qobuz_login_success))
            onConnected()
        }
    }

    fun onCredentials(token: String, appId: String) {
        val secret = scrapedSecret
        if (secret != null) {
            saveAccount(token, appId, secret)
        } else {
            captured = token to appId
        }
    }

    fun onSecret(secret: String) {
        scrapedSecret = secret
        val pair = captured ?: return
        saveAccount(pair.first, pair.second, secret)
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
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = !isLoginHost(request.url)

                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            canGoBack = view.canGoBack()
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            canGoBack = view.canGoBack()
                            if (url?.contains("play.qobuz.com", ignoreCase = true) == true) {
                                view.loadUrl(HOOK_JS)
                            }
                        }
                    }

                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onCredentials(token: String?, appId: String?) {
                                if (token.isNullOrBlank() || appId.isNullOrBlank()) return
                                if (!handled.compareAndSet(false, true)) return
                                scope.launch {
                                    withContext(Dispatchers.Main) { onCredentials(token, appId) }
                                }
                            }

                            @JavascriptInterface
                            fun onSecret(secret: String?) {
                                if (secret.isNullOrBlank()) return
                                scope.launch {
                                    withContext(Dispatchers.Main) { onSecret(secret) }
                                }
                            }
                        },
                        "NeiroQobuzAuth",
                    )
                    loadUrl(WEB_PLAYER_URL)
                    webView = this
                }
            },
            onRelease = { it.destroy() },
        )

        if (captured != null && scrapedSecret == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.qobuz_app_secret_title))
                OutlinedTextField(
                    value = secretInput,
                    onValueChange = { secretInput = it },
                    label = { Text(stringResource(R.string.qobuz_app_secret_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        val pair = captured
                        val secret = secretInput.trim()
                        if (pair != null && secret.length >= 16) {
                            captured = null
                            saveAccount(pair.first, pair.second, secret)
                        }
                    },
                    enabled = !verifying && secretInput.trim().length >= 16,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (verifying) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.qobuz_app_secret_apply))
                }
            }
        }
    }

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}
