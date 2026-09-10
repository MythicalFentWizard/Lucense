package com.exo.musicplayer.playback

import android.media.audiofx.EnvironmentalReverb
import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.AuxEffectInfo

/**
 * Applies [AudioFxState] to a live ExoPlayer.
 *
 * Speed and pitch go through Media3's Sonic processor, which time-stretches and
 * pitch-shifts independently — that is what lets "slowed" and "pitch" be
 * separate controls instead of the single tempo knob you get from naive
 * resampling.
 *
 * Reverb is an Android AudioEffect, so it is wired as an *auxiliary* effect with
 * a send level rather than an insert on the session. That costs a little setup
 * but gives a real wet/dry amount, and leaves the dry signal untouched when the
 * amount is at zero.
 */
@UnstableApi
class AudioFxController {

    private var reverb: EnvironmentalReverb? = null
    private var reverbFailed = false

    fun apply(player: ExoPlayer, state: AudioFxState) {
        player.playbackParameters = PlaybackParameters(state.speed, state.pitchRatio)
        applyReverb(player, state)
    }

    private fun applyReverb(player: ExoPlayer, state: AudioFxState) {
        if (!state.reverbEnabled) {
            detach(player)
            return
        }
        if (reverbFailed) return

        val effect = reverb ?: createReverb() ?: run {
            // Some devices ship no environmental reverb at all. Fail once, keep
            // playing dry, and never retry on every slider tick.
            reverbFailed = true
            Log.w(TAG, "Environmental reverb unavailable on this device")
            return
        }
        reverb = effect

        runCatching {
            val room = state.reverbRoom
            effect.decayTime = room.decayMs
            effect.roomLevel = room.roomLevelMb.toShort()
            effect.decayHFRatio = room.decayHfRatio.toShort()
            effect.reverbLevel = room.reverbLevelMb.toShort()
            effect.diffusion = room.diffusion.toShort()
            effect.density = room.density.toShort()
            effect.enabled = true
            player.setAuxEffectInfo(AuxEffectInfo(effect.id, state.reverbAmount))
        }.onFailure {
            Log.w(TAG, "Could not configure reverb", it)
            reverbFailed = true
            detach(player)
        }
    }

    /** Session id 0 creates the effect as an auxiliary bus rather than an insert. */
    private fun createReverb(): EnvironmentalReverb? = runCatching {
        EnvironmentalReverb(PRIORITY, 0).apply { enabled = true }
    }.getOrNull()

    private fun detach(player: ExoPlayer) {
        runCatching {
            player.setAuxEffectInfo(AuxEffectInfo(AuxEffectInfo.NO_AUX_EFFECT_ID, 0f))
        }
        reverb?.let { runCatching { it.release() } }
        reverb = null
    }

    fun release() {
        reverb?.let { runCatching { it.release() } }
        reverb = null
    }

    private companion object {
        const val TAG = "AudioFxController"
        const val PRIORITY = 1
    }
}
