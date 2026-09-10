package com.exo.musicplayer.data.ingest

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import com.exo.musicplayer.data.db.MusicDatabase
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.util.AudioTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** What happened to one shared URI. */
sealed interface ImportResult {
    val displayName: String?

    data class Imported(val track: Track) : ImportResult {
        override val displayName: String get() = track.title
    }

    data class Duplicate(val existing: Track) : ImportResult {
        override val displayName: String get() = existing.title
    }

    data class Rejected(override val displayName: String?, val reason: String) : ImportResult
    data class Failed(override val displayName: String?, val error: Throwable) : ImportResult
}

/**
 * Copies shared audio into storage this app owns.
 *
 * The whole reason this class exists: an app sharing to us (Telegram, a file
 * manager, a browser download) passes a content:// URI backed by a temporary
 * read grant. That grant is tied to the receiving task and is revoked when the
 * task goes away, and it cannot be persisted — only ACTION_OPEN_DOCUMENT URIs
 * can be. Holding on to the URI would give us a library full of links that go
 * dead within minutes. So we read the bytes *now*, while the grant is live, and
 * write our own copy.
 */
class TrackImporter(private val context: Context) {

    private val db = MusicDatabase.get(context)

    /** Where imported audio lives. No runtime permission needed for this directory. */
    private val musicDir: File
        get() {
            val external = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            val dir = external ?: File(context.filesDir, "Music")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val artDir: File
        get() = File(context.filesDir, "art").apply { if (!exists()) mkdirs() }

    /**
     * @param mimeHint the sharing intent's own type. Telegram sometimes leaves the
     *   provider's [ContentResolver.getType] null while setting it on the intent,
     *   so it is used as a fallback rather than ignored.
     */
    suspend fun import(
        uri: Uri,
        sourceApp: String?,
        mimeHint: String? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val meta = queryOpenable(resolver, uri)
        val displayName = meta.first
        val declaredMime = resolver.getType(uri)
            ?: mimeHint?.takeIf { it.isNotBlank() && it != "*/*" }

        if (!AudioTypes.isProbablyAudio(declaredMime, displayName)) {
            return@withContext ImportResult.Rejected(
                displayName,
                "That does not look like an audio file."
            )
        }

        var staged: File? = null
        try {
            val extension = AudioTypes.extensionFor(declaredMime, displayName)
            // Staged inside the destination directory so the final move is a
            // rename rather than a second full copy across filesystems.
            val temp = File.createTempFile("import_", PART_SUFFIX, musicDir)
            staged = temp

            val hash = resolver.openInputStream(uri).use { input ->
                if (input == null) {
                    return@withContext ImportResult.Failed(
                        displayName,
                        IllegalStateException("Could not open the shared file.")
                    )
                }
                copyAndHash(input, temp)
            }

            if (temp.length() == 0L) {
                return@withContext ImportResult.Failed(
                    displayName,
                    IllegalStateException("The shared file was empty.")
                )
            }

            // Re-sharing the same song is common; keep one copy.
            db.trackDao().findByHash(hash)?.let { existing ->
                return@withContext ImportResult.Duplicate(existing)
            }

            val tags = readTags(temp)
            val fallbackTitle = displayName
                ?.substringBeforeLast('.')
                ?.takeIf { it.isNotBlank() }
                ?: "Unknown track"
            val title = tags.title ?: fallbackTitle

            val baseName = if (tags.artist.isNullOrBlank()) {
                title
            } else {
                "${tags.artist} - $title"
            }
            val destination = uniqueFile(AudioTypes.sanitizeFileName(baseName), extension)

            if (!temp.renameTo(destination)) {
                temp.copyTo(destination, overwrite = true)
                temp.delete()
            }
            staged = null

            val artPath = tags.artwork?.let { bytes -> writeArtwork(hash, bytes) }

            val track = Track(
                filePath = destination.absolutePath,
                contentHash = hash,
                title = title,
                artist = tags.artist,
                album = tags.album,
                albumArtist = tags.albumArtist,
                durationMs = tags.durationMs,
                trackNumber = tags.trackNumber,
                year = tags.year,
                mimeType = declaredMime,
                sizeBytes = destination.length(),
                artPath = artPath,
                sourceApp = sourceApp,
                originalName = displayName
            )
            val id = db.trackDao().insert(track)
            ImportResult.Imported(track.copy(id = id))
        } catch (t: Throwable) {
            Log.w(TAG, "Import failed for $uri", t)
            ImportResult.Failed(displayName, t)
        } finally {
            staged?.delete()
        }
    }

    private fun copyAndHash(input: java.io.InputStream, destination: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        FileOutputStream(destination).use { output ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
            output.fd.sync()
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun queryOpenable(resolver: ContentResolver, uri: Uri): Pair<String?, Long?> {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = uri.path?.let(::File)
            return file?.name to file?.length()
        }
        return runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null to null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex)
                } else {
                    null
                }
                name to size
            } ?: (null to null)
        }.getOrDefault(uri.lastPathSegment to null)
    }

    private data class Tags(
        val title: String?,
        val artist: String?,
        val album: String?,
        val albumArtist: String?,
        val durationMs: Long,
        val trackNumber: Int?,
        val year: Int?,
        val artwork: ByteArray?
    )

    private fun readTags(file: File): Tags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            fun key(k: Int) = retriever.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
            Tags(
                title = key(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = key(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = key(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = key(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                durationMs = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                trackNumber = key(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.trim()?.toIntOrNull(),
                year = key(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull(),
                artwork = runCatching { retriever.embeddedPicture }.getOrNull()
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read tags from ${file.name}", t)
            Tags(null, null, null, null, 0L, null, null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun writeArtwork(hash: String, bytes: ByteArray): String? = runCatching {
        val file = File(artDir, "$hash.jpg")
        if (!file.exists()) file.writeBytes(bytes)
        file.absolutePath
    }.getOrNull()

    private fun uniqueFile(baseName: String, extension: String): File {
        val dir = musicDir
        var candidate = File(dir, "$baseName.$extension")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(dir, "$baseName ($counter).$extension")
            counter++
        }
        return candidate
    }

    /**
     * Deletes half-written imports left behind if the process died mid-copy.
     * Cheap enough to run at startup.
     */
    suspend fun cleanupOrphans() = withContext(Dispatchers.IO) {
        runCatching {
            musicDir.listFiles { file -> file.name.endsWith(PART_SUFFIX) }
                ?.forEach { it.delete() }
        }
        Unit
    }

    private companion object {
        const val TAG = "TrackImporter"
        const val PART_SUFFIX = ".part"
    }
}
