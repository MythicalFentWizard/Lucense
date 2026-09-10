package com.exo.musicplayer.playback

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Duplicates the decoded PCM stream onto extra output devices.
 *
 * This replaces an earlier attempt that ran a whole second ExoPlayer per device.
 * That approach decodes the file N times, so the copies drift apart immediately
 * and can only be dragged back with periodic seeks — audible as stutter.
 *
 * Here the file is decoded **once**. This sink taps the PCM on its way to the
 * primary AudioTrack and writes the identical bytes to a secondary AudioTrack
 * per extra device, each pinned with setPreferredDevice. Every device therefore
 * receives sample-identical audio from the same buffer, and there is no drift to
 * correct: the only remaining offset is each device's fixed output latency.
 *
 * Two properties matter for safety, because this code runs on the audio thread:
 *
 *  - Writes are WRITE_NON_BLOCKING. A slow or stalled secondary device must
 *    never block the buffer on its way to the primary output, which would
 *    stutter the audio the user is actually listening to.
 *  - Every failure is swallowed and the offending track dropped. A mirror going
 *    away is not a reason to interrupt playback.
 */
@UnstableApi
class MultiDeviceTap : TeeAudioProcessor.AudioBufferSink {

    private class Mirror(val key: String, val track: AudioTrack)

    @Volatile
    private var wanted: List<AudioDeviceInfo> = emptyList()

    private var mirrors = listOf<Mirror>()
    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = 0

    /** Called from the UI thread; picked up on the next [flush]. */
    @Synchronized
    fun setDevices(devices: List<AudioDeviceInfo>) {
        wanted = devices
        if (sampleRate > 0) rebuild()
    }

    @Synchronized
    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.sampleRate = sampleRateHz
        this.channelCount = channelCount
        this.encoding = encoding
        rebuild()
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val current = mirrors
        if (current.isEmpty()) return

        // Each track needs its own view: write() advances the position.
        for (mirror in current) {
            val slice = buffer.duplicate()
            val remaining = slice.remaining()
            if (remaining <= 0) continue
            try {
                mirror.track.write(slice, remaining, AudioTrack.WRITE_NON_BLOCKING)
            } catch (t: Throwable) {
                Log.w(TAG, "Mirror write failed on ${mirror.key}", t)
            }
        }
    }

    @Synchronized
    private fun rebuild() {
        releaseTracks()
        val devices = wanted
        if (devices.isEmpty() || sampleRate <= 0) return

        mirrors = devices.mapNotNull { device ->
            buildTrack(device)?.let { Mirror("${device.type}|${device.id}", it) }
        }
        Log.i(TAG, "Mirroring to ${mirrors.size} device(s) at ${sampleRate}Hz")
    }

    private fun buildTrack(device: AudioDeviceInfo): AudioTrack? = runCatching {
        val channelMask = when (channelCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            6 -> AudioFormat.CHANNEL_OUT_5POINT1
            else -> AudioFormat.CHANNEL_OUT_STEREO
        }
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        if (minBytes <= 0) return@runCatching null

        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            // Generous buffer: a mirror underrunning is far more audible than
            // the extra latency, and non-blocking writes drop data otherwise.
            .setBufferSizeInBytes(minBytes * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .apply {
                setPreferredDevice(device)
                play()
            }
    }.onFailure { Log.w(TAG, "Could not open mirror track", it) }.getOrNull()

    @Synchronized
    private fun releaseTracks() {
        mirrors.forEach {
            runCatching { it.track.pause() }
            runCatching { it.track.flush() }
            runCatching { it.track.release() }
        }
        mirrors = emptyList()
    }

    @Synchronized
    fun release() {
        wanted = emptyList()
        releaseTracks()
    }

    private companion object {
        const val TAG = "MultiDeviceTap"
    }
}
