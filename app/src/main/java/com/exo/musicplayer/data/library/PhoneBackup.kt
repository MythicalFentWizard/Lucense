package com.exo.musicplayer.data.library

import androidx.room.withTransaction
import com.exo.musicplayer.data.db.MusicDatabase
import com.exo.musicplayer.data.db.PlayEvent
import com.exo.musicplayer.data.db.Playlist
import com.exo.musicplayer.data.db.SmartPlaylist
import com.exo.musicplayer.data.db.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

/**
 * Backups for the phone: favourites, ratings, plays and listening time,
 * playlists and smart lists, as a plain text file you can keep anywhere.
 *
 * The same idea and line format as the Windows backup, with one difference
 * that matters. Windows names a song by where its file is, and a phone keeps
 * its songs in its own storage under different names - so here a song is named
 * by its content hash, with its artist and title alongside for a phone where
 * the same song came from a different copy. The header is different too, so
 * neither app mistakes the other's file for its own.
 *
 * Restoring adds and never deletes: favourites come back, ratings fill in,
 * playlists you don't already have are recreated, play counts are topped up.
 */
class PhoneBackup(private val db: MusicDatabase) {

    data class Outcome(val favourites: Int, val ratings: Int, val playlists: Int, val plays: Int, val missing: Int) {
        val summary: String
            get() = buildString {
                append(listOf(count(favourites, "favourite"), count(ratings, "rating"), count(playlists, "playlist"), count(plays, "play")).joinToString(", "))
                if (missing > 0) {
                    append(if (missing == 1) " · 1 song isn't on this phone" else " · $missing songs aren't on this phone")
                }
            }

        private fun count(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
    }

    suspend fun write(): String = withContext(Dispatchers.IO) {
        val tracks = db.trackDao().allOnce()
        val keyOf = HashMap<Long, Int>()
        val listened = db.playEventDao().observeListenTotals().first().associateBy { it.trackId }
        buildString {
            appendLine("$HEADER\t$VERSION")
            appendLine("taken\t${System.currentTimeMillis()}")
            fun key(track: Track): Int = keyOf.getOrPut(track.id) {
                val next = keyOf.size + 1
                appendLine("song\t$next\t${track.contentHash}\t${clean(track.artist)}\t${clean(track.title)}")
                next
            }
            tracks.filter { it.isFavorite }.forEach { appendLine("fav\t${key(it)}") }
            tracks.filter { it.rating > 0 }.forEach { appendLine("rate\t${key(it)}\t${it.rating}") }
            tracks.filter { it.playCount > 0 }.forEach {
                appendLine("play\t${key(it)}\t${it.playCount}\t${listened[it.id]?.totalMs ?: 0L}")
            }
            db.playlistDao().observeSummaries().first().forEach { playlist ->
                val songs = db.playlistDao().tracksOnce(playlist.id)
                val keys = songs.map { key(it) }
                appendLine("playlist\t${clean(playlist.name)}")
                keys.forEach { appendLine("track\t$it") }
            }
            db.smartPlaylistDao().allOnce().forEach {
                appendLine("smart\t${clean(it.name)}\t${clean(it.rule)}")
            }
        }
    }

    suspend fun restore(text: String): Outcome? = withContext(Dispatchers.IO) {
        val lines = text.lines()
        if (lines.firstOrNull()?.startsWith(HEADER) != true) return@withContext null

        val tracks = db.trackDao().allOnce()
        val byHash = tracks.associateBy { it.contentHash }
        val byName = tracks.groupBy { nameKey(it.artist, it.title) }
        val songs = HashMap<Int, Track?>()
        var missing = 0

        var favourites = 0
        var ratings = 0
        var plays = 0
        var playlists = 0
        val existingLists = db.playlistDao().observeSummaries().first().map { it.name }.toSet()
        val existingSmart = db.smartPlaylistDao().allOnce().map { it.name }.toSet()
        var openList: String? = null
        val openTracks = mutableListOf<Long>()

        suspend fun closeList() {
            val name = openList ?: return
            if (name !in existingLists && openTracks.isNotEmpty()) {
                val id = db.playlistDao().insertPlaylist(Playlist(name = name))
                db.playlistDao().appendTracks(id, openTracks.distinct())
                playlists++
            }
            openList = null
            openTracks.clear()
        }

        // One transaction for the lot: a long listening history is thousands of
        // writes, and one at a time each was its own commit, with the library
        // on screen re-read after every one.
        db.withTransaction {
            for (line in lines.drop(1)) {
                val parts = line.split('\t')
                fun song(index: Int): Track? = parts.getOrNull(index)?.toIntOrNull()?.let { songs[it] }
                when (parts.firstOrNull()) {
                    "song" -> {
                        val key = parts.getOrNull(1)?.toIntOrNull() ?: continue
                        val found = byHash[parts.getOrNull(2)]
                            ?: byName[nameKey(parts.getOrNull(3), parts.getOrNull(4).orEmpty())]?.firstOrNull()
                        if (found == null) missing++
                        songs[key] = found
                    }
                    "fav" -> song(1)?.let {
                        if (!it.isFavorite) {
                            db.trackDao().setFavorite(it.id, true)
                            favourites++
                        }
                    }
                    "rate" -> {
                        val track = song(1) ?: continue
                        val stars = parts.getOrNull(2)?.toIntOrNull() ?: continue
                        if (track.rating == 0 && stars in 1..5) {
                            db.trackDao().setRating(track.id, stars)
                            ratings++
                        }
                    }
                    "play" -> {
                        val track = song(1) ?: continue
                        val count = parts.getOrNull(2)?.toIntOrNull() ?: continue
                        val listenedMs = parts.getOrNull(3)?.toLongOrNull() ?: 0L
                        val owed = count - track.playCount
                        if (owed <= 0) continue
                        // Stamped now: the original times aren't in the file, so
                        // the by-hour chart can't be rebuilt, as on Windows.
                        val now = System.currentTimeMillis()
                        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                        val each = if (count > 0) listenedMs / count else 0L
                        repeat(owed) {
                            db.playEventDao().insert(
                                PlayEvent(trackId = track.id, startedAt = now, listenedMs = each, hourOfDay = hour)
                            )
                            db.trackDao().markPlayed(track.id, now)
                            plays++
                        }
                    }
                    "playlist" -> {
                        closeList()
                        openList = parts.getOrNull(1)
                    }
                    "track" -> song(1)?.let { openTracks += it.id }
                    "smart" -> {
                        closeList()
                        val name = parts.getOrNull(1) ?: continue
                        val rule = parts.getOrNull(2) ?: continue
                        if (name !in existingSmart) db.smartPlaylistDao().insert(SmartPlaylist(name = name, rule = rule))
                    }
                }
            }
            closeList()
        }
        Outcome(favourites, ratings, playlists, plays, missing)
    }

    private fun clean(text: String?): String = text.orEmpty().replace('\t', ' ').replace('\n', ' ')

    private fun nameKey(artist: String?, title: String): String =
        "${artist.orEmpty().trim().lowercase(Locale.ROOT)}\u0000${title.trim().lowercase(Locale.ROOT)}"

    private companion object {
        const val HEADER = "lucense-phone-backup"
        const val VERSION = 1
    }
}
