package com.exo.musicplayer.data.repo

import android.content.Context
import com.exo.musicplayer.data.db.HourBucket
import com.exo.musicplayer.data.db.MusicDatabase
import com.exo.musicplayer.data.db.PlayEvent
import com.exo.musicplayer.data.db.TrackListenTime
import com.exo.musicplayer.data.db.WeatherBucket
import com.exo.musicplayer.data.weather.Affinity
import com.exo.musicplayer.data.weather.WeatherAffinity
import com.exo.musicplayer.data.weather.WeatherCondition
import com.exo.musicplayer.data.weather.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class StatsRepository(context: Context) {

    private val dao = MusicDatabase.get(context.applicationContext).playEventDao()

    /**
     * A play only counts once this much audio has actually been heard. Skipping
     * through a queue should not rewrite your taste profile.
     */
    private val minMs = QUALIFYING_MS

    fun observeTotalListenedMs(): Flow<Long> = dao.observeTotalListenedMs()
    fun observePlayCount(): Flow<Int> = dao.observePlayCount(minMs)
    fun observeDistinctTracksPlayed(): Flow<Int> = dao.observeDistinctTracksPlayed(minMs)
    fun observeTopByListenTime(limit: Int = 25): Flow<List<TrackListenTime>> =
        dao.observeTopByListenTime(minMs, limit)

    fun observeTopThisWeek(limit: Int = 10): Flow<List<TrackListenTime>> =
        dao.observeTopSince(System.currentTimeMillis() - WEEK_MS, minMs, limit)

    fun observeByHour(): Flow<List<HourBucket>> = dao.observeByHour(minMs)
    fun observeByWeather(): Flow<List<WeatherBucket>> = dao.observeByWeather(minMs)
    fun observeRecentTrackIds(limit: Int = 25): Flow<List<Long>> =
        dao.observeRecentTrackIds(limit)

    /** Opens an event when a track starts; returns its id for later updates. */
    suspend fun startPlay(trackId: Long, weather: WeatherSnapshot?): Long {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return dao.insert(
            PlayEvent(
                trackId = trackId,
                startedAt = System.currentTimeMillis(),
                listenedMs = 0L,
                hourOfDay = hour,
                weatherGroup = weather?.condition?.name,
                weatherCode = weather?.wmoCode,
                temperatureC = weather?.temperatureC?.takeIf { !it.isNaN() },
                isDay = weather?.isDay
            )
        )
    }

    suspend fun updateListened(eventId: Long, listenedMs: Long) =
        dao.updateListened(eventId, listenedMs)

    suspend fun hasEnoughHistory(): Boolean =
        dao.totalPlaysWithWeather(minMs) >= WeatherAffinity.MIN_HISTORY

    /** Ranks tracks by how disproportionately they are played in [condition]. */
    suspend fun affinitiesFor(condition: WeatherCondition): List<Affinity> {
        val group = condition.name
        return WeatherAffinity.score(
            conditionCounts = dao.playsPerTrackInCondition(group, minMs),
            totalCounts = dao.playsPerTrackWithWeather(minMs),
            totalPlaysInCondition = dao.totalPlaysInCondition(group, minMs),
            totalPlaysWithWeather = dao.totalPlaysWithWeather(minMs)
        )
    }

    suspend fun weatherTaggedPlays(): Int = dao.totalPlaysWithWeather(minMs)

    companion object {
        /** 30s of audio before a play counts, matching common scrobbling practice. */
        const val QUALIFYING_MS = 30_000L
        private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}
