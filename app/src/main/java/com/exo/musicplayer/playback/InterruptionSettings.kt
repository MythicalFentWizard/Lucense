package com.exo.musicplayer.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What to do when something else briefly wants the speaker. */
enum class InterruptionBehavior(val label: String, val description: String) {
    DUCK(
        "Turn down",
        "Drop the volume while a notification or other audio plays, then come back up."
    ),
    PAUSE(
        "Pause",
        "Fade out and pause completely, then resume when the other audio finishes."
    );

    companion object {
        fun fromName(name: String?): InterruptionBehavior =
            entries.firstOrNull { it.name == name } ?: DUCK
    }
}

data class InterruptionState(
    val behavior: InterruptionBehavior = InterruptionBehavior.DUCK,
    /** Allow starting playback while a call is already in progress, quietly. */
    val playDuringCalls: Boolean = true,
    /** Volume used while ducked, 0..1. */
    val duckVolume: Float = 0.25f
)

class InterruptionSettings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("interruption", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        InterruptionState(
            behavior = InterruptionBehavior.fromName(prefs.getString(KEY_BEHAVIOR, null)),
            playDuringCalls = prefs.getBoolean(KEY_IN_CALL, true),
            duckVolume = prefs.getFloat(KEY_DUCK_VOLUME, 0.25f)
        )
    )
    val state: StateFlow<InterruptionState> = _state.asStateFlow()

    fun setBehavior(behavior: InterruptionBehavior) = update {
        it.copy(behavior = behavior)
    }

    fun setPlayDuringCalls(enabled: Boolean) = update { it.copy(playDuringCalls = enabled) }

    fun setDuckVolume(value: Float) = update { it.copy(duckVolume = value.coerceIn(0.05f, 1f)) }

    private inline fun update(transform: (InterruptionState) -> InterruptionState) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putString(KEY_BEHAVIOR, next.behavior.name)
            .putBoolean(KEY_IN_CALL, next.playDuringCalls)
            .putFloat(KEY_DUCK_VOLUME, next.duckVolume)
            .apply()
    }

    private companion object {
        const val KEY_BEHAVIOR = "behavior"
        const val KEY_IN_CALL = "play_during_calls"
        const val KEY_DUCK_VOLUME = "duck_volume"
    }
}
