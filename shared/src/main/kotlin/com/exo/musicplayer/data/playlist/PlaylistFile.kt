package com.exo.musicplayer.data.playlist

import com.exo.musicplayer.data.library.DuplicateMatcher
import kotlin.math.abs

/** One line of a shared playlist. */
data class PlaylistEntry(
    val artist: String?,
    val title: String,
    val durationMs: Long = 0L
) {
    val display: String get() = if (artist.isNullOrBlank()) title else "$artist — $title"
}

/** A parsed playlist file. */
data class ParsedPlaylist(
    val name: String,
    val entries: List<PlaylistEntry>
)

/** What happened when a shared playlist was matched against a local library. */
data class ImportResult<T>(
    val name: String,
    val matched: List<Pair<PlaylistEntry, T>>,
    val missing: List<PlaylistEntry>
) {
    val total: Int get() = matched.size + missing.size
}

/**
 * Playlists as plain text, so they can be swapped like any other file.
 *
 * The point is exchange without a server: send a friend a file, they import it,
 * and both libraries end up with the same playlist. Nothing is uploaded and no
 * account is involved, so the format has to survive being emailed, pasted into a
 * chat, and opened in Notepad — which rules out anything binary and argues for
 * something a person can read and edit by hand.
 *
 * Written as tab-separated artist / title / seconds under a small header. The
 * parser is deliberately more permissive than the writer: it also accepts
 * `Artist - Title`, an em dash, or a bare title on its own line, because the
 * most likely second source of these files is somebody typing one out.
 *
 * Files reference songs, never audio. An import matches against what is already
 * in the library and reports what is missing rather than fetching anything.
 */
object PlaylistFile {

    const val EXTENSION = "txt"
    private const val MAGIC = "#LUCENSE-PLAYLIST"
    private const val VERSION = 1

    fun export(name: String, entries: List<PlaylistEntry>): String = buildString {
        appendLine("$MAGIC v$VERSION")
        appendLine("#NAME\t${name.replace('\t', ' ')}")
        appendLine("#COUNT\t${entries.size}")
        appendLine("#")
        appendLine("# artist <tab> title <tab> seconds")
        appendLine("# Edit freely; lines beginning with # are ignored.")
        appendLine("#")
        for (entry in entries) {
            val artist = entry.artist.orEmpty().replace('\t', ' ')
            val title = entry.title.replace('\t', ' ')
            val seconds = if (entry.durationMs > 0) (entry.durationMs / 1000).toString() else ""
            appendLine(listOf(artist, title, seconds).joinToString("\t"))
        }
    }

    private val DASH_SPLIT = Regex("""\s+[—–-]\s+""")

    fun parse(text: String, fallbackName: String = "Imported playlist"): ParsedPlaylist {
        var name = fallbackName
        val entries = mutableListOf<PlaylistEntry>()

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#")) {
                // Header lines carry the name; everything else starting with #
                // is a comment, including the ones this file writes itself.
                val body = line.removePrefix("#").trim()
                if (body.startsWith("NAME", ignoreCase = true)) {
                    body.removePrefix("NAME").removePrefix("name")
                        .trim().trim(':').trim()
                        .takeIf { it.isNotEmpty() }
                        ?.let { name = it }
                }
                continue
            }

            entries += parseLine(line) ?: continue
        }
        return ParsedPlaylist(name, entries)
    }

    private fun parseLine(line: String): PlaylistEntry? {
        if (line.contains('\t')) {
            val parts = line.split('\t')
            val artist = parts.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() }
            val title = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
                // A single tabbed column is a title, not an artist.
                ?: return artist?.let { PlaylistEntry(null, it) }
            val seconds = parts.getOrNull(2)?.trim()?.toLongOrNull() ?: 0L
            return PlaylistEntry(artist, title, seconds * 1000)
        }

        // "Artist - Title", which is what a hand-written list almost always is.
        val split = DASH_SPLIT.split(line, limit = 2)
        return if (split.size == 2 && split[0].isNotBlank() && split[1].isNotBlank()) {
            PlaylistEntry(split[0].trim(), split[1].trim())
        } else {
            PlaylistEntry(null, line)
        }
    }

    /**
     * Matches a parsed playlist against a local library.
     *
     * Uses the same normalisation as the duplicate finder, so "Song (Official
     * Video)" and "Song" are understood to be the same track — a playlist coming
     * from someone else's library will not have tags formatted like yours.
     * Matching is by title first, narrowed by artist and length when both sides
     * know them, because two people can easily hold different recordings of the
     * same song and the closer one should win.
     */
    fun <T> matchAgainst(
        playlist: ParsedPlaylist,
        library: List<T>,
        artistOf: (T) -> String?,
        titleOf: (T) -> String,
        durationOf: (T) -> Long
    ): ImportResult<T> {
        val byTitle = library.groupBy { DuplicateMatcher.normalize(titleOf(it)) }

        val matched = mutableListOf<Pair<PlaylistEntry, T>>()
        val missing = mutableListOf<PlaylistEntry>()
        val used = mutableSetOf<T>()

        for (entry in playlist.entries) {
            val candidates = byTitle[DuplicateMatcher.normalize(entry.title)].orEmpty()
                .filterNot { it in used }
            if (candidates.isEmpty()) {
                missing += entry
                continue
            }

            val wantedArtist = DuplicateMatcher.normalize(entry.artist)
            val best = candidates.minByOrNull { candidate ->
                val artistMismatch = if (
                    wantedArtist.isNotEmpty() &&
                    DuplicateMatcher.normalize(artistOf(candidate)) != wantedArtist
                ) 1 else 0
                val lengthGap = if (entry.durationMs > 0 && durationOf(candidate) > 0) {
                    abs(entry.durationMs - durationOf(candidate))
                } else {
                    0L
                }
                // Artist agreement outweighs any length difference.
                artistMismatch * 1_000_000L + lengthGap
            }!!

            matched += entry to best
            used += best
        }
        return ImportResult(playlist.name, matched, missing)
    }
}
