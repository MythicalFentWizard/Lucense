package com.exo.musicplayer.desktop.audio

import com.exo.musicplayer.data.recognition.Dsp
import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem

/**
 * Turns any supported file into 44.1 kHz stereo float samples.
 *
 * Java Sound resolves the codec from the service providers on the classpath
 * (MP3, OGG Vorbis, FLAC; WAV and AIFF are built in). It will happily decode to
 * PCM at the file's *own* sample rate but often cannot resample, so the rate
 * conversion is done here with the same windowed-sinc resampler the Android app
 * uses for fingerprinting — shared code, one implementation.
 */
class Decoder(file: File) : AutoCloseable {

    private val source: AudioInputStream = AudioSystem.getAudioInputStream(file)
    private val pcm: AudioInputStream
    private val sourceRate: Float
    private val sourceChannels: Int

    /** Total frames at the output rate, or -1 when the container doesn't say. */
    val totalFrames: Long

    init {
        val base = source.format
        sourceRate = base.sampleRate.takeIf { it > 0 } ?: 44100f
        sourceChannels = base.channels.coerceAtLeast(1)

        val decoded = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sourceRate,
            16,
            sourceChannels,
            sourceChannels * 2,
            sourceRate,
            false
        )
        pcm = AudioSystem.getAudioInputStream(decoded, source)

        val frames = source.frameLength
        totalFrames = if (frames > 0) {
            (frames * (AudioDevices.FORMAT.sampleRate / sourceRate)).toLong()
        } else {
            -1L
        }
    }

    /**
     * Reads up to [frames] output frames as interleaved stereo floats in -1..1,
     * or null at end of stream.
     */
    fun read(frames: Int): FloatArray? {
        val inFrames = (frames * (sourceRate / AudioDevices.FORMAT.sampleRate)).toInt() + 2
        val bytes = ByteArray(inFrames * sourceChannels * 2)
        var filled = 0
        while (filled < bytes.size) {
            val read = pcm.read(bytes, filled, bytes.size - filled)
            if (read <= 0) break
            filled += read
        }
        if (filled <= 0) return null

        val sampleCount = filled / 2
        val mono = sourceChannels == 1

        // 16-bit little-endian to float, folding to stereo.
        val frameCount = sampleCount / sourceChannels
        var left = FloatArray(frameCount)
        var right = FloatArray(frameCount)
        for (i in 0 until frameCount) {
            val base = i * sourceChannels * 2
            val l = ((bytes[base + 1].toInt() shl 8) or (bytes[base].toInt() and 0xFF)) / 32768f
            left[i] = l
            right[i] = if (mono) {
                l
            } else {
                val o = base + 2
                ((bytes[o + 1].toInt() shl 8) or (bytes[o].toInt() and 0xFF)) / 32768f
            }
        }

        if (sourceRate.toInt() != AudioDevices.FORMAT.sampleRate.toInt()) {
            left = Dsp.resample(left, sourceRate.toInt(), AudioDevices.FORMAT.sampleRate.toInt())
            right = Dsp.resample(right, sourceRate.toInt(), AudioDevices.FORMAT.sampleRate.toInt())
        }

        val out = FloatArray(minOf(left.size, right.size) * 2)
        for (i in 0 until out.size / 2) {
            out[i * 2] = left[i]
            out[i * 2 + 1] = right[i]
        }
        return out
    }

    override fun close() {
        runCatching { pcm.close() }
        runCatching { source.close() }
    }

    companion object {
        fun canOpen(file: File): Boolean = runCatching {
            AudioSystem.getAudioInputStream(file).close(); true
        }.getOrDefault(false)
    }
}
