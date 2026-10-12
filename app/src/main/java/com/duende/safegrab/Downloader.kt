package com.duende.safegrab

import android.app.Application
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Downloader {
    @Volatile private var ready = false
    private const val PID = "safegrab-dl"

    @Volatile var cancelled = false

    /** Detiene la descarga en curso (los archivos parciales se borran solos). */
    fun cancel() {
        cancelled = true
        runCatching { YoutubeDL.getInstance().destroyProcessById(PID) }
    }

    @Synchronized
    internal fun ensureInit(app: Application) {
        if (ready) return
        try {
            YoutubeDL.getInstance().init(app)
            FFmpeg.getInstance().init(app)
            ready = true
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    /** Al abrir la app: borra restos de cookies o descargas si el proceso murió a medias. */
    fun cleanup(app: Application) {
        File(app.cacheDir, "cookies.txt").delete()
        File(app.cacheDir, "dl").deleteRecursively()
    }

    private inline fun <T> withCookieFile(app: Application, url: String, cookies: String?, block: (File?) -> T): T {
        val host = Cookies.hostOf(url)
        val effectiveCookies = cookies ?: SessionHolder.tempCookies ?: SessionHolder.cookies
        val f = if (!effectiveCookies.isNullOrBlank() && host != null) {
            File(app.cacheDir, "cookies.txt").apply { writeText(Cookies.toNetscape(host, effectiveCookies)) }
        } else null
        try {
            return block(f)
        } finally {
            f?.delete()   // las cookies temporales no quedan en disco
        }
    }

    /** Paso 1: lee los formatos disponibles (sin descargar nada). */
    suspend fun analyze(app: Application, url: String, cookies: String?): Result<MediaInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                ensureInit(app)
                withCookieFile(app, url, cookies) { cf ->
                    val req = YoutubeDLRequest(url).apply {
                        addOption("--no-playlist")
                        addOption("--playlist-items", "1")
                        if (url.contains("x.com") || url.contains("twitter.com")) {
                            addOption("--extractor-args", "twitter:api=graphql")
                        }
                        cf?.let { addOption("--cookies", it.absolutePath) }
                        val cookieHeader = cookies ?: SessionHolder.tempCookies ?: SessionHolder.cookies
                        if (!cookieHeader.isNullOrBlank()) {
                            addOption("--add-header", "Cookie:$cookieHeader")
                        }
                    }
                    val info = YoutubeDL.getInstance().getInfo(req)
                    FormatParser.parse(info)
                }
            }
        }

    /** Paso 2: descarga UN elemento con los argumentos del formato elegido. */
    suspend fun download(
        app: Application, url: String, cookies: String?, args: List<String>,
        onProgress: (Float, String) -> Unit
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            ensureInit(app)
            cancelled = false
            val dir = File(app.cacheDir, "dl").apply { deleteRecursively(); mkdirs() }
            try {
                withCookieFile(app, url, cookies) { cf ->
                    val req = YoutubeDLRequest(url).apply {
                        addOption("-o", "${dir.absolutePath}/%(title).80s.%(ext)s")
                        addOption("--restrict-filenames")
                        addOption("--no-playlist")
                        addOption("--playlist-items", "1")
                        if (url.contains("x.com") || url.contains("twitter.com")) {
                            addOption("--extractor-args", "twitter:api=graphql")
                        }
                        var i = 0
                        while (i < args.size) {
                            if (i + 1 < args.size && !args[i + 1].startsWith("-")) {
                                addOption(args[i], args[i + 1]); i += 2
                            } else {
                                addOption(args[i]); i += 1
                            }
                        }
                        cf?.let { addOption("--cookies", it.absolutePath) }
                        val cookieHeader = cookies ?: SessionHolder.tempCookies ?: SessionHolder.cookies
                        if (!cookieHeader.isNullOrBlank()) {
                            addOption("--add-header", "Cookie:$cookieHeader")
                        }
                    }
                    YoutubeDL.getInstance().execute(req, PID) { p, _, line ->
                        onProgress(p / 100f, line ?: "")
                    }
                }
                val files = dir.listFiles()?.filter { it.isFile && it.length() > 0 }.orEmpty()
                check(files.isNotEmpty()) { "No se descargó ningún archivo válido" }
                files.forEach { file ->
                    saveToPrivateVault(app, file)
                    runCatching { saveToDownloads(app, file) }
                }
                files.size
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    private fun saveToPrivateVault(app: Application, f: File) {
        val vaultDir = File(app.getExternalFilesDir(null), "Vault")
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
            File(vaultDir, ".nomedia").createNewFile()
        }
        val destFile = File(vaultDir, f.name)
        f.copyTo(destFile, overwrite = true)
    }

    private fun saveToDownloads(app: Application, f: File) {
        val mime = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(f.extension.lowercase()) ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, f.name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SafeGrab")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("No se pudo crear entrada en MediaStore")

        try {
            app.contentResolver.openOutputStream(uri)?.use { out ->
                f.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("No se pudo abrir el stream de salida para MediaStore")

            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            app.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            app.contentResolver.delete(uri, null, null)
            throw e
        }
    }
}
