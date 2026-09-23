package com.exo.musicplayer.desktop.library

import com.exo.musicplayer.data.library.SearchQuery
import com.exo.musicplayer.data.library.Genres
import androidx.compose.runtime.Immutable
import com.exo.musicplayer.desktop.data.ToolPaths
import com.exo.musicplayer.util.AudioTypes
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/** Immutable so Compose can skip rows whose track hasn't changed. */
@Immutable
data class DesktopTrack(
    val file: File,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    val sizeBytes: Long,
    /** The album artist tag, where the file has one. */
    val albumArtist: String? = null,
    /**
     * The genre tag, as the file spells it.
     *
     * Often several at once - "Rock; Alternative" - so anything counting or
     * matching genres goes through [Genres.split] rather than taking it whole.
     */
    val genre: String? = null
) {
    val displayArtist: String get() = artist?.takeIf { it.isNotBlank() } ?: "Unknown artist"
    val displayAlbum: String get() = album?.takeIf { it.isNotBlank() } ?: "—"
}

/**
 * Reads a music library straight off the filesystem.
 *
 * The desktop import model is deliberately different from Android's: there is no
 * share sheet to receive from, and no reason to copy files into private storage
 * when the user already has them organised in folders. Point it at a directory
 * and it reads what is there, leaving the files exactly where they are.
 */
object FolderLibrary {

    init {
        // jaudiotagger logs an INFO line per file, which is unusable noise when
        // scanning thousands of tracks.
        Logger.getLogger("org.jaudiotagger").level = Level.SEVERE
    }

    private const val MAX_DEPTH = 12

    suspend fun scan(
        roots: List<File>,
        onProgress: (scanned: Int, current: String) -> Unit = { _, _ -> }
    ): List<DesktopTrack> = withContext(Dispatchers.IO) {
        val files = mutableListOf<File>()
        roots.filter { it.isDirectory }.forEach { collect(it, files, 0) }

        // A download folder inside a music folder is scanned from both roots, so
        // the same file would otherwise appear twice.
        files.distinctBy { it.absolutePath.lowercase() }.mapIndexedNotNull { index, file ->
            onProgress(index + 1, file.name)
            read(file)
        }.sortedWith(
            compareBy({ it.displayArtist.lowercase() }, { it.title.lowercase() })
        )
    }

    /**
     * Reads particular files, so a finished download can join the library without
     * re-reading every tag in it. Anything that is not audio is skipped, by the
     * same test a scan applies.
     */
    suspend fun readFiles(files: List<File>): List<DesktopTrack> = withContext(Dispatchers.IO) {
        files.filter { it.isFile && AudioTypes.isProbablyAudio(null, it.name) }
            .mapNotNull { read(it) }
    }

    private fun collect(dir: File, into: MutableList<File>, depth: Int) {
        if (depth > MAX_DEPTH) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            when {
                child.isDirectory -> collect(child, into, depth + 1)
                // Reuses the same extension table the Android app imports with,
                // so both platforms agree on what counts as audio.
                AudioTypes.isProbablyAudio(null, child.name) -> into += child
            }
        }
    }

    /**
     * How long [file] is when its header won't say.
     *
     * Telegram and some converters strip the Xing header an MP3 needs before
     * its length can be stated, and jaudiotagger then reports zero. A track at
     * 0:00 could not be seeked - the bar had nothing to scale against - and the
     * engine, having no length either, reported the position as the length,
     * which made pausing look exactly like reaching the end. MP3s are counted
     * frame by frame, which needs no header and is exact; anything else is
     * asked of the ffmpeg that ships with the app.
     */
    internal fun measure(file: File): Long = runCatching {
        if (file.extension.equals("mp3", ignoreCase = true)) mp3Millis(file) else ffmpegMillis(file)
    }.getOrDefault(0L)

    private val BITRATES = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    private val SAMPLE_RATES = intArrayOf(44100, 48000, 32000, 0)

    private fun mp3Millis(file: File): Long {
        if (file.length() > 200L * 1024 * 1024) return 0L
        val bytes = file.readBytes()
        var at = 0
        if (bytes.size > 10 && bytes[0] == 'I'.code.toByte() &&
            bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()
        ) {
            at = 10 + (((bytes[6].toInt() and 0x7f) shl 21) or ((bytes[7].toInt() and 0x7f) shl 14) or
                ((bytes[8].toInt() and 0x7f) shl 7) or (bytes[9].toInt() and 0x7f))
            // A damaged tag can claim to be longer than the whole file, which
            // would leave nothing to count. The frames are still in there.
            if (at >= bytes.size - 4) at = 0
        }
        var millis = 0.0
        var frames = 0
        while (at < bytes.size - 4) {
            val first = bytes[at].toInt() and 0xFF
            val second = bytes[at + 1].toInt() and 0xFF
            if (first != 0xFF || (second and 0xE0) != 0xE0) {
                at++
                continue
            }
            val third = bytes[at + 2].toInt() and 0xFF
            val bitrate = BITRATES[(third shr 4) and 0xF]
            val rate = SAMPLE_RATES[(third shr 2) and 3]
            if (bitrate == 0 || rate == 0) {
                at++
                continue
            }
            // MPEG-1 carries 1152 samples a frame, MPEG-2 and 2.5 half that.
            val samples = if (((second shr 3) and 3) == 3) 1152 else 576
            val length = 144000 * bitrate / rate + ((third shr 1) and 1)
            if (length <= 4) {
                at++
                continue
            }
            millis += samples * 1000.0 / rate
            frames++
            at += length
        }
        return if (frames < 4) 0L else millis.toLong()
    }

    private fun ffmpegMillis(file: File): Long {
        val ffmpeg = ToolPaths.ffmpeg
        if (!ffmpeg.isFile) return 0L
        val process = ProcessBuilder(ffmpeg.absolutePath, "-hide_banner", "-i", file.absolutePath)
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return 0L
        }
        val at = text.indexOf("Duration: ")
        if (at < 0 || at + 21 > text.length) return 0L
        val stamp = text.substring(at + 10, at + 21).split(":", ".")
        if (stamp.size < 4) return 0L
        val hours = stamp[0].toLongOrNull() ?: return 0L
        val minutes = stamp[1].toLongOrNull() ?: return 0L
        val seconds = stamp[2].toLongOrNull() ?: return 0L
        val hundredths = stamp[3].toLongOrNull() ?: 0L
        return ((hours * 3600 + minutes * 60 + seconds) * 1000) + hundredths * 10
    }

    private fun read(file: File): DesktopTrack? = runCatching {
        val audio = AudioFileIO.read(file)
        val tag = audio.tag
        val header = audio.audioHeader

        fun field(key: FieldKey): String? =
            runCatching { tag?.getFirst(key)?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()

        DesktopTrack(
            file = file,
            title = field(FieldKey.TITLE) ?: file.nameWithoutExtension,
            artist = field(FieldKey.ARTIST) ?: field(FieldKey.ALBUM_ARTIST),
            album = field(FieldKey.ALBUM),
            durationMs = (header?.preciseTrackLength ?: 0.0).times(1000).toLong().takeIf { it > 0 }
                ?: measure(file),
            trackNumber = field(FieldKey.TRACK)?.substringBefore('/')?.toIntOrNull(),
            year = field(FieldKey.YEAR)?.take(4)?.toIntOrNull(),
            sizeBytes = file.length(),
            albumArtist = field(FieldKey.ALBUM_ARTIST),
            genre = field(FieldKey.GENRE)
        )
    }.getOrElse {
        // A file jaudiotagger cannot parse is still probably playable, so it is
        // kept with filename-derived metadata rather than dropped.
        DesktopTrack(
            file = file,
            title = file.nameWithoutExtension,
            artist = null,
            album = null,
            durationMs = measure(file),
            trackNumber = null,
            year = null,
            sizeBytes = file.length()
        )
    }
}
