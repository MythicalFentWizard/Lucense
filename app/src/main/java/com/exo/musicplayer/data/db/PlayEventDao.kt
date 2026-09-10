package com.exo.musicplayer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayEventDao {

    @Insert
    suspend fun insert(event: PlayEvent): Long

    @Query("UPDATE play_events SET listenedMs = :listenedMs WHERE id = :id")
    suspend fun updateListened(id: Long, listenedMs: Long)

    @Query("SELECT COALESCE(SUM(listenedMs), 0) FROM play_events")
    fun observeTotalListenedMs(): Flow<Long>

    @Query("SELECT COUNT(*) FROM play_events WHERE listenedMs >= :minMs")
    fun observePlayCount(minMs: Long): Flow<Int>

    @Query("SELECT COUNT(DISTINCT trackId) FROM play_events WHERE listenedMs >= :minMs")
    fun observeDistinctTracksPlayed(minMs: Long): Flow<Int>

    @Query(
        "SELECT trackId, COALESCE(SUM(listenedMs), 0) AS totalMs, COUNT(*) AS plays " +
            "FROM play_events WHERE listenedMs >= :minMs " +
            "GROUP BY trackId ORDER BY totalMs DESC LIMIT :limit"
    )
    fun observeTopByListenTime(minMs: Long, limit: Int): Flow<List<TrackListenTime>>

    @Query(
        "SELECT trackId, COALESCE(SUM(listenedMs), 0) AS totalMs, COUNT(*) AS plays " +
            "FROM play_events WHERE startedAt >= :since AND listenedMs >= :minMs " +
            "GROUP BY trackId ORDER BY totalMs DESC LIMIT :limit"
    )
    fun observeTopSince(since: Long, minMs: Long, limit: Int): Flow<List<TrackListenTime>>

    @Query(
        "SELECT hourOfDay, COUNT(*) AS plays FROM play_events " +
            "WHERE listenedMs >= :minMs GROUP BY hourOfDay ORDER BY hourOfDay ASC"
    )
    fun observeByHour(minMs: Long): Flow<List<HourBucket>>

    @Query(
        "SELECT weatherGroup, COUNT(*) AS plays FROM play_events " +
            "WHERE listenedMs >= :minMs AND weatherGroup IS NOT NULL " +
            "GROUP BY weatherGroup ORDER BY plays DESC"
    )
    fun observeByWeather(minMs: Long): Flow<List<WeatherBucket>>

    @Query("SELECT trackId FROM play_events ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentTrackIds(limit: Int): Flow<List<Long>>

    // ---- Weather affinity inputs ----
    //
    // Deliberately returned as raw aggregates rather than a ranked SQL query:
    // the scoring needs a global baseline and smoothing, which is clearer in
    // Kotlin than in SQL. See WeatherAffinity.

    @Query(
        "SELECT trackId, COUNT(*) AS plays FROM play_events " +
            "WHERE weatherGroup = :group AND listenedMs >= :minMs GROUP BY trackId"
    )
    suspend fun playsPerTrackInCondition(group: String, minMs: Long): List<TrackPlayCount>

    @Query(
        "SELECT trackId, COUNT(*) AS plays FROM play_events " +
            "WHERE weatherGroup IS NOT NULL AND listenedMs >= :minMs GROUP BY trackId"
    )
    suspend fun playsPerTrackWithWeather(minMs: Long): List<TrackPlayCount>

    @Query(
        "SELECT COUNT(*) FROM play_events WHERE weatherGroup IS NOT NULL AND listenedMs >= :minMs"
    )
    suspend fun totalPlaysWithWeather(minMs: Long): Int

    @Query(
        "SELECT COUNT(*) FROM play_events WHERE weatherGroup = :group AND listenedMs >= :minMs"
    )
    suspend fun totalPlaysInCondition(group: String, minMs: Long): Int

    @Query("SELECT COUNT(*) FROM play_events")
    suspend fun eventCount(): Int
}
