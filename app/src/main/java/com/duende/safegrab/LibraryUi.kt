package com.duende.safegrab

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class Kind { IMAGE, VIDEO, AUDIO }

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val mime: String,
    val kind: Kind,
    val file: File? = null,
    val isPrivate: Boolean = false
)

/** Lista lo descargado con SafeGrab tanto en la Bóveda Privada como en MediaStore. */
object Library {
    private val audioExt = setOf("mp3", "m4a", "opus", "ogg", "aac", "flac", "wav")

    private fun kindOf(name: String, mime: String): Kind? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            mime.startsWith("image/") -> Kind.IMAGE
            ext in audioExt || mime.startsWith("audio/") -> Kind.AUDIO
            mime.startsWith("video/") -> Kind.VIDEO
            else -> null
        }
    }

    fun list(ctx: Context, kind: Kind): List<MediaItem> {
        val out = mutableListOf<MediaItem>()

        // 1. Archivos en Bóveda Privada con .nomedia
        val vaultDir = File(ctx.getExternalFilesDir(null), "Vault")
        if (vaultDir.exists()) {
            vaultDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.forEachIndexed { idx, file ->
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
                val k = kindOf(file.name, mime)
                if (k == kind) {
                    out += MediaItem(
                        id = -1000L - idx,
                        uri = Uri.fromFile(file),
                        name = "🔒 " + file.name,
                        size = file.length(),
                        mime = mime,
                        kind = kind,
                        file = file,
                        isPrivate = true
                    )
                }
            }
        }

        // 2. Archivos en MediaStore Público
        val coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.MIME_TYPE, MediaStore.Downloads.SIZE
        )
        runCatching {
            ctx.contentResolver.query(
                coll, proj, "${MediaStore.Downloads.RELATIVE_PATH} LIKE ?",
                arrayOf(Environment.DIRECTORY_DOWNLOADS + "/SafeGrab%"),
                "${MediaStore.Downloads.DATE_ADDED} DESC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.Downloads.MIME_TYPE)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol).orEmpty()
                    val mime = c.getString(mimeCol).orEmpty()
                    val size = c.getLong(sizeCol)
                    if (kindOf(name, mime) == kind)
                        out += MediaItem(id, ContentUris.withAppendedId(coll, id), name, size, mime, kind)
                }
            }
        }
        return out
    }

    fun open(ctx: Context, i: MediaItem) = runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(i.uri, i.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun share(ctx: Context, i: MediaItem) = runCatching {
        val send = Intent(Intent.ACTION_SEND).setType(i.mime).putExtra(Intent.EXTRA_STREAM, i.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun delete(ctx: Context, i: MediaItem) = runCatching {
        if (i.isPrivate && i.file != null) {
            i.file.delete()
        } else {
            ctx.contentResolver.delete(i.uri, null, null)
        }
    }
}

@Composable
fun Thumb(item: MediaItem) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(item.uri, Size(128, 128), null).asImageBitmap() }.getOrNull()
        }
    }
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(when (item.kind) { Kind.IMAGE -> "🖼️"; Kind.VIDEO -> "🎬"; Kind.AUDIO -> "🎵" })
    }
}

@Composable
fun LibraryScreen(kind: Kind, modifier: Modifier) {
    val ctx = LocalContext.current
    var files by remember(kind) { mutableStateOf(emptyList<MediaItem>()) }
    var tick by remember { mutableIntStateOf(0) }
    var toDelete by remember { mutableStateOf<MediaItem?>(null) }
    var videoToPlay by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(kind, tick) { files = withContext(Dispatchers.IO) { Library.list(ctx, kind) } }
    val title = when (kind) { Kind.IMAGE -> "Imágenes"; Kind.VIDEO -> "Videos"; Kind.AUDIO -> "Música" }

    if (videoToPlay != null) {
        PrivateVideoPlayer(
            videoFile = videoToPlay!!,
            onClose = { videoToPlay = null }
        )
    } else {
        Column(modifier.fillMaxSize()) {
            Text("$title descargados con SafeGrab", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            if (files.isEmpty()) Text("Aún no hay archivos aquí.", Modifier.padding(horizontal = 16.dp))
            LazyColumn {
                items(files, key = { it.id }) { f ->
                    ListItem(
                        leadingContent = { Thumb(f) },
                        headlineContent = { Text(f.name, maxLines = 2) },
                        supportingContent = { Text(humanSize(f.size)) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { Library.share(ctx, f) }) { Text("Enviar") }
                                TextButton(onClick = { toDelete = f }) { Text("Borrar") }
                            }
                        },
                        modifier = Modifier.clickable {
                            if (f.file != null && (f.kind == Kind.VIDEO || f.kind == Kind.AUDIO)) {
                                videoToPlay = f.file
                            } else {
                                Library.open(ctx, f)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar este archivo?") },
            text = { Text(d.name) },
            confirmButton = { TextButton(onClick = { Library.delete(ctx, d); toDelete = null; tick++ }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}
