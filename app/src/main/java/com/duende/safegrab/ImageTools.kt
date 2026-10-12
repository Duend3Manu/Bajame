package com.duende.safegrab

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Descarga de imágenes sin yt-dlp: enlaces directos y las que el usuario marca en el navegador temporal. */
object ImageTools {
    fun isDirectImage(url: String): Boolean {
        val u = Uri.parse(url)
        val p = u.path.orEmpty().lowercase()
        return Regex(".*\\.(jpe?g|png|webp|gif)$").matches(p) ||
            (u.host == "pbs.twimg.com" && p.startsWith("/media/"))
    }

    /** En X, name=orig pide la calidad original. */
    fun normalize(url: String): String {
        val u = Uri.parse(url)
        if (u.host == "pbs.twimg.com" && u.path.orEmpty().startsWith("/media/")) {
            val b = u.buildUpon().clearQuery()
            u.getQueryParameter("format")?.let { b.appendQueryParameter("format", it) }
            return b.appendQueryParameter("name", "orig").build().toString()
        }
        return url
    }

    suspend fun download(app: Application, urls: List<String>, onDone: (Int) -> Unit): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                var ok = 0
                urls.forEachIndexed { i, raw ->
                    runCatching { save(app, normalize(raw)); ok++ }
                    onDone(i + 1)
                }
                check(ok > 0) { "No se pudo descargar ninguna imagen" }
                ok
            }
        }

    private fun save(app: Application, url: String) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val mime = c.contentType?.substringBefore(';')?.trim() ?: "image/jpeg"
            check(mime.startsWith("image/")) { "No es una imagen" }
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            val base = Uri.parse(url).lastPathSegment?.substringBeforeLast('.')
                ?.replace(Regex("[^A-Za-z0-9_-]"), "_")?.take(40).orEmpty().ifBlank { "imagen" }
            val filename = "${base}_${System.currentTimeMillis()}.$ext"

            val bytes = c.inputStream.use { it.readBytes() }

            // Guardado en Bóveda Privada con .nomedia
            runCatching { saveToPrivateVault(app, filename, bytes) }

            // Guardado en MediaStore público
            runCatching { saveToDownloads(app, filename, mime, bytes) }
        } finally {
            c.disconnect()
        }
    }

    private fun saveToPrivateVault(app: Application, filename: String, bytes: ByteArray) {
        val vaultDir = File(app.getExternalFilesDir(null), "Vault")
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
            File(vaultDir, ".nomedia").createNewFile()
        }
        File(vaultDir, filename).writeBytes(bytes)
    }

    private fun saveToDownloads(app: Application, filename: String, mime: String, bytes: ByteArray) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SafeGrab")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = app.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return

        try {
            app.contentResolver.openOutputStream(uri)?.use { o -> o.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            app.contentResolver.update(uri, values, null, null)
        } catch (_: Exception) {
            app.contentResolver.delete(uri, null, null)
        }
    }
}
