package com.exo.musicplayer.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

/**
 * The ten-band equalizer and the levelling gain, inside the player.
 *
 * The same filters as Windows - RBJ peaking bands an octave apart from 31 Hz
 * to 16 kHz, +-12 dB, Q 1.41 - so a setting sounds the same on both. Done here
 * rather than through Android's Equalizer effect because that one has however
 * many bands the phone's maker chose, usually five, at frequencies that differ
 * from phone to phone.
 *
 * Levelling is here too because it has to be able to raise a quiet song, and
 * the player's own volume only goes down. Anything pushed past the knee eases
 * into a soft ceiling instead of clipping, as on Windows.
 *
 * Settings arrive from the main thread and are picked up at the start of the
 * next buffer; the filters keep their memory, so moving a band doesn't click.
 */
@UnstableApi
class ToneProcessor : BaseAudioProcessor() {

    private class Settings(val enabled: Boolean, val gains: FloatArray, val level: Float)

    private val incoming = AtomicReference<Settings?>(Settings(false, FloatArray(BANDS.size), 1f))
    private var enabled = false
    private var gains = FloatArray(BANDS.size)
    private var level = 1f

    private var sampleRate = 44_100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT

    private val b0 = DoubleArray(BANDS.size)
    private val b1 = DoubleArray(BANDS.size)
    private val b2 = DoubleArray(BANDS.size)
    private val a1 = DoubleArray(BANDS.size)
    private val a2 = DoubleArray(BANDS.size)
    private var z1 = DoubleArray(BANDS.size * 2)
    private var z2 = DoubleArray(BANDS.size * 2)

    /** Called from any thread; heard from the next buffer on. */
    fun set(eqEnabled: Boolean, eqGains: List<Float>, levelGain: Float) {
        incoming.set(
            Settings(
                eqEnabled,
                FloatArray(BANDS.size) { eqGains.getOrElse(it) { 0f }.coerceIn(-12f, 12f) },
                levelGain.coerceIn(0.05f, 4f)
            )
        )
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        sampleRate = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 44_100
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        encoding = inputAudioFormat.encoding
        z1 = DoubleArray(BANDS.size * channels)
        z2 = DoubleArray(BANDS.size * channels)
        design()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        incoming.getAndSet(null)?.let {
            enabled = it.enabled
            gains = it.gains
            level = it.level
            design()
        }
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val output = replaceOutputBuffer(bytes)
        val untouched = !enabled && abs(level - 1f) < 0.001f
        if (untouched) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        if (encoding == C.ENCODING_PCM_FLOAT) {
            val count = bytes / 4
            val samples = FloatArray(count) { inputBuffer.getFloat() }
            process(samples)
            samples.forEach { output.putFloat(it) }
        } else {
            val count = bytes / 2
            val samples = FloatArray(count) { inputBuffer.getShort() / 32_768f }
            process(samples)
            samples.forEach {
                output.putShort((it * 32_767f).coerceIn(-32_768f, 32_767f).toInt().toShort())
            }
        }
        output.flip()
    }

    private fun process(buffer: FloatArray) {
        var raised = level > 1.001f
        if (abs(level - 1f) >= 0.001f) {
            for (i in buffer.indices) buffer[i] *= level
        }
        if (enabled) {
            for (band in BANDS.indices) {
                if (abs(gains[band]) < 0.05f) continue
                if (gains[band] > 0f) raised = true
                val c0 = b0[band]
                val c1 = b1[band]
                val c2 = b2[band]
                val d1 = a1[band]
                val d2 = a2[band]
                var i = 0
                while (i + channels - 1 < buffer.size) {
                    for (channel in 0 until channels) {
                        val k = band * channels + channel
                        val x = buffer[i + channel].toDouble()
                        val y = c0 * x + z1[k]
                        z1[k] = c1 * x - d1 * y + z2[k]
                        z2[k] = c2 * x - d2 * y
                        buffer[i + channel] = y.toFloat()
                    }
                    i += channels
                }
            }
        }
        if (raised) {
            for (i in buffer.indices) {
                val x = buffer[i]
                val magnitude = abs(x)
                if (magnitude > KNEE) {
                    buffer[i] = sign(x) * (KNEE + (1f - KNEE) * tanh((magnitude - KNEE) / (1f - KNEE)))
                }
            }
        }
    }

    /** RBJ cookbook peaking filters, as on Windows. */
    private fun design() {
        for (band in BANDS.indices) {
            val amplitude = 10.0.pow(gains[band] / 40.0)
            // A band at or past Nyquist would be unstable; it is left flat.
            val frequency = BANDS[band].coerceAtMost(sampleRate * 0.45)
            val w0 = 2.0 * PI * frequency / sampleRate
            val alpha = sin(w0) / (2.0 * Q)
            val cosine = cos(w0)
            val a0 = 1.0 + alpha / amplitude
            b0[band] = (1.0 + alpha * amplitude) / a0
            b1[band] = -2.0 * cosine / a0
            b2[band] = (1.0 - alpha * amplitude) / a0
            a1[band] = -2.0 * cosine / a0
            a2[band] = (1.0 - alpha / amplitude) / a0
        }
    }

    override fun onReset() {
        z1.fill(0.0)
        z2.fill(0.0)
    }

    companion object {
        val BANDS = doubleArrayOf(31.0, 62.0, 125.0, 250.0, 500.0, 1_000.0, 2_000.0, 4_000.0, 8_000.0, 16_000.0)
        val LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
        private const val Q = 1.41
        private const val KNEE = 0.9f
    }
}
