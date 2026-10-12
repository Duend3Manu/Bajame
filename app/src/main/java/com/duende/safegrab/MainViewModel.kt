package com.duende.safegrab

import android.app.Application
import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed interface UiState {
    data object Idle : UiState
    data object Analyzing : UiState
    data class Choose(val info: MediaInfo) : UiState
    data class Downloading(val progress: Float, val line: String) : UiState
    data class NeedsLogin(val host: String, val url: String, val reason: String) : UiState
    data class Logging(val host: String, val url: String) : UiState
    data class ImagePick(val host: String, val url: String) : UiState
    data class AskKeep(val host: String) : UiState
    data class Done(val message: String) : UiState
    data class Error(val message: String) : UiState
}

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val store = SessionStore(app)

    private var host = ""
    private var currentUrl = ""
    private var activeCookies: String? = null   // cookies en uso (solo RAM)
    private var tempCookies: String? = null     // sesión temporal de un login reciente (solo RAM)
    private var fromLogin = false
    private var retried = false
    private var stoppedAt = 0L
    private var imgJob: Job? = null

    var state by mutableStateOf<UiState>(UiState.Idle)
        private set
    var appUpdate by mutableStateOf<AppUpdate?>(null)
        private set
    var autoUpdates by mutableStateOf(Updater.isAuto(app))
        private set
    var locked by mutableStateOf(prefs.lockEnabled)
        private set
    var lockMessage by mutableStateOf<String?>(null)
        private set
    var showSettings by mutableStateOf(false)
        private set
    var sessions by mutableStateOf(emptyList<Pair<String, Long>>())
        private set

    init {
        Downloader.cleanup(app)
        if (autoUpdates) checkUpdates(false)
        viewModelScope.launch {
            Bus.state.collect { s ->
                when (s) {
                    is DlState.Progress -> state = UiState.Downloading(s.p, s.line)
                    is DlState.Success -> {
                        Bus.state.value = DlState.Idle
                        state = if (fromLogin) UiState.AskKeep(host)
                        else UiState.Done("Listo: ${s.files} archivo(s) en Descargas/SafeGrab")
                    }
                    is DlState.Failure -> {
                        Bus.state.value = DlState.Idle
                        if (looksLikeAuth(s.msg)) {
                            store.clear(host)
                            SessionHolder.clear()
                            tempCookies = null
                            activeCookies = null
                            state = UiState.NeedsLogin(host, currentUrl, "La sesión en $host expiró o fue rechazada. Inicia sesión de nuevo.")
                        } else {
                            state = UiState.Error(s.msg)
                        }
                    }
                    DlState.Idle -> {}
                }
            }
        }
    }

    // ---------- Descargas de video / audio ----------
    fun start(raw: String) {
        var url = raw.trim()

        // Limpieza de parámetros de rastreo de TikTok
        if (url.contains("tiktok.com")) {
            url = url.substringBefore("?")
        }

        val h = Cookies.hostOf(url) ?: run { state = UiState.Error("Enlace no válido"); return }
        if (ImageTools.isDirectImage(url)) { downloadImages(listOf(url)); return }
        host = h; currentUrl = url; fromLogin = false; retried = false
        analyze(store.get(h))
    }

    private fun analyze(cookies: String?) {
        activeCookies = cookies

        // Si es X/Twitter y no tenemos cookies activas ni temporales, enviamos a login directamente
        if ((host == "x.com" || host == "twitter.com") && cookies.isNullOrBlank() && tempCookies.isNullOrBlank()) {
            store.clear(host)
            state = UiState.NeedsLogin(host, currentUrl, "X (Twitter) requiere iniciar sesión para descargar videos.")
            return
        }

        state = UiState.Analyzing
        viewModelScope.launch {
            Downloader.analyze(app, currentUrl, cookies)
                .onSuccess { state = UiState.Choose(it) }
                .onFailure { e ->
                    val msg = e.message.orEmpty()
                    if (looksLikeAuth(msg) || host == "x.com" || host == "twitter.com") {
                        store.clear(host)
                        SessionHolder.clear()
                        tempCookies = null
                        activeCookies = null
                        state = UiState.NeedsLogin(host, currentUrl, "X requiere una sesión activa para este contenido.")
                    } else if (!retried && autoUpdates) {
                        retried = true
                        Updater.updateEngine(app, true)   // el sitio pudo cambiar: actualiza el motor y reintenta
                        analyze(cookies)
                    } else state = UiState.Error(Errors.friendly(msg))
                }
        }
    }

    private fun looksLikeAuth(m: String): Boolean {
        val lower = m.lowercase()
        val generalAuthTerms = listOf(
            "login", "log in", "sign in", "cookies", "private", "authentication",
            "rate-limit", "restricted", "login_required", "consent_required",
            "please log in", "isn't available to everyone", "certain audiences"
        )
        val xSpecificTerms = listOf(
            "twitter", "x.com", "graphql", "guest", "authorization",
            "token", "denied", "post unavailable", "unavailable"
        )
        return (generalAuthTerms + xSpecificTerms).any { lower.contains(it) }
    }

    /** El usuario eligió una calidad: la descarga corre en un servicio en primer plano. */
    fun choose(o: Option, title: String) {
        SessionHolder.cookies = activeCookies
        SessionHolder.tempCookies = activeCookies
        val i = Intent(app, DownloadService::class.java)
            .putExtra("url", currentUrl)
            .putExtra("title", title)
            .putStringArrayListExtra("args", ArrayList(o.args))
        ContextCompat.startForegroundService(app, i)
        state = UiState.Downloading(0f, "Iniciando…")
    }

    fun cancelDownload() {
        if (imgJob?.isActive == true) { imgJob?.cancel(); state = UiState.Idle; return }
        viewModelScope.launch(Dispatchers.IO) { Downloader.cancel() }
    }

    // ---------- Imágenes ----------
    fun startImages(raw: String) {
        val url = raw.trim()
        val h = Cookies.hostOf(url) ?: run { state = UiState.Error("Pega primero un enlace válido"); return }
        state = UiState.ImagePick(h, url)
    }

    fun downloadImages(urls: List<String>) {
        state = UiState.Downloading(0f, "Descargando imágenes…")
        imgJob = viewModelScope.launch {
            ImageTools.download(app, urls) { n -> state = UiState.Downloading(n.toFloat() / urls.size, "$n de ${urls.size}") }
                .onSuccess { state = UiState.Done("Listo: $it imagen(es) en Descargas/SafeGrab") }
                .onFailure { state = UiState.Error(Errors.friendly(it.message.orEmpty())) }
        }
    }

    // ---------- Login temporal ----------
    fun openLogin(h: String, url: String) { state = UiState.Logging(h, url) }
    fun cancel() { state = UiState.Idle }

    fun loginDone(h: String, url: String, cookieHeader: String?) {
        val cm = CookieManager.getInstance()
        val targetUrl = if (h == "x.com" || h == "twitter.com") "https://x.com" else "https://www.$h"

        val finalCookies = cookieHeader.takeIf { !it.isNullOrBlank() }
            ?: cm.getCookie(targetUrl)
            ?: cm.getCookie("https://x.com")
            ?: cm.getCookie("https://twitter.com")

        Cookies.wipeWebView()                       // el WebView queda limpio al instante

        if (finalCookies.isNullOrBlank() || ((h == "x.com" || h == "twitter.com") && !finalCookies.contains("auth_token"))) {
            state = UiState.Error("No se detectó el inicio de sesión en X. Inicia sesión completamente.")
            return
        }

        tempCookies = finalCookies
        SessionHolder.tempCookies = finalCookies
        fromLogin = true
        analyze(finalCookies)
    }

    fun keepSession(h: String) {
        tempCookies?.let { store.save(h, it) }
        tempCookies = null; fromLogin = false
        SessionHolder.clear()
        state = UiState.Done("Sesión de $h guardada cifrada en este teléfono")
    }

    fun closeSession(h: String) {
        tempCookies = null; fromLogin = false
        SessionHolder.clear()
        store.clear(h); Cookies.wipeWebView()
        state = UiState.Done("Descarga lista. Sesión de $h cerrada y borrada")
    }

    // ---------- Ajustes, privacidad y bloqueo ----------
    fun openSettings() { sessions = store.list(); lockMessage = null; showSettings = true }
    fun closeSettings() { showSettings = false }
    fun deleteSession(h: String) { store.clear(h); sessions = store.list() }

    fun panic() {
        tempCookies = null; activeCookies = null; fromLogin = false
        SessionHolder.clear()
        store.clearAll(); Cookies.wipeWebView(); Downloader.cleanup(app)
        sessions = emptyList()
        state = UiState.Done("Todo borrado: sesiones, cookies y archivos temporales")
    }

    fun setLock(ctx: Context, on: Boolean) {
        if (on && !Lock.available(ctx)) {
            lockMessage = "Primero configura huella, rostro o PIN en los ajustes del teléfono."
            return
        }
        lockMessage = null
        prefs.setLock(on)
        if (!on) locked = false
    }

    fun unlock() { locked = false }
    fun onBackgrounded() { stoppedAt = System.currentTimeMillis() }
    fun onForegrounded() {
        if (prefs.lockEnabled && !locked && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > 60_000) locked = true
    }

    // ---------- Actualizaciones ----------
    fun setAuto(v: Boolean) { autoUpdates = v; Updater.setAuto(app, v) }

    fun checkUpdates(force: Boolean) {
        viewModelScope.launch {
            Updater.updateEngine(app, force)
            Updater.checkAppUpdate(BuildConfig.VERSION_NAME).onSuccess { appUpdate = it }
        }
    }

    fun dismissUpdate() { appUpdate = null }
}
