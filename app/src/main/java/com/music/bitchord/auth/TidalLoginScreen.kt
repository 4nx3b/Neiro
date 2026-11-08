package com.music.bitchord.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.music.bitchord.data.tidal.TidalAccountStore
import com.music.bitchord.data.tidal.TidalService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val WEB_PLAYER_URL = "https://listen.tidal.com"

private val LOGIN_HOST_SUFFIXES = listOf(
    "tidal.com", "login.tidal.com", "auth.tidal.com", "google.com", "gstatic.com",
    "facebook.com", "apple.com",
)

private val BEARER_HOOK_JS = """
    javascript:(function(){
      if(window.__neiroTidalHook)return;window.__neiroTidalHook=1;
      function send(h){try{if(!h)return;var m=/Bearer\s+([A-Za-z0-9._\-]+)/i.exec(h);
        if(m&&m[1]&&m[1].length>20){NeiroTidalAuth.onBearer(m[1]);}}catch(e){}}
      try{
        var of=window.fetch;
        if(of){window.fetch=function(){try{var a=arguments[1];if(a&&a.headers){
          var hh=a.headers;var v=hh.get?hh.get('Authorization'):(hh['Authorization']||hh['authorization']);send(v);
        }}catch(e){}return of.apply(this,arguments);};}
      }catch(e){}
      try{
        var os=XMLHttpRequest.prototype.setRequestHeader;
        XMLHttpRequest.prototype.setRequestHeader=function(k,v){
          try{if(k&&String(k).toLowerCase()==='authorization'){send(v);}}catch(e){}
          return os.apply(this,arguments);};
      }catch(e){}
    })()
""".trimIndent()

private fun isLoginHost(uri: Uri?): Boolean {
    if (uri?.scheme != "https") return false
    val host = uri.host?.lowercase() ?: return false
    return LOGIN_HOST_SUFFIXES.any { host == it || host.endsWith(".$it") }
}

/**
 * In-app Tidal sign-in, ported from ArchiveTune's `TidalLoginScreen`.
 *
 * Two ways in, tried in order, and the first that answers wins:
 *
 *  - **PKCE** — the official Android client's authorization page, whose
 *    redirect carries a code exchangeable for a refreshable token. This is
 *    the durable session, and the only one that can outlive an hour.
 *  - **Web capture** — when the PKCE redirect comes back without a code (or
 *    the exchange is refused), the WebView falls through to the listen.tidal.com
 *    player and catches the Bearer its own requests carry. An hour of a
 *    working session beats a sign-in that failed.
 *
 * Hosted as a root overlay for the same reason as [SpotifyLoginScreen]: the
 * page subtree records into the haze layer, and a WebView drawn twice
 * flickers or worse.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TidalLoginScreen(
    onConnected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val pkce = remember { TidalService.buildPkceChallenge() }
    val handled = remember { AtomicBoolean(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    fun complete(session: TidalAccountStore.Session) {
        TidalAccountStore.adopt(session)
        onConnected()
    }

    fun switchToCapture(view: WebView) {
        view.loadUrl(WEB_PLAYER_URL)
    }

    fun handleRedirect(view: WebView, url: String?): Boolean {
        if (url == null || !url.startsWith(TidalService.PKCE_REDIRECT_URI)) return false
        if (!handled.compareAndSet(false, true)) return true
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val code = uri?.getQueryParameter("code")
        if (code.isNullOrBlank()) {
            handled.set(false)
            switchToCapture(view)
            return true
        }
        scope.launch {
            val session = withContext(Dispatchers.IO) {
                TidalService.exchangePkceCode(code, pkce.verifier, pkce.uniqueKey)
            }
            if (session != null) {
                complete(session)
            } else {
                handled.set(false)
                withContext(Dispatchers.Main) { switchToCapture(view) }
            }
        }
        return true
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = browserUserAgent(settings.userAgentString)
                setBackgroundColor(0xFF121212.toInt())

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        if (handleRedirect(view, request.url.toString())) return true
                        return !isLoginHost(request.url)
                    }

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        canGoBack = view.canGoBack()
                        handleRedirect(view, url)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        canGoBack = view.canGoBack()
                        if (url?.contains("listen.tidal.com", ignoreCase = true) == true) {
                            view.loadUrl(BEARER_HOOK_JS)
                        }
                    }
                }

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onBearer(bearer: String?) {
                            if (bearer.isNullOrBlank()) return
                            if (!handled.compareAndSet(false, true)) return
                            scope.launch {
                                val session = withContext(Dispatchers.IO) {
                                    TidalService.sessionFromBearer(bearer)
                                }
                                if (session != null) {
                                    complete(session)
                                } else {
                                    handled.set(false)
                                }
                            }
                        }
                    },
                    "NeiroTidalAuth",
                )
                webView = this
                loadUrl(pkce.authUrl)
            }
        },
        onRelease = { it.destroy() },
    )

    BackHandler(enabled = canGoBack) {
        webView?.goBack()
    }
}
