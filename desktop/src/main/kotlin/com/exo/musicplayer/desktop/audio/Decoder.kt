package com.exo.musicplayer.desktop.audio

import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.ceil

/**
 * Turns any supported file into 44.1 kHz stereo float samples.
 *
 * Java Sound resolves the codec from the service providers on the classpath
 * (MP3, OGG Vorbis, FLAC; WAV and AIFF are built in). It will decode to PCM at
 * the file's own sample rate but often cannot convert the rate, so that part is
 * done here.
 *
 * Two things in this file are shaped by measurements on a five-minute MP3:
 *
 *  - Rate conversion goes through [StreamingResampler] rather than the
 *    fingerprinting resampler this used to share, which cost 11.6 ms of every
 *    92.9 ms buffer on a 48 kHz file.
 *  - [skip] moves through the decoded stream without converting or resampling
 *    what it passes over. Seeking used to decode, convert and resample
 *    everything up to the target: 0.9 s to reach three minutes into a 44.1 kHz
 *    file, and 22.8 s into a 48 kHz one.
 *
 * Buffers are reused between reads. This runs on the playback thread, where a
 * steady stream of garbage is what turns into audible hitches.
 */
class Decoder(file: File) : AutoCloseable {

    private val source: AudioInputStream = AudioSystem.getAudioInputStream(file)
    private val pcm: AudioInputStream
    private val sourceRate: Float
    private val sourceChannels: Int

    /** Source frames per output frame. */
    private val step: Double
    private val resampling: Boolean
    private val resampler = StreamingResampler(2)

    /** Total frames at the output rate, or -1 when the container doesn't say. */
    val totalFrames: Long

    /** Output frames read or skipped so far. */
    var positionFrames: Long = 0L
        private set

    private var bytes = ByteArray(0)
    private var stereo = FloatArray(0)

    init {
        val base = source.format
        sourceRate = base.sampleRate.takeIf { it > 0 } ?: OUTPUT_RATE
        sourceChannels = base.channels.coerceAtLeast(1)
        step = sourceRate.toDouble() / OUTPUT_RATE
        resampling = sourceRate.toInt() != OUTPUT_RATE.toInt()

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
        totalFrames = if (frames > 0) (frames * (OUTPUT_RATE / sourceRate)).toLong() else -1L
    }

    /**
     * Reads up to [frames] output frames as interleaved stereo floats in -1..1,
     * or null at the end of the stream.
     */
    fun read(frames: Int): FloatArray? {
        val frameBytes = sourceChannels * 2
        val sourceFrames = if (resampling) ceil(frames * step).toInt() + 2 else frames
        val wanted = sourceFrames * frameBytes
        if (bytes.size < wanted) bytes = ByteArray(wanted)

        var filled = 0
        while (filled < wanted) {
            val read = pcm.read(bytes, filled, wanted - filled)
            if (read <= 0) break
            filled += read
        }
        val frameCount = filled / frameBytes
        if (frameCount <= 0) return null

        // 16-bit little-endian to float, folding to stereo.
        if (stereo.size < frameCount * 2) stereo = FloatArray(frameCount * 2)
        val mono = sourceChannels == 1
        for (i in 0 until frameCount) {
            val base = i * frameBytes
            val left = ((bytes[base + 1].toInt() shl 8) or (bytes[base].toInt() and 0xFF)) / 32768f
            stereo[i * 2] = left
            stereo[i * 2 + 1] = if (mono) {
                left
            } else {
                val o = base + 2
                ((bytes[o + 1].toInt() shl 8) or (bytes[o].toInt() and 0xFF)) / 32768f
            }
        }

        val out = if (resampling) {
            resampler.process(stereo, frameCount, step)
        } else {
            stereo.copyOf(frameCount * 2)
        }
        positionFrames += out.size / 2
        return out
    }

    /**
     * Moves forward by [frames] output frames without producing them.
     *
     * Skipped on the decoded stream in whole source frames, so nothing on the
     * way is converted or resampled. Whether the codec underneath decodes what it
     * passes over is up to its service provider.
     */
    fun skip(frames: Long): Long {
        if (frames <= 0) return 0L
        val frameBytes = (sourceChannels * 2).toLong()
        val target = (frames * step).toLong() * frameBytes
        var remaining = target

        // Read and discarded in large chunks rather than through pcm.skip(),
        // which was measured at 50 to 62 seconds to reach three minutes into a
        // five-minute MP3.
        while (remaining > 0) {
            val chunk = (minOf(remaining, 65536L) / frameBytes * frameBytes).toInt()
            if (chunk <= 0) break
            if (bytes.size < chunk) bytes = ByteArray(chunk)
            val read = pcm.read(bytes, 0, chunk)
            if (read <= 0) break
            remaining -= read
        }

        // The interpolation history belongs to the old position.
        resampler.reset()
        val moved = (((target - remaining) / frameBytes) / step).toLong()
        positionFrames += moved
        return moved
    }

    override fun close() {
        runCatching { pcm.close() }
        runCatching { source.close() }
    }

    companion object {
        private val OUTPUT_RATE: Float = AudioDevices.FORMAT.sampleRate

        fun canOpen(file: File): Boolean = runCatching {
            AudioSystem.getAudioInputStream(file).close(); true
        }.getOrDefault(false)
    }
}
