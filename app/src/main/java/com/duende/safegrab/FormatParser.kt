package com.duende.safegrab

import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import java.util.Locale
import java.util.TreeMap

data class Option(
    val label: String,
    val sizeBytes: Long?,
    val approx: Boolean,
    val args: List<String>
) {
    val sizeText: String
        get() = sizeBytes?.let { (if (approx) "≈ " else "") + humanSize(it) } ?: "tamaño desconocido"
}

data class MediaInfo(
    val title: String,
    val durationSec: Int,
    val both: List<Option>,        // video + audio
    val videoOnly: List<Option>,   // solo video
    val audio: List<Option>        // MP3 y audios originales
)

fun humanSize(b: Long): String {
    if (b <= 0) return "0 KB"
    val mb = b / 1_048_576.0
    return if (mb >= 1) String.format(Locale.US, "%.1f MB", mb)
    else String.format(Locale.US, "%d KB", b / 1024)
}

object FormatParser {
    private enum class Kind { AUDIO, VIDEO, BOTH, SKIP }

    private fun kind(f: VideoFormat): Kind {
        val ext = f.ext.orEmpty().lowercase()
        if (ext == "mhtml") return Kind.SKIP
        val vcodec = f.vcodec
        val acodec = f.acodec
        val noV = vcodec == null || vcodec == "none" || vcodec == "null" || vcodec.isBlank()
        val noA = acodec == null || acodec == "none" || acodec == "null" || acodec.isBlank()
        return when {
            noV && noA -> Kind.SKIP
            noV -> Kind.AUDIO
            noA -> Kind.VIDEO
            else -> Kind.BOTH
        }
    }

    /** Devuelve (bytes, esAproximado). */
    private fun size(f: VideoFormat, dur: Int): Pair<Long?, Boolean> {
        val exact = (runCatching { f.fileSize }.getOrNull() as? Number)?.toLong() ?: 0L
        if (exact > 0) return exact to false

        val approx = (runCatching { f.fileSizeApproximate }.getOrNull() as? Number)?.toLong() ?: 0L
        if (approx > 0) return approx to true

        val tbr = (runCatching { f.tbr }.getOrNull() as? Number)?.toDouble() ?: 0.0
        if (tbr > 0.0 && dur > 0) {
            val bytes = (tbr * 1000.0 / 8.0 * dur).toLong()
            return bytes to true
        }
        return null to true
    }

    private fun bestOf(list: List<VideoFormat>): VideoFormat? =
        list.sortedWith(
            compareByDescending<VideoFormat> { it.ext.orEmpty().lowercase() == "mp4" }
                .thenByDescending { (runCatching { it.tbr }.getOrNull() as? Number)?.toDouble() ?: 0.0 }
        ).firstOrNull()

    fun parse(info: VideoInfo?): MediaInfo {
        if (info == null) {
            return MediaInfo(
                title = "Sin título",
                durationSec = 0,
                both = listOf(Option("Mejor calidad disponible", null, true, listOf("-f", "best"))),
                videoOnly = emptyList(),
                audio = emptyList()
            )
        }

        val dur = ((runCatching { info.duration }.getOrNull() as? Number)?.toInt() ?: 0).coerceAtLeast(0)
        val fmts = info.formats.orEmpty()

        val audios = fmts.filter { kind(it) == Kind.AUDIO }
            .sortedByDescending { (runCatching { it.abr }.getOrNull() as? Number)?.toDouble() ?: 0.0 }
        val videos = fmts.filter { kind(it) == Kind.VIDEO }
        val boths = fmts.filter { kind(it) == Kind.BOTH }
        val bestAudio = audios.firstOrNull()
        val bestM4a = audios.firstOrNull { it.ext.orEmpty().lowercase() == "m4a" } ?: bestAudio

        // ---- Solo audio: MP3 convertido (peso estimado) + audios originales (peso real) ----
        val audioOpts = mutableListOf<Option>()
        if (audios.isNotEmpty() || boths.isNotEmpty() || fmts.isEmpty()) {
            for (k in listOf(320, 192, 128)) {
                audioOpts += Option(
                    "MP3 · $k kbps",
                    if (dur > 0) k * 1000L / 8 * dur else null, true,
                    listOf("-f", "bestaudio/best", "-x", "--audio-format", "mp3", "--audio-quality", "${k}K")
                )
            }
        }
        for (f in audios.take(4)) {
            val id = f.formatId ?: continue
            val (s, a) = size(f, dur)
            val br = ((runCatching { f.abr }.getOrNull() as? Number)?.toDouble() ?: 0.0).toInt()
            val ext = f.ext.orEmpty().ifBlank { "audio" }
            audioOpts += Option("Original · $ext" + if (br > 0) " · $br kbps" else "", s, a, listOf("-f", id))
        }

        // ---- Solo video (sin audio) ----
        val vByHeight = videos.groupBy { ((runCatching { it.height }.getOrNull() as? Number)?.toInt() ?: 0) }
            .filterKeys { it > 0 }
            .toSortedMap(Comparator.reverseOrder())
        val videoOpts = vByHeight.mapNotNull { (h, list) ->
            val f = bestOf(list) ?: return@mapNotNull null
            val id = f.formatId ?: return@mapNotNull null
            val (s, a) = size(f, dur)
            val ext = f.ext.orEmpty().ifBlank { "mp4" }
            Option("${h}p · $ext", s, a, listOf("-f", id))
        }

        // ---- Video + audio: unidos con ffmpeg (YouTube) o ya completos (X, Instagram, Facebook) ----
        val bothByHeight = TreeMap<Int, Option>(Comparator.reverseOrder())
        if (bestAudio != null) {
            vByHeight.forEach { (h, list) ->
                val v = bestOf(list) ?: return@forEach
                val vExt = v.ext.orEmpty().lowercase()
                val aExt = (bestM4a ?: bestAudio).ext.orEmpty().lowercase()
                val a = if (vExt == "mp4") (bestM4a ?: bestAudio) else bestAudio
                val vid = v.formatId ?: return@forEach
                val aid = a.formatId ?: return@forEach
                val (vs, va) = size(v, dur)
                val (asz, aa) = size(a, dur)
                val total = if (vs != null && asz != null) vs + asz else null
                val container = if (vExt == "mp4" && aExt == "m4a") "mp4" else "mkv"
                bothByHeight[h] = Option(
                    "${h}p · $container · video + audio", total, va || aa,
                    listOf("-f", "$vid+$aid", "--merge-output-format", container)
                )
            }
        }
        boths.groupBy { ((runCatching { it.height }.getOrNull() as? Number)?.toInt() ?: 0) }.forEach { (h, list) ->
            if (h in bothByHeight && h > 0) return@forEach
            val f = bestOf(list) ?: return@forEach
            val id = f.formatId ?: return@forEach
            val (s, a) = size(f, dur)
            val ext = f.ext.orEmpty().ifBlank { "mp4" }
            val label = if (h > 0) "${h}p · $ext" else "Mejor calidad · $ext"
            bothByHeight[h] = Option(label, s, a, listOf("-f", id))
        }
        if (bothByHeight.isEmpty()) {
            bothByHeight[0] = Option("Mejor calidad disponible", null, true, listOf("-f", "best"))
        }

        val title = info.title.orEmpty().ifBlank { "Sin título" }
        return MediaInfo(title, dur, bothByHeight.values.toList(), videoOpts, audioOpts)
    }
}
