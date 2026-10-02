package com.exo.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackPrintDao {

    @Query("SELECT * FROM track_prints")
    suspend fun all(): List<TrackPrint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(print: TrackPrint)

    @Query("DELETE FROM track_prints WHERE trackId = :trackId")
    suspend fun delete(trackId: Long)
}
