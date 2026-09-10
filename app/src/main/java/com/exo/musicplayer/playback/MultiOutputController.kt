package com.exo.musicplayer.playback

import android.media.AudioDeviceInfo
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Decides where audio goes.
 *
 * The primary device is pinned on the player itself. Any extra devices are fed
 * by [MultiDeviceTap], which duplicates the already-decoded PCM rather than
 * running a second player — see that class for why that distinction matters.
 *
 * What still cannot be fixed from inside an app: each output has its own fixed
 * hardware latency. Bluetooth buffers roughly 150-250ms where the built-in
 * speaker is nearer 50ms, so speaker + Bluetooth stays audibly offset even
 * though both receive identical samples at the same instant. Two wired or two
 * USB outputs line up closely. Android exposes no way to delay one output to
 * match another, which is why true multi-device playback is a system feature
 * (LE Audio broadcast, Samsung Dual Audio) rather than an app one.
 */
@UnstableApi
class MultiOutputController(private val tap: MultiDeviceTap) {

    fun apply(
        primary: ExoPlayer,
        devices: List<AudioDeviceInfo>,
        mirrorEnabled: Boolean
    ) {
        // Null hands routing back to Android's own policy.
        runCatching { primary.setPreferredAudioDevice(devices.firstOrNull()) }
            .onFailure { Log.w(TAG, "Could not pin primary output", it) }

        tap.setDevices(if (mirrorEnabled) devices.drop(1) else emptyList())
    }

    fun release() = tap.release()

    private companion object {
        const val TAG = "MultiOutputController"
    }
}
