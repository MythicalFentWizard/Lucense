package com.exo.musicplayer.playback

import android.content.Context
import com.exo.musicplayer.data.db.MusicDatabase
import com.exo.musicplayer.data.db.TrackFx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Effect settings, shared between the UI and [PlaybackService].
 *
 * Both live in the same process, so the service observes this StateFlow directly
 * rather than routing every slider drag through a Media3 custom session command.
 *
 * Two layers. The everyday settings are saved and come back with the app. A
 * song can also have its own, saved with "Remember for this song": those take
 * over while that song plays and give way to the everyday ones again when it
 * ends, so a song slowed down on purpose doesn't slow down the next one too.
 */
class AudioFxSettings(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences("audiofx", Context.MODE_PRIVATE)
    private val perSong = MusicDatabase.get(context).trackFxDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var everyday = AudioFxState(
        speed = prefs.getFloat(KEY_SPEED, 1f),
        pitchSemitones = prefs.getFloat(KEY_PITCH, 0f),
        reverbEnabled = prefs.getBoolean(KEY_REVERB_ON, false),
        reverbRoom = ReverbRoom.fromName(prefs.getString(KEY_REVERB_ROOM, null)),
        reverbAmount = prefs.getFloat(KEY_REVERB_AMOUNT, 0.4f),
        eqEnabled = prefs.getBoolean(KEY_EQ_ON, false),
        eqGains = prefs.getString(KEY_EQ_GAINS, null)
            ?.split(',')?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 10 } ?: List(10) { 0f }
    )

    private val _state = MutableStateFlow(everyday)
    /** What is being heard right now. */
    val state: StateFlow<AudioFxState> = _state.asStateFlow()

    private val _remembered = MutableStateFlow(false)
    /** True while the song playing has effects of its own. */
    val remembered: StateFlow<Boolean> = _remembered.asStateFlow()

    private var currentTrackId: Long? = null

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

    fun setEqEnabled(enabled: Boolean) = update { it.copy(eqEnabled = enabled) }

    fun setEqBand(band: Int, db: Float) = update {
        it.copy(eqGains = it.eqGains.toMutableList().also { gains ->
            if (band in gains.indices) gains[band] = db.coerceIn(-12f, 12f)
        })
    }

    fun flattenEq() = update { it.copy(eqGains = List(10) { 0f }) }

    /** Presets set speed, pitch and reverb; the equalizer is left as it is. */
    fun applyPreset(preset: FxPreset) = update {
        preset.state.copy(eqEnabled = it.eqEnabled, eqGains = it.eqGains)
    }

    fun reset() = update { AudioFxState() }

    /** Saves what is set now against the song playing. */
    fun rememberForCurrent() {
        val id = currentTrackId ?: return
        val now = _state.value
        _remembered.value = true
        scope.launch { perSong.save(TrackFx(id, now.encode())) }
    }

    /** The song playing goes back to the everyday effects. */
    fun forgetForCurrent() {
        val id = currentTrackId ?: return
        _remembered.value = false
        _state.value = everyday
        scope.launch { perSong.delete(id) }
    }

    /** Called by the player whenever a different song starts. */
    fun onTrackChanged(trackId: Long?) {
        if (trackId == currentTrackId) return
        currentTrackId = trackId
        scope.launch {
            val own = trackId?.let { AudioFxState.decode(perSong.find(it)) }
            // Still the same song by the time the database answered?
            if (trackId != currentTrackId) return@launch
            _remembered.value = own != null
            _state.value = own ?: everyday
        }
    }

    private inline fun update(transform: (AudioFxState) -> AudioFxState) {
        val next = transform(_state.value)
        _state.value = next
        // A song with its own effects is being adjusted for itself: the
        // everyday settings are left alone until it ends. Pressing Remember
        // again keeps the adjustment.
        if (_remembered.value) return
        everyday = next
        prefs.edit()
            .putFloat(KEY_SPEED, next.speed)
            .putFloat(KEY_PITCH, next.pitchSemitones)
            .putBoolean(KEY_REVERB_ON, next.reverbEnabled)
            .putString(KEY_REVERB_ROOM, next.reverbRoom.name)
            .putFloat(KEY_REVERB_AMOUNT, next.reverbAmount)
            .putBoolean(KEY_EQ_ON, next.eqEnabled)
            .putString(KEY_EQ_GAINS, next.eqGains.joinToString(","))
            .apply()
    }

    private companion object {
        const val KEY_SPEED = "speed"
        const val KEY_PITCH = "pitch"
        const val KEY_REVERB_ON = "reverb_on"
        const val KEY_REVERB_ROOM = "reverb_room"
        const val KEY_REVERB_AMOUNT = "reverb_amount"
        const val KEY_EQ_ON = "eq_on"
        const val KEY_EQ_GAINS = "eq_gains"
    }
}
