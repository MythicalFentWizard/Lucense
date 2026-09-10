package com.exo.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query(
        "SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt, " +
            "COUNT(e.trackId) AS trackCount, " +
            "COALESCE(SUM(t.durationMs), 0) AS totalDurationMs " +
            "FROM playlists p " +
            "LEFT JOIN playlist_entries e ON e.playlistId = p.id " +
            "LEFT JOIN tracks t ON t.id = e.trackId " +
            "GROUP BY p.id ORDER BY p.name COLLATE NOCASE ASC"
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<Playlist?>

    @Query(
        "SELECT t.* FROM tracks t " +
            "INNER JOIN playlist_entries e ON e.trackId = t.id " +
            "WHERE e.playlistId = :playlistId ORDER BY e.position ASC"
    )
    fun observeTracks(playlistId: Long): Flow<List<Track>>

    @Query(
        "SELECT t.* FROM tracks t " +
            "INNER JOIN playlist_entries e ON e.trackId = t.id " +
            "WHERE e.playlistId = :playlistId ORDER BY e.position ASC"
    )
    suspend fun tracksOnce(playlistId: Long): List<Track>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: PlaylistEntry)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeEntry(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: Long)

    @Transaction
    suspend fun appendTracks(playlistId: Long, trackIds: List<Long>) {
        var next = maxPosition(playlistId) + 1
        for (trackId in trackIds) {
            insertEntry(PlaylistEntry(playlistId, trackId, next))
            next++
        }
    }

    /** Rewrites positions to match [orderedTrackIds] exactly. */
    @Transaction
    suspend fun reorder(playlistId: Long, orderedTrackIds: List<Long>) {
        clearEntries(playlistId)
        orderedTrackIds.forEachIndexed { index, trackId ->
            insertEntry(PlaylistEntry(playlistId, trackId, index))
        }
    }
}
