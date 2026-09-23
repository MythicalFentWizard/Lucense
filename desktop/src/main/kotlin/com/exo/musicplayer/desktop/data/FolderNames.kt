package com.exo.musicplayer.desktop.data

import java.io.File

/**
 * Names worked out from where a file sits, for libraries that were never tagged.
 *
 * A folder tree is a set of tags somebody already wrote down, just not into the
 * files: `Artist/Album/01 Title.mp3` states three things perfectly clearly.
 * This reads them back out.
 *
 * It is deliberately conservative about what it treats as a track number. A
 * plain number at the front of a title is very often part of the title, so one
 * only counts when it is zero-padded ("01 Title") or followed by a separator
 * ("1 - Title", "12. Title"). Without that rule "99 Problems" becomes track 99
 * of an album called Problems, which is worse than leaving it alone.
 */
object FolderNames {

    data class Derived(
        val title: String?,
        val artist: String?,
        val album: String?,
        val trackNumber: Int?
    )

    /** "01 Title" — zero-padded, so it is a number somebody wrote as an index. */
    private val PADDED = Regex("^\\s*(0\\d{1,2})[\\s._-]+")

    /** "1 - Title", "12. Title", "3) Title" — a number with a separator after it. */
    private val SEPARATED = Regex("^\\s*(\\d{1,3})\\s*[-._)]\\s*")

    /** A spaced hyphen. A hyphen inside a word is part of the word. */
    private const val SPLIT = " - "

    /**
     * Folder names that are never an artist or an album.
     *
     * Most libraries are not laid out as Artist/Album at all - they are a pile
     * of files in Downloads, or in a folder called Music. Reading those names
     * out as tags is worse than leaving the field blank, because a blank is
     * obviously missing and "Album: Songs" looks like an answer.
     */
    private val GENERIC = setOf(
        "music", "musics", "song", "songs", "audio", "audios", "sound", "sounds",
        "download", "downloads", "downloaded", "media", "mp3", "mp3s", "flac",
        "library", "tracks", "track", "album", "albums", "artist", "artists",
        "new folder", "unsorted", "misc", "other", "others", "various", "va",
        "untitled", "temp", "tmp", "stuff", "files", "my music", "itunes",
        "telegram", "telegram desktop", "desktop", "documents", "videos", "saved"
    )

    private fun usable(name: String?): String? =
        name?.trim()?.takeIf { it.isNotEmpty() && it.lowercase() !in GENERIC }

    fun of(file: File, root: File? = null): Derived {
        var stem = file.nameWithoutExtension.trim()

        var track: Int? = null
        val numbered = PADDED.find(stem) ?: SEPARATED.find(stem)
        if (numbered != null) {
            track = numbered.groupValues[1].toIntOrNull()
            stem = stem.removeRange(numbered.range).trim()
        }

        // "Artist - Title" in the file's own name.
        var artist: String? = null
        var title: String? = stem.ifBlank { null }
        val dash = stem.indexOf(SPLIT)
        if (dash > 0) {
            artist = stem.take(dash).trim().ifBlank { null }
            title = stem.drop(dash + SPLIT.length).trim().ifBlank { null }
        }

        // The folders above it, nearest first, stopping at the library root.
        val above = folders(file, root)
        var album: String? = null
        above.getOrNull(0)?.let { parent ->
            val split = parent.indexOf(SPLIT)
            when {
                // One folder holding both, as "Artist - Album".
                split > 0 -> {
                    if (artist == null) artist = usable(parent.take(split))
                    album = usable(parent.drop(split + SPLIT.length))
                }
                // Two levels is the ordinary Artist/Album/song shape.
                above.size > 1 -> album = usable(parent)
                // Only one folder between the song and the library, so it is a
                // toss-up. A track number means somebody laid out an album in
                // there; without one it is far more often a folder per artist.
                track != null -> album = usable(parent)
                else -> if (artist == null) artist = usable(parent)
            }
        }
        if (artist == null) artist = usable(above.getOrNull(1))

        return Derived(title = title, artist = artist, album = album, trackNumber = track)
    }

    /**
     * The two folder names above [file], nearest first.
     *
     * Two is the whole useful depth: the one holding the file is the album and
     * the one above that is the artist. Anything further up is the library's own
     * shape rather than anything about the song, which is why [root] stops it.
     */
    private fun folders(file: File, root: File?): List<String> {
        val stop = root?.absoluteFile?.normalize()?.path?.lowercase()
        val names = mutableListOf<String>()
        var at = file.absoluteFile.normalize().parentFile
        while (at != null && names.size < 2) {
            if (stop != null && at.path.lowercase() == stop) break
            val name = at.name.trim()
            // A drive root has no name of its own to take.
            if (name.isEmpty()) break
            names += name
            at = at.parentFile
        }
        return names
    }
}
