package com.exo.musicplayer.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Audio focus, handled by hand.
 *
 * ExoPlayer's built-in focus handling is switched off because the required
 * behaviour is asymmetric in a way it can't express:
 *
 *  - A call *starting* must silence the music outright, no ducking.
 *  - Starting the music *during* a call that is already up should be allowed,
 *    quietly, rather than refused.
 *
 * Those look the same to a stock focus handler — both are "a call is happening"
 * — so the two cases are told apart by whether we already held focus when the
 * interruption arrived. Everything else (a notification, another app's audio)
 * follows the user's Turn down / Pause preference.
 */
@UnstableApi
class AudioFocusController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsProvider: () -> InterruptionState
) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var player: ExoPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var holdsFocus = false

    /** Set when we paused ourselves, so we know a later GAIN should resume. */
    private var pausedByFocusLoss = false
    private var fadeJob: Job? = null

    fun attach(player: ExoPlayer) {
        this.player = player
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (playWhenReady) {
                    if (!requestFocus()) {
                        // Refused outright: don't sit there silently "playing".
                        player.playWhenReady = false
                        return
                    }
                    // Starting up mid-call is allowed but stays out of the way.
                    applyVolume(target = if (isCallActive()) duckVolume() else 1f)
                } else if (!pausedByFocusLoss) {
                    abandonFocus()
                }
            }
        })
    }

    /** True when the telephony stack has the audio mode, no permission needed. */
    private fun isCallActive(): Boolean {
        val mode = audioManager.mode
        return mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE
    }

    private fun duckVolume(): Float = settingsProvider().duckVolume

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val player = this.player ?: return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Something took over for good.
                holdsFocus = false
                pausedByFocusLoss = false
                fadeOutAndPause(player)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // This is the call-starting case. Always silence, never duck,
                // regardless of the Turn down / Pause preference.
                pausedByFocusLoss = true
                fadeOutAndPause(player)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (settingsProvider().behavior == InterruptionBehavior.PAUSE) {
                    pausedByFocusLoss = true
                    fadeOutAndPause(player)
                } else {
                    applyVolume(duckVolume())
                }
            }

            AudioManager.AUDIOFOCUS_GAIN -> {
                holdsFocus = true
                applyVolume(if (isCallActive()) duckVolume() else 1f)
                if (pausedByFocusLoss) {
                    pausedByFocusLoss = false
                    player.play()
                }
            }
        }
    }

    private fun requestFocus(): Boolean {
        if (holdsFocus) return true
        val settings = settingsProvider()

        // Asking for TRANSIENT_MAY_DUCK during a call is what makes the system
        // let us in alongside the call instead of refusing.
        val gain = if (isCallActive() && settings.playDuringCalls) {
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        } else {
            AudioManager.AUDIOFOCUS_GAIN
        }

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val request = AudioFocusRequest.Builder(gain)
                .setAudioAttributes(attributes)
                .setWillPauseWhenDucked(
                    settings.behavior == InterruptionBehavior.PAUSE
                )
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener, AudioManager.STREAM_MUSIC, gain
            )
        }

        holdsFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return holdsFocus
    }

    fun abandonFocus() {
        if (!holdsFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
        holdsFocus = false
    }

    private fun fadeOutAndPause(player: ExoPlayer) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            fade(player, from = player.volume, to = 0f, durationMs = FADE_OUT_MS)
            player.pause()
            // Restore the level so a later resume isn't silent.
            player.volume = 1f
        }
    }

    private fun applyVolume(target: Float) {
        val player = this.player ?: return
        fadeJob?.cancel()
        fadeJob = scope.launch {
            fade(player, from = player.volume, to = target, durationMs = FADE_MS)
        }
    }

    private suspend fun fade(player: ExoPlayer, from: Float, to: Float, durationMs: Long) {
        val steps = (durationMs / STEP_MS).toInt().coerceAtLeast(1)
        for (step in 1..steps) {
            if (!scope.isActive) return
            player.volume = from + (to - from) * (step.toFloat() / steps)
            delay(STEP_MS)
        }
        player.volume = to
    }

    fun release() {
        fadeJob?.cancel()
        abandonFocus()
        player = null
    }

    private companion object {
        const val FADE_MS = 350L
        const val FADE_OUT_MS = 220L
        const val STEP_MS = 20L
    }
}
