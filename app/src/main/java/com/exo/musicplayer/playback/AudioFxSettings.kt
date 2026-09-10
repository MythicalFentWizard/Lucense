package com.exo.musicplayer.playback

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Effect settings, shared between the UI and [PlaybackService].
 *
 * Both live in the same process, so the service observes this StateFlow directly
 * rather than routing every slider drag through a Media3 custom session command.
 */
class AudioFxSettings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("audiofx", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        AudioFxState(
            speed = prefs.getFloat(KEY_SPEED, 1f),
            pitchSemitones = prefs.getFloat(KEY_PITCH, 0f),
            reverbEnabled = prefs.getBoolean(KEY_REVERB_ON, false),
            reverbRoom = ReverbRoom.fromName(prefs.getString(KEY_REVERB_ROOM, null)),
            reverbAmount = prefs.getFloat(KEY_REVERB_AMOUNT, 0.4f)
        )
    )
    val state: StateFlow<AudioFxState> = _state.asStateFlow()

    fun setSpeed(value: Float) = update {
        it.copy(speed = value.coerceIn(AudioFxState.MIN_SPEED, AudioFxState.MAX_SPEED))
    }

    fun setPitch(semitones: Float) = update {
        it.copy(
            pitchSemitones = semitones
                .coerceIn(AudioFxState.MIN_SEMITONES, AudioFxState.MAX_SEMITONES)
        )
    }

    fun setReverbEnabled(enabled: Boolean) = update { it.copy(reverbEnabled = enabled) }
    fun setReverbRoom(room: ReverbRoom) = update { it.copy(reverbRoom = room) }
    fun setReverbAmount(amount: Float) = update {
        it.copy(reverbAmount = amount.coerceIn(0f, 1f))
    }

    fun applyPreset(preset: FxPreset) = update { preset.state }

    fun reset() = update { AudioFxState() }

    private inline fun update(transform: (AudioFxState) -> AudioFxState) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit()
            .putFloat(KEY_SPEED, next.speed)
            .putFloat(KEY_PITCH, next.pitchSemitones)
            .putBoolean(KEY_REVERB_ON, next.reverbEnabled)
            .putString(KEY_REVERB_ROOM, next.reverbRoom.name)
            .putFloat(KEY_REVERB_AMOUNT, next.reverbAmount)
            .apply()
    }

    private companion object {
        const val KEY_SPEED = "speed"
        const val KEY_PITCH = "pitch"
        const val KEY_REVERB_ON = "reverb_on"
        const val KEY_REVERB_ROOM = "reverb_room"
        const val KEY_REVERB_AMOUNT = "reverb_amount"
    }
}
