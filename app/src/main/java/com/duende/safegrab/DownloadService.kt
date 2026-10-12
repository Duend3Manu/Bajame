package com.duende.safegrab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Sesión temporal SOLO en RAM. Por eso no usamos WorkManager: guardaría los datos en disco. */
object SessionHolder {
    @Volatile var cookies: String? = null
    @Volatile var tempCookies: String? = null

    fun clear() {
        cookies = null
        tempCookies = null
    }
}

sealed interface DlState {
    data object Idle : DlState
    data class Progress(val p: Float, val line: String) : DlState
    data class Success(val files: Int) : DlState
    data class Failure(val msg: String) : DlState
}

object Bus {
    val state = MutableStateFlow<DlState>(DlState.Idle)
}

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val channel = "downloads"
    private val notifId = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url") ?: run { stopSelf(); return START_NOT_STICKY }
        val args = intent.getStringArrayListExtra("args").orEmpty()
        val title = intent.getStringExtra("title").orEmpty()
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(channel, "Descargas", NotificationManager.IMPORTANCE_LOW)
        )

        startForeground(
            notifId, progressNotif(title, 0, "Preparando…"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        scope.launch {
            val cookies = SessionHolder.tempCookies ?: SessionHolder.cookies
            val result = Downloader.download(application, url, cookies, args) { p, line ->
                Bus.state.value = DlState.Progress(p, line)
                val pct = if (p.isNaN() || p.isInfinite()) 0 else (p * 100).toInt().coerceIn(0, 100)
                nm?.notify(notifId, progressNotif(title, pct, line))
            }
            SessionHolder.clear()      // la sesión temporal sale de la memoria
            Bus.state.value = result.fold(
                { DlState.Success(it) },
                { DlState.Failure(if (Downloader.cancelled) "Descarga cancelada." else Errors.friendly(it.message.orEmpty())) }
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            nm?.notify(
                notifId + 1,
                NotificationCompat.Builder(this@DownloadService, channel)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(title.ifBlank { "SafeGrab" })
                    .setContentText(if (result.isSuccess) "Descarga lista" else "La descarga falló")
                    .setAutoCancel(true).build()
            )
            stopSelf()
        }
        return START_NOT_STICKY   // si el sistema mata el proceso, no se reinicia (las cookies se perderían)
    }

    private fun progressNotif(title: String, pct: Int, text: String): Notification =
        NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title.ifBlank { "SafeGrab" })
            .setContentText(text.take(80))
            .setProgress(100, pct.coerceIn(0, 100), pct <= 0)
            .setOngoing(true).setOnlyAlertOnce(true).build()

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
