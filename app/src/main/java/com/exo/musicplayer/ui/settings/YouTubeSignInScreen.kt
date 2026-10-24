package com.exo.musicplayer.ui.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.exo.musicplayer.data.download.YouTubeCookies

/**
 * YouTube's own sign-in page, inside the app, so yt-dlp can download as the
 * user's account when YouTube refuses their VPN's address. The password goes
 * to Google's page and nowhere else; once YouTube opens signed in, its
 * cookies are kept for yt-dlp and the page closes by itself.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeSignInScreen(cookies: YouTubeCookies, onClose: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                    Column(Modifier.weight(1f)) {
                        Text("Sign in to YouTube", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Google's own page. Only YouTube's sign-in is kept, on this phone, for downloads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        if (cookies.saveFromWebView()) onClose() else note = "Not signed in yet: finish signing in above."
                    }) { Text("Done") }
                }
                note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // Google turns away sign-ins it can tell come from an
                            // app's web view, and the "; wv" is how it tells.
                            settings.userAgentString = settings.userAgentString.replace("; wv", "")
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    loading = false
                                    // Back on YouTube, signed in: kept, and done.
                                    val host = url?.let { Uri.parse(it).host }.orEmpty()
                                    if (host.endsWith("youtube.com") && cookies.saveFromWebView()) onClose()
                                }
                            }
                            loadUrl(YouTubeCookies.SIGN_IN_URL)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
