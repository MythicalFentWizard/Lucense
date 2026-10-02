package com.exo.musicplayer.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** When the music should stop on its own. */
sealed interface Sleep {
    data object Off : Sleep

    /** At a time on the wall clock. */
    data class At(val endsAtMs: Long, val minutes: Int) : Sleep

    /** When the song playing now finishes. */
    data object EndOfSong : Sleep
}

/**
 * The sleep timer, as on Windows: 15 to 90 minutes or the end of this song.
 *
 * Only the choice lives here. The player service watches it and does the
 * stopping, fading the last half minute down rather than cutting off mid-bar,
 * so the timer still runs with the app closed.
 */
class SleepTimer {

    private val _state = MutableStateFlow<Sleep>(Sleep.Off)
    val state: StateFlow<Sleep> = _state.asStateFlow()

    fun setMinutes(minutes: Int) {
        _state.value = Sleep.At(System.currentTimeMillis() + minutes * 60_000L, minutes)
    }

    fun endOfSong() {
        _state.value = Sleep.EndOfSong
    }

    fun cancel() {
        _state.value = Sleep.Off
    }

    companion object {
        val CHOICES = listOf(15, 30, 45, 60, 90)
        const val FADE_MS = 30_000L
    }
}
