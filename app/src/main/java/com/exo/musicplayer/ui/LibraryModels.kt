package com.exo.musicplayer.ui

import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.prefs.GroupSort
import com.exo.musicplayer.data.prefs.LibrarySort
import java.util.Locale

/** Songs gathered under one album or one artist, for the Albums and Artists views. */
data class TrackGroup(
    val key: String,
    val name: String,
    /** The album's artist, or for an artist how many albums. */
    val subtitle: String?,
    val year: Int?,
    val tracks: List<Track>,
    val addedAt: Long
) {
    /** The first song with a cover, to stand for the whole group. */
    val cover: Track? get() = tracks.firstOrNull { it.artPath != null } ?: tracks.firstOrNull()
}

/** The library tools, as on Windows, minus Names from folders: a phone keeps its songs in one flat folder. */
enum class LibraryTool(val label: String, val note: String, val group: String) {
    NAMES_TAGS(
        "Names & tags",
        "Looks songs up by name, then writes the song's own name, or the album, year and genre, or both — it asks which.",
        "Naming"
    ),
    IDENTIFY(
        "Identify by sound",
        "Fingerprints the audio itself. Slower, but it works on files with no usable name at all.",
        "Naming"
    ),
    TELEGRAM(
        "Mass fix Telegram songs",
        "Names songs from \"Title   Artist\" file names, the shape Telegram exports come in, and fills in lengths the file never stated.",
        "Naming"
    ),
    COVERS("Covers", "Finds missing cover art, asking six sources at once. Songs that already have art are left alone.", "Artwork and words"),
    LYRICS("Lyrics", "Four services in turn, keeping timed lyrics where they exist.", "Artwork and words"),
    LEVELS("Level volumes", "Measures how loud each song really is, so they all play at the same level.", "Sound")
}

/** A library job in progress or just finished, shown in place in the tools sheet and in Fix. */
data class ToolJob(
    val label: String,
    val done: Int = 0,
    val total: Int = 0,
    val current: String = "",
    val running: Boolean = true,
    val note: String? = null
) {
    val progress: Float get() = if (total > 0) done.toFloat() / total else 0f
}

/** Whether there's a newer build to install, as Settings shows it. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class UpToDate(val version: String) : UpdateState
    data class Available(val version: String, val url: String) : UpdateState
    data object Failed : UpdateState
}

internal object Sorting {

    private fun key(text: String?): String = text.orEmpty().trim().lowercase(Locale.ROOT)

    /** The same nine orders, and directions, as Windows. */
    fun tracks(all: List<Track>, sort: LibrarySort, listened: Map<Long, Long>): List<Track> = when (sort) {
        LibrarySort.ARTIST -> all.sortedWith(
            compareBy<Track>({ it.artist.isNullOrBlank() }, { key(it.artist) }, { key(it.title) })
        )
        LibrarySort.TITLE -> all.sortedBy { key(it.title) }
        LibrarySort.ALBUM -> all.sortedWith(
            compareBy<Track>({ it.album.isNullOrBlank() }, { key(it.album) }, { it.trackNumber ?: 0 }, { key(it.title) })
        )
        LibrarySort.DURATION -> all.sortedByDescending { it.durationMs }
        LibrarySort.PLAYS -> all.sortedWith(compareByDescending<Track> { it.playCount }.thenBy { key(it.title) })
        LibrarySort.LISTEN_TIME -> all.sortedWith(
            compareByDescending<Track> { listened[it.id] ?: 0L }.thenBy { key(it.title) }
        )
        LibrarySort.ADDED -> all.sortedByDescending { it.addedAt }
        LibrarySort.RATING -> all.sortedWith(compareByDescending<Track> { it.rating }.thenBy { key(it.title) })
        LibrarySort.YEAR -> all.sortedWith(
            compareByDescending<Track> { it.year ?: 0 }.thenBy { key(it.artist) }.thenBy { key(it.title) }
        )
    }

    fun albums(all: List<Track>, sort: GroupSort): List<TrackGroup> {
        val groups = all.filter { !it.album.isNullOrBlank() }
            .groupBy { key(it.album) + "\u0000" + key(it.albumArtist ?: it.artist) }
            .map { (k, songs) ->
                val ordered = songs.sortedWith(compareBy<Track>({ it.trackNumber ?: 0 }, { key(it.title) }))
                TrackGroup(
                    key = k,
                    name = ordered.first().album.orEmpty(),
                    subtitle = ordered.first().albumArtist ?: ordered.first().artist,
                    year = ordered.mapNotNull { it.year }.maxOrNull(),
                    tracks = ordered,
                    addedAt = ordered.maxOf { it.addedAt }
                )
            }
        return groups(groups, sort)
    }

    fun artists(all: List<Track>, sort: GroupSort): List<TrackGroup> {
        val groups = all.filter { !it.artist.isNullOrBlank() }
            .groupBy { key(it.artist) }
            .map { (k, songs) ->
                val ordered = songs.sortedWith(compareBy<Track>({ key(it.album) }, { it.trackNumber ?: 0 }, { key(it.title) }))
                val albums = ordered.mapNotNull { it.album?.takeIf(String::isNotBlank) }.distinct().size
                TrackGroup(
                    key = k,
                    name = ordered.first().artist.orEmpty(),
                    subtitle = when (albums) {
                        0 -> null
                        1 -> "1 album"
                        else -> "$albums albums"
                    },
                    year = ordered.mapNotNull { it.year }.maxOrNull(),
                    tracks = ordered,
                    addedAt = ordered.maxOf { it.addedAt }
                )
            }
        return groups(groups, sort)
    }

    private fun groups(groups: List<TrackGroup>, sort: GroupSort): List<TrackGroup> = when (sort) {
        GroupSort.NAME -> groups.sortedBy { key(it.name) }
        GroupSort.ARTIST -> groups.sortedWith(compareBy<TrackGroup>({ key(it.subtitle) }, { key(it.name) }))
        GroupSort.TRACKS -> groups.sortedWith(compareByDescending<TrackGroup> { it.tracks.size }.thenBy { key(it.name) })
        GroupSort.YEAR -> groups.sortedWith(compareByDescending<TrackGroup> { it.year ?: 0 }.thenBy { key(it.name) })
        GroupSort.ADDED -> groups.sortedByDescending { it.addedAt }
    }
}
