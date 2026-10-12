package com.duende.safegrab

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** WebView endurecido: solo HTTPS y solo los dominios de la red elegida; sin puentes de JavaScript. */
object SafeWebView {
    fun allowed(host: String): List<String> = when (host) {
        "x.com", "twitter.com" -> listOf("x.com", "twitter.com", "twimg.com")
        "instagram.com" -> listOf("instagram.com", "facebook.com", "cdninstagram.com", "fbcdn.net")
        "facebook.com", "fb.watch" -> listOf("facebook.com", "fb.com", "fb.watch", "fbcdn.net", "instagram.com")
        else -> listOf(host)
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun create(ctx: Context, host: String, onHost: (String) -> Unit): WebView = WebView(ctx).apply {
        layoutParams = ViewGroup.LayoutParams(-1, -1)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        val ok = allowed(host)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val h = request.url.host.orEmpty()
                val good = request.url.scheme == "https" && ok.any { h == it || h.endsWith(".$it") }
                if (!good) Toast.makeText(view.context, "Bloqueado: $h", Toast.LENGTH_SHORT).show()
                return !good
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                onHost(Uri.parse(url).host.orEmpty())
            }
        }
    }
}

/** Impide capturas de pantalla mientras esta pantalla está visible. */
@Composable
fun SecureWindow() {
    val w = (LocalContext.current as Activity).window
    DisposableEffect(Unit) {
        w.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
