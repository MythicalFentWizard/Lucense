package com.exo.musicplayer.data.library

import com.exo.musicplayer.data.db.Track
import java.util.Locale

/** A set of tracks judged to be the same song, with one chosen to survive. */
data class DuplicateGroup(
    val keep: Track,
    val remove: List<Track>
) {
    val title: String get() = keep.title
    val artist: String get() = keep.artist ?: "Unknown artist"
}

/**
 * Finds the same song stored more than once.
 *
 * Byte-identical files never get this far — the importer already deduplicates on
 * a SHA-256 of the audio. What is left are genuinely different files of the same
 * recording: a 320kbps rip alongside a 128kbps one, the same track shared twice
 * from different chats, or a download that duplicates something already ripped.
 * Those need fuzzy matching on tags rather than on bytes.
 *
 * The matching itself lives in [DuplicateMatcher] so the desktop build applies
 * exactly the same rules. What stays here is which copy to keep, which is the
 * one genuinely storage-specific part.
 *
 * Nothing here deletes anything. It returns a proposal for the user to review,
 * because a wrong guess costs them a file they cannot get back.
 */
object DuplicateFinder {

    fun normalize(text: String?): String = DuplicateMatcher.normalize(text)

    fun find(tracks: List<Track>): List<DuplicateGroup> =
        DuplicateMatcher.group(
            items = tracks,
            artistOf = { it.artist },
            titleOf = { it.title },
            durationOf = { it.durationMs },
            // Biggest file first, as a stand-in for bitrate, then the one with
            // artwork, then the one actually played, then whichever arrived first.
            keeperOrder = compareByDescending<Track> { it.sizeBytes }
                .thenByDescending { !it.artPath.isNullOrBlank() }
                .thenByDescending { it.playCount }
                .thenBy { it.addedAt }
        ).map { group ->
            DuplicateGroup(keep = group.keep, remove = group.remove)
        }.sortedBy { it.title.lowercase(Locale.ROOT) }
}
