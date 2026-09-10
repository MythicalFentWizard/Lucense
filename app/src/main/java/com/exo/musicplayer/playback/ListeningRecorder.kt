package com.exo.musicplayer.playback

import com.exo.musicplayer.data.repo.LibraryRepository
import com.exo.musicplayer.data.repo.StatsRepository
import com.exo.musicplayer.data.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Turns playback ticks into listening history.
 *
 * Counts real elapsed audio rather than assuming a started track was heard: a
 * queue skipped through in ten seconds should not register ten plays, and the
 * weather profile in particular is only as good as the plays feeding it.
 *
 * The weather snapshot is taken once per track start and reused, so a session
 * makes at most one network call every 30 minutes.
 */
class ListeningRecorder(
    private val scope: CoroutineScope,
    private val stats: StatsRepository,
    private val library: LibraryRepository,
    private val weather: WeatherRepository
) {

    private var currentTrackId: Long? = null
    private var currentEventId: Long? = null
    private var accumulatedMs = 0L
    private var flushedMs = 0L
    private var countedAsPlay = false

    /** Called when the player moves to a new track. */
    fun onTrackStarted(trackId: Long) {
        if (trackId == currentTrackId) return
        finish()

        currentTrackId = trackId
        accumulatedMs = 0L
        flushedMs = 0L
        countedAsPlay = false
        currentEventId = null

        scope.launch {
            val snapshot = runCatching { weather.currentWeather() }.getOrNull()
            // The track may have changed again while the snapshot was in flight.
            if (currentTrackId != trackId) return@launch
            currentEventId = runCatching { stats.startPlay(trackId, snapshot) }.getOrNull()
        }
    }

    /** Called from the playback ticker with the time actually elapsed. */
    fun onElapsed(deltaMs: Long) {
        if (currentTrackId == null || deltaMs <= 0L || deltaMs > MAX_SANE_DELTA_MS) return
        accumulatedMs += deltaMs

        val trackId = currentTrackId
        if (!countedAsPlay && accumulatedMs >= StatsRepository.QUALIFYING_MS && trackId != null) {
            countedAsPlay = true
            scope.launch { library.markPlayed(trackId) }
        }

        if (accumulatedMs - flushedMs >= FLUSH_INTERVAL_MS) flush()
    }

    /** Persists the running total; called periodically and on track change. */
    fun flush() {
        val eventId = currentEventId ?: return
        val total = accumulatedMs
        if (total == flushedMs) return
        flushedMs = total
        scope.launch { runCatching { stats.updateListened(eventId, total) } }
    }

    fun finish() {
        flush()
        currentTrackId = null
        currentEventId = null
        accumulatedMs = 0L
        flushedMs = 0L
        countedAsPlay = false
    }

    private companion object {
        const val FLUSH_INTERVAL_MS = 10_000L
        /** Guards against a clock jump or a long pause being counted as listening. */
        const val MAX_SANE_DELTA_MS = 5_000L
    }
}
