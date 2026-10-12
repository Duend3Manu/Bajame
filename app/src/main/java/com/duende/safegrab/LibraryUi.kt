package com.duende.safegrab

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
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

fun shareFileFromVault(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir archivo con..."))
}

/** Lista lo descargado con Bajame tanto en la Bóveda Privada como en MediaStore. */
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

        // 1. Archivos en Bóveda Privada (excluyendo archivos ocultos como .nomedia)
        val vaultDir = File(ctx.getExternalFilesDir(null), "Vault")
        if (vaultDir.exists()) {
            vaultDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.forEachIndexed { idx, file ->
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
                val k = kindOf(file.name, mime)
                if (k == kind) {
                    out += MediaItem(
                        id = -1000L - idx,
                        uri = Uri.fromFile(file),
                        name = file.name,
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
        if (i.isPrivate && i.file != null) {
            shareFileFromVault(ctx, i.file)
        } else {
            val send = Intent(Intent.ACTION_SEND).setType(i.mime).putExtra(Intent.EXTRA_STREAM, i.uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
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
fun VaultItemCard(
    item: MediaItem,
    onPlayClick: (File) -> Unit,
    onDeleteClick: (MediaItem) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable {
                if (item.file != null && (item.kind == Kind.VIDEO || item.kind == Kind.AUDIO)) {
                    onPlayClick(item.file)
                } else {
                    Library.open(context, item)
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.file != null) {
                AsyncImage(
                    model = item.file,
                    contentDescription = "Miniatura",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(when (item.kind) { Kind.IMAGE -> "🖼️"; Kind.VIDEO -> "🎬"; Kind.AUDIO -> "🎵" })
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = humanSize(item.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row {
                IconButton(onClick = { Library.share(context, item) }) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Compartir", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { onDeleteClick(item) }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Borrar", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun VaultScreen(modifier: Modifier) {
    var subTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("Videos" to Kind.VIDEO, "Música" to Kind.AUDIO, "Imágenes" to Kind.IMAGE)
    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = subTab) {
            subTabs.forEachIndexed { i, (label, _) ->
                Tab(selected = subTab == i, onClick = { subTab = i }, text = { Text(label) })
            }
        }
        LibraryScreen(kind = subTabs[subTab].second, modifier = Modifier.weight(1f))
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
            Text("Tus archivos en la Bóveda ($title)", Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            if (files.isEmpty()) Text("Aún no hay archivos aquí.", Modifier.padding(horizontal = 16.dp))
            LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(files, key = { it.id }) { f ->
                    VaultItemCard(
                        item = f,
                        onPlayClick = { file -> videoToPlay = file },
                        onDeleteClick = { item -> toDelete = item }
                    )
                }
            }
        }
    }

    toDelete?.let { d ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar este archivo?") },
            text = { Text(d.name) },
            confirmButton = {
                TextButton(onClick = {
                    Library.delete(ctx, d)
                    toDelete = null
                    tick++
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}
