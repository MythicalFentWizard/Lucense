package com.exo.musicplayer.desktop.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.exo.musicplayer.desktop.library.DesktopTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Cover art for the desktop library.
 *
 * Three places art can come from, in the order they are cheapest to reach: the
 * downloaded cache, the tag embedded in the file, and a `cover.jpg` sitting next
 * to it — the convention every ripper and downloader already follows, so a
 * folder-based library usually has art before Lucense fetches anything.
 *
 * Decoding happens off the UI thread and results are held in a bounded
 * memory cache. This is the same lesson the Android build learned the hard way:
 * touching the filesystem while a list scrolls is what makes a list stutter.
 */
object Covers {

    private val memory = Thumbnails.ByteBudgetCache()

    /** Paths already checked and found to have no art, so they aren't re-read. */
    private val misses = Thumbnails.BoundedKeySet()

    /**
     * Goes up whenever a cover is fetched, so covers already on screen swap in
     * while a bulk run is still going rather than only after they scroll away.
     */
    var revision by mutableStateOf(0)
        private set

    private val sidecarNames = listOf(
        "cover.jpg", "cover.png", "folder.jpg", "folder.png",
        "front.jpg", "front.png", "album.jpg", "albumart.jpg"
    )

    fun cached(track: DesktopTrack): ImageBitmap? = memory[track.file.absolutePath]

    fun knownMissing(track: DesktopTrack): Boolean = track.file.absolutePath in misses

    suspend fun load(track: DesktopTrack): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = track.file.absolutePath
        memory[key]?.let { return@withContext it }
        if (key in misses) return@withContext null

        val bytes = downloadedFile(track).takeIf { it.exists() }?.readBytes()
            ?: embedded(track.file)
            ?: sidecar(track.file)

        if (bytes == null) {
            misses += key
            return@withContext null
        }

        // Scaled on the way in: the source is often 1000x1000, and the largest
        // place it is ever drawn is a 144dp grid cell.
        val bitmap = Thumbnails.decodeScaled(bytes)
        if (bitmap == null) {
            misses += key
        } else {
            memory[key] = bitmap
        }
        bitmap
    }

    /** Fetches [url], caches it beside the library and, where possible, embeds it. */
    suspend fun fetchAndStore(
        track: DesktopTrack,
        url: String,
        embedInFile: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val bytes = download(url)?.let(Thumbnails::squared) ?: return@withContext false
        // Decoded once to prove it is a real image, and that same result is
        // what goes in the cache rather than decoding a second time.
        val bitmap = Thumbnails.decodeScaled(bytes) ?: return@withContext false

        downloadedFile(track).writeBytes(bytes)
        if (embedInFile) runCatching { embed(track.file, bytes) }

        val key = track.file.absolutePath
        misses -= key
        memory[key] = bitmap
        revision++
        true
    }

    fun forget(track: DesktopTrack) {
        val key = track.file.absolutePath
        memory -= key
        misses -= key
    }

    /** Whether art is available without any network work. */
    fun hasLocalArt(track: DesktopTrack): Boolean =
        downloadedFile(track).exists() ||
            embedded(track.file) != null ||
            sidecar(track.file) != null

    private fun downloadedFile(track: DesktopTrack): File =
        File(AppDirs.covers, hash(track.file.absolutePath) + ".img")

    private fun embedded(file: File): ByteArray? = runCatching {
        AudioFileIO.read(file).tag?.firstArtwork?.binaryData
    }.getOrNull()

    private fun sidecar(file: File): ByteArray? {
        val dir = file.parentFile ?: return null
        for (name in sidecarNames) {
            val candidate = File(dir, name)
            if (candidate.isFile) return runCatching { candidate.readBytes() }.getOrNull()
        }
        return null
    }

    private fun embed(file: File, bytes: ByteArray) {
        val temp = File.createTempFile("lucense-art", ".jpg")
        try {
            temp.writeBytes(bytes)
            val audio = AudioFileIO.read(file)
            val tag = audio.tagOrCreateAndSetDefault
            tag.deleteArtworkField()
            tag.setField(ArtworkFactory.createArtworkFromFile(temp))
            audio.commit()
        } finally {
            temp.delete()
        }
    }

    private fun download(url: String): ByteArray? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Lucense/1.0 (desktop)")
        }
        try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }.takeIf { it.size > 512 }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/**
 * Writes metadata back into a file's tags.
 *
 * Two callers with opposite needs, hence [clearBlanks]. Automatic
 * identification must never wipe a field it has no opinion about, so a null or
 * blank value there means "leave it alone". Hand editing must be able to empty
 * a field, because correcting a wrong album name to nothing is a legitimate
 * edit and a writer that silently ignored it would look broken.
 */
/** A song's tags exactly as its own file had them. */
data class OriginalTags(
    val title: String?,
    val artist: String?,
    val album: String?,
    val year: String?,
    val genre: String?
)

object TagWriter {

    /**
     * Told what a file's tags say, immediately before anything changes them.
     *
     * A hook here rather than a call at each of the seven places tags get
     * written: the entire value of the snapshot is that it is never missed,
     * and a write path added later would otherwise quietly not be covered.
     */
    var beforeChange: ((File, OriginalTags) -> Unit)? = null

    private fun snapshot(file: File, tag: Tag) {
        val hook = beforeChange ?: return
        fun field(key: FieldKey): String? =
            runCatching { tag.getFirst(key)?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()
        // Never let remembering the old tags stop the new ones being written.
        runCatching {
            hook(
                file,
                OriginalTags(
                    title = field(FieldKey.TITLE),
                    artist = field(FieldKey.ARTIST),
                    album = field(FieldKey.ALBUM),
                    year = field(FieldKey.YEAR),
                    genre = field(FieldKey.GENRE)
                )
            )
        }
    }

    fun write(
        file: File,
        title: String?,
        artist: String?,
        album: String?,
        year: Int?,
        genre: String? = null,
        clearBlanks: Boolean = false
    ): Result<Unit> = runCatching {
        val audio = AudioFileIO.read(file)
        val tag = audio.tagOrCreateAndSetDefault
        snapshot(file, tag)

        fun apply(key: FieldKey, value: String?) {
            val text = value?.trim()
            when {
                !text.isNullOrEmpty() -> tag.setField(key, text)
                clearBlanks -> runCatching { tag.deleteField(key) }
            }
        }

        apply(FieldKey.TITLE, title)
        apply(FieldKey.ARTIST, artist)
        apply(FieldKey.ALBUM, album)
        apply(FieldKey.YEAR, year?.toString())
        apply(FieldKey.GENRE, genre)
        audio.commit()
    }

    /** Writes only the fields [change] names, leaving the rest of the tag alone. */
    fun change(change: TagChange): Result<Unit> = runCatching {
        val audio = AudioFileIO.read(change.file)
        val tag = audio.tagOrCreateAndSetDefault
        snapshot(change.file, tag)
        change.title?.let { tag.setField(FieldKey.TITLE, it) }
        change.artist?.let { tag.setField(FieldKey.ARTIST, it) }
        change.album?.let { tag.setField(FieldKey.ALBUM, it) }
        change.albumArtist?.let { tag.setField(FieldKey.ALBUM_ARTIST, it) }
        change.year?.let { tag.setField(FieldKey.YEAR, it) }
        change.genre?.let { tag.setField(FieldKey.GENRE, it) }
        audio.commit()
    }
}
