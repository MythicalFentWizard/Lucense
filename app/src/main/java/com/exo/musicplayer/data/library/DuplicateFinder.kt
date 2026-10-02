package com.exo.musicplayer.data.library

import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.library.AudioPrint
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
 * Finds the same song stored more than once, by how it sounds.
 *
 * Byte-identical files never get this far - the importer already deduplicates
 * on a SHA-256 of the audio. What is left are different files of the same
 * recording: a 320 kbps rip alongside a 128 kbps one, the same song shared
 * twice from different chats under different names, a download of something
 * already ripped. Names say little about those - "Track 03" and a properly
 * tagged copy are the same song, two songs can share a title - so the matching
 * is done on sound prints, by the same shared code the PC uses.
 *
 * What stays here is which copy to keep, the storage-specific part.
 *
 * Nothing here deletes anything. It returns a proposal for the user to review,
 * because a wrong guess costs them a file they cannot get back.
 */
object DuplicateFinder {

    // Biggest file first, as a stand-in for bitrate, then the one with
    // artwork, then the one actually played, then whichever arrived first.
    private val keeperOrder = compareByDescending<Track> { it.sizeBytes }
        .thenByDescending { !it.artPath.isNullOrBlank() }
        .thenByDescending { it.playCount }
        .thenBy { it.addedAt }

    fun find(tracks: List<Track>, prints: Map<Long, IntArray>): List<DuplicateGroup> {
        val byId = tracks.associateBy { it.id }
        return AudioPrint.groups(
            prints.mapNotNull { (id, print) -> byId[id]?.let { AudioPrint.Entry(id, print, it.durationMs) } }
        ).map { ids ->
            val members = ids.mapNotNull { byId[it] }.sortedWith(keeperOrder)
            DuplicateGroup(keep = members.first(), remove = members.drop(1))
        }.sortedBy { it.title.lowercase(Locale.ROOT) }
    }
}
