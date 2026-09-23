package com.exo.musicplayer.data.ingest

import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File

/**
 * What a file itself says about the song in it.
 *
 * This app never writes tags back into a file - editing a song changes the row
 * in the database and leaves the bytes alone. That makes the file a permanent
 * record of what the song originally claimed to be, which is what lets a bad
 * edit or a wrong identification be undone at any point, without having had to
 * squirrel a copy away beforehand.
 */
object FileTags {

    private const val TAG = "FileTags"

    data class Read(
        val title: String?,
        val artist: String?,
        val album: String?,
        val albumArtist: String?,
        val durationMs: Long,
        val trackNumber: Int?,
        val year: Int?,
        val genre: String?,
        val artwork: ByteArray?
    ) {
        /** Room rows are compared by value; a ByteArray is not, so it is left out. */
        override fun equals(other: Any?): Boolean =
            this === other || (other is Read && title == other.title && artist == other.artist &&
                album == other.album && albumArtist == other.albumArtist &&
                durationMs == other.durationMs && trackNumber == other.trackNumber &&
                year == other.year && genre == other.genre)

        override fun hashCode(): Int =
            listOf(title, artist, album, albumArtist, durationMs, trackNumber, year, genre)
                .fold(0) { hash, part -> hash * 31 + (part?.hashCode() ?: 0) }
    }

    private val EMPTY = Read(null, null, null, null, 0L, null, null, null, null)

    fun of(file: File): Read {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            fun key(which: Int) =
                retriever.extractMetadata(which)?.trim()?.takeIf { it.isNotEmpty() }
            Read(
                title = key(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = key(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                album = key(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                albumArtist = key(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                durationMs = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                trackNumber = key(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    ?.substringBefore('/')?.trim()?.toIntOrNull(),
                year = key(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull(),
                genre = key(MediaMetadataRetriever.METADATA_KEY_GENRE),
                artwork = runCatching { retriever.embeddedPicture }.getOrNull()
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read tags from ${file.name}", t)
            EMPTY
        } finally {
            runCatching { retriever.release() }
        }
    }
}
