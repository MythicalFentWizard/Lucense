package com.exo.musicplayer.playback

import kotlin.math.pow
import kotlin.math.roundToInt

/** Room character for the reverb send. */
enum class ReverbRoom(
    val label: String,
    val decayMs: Int,
    val roomLevelMb: Int,
    val decayHfRatio: Int,
    val reverbLevelMb: Int,
    val diffusion: Int,
    val density: Int
) {
    ROOM("Room", decayMs = 1100, roomLevelMb = -1200, decayHfRatio = 600, reverbLevelMb = -400, diffusion = 900, density = 900),
    HALL("Hall", decayMs = 2400, roomLevelMb = -1000, decayHfRatio = 700, reverbLevelMb = -200, diffusion = 1000, density = 1000),
    CATHEDRAL("Cathedral", decayMs = 4500, roomLevelMb = -900, decayHfRatio = 800, reverbLevelMb = 0, diffusion = 1000, density = 1000),
    PLATE("Plate", decayMs = 1800, roomLevelMb = -1100, decayHfRatio = 1000, reverbLevelMb = -300, diffusion = 700, density = 600);

    companion object {
        fun fromName(name: String?): ReverbRoom =
            entries.firstOrNull { it.name == name } ?: HALL
    }
}

/**
 * The three effects, held together.
 *
 * Deliberately not an enum of modes: "slowed", "sped up" and "reverb" are
 * independent axes, and the whole point of the request is that they stack. The
 * presets below just set all three at once.
 */
data class AudioFxState(
    /** Tempo multiplier. 1.0 is untouched. */
    val speed: Float = 1f,
    /** Pitch shift in semitones, independent of tempo. */
    val pitchSemitones: Float = 0f,
    val reverbEnabled: Boolean = false,
    val reverbRoom: ReverbRoom = ReverbRoom.HALL,
    /** Wet send level, 0..1. */
    val reverbAmount: Float = 0.4f
) {
    /** Media3 wants a frequency ratio, not semitones. */
    val pitchRatio: Float get() = 2f.pow(pitchSemitones / 12f)

    val isDefault: Boolean
        get() = speed == 1f && pitchSemitones == 0f && !reverbEnabled

    val speedLabel: String get() = "${(speed * 100).roundToInt() / 100f}×"

    val pitchLabel: String
        get() {
            val rounded = (pitchSemitones * 10).roundToInt() / 10f
            return when {
                rounded > 0 -> "+$rounded st"
                rounded < 0 -> "$rounded st"
                else -> "0 st"
            }
        }

    companion object {
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 2.0f
        const val MIN_SEMITONES = -12f
        const val MAX_SEMITONES = 12f
    }
}

/** One-tap combinations. Every slider stays adjustable afterwards. */
enum class FxPreset(val label: String, val state: AudioFxState) {
    NORMAL("Normal", AudioFxState()),

    /** The familiar "slowed + reverb" edit: a touch under tempo, pitch follows. */
    SLOWED(
        "Slowed",
        AudioFxState(speed = 0.85f, pitchSemitones = -1.5f)
    ),

    SLOWED_REVERB(
        "Slowed + reverb",
        AudioFxState(
            speed = 0.82f,
            pitchSemitones = -2f,
            reverbEnabled = true,
            reverbRoom = ReverbRoom.HALL,
            reverbAmount = 0.55f
        )
    ),

    SPED_UP(
        "Sped up",
        AudioFxState(speed = 1.25f, pitchSemitones = 1.5f)
    ),

    /** Nightcore: fast and clearly pitched up. */
    NIGHTCORE(
        "Nightcore",
        AudioFxState(speed = 1.3f, pitchSemitones = 4f)
    ),

    /** Tempo change with the original key preserved. */
    DEEP(
        "Deep",
        AudioFxState(
            speed = 0.92f,
            pitchSemitones = -4f,
            reverbEnabled = true,
            reverbRoom = ReverbRoom.CATHEDRAL,
            reverbAmount = 0.45f
        )
    )
}
