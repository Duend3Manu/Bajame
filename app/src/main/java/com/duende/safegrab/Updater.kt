package com.duende.safegrab

import android.app.Application
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(val version: String, val pageUrl: String, val notes: String)

/**
 * Actualizaciones sin servidor propio. Solo habla con GitHub y no envía ningún dato del usuario
 * (ni siquiera un identificador): solo pide la última versión publicada.
 */
object Updater {
    private const val REPO = "Duend3Manu/Bajame"
    private const val PREFS = "updater"              // solo guarda un interruptor y fechas

    fun isAuto(app: Application) = app.getSharedPreferences(PREFS, 0).getBoolean("auto", true)
    fun setAuto(app: Application, v: Boolean) =
        app.getSharedPreferences(PREFS, 0).edit().putBoolean("auto", v).apply()

    /** Actualiza yt-dlp (el motor). Máximo una vez al día, salvo que se fuerce. */
    suspend fun updateEngine(app: Application, force: Boolean = false): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val p = app.getSharedPreferences(PREFS, 0)
                val now = System.currentTimeMillis()
                if (!force && now - p.getLong("engine_last", 0L) < 24 * 3600_000L) return@runCatching "al día"
                Downloader.ensureInit(app)
                val status = YoutubeDL.getInstance().updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
                p.edit().putLong("engine_last", now).apply()
                status?.toString() ?: "al día"
            }
        }

    /** Consulta la última release en GitHub. Devuelve null si ya tienes la última. */
    suspend fun checkAppUpdate(currentVersion: String): Result<AppUpdate?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val c = (URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "SafeGrab")   // GitHub exige un User-Agent
                }
                try {
                    if (c.responseCode != 200) return@runCatching null
                    val j = JSONObject(c.inputStream.bufferedReader().readText())
                    val tag = j.getString("tag_name").removePrefix("v")
                    if (isNewer(tag, currentVersion))
                        AppUpdate(tag, j.getString("html_url"), j.optString("body").take(600))
                    else null
                } finally {
                    c.disconnect()
                }
            }
        }

    private fun isNewer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d > 0
        }
        return false
    }
}
