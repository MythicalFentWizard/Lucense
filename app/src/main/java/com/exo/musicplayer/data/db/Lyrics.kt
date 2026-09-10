package com.exo.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Lyrics for one track. Separate from [Track] so a long body of text isn't
 * loaded every time the library list is drawn.
 */
@Entity(
    tableName = "lyrics",
    foreignKeys = [
        ForeignKey(
            entity = Track::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Lyrics(
    @PrimaryKey val trackId: Long,
    /** Plain text, always present when there are lyrics at all. */
    val plainText: String?,
    /** LRC format with `[mm:ss.xx]` stamps, when the source had them. */
    val syncedText: String? = null,
    /** [SOURCE_FETCHED] or [SOURCE_MANUAL]. */
    val source: String = SOURCE_FETCHED,
    val updatedAt: Long = System.currentTimeMillis()
) {
    val isManual: Boolean get() = source == SOURCE_MANUAL
    val hasSynced: Boolean get() = !syncedText.isNullOrBlank()

    companion object {
        const val SOURCE_FETCHED = "lrclib"
        const val SOURCE_MANUAL = "manual"
    }
}

@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    fun observe(trackId: Long): Flow<Lyrics?>

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    suspend fun find(trackId: Long): Lyrics?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lyrics: Lyrics)

    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun delete(trackId: Long)

    @Query("SELECT COUNT(*) FROM lyrics")
    fun observeCount(): Flow<Int>
}
