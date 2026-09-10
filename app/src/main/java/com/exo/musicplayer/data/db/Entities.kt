package com.exo.musicplayer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A track that lives in our own storage. [filePath] always points at a file this
 * app owns and copied itself, never at the transient content:// URI the sharing
 * app handed us. That is what makes an imported song survive.
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["addedAt"])
    ]
)
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val filePath: String,
    /** SHA-256 of the audio bytes; the dedupe key across re-shares. */
    val contentHash: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val durationMs: Long = 0L,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    /** Absolute path of the extracted cover art, if the file embedded one. */
    val artPath: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,
    val isFavorite: Boolean = false,
    /** Where it came from, e.g. "Telegram" — shown in the library. */
    val sourceApp: String? = null,
    /** The original filename as the sharing app named it. */
    val originalName: String? = null,

    // Bulk-job bookkeeping. Set whether the attempt succeeded or not: a track
    // Shazam cannot recognise should not be re-fingerprinted on every run.
    // The "redo" checkbox is what deliberately ignores these.
    val artCheckedAt: Long? = null,
    val identifiedAt: Long? = null,
    val lyricsCheckedAt: Long? = null
)

@Entity(tableName = "playlists", indices = [Index(value = ["name"], unique = true)])
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index(value = ["trackId"]), Index(value = ["playlistId", "position"])],
    foreignKeys = [
        ForeignKey(
            entity = Playlist::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Track::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlaylistEntry(
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)

/** A playlist plus the counts the list screen needs, in one query. */
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
    val totalDurationMs: Long
)
