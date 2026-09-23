package com.exo.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Lists kept as a rule rather than as a set of songs.
 *
 * Nothing here joins to tracks, because a smart list is not a set of rows: it
 * is a question, answered against whatever is in the library at the moment it
 * is asked. The answering happens in the repository, using the same parser the
 * search box and the desktop app use.
 */
@Dao
interface SmartPlaylistDao {

    @Query("SELECT * FROM smart_playlists ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SmartPlaylist>>

    @Query("SELECT * FROM smart_playlists ORDER BY name COLLATE NOCASE")
    suspend fun allOnce(): List<SmartPlaylist>

    @Insert
    suspend fun insert(list: SmartPlaylist): Long

    @Query("UPDATE smart_playlists SET name = :name, rule = :rule WHERE id = :id")
    suspend fun update(id: Long, name: String, rule: String)

    @Query("DELETE FROM smart_playlists WHERE id = :id")
    suspend fun delete(id: Long)
}
