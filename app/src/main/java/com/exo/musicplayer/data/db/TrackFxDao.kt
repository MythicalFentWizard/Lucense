package com.exo.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrackFxDao {

    @Query("SELECT data FROM track_fx WHERE trackId = :trackId")
    suspend fun find(trackId: Long): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(fx: TrackFx)

    @Query("DELETE FROM track_fx WHERE trackId = :trackId")
    suspend fun delete(trackId: Long)
}
