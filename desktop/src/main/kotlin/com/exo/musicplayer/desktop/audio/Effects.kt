package com.exo.musicplayer.desktop.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

/**
 * Time-stretching by WSOLA (waveform similarity overlap-add).
 *
 * Needed because "slowed" and "pitch" have to stay independent, exactly as on
 * Android. Plain resampling couples them — slow it down and it inevitably goes
 * deeper. WSOLA changes duration while leaving pitch alone by overlapping
 * windows at a shifted rate, choosing each window's offset by cross-correlation
 * so successive windows stay phase-aligned. Without that correlation search
 * (i.e. plain OLA) the result develops a distinctive metallic warble.
 *
 * Operates on interleaved stereo; the search runs on the summed channels so both
 * stay locked together.
 *
 * Its buffers are kept and reused. An earlier version collected output in an
 * ArrayList of boxed floats and concatenated arrays on every call: tens of
 * thousands of small objects a second on the playback thread whenever a speed
 * effect was on, which is garbage the collector has to stop for.
 */
class TimeStretcher {

    @Volatile
    var factor: Float = 1f      // >1 plays faster

    private val window = 2048   // frames
    private val synthesisHop = window / 4
    private val searchRadius = 256

    /** Input not yet consumed, interleaved stereo, and how much of it is valid. */
    private var pending = FloatArray(0)
    private var pendingLength = 0
    private val tail = FloatArray(window * 2)
    private var primed = false
    private var output = FloatArray(0)

    private val fade = FloatArray(window) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (window - 1))).toFloat()
    }

    fun reset() {
        pendingLength = 0
        tail.fill(0f)
        primed = false
    }

    fun process(input: FloatArray): FloatArray {
        val f = factor
        if (f in 0.999f..1.001f) return input

        if (pending.size < pendingLength + input.size) {
            pending = pending.copyOf(maxOf(pendingLength + input.size, pending.size * 2))
        }
        System.arraycopy(input, 0, pending, pendingLength, input.size)
        pendingLength += input.size

        val analysisHop = (synthesisHop * f).toInt().coerceAtLeast(1)
        val windows = ((pendingLength / 2 - searchRadius - window) / analysisHop + 1).coerceAtLeast(0)
        if (output.size < windows * synthesisHop * 2) output = FloatArray(windows * synthesisHop * 2)

        var written = 0
        var read = 0
        while (true) {
            val need = (read + searchRadius + window) * 2
            if (need > pendingLength) break

            val offset = if (primed) bestOffset(read) else 0
            val start = (read + offset).coerceAtLeast(0)
            if ((start + window) * 2 > pendingLength) break
            if (written + synthesisHop * 2 > output.size) {
                output = output.copyOf(output.size * 2 + synthesisHop * 2)
            }

            // Overlap-add the new window against the previous one's tail.
            for (i in 0 until synthesisHop) {
                val w = fade[i]
                output[written++] = tail[i * 2] * (1f - w) + pending[(start + i) * 2] * w
                output[written++] = tail[i * 2 + 1] * (1f - w) + pending[(start + i) * 2 + 1] * w
            }
            // Keep the remainder as the next overlap source.
            for (i in 0 until window - synthesisHop) {
                val src = (start + synthesisHop + i) * 2
                if (src + 1 >= pendingLength) break
                tail[i * 2] = pending[src]
                tail[i * 2 + 1] = pending[src + 1]
            }
            primed = true
            read += analysisHop
        }

        if (read > 0) {
            val keepFrom = (read * 2).coerceAtMost(pendingLength)
            System.arraycopy(pending, keepFrom, pending, 0, pendingLength - keepFrom)
            pendingLength -= keepFrom
        }
        return output.copyOf(written)
    }

    /** Offset within the search window whose waveform best matches the tail. */
    private fun bestOffset(read: Int): Int {
        var best = 0
        var bestScore = -Float.MAX_VALUE
        val compare = minOf(synthesisHop, 512)

        var offset = -searchRadius
        while (offset <= searchRadius) {
            val start = read + offset
            if (start < 0 || (start + compare) * 2 > pendingLength) {
                offset += 32
                continue
            }
            var score = 0f
            var i = 0
            while (i < compare) {
                // Summed channels: keeps left and right from drifting apart.
                val a = tail[i * 2] + tail[i * 2 + 1]
                val b = pending[(start + i) * 2] + pending[(start + i) * 2 + 1]
                score += a * b
                i += 4          // sparse sampling; full correlation is wasted here
            }
            if (score > bestScore) {
                bestScore = score
                best = offset
            }
            offset += 32
        }
        return best
    }
}

/**
 * Schroeder reverb: four parallel comb filters into two series all-pass stages.
 *
 * The classic arrangement, and the right size of tool here — a convolution
 * reverb would need impulse responses shipped with the app for a difference
 * most listeners would not pick out over a music player's output.
 */
class Reverb {

    @Volatile
    var enabled: Boolean = false

    @Volatile
    var mix: Float = 0.35f      // wet proportion

    @Volatile
    var decay: Float = 0.82f    // comb feedback

    private val combDelays = intArrayOf(1557, 1617, 1491, 1422)
    private val allPassDelays = intArrayOf(225, 556)

    private val combL = combDelays.map { FloatArray(it) }
    private val combR = combDelays.map { FloatArray(it + 23) }   // detune for width
    private val combIndexL = IntArray(combDelays.size)
    private val combIndexR = IntArray(combDelays.size)

    private val apL = allPassDelays.map { FloatArray(it) }
    private val apR = allPassDelays.map { FloatArray(it + 11) }
    private val apIndexL = IntArray(allPassDelays.size)
    private val apIndexR = IntArray(allPassDelays.size)

    fun reset() {
        (combL + combR + apL + apR).forEach { it.fill(0f) }
        combIndexL.fill(0); combIndexR.fill(0); apIndexL.fill(0); apIndexR.fill(0)
    }

    fun process(buffer: FloatArray) {
        if (!enabled || mix <= 0.001f) return
        val wet = mix.coerceIn(0f, 1f)
        val dry = 1f - wet * 0.5f      // gentle dry trim, not a full crossfade

        var i = 0
        while (i + 1 < buffer.size) {
            buffer[i] = dry * buffer[i] + wet * channel(
                buffer[i], combL, combIndexL, apL, apIndexL
            )
            buffer[i + 1] = dry * buffer[i + 1] + wet * channel(
                buffer[i + 1], combR, combIndexR, apR, apIndexR
            )
            i += 2
        }
    }

    private fun channel(
        input: Float,
        combs: List<FloatArray>,
        combIndex: IntArray,
        allPass: List<FloatArray>,
        apIndex: IntArray
    ): Float {
        var sum = 0f
        for (c in combs.indices) {
            val line = combs[c]
            val idx = combIndex[c]
            val delayed = line[idx]
            line[idx] = input + delayed * decay
            combIndex[c] = (idx + 1) % line.size
            sum += delayed
        }
        sum /= combs.size

        for (a in allPass.indices) {
            val line = allPass[a]
            val idx = apIndex[a]
            val delayed = line[idx]
            val out = delayed - sum * ALL_PASS_GAIN
            line[idx] = sum + delayed * ALL_PASS_GAIN
            apIndex[a] = (idx + 1) % line.size
            sum = out
        }
        return sum
    }

    private companion object {
        const val ALL_PASS_GAIN = 0.7f
    }
}

/**
 * Speed, pitch and reverb, composed the same way as the Android build: three
 * independent axes that stack.
 *
 * Pitch is a resample (which moves tempo too), then the stretcher restores the
 * requested tempo. So `resample(p)` followed by `stretch(speed / p)` yields
 * tempo = speed and pitch = p, with either adjustable on its own.
 */
class EffectChain {

    val stretcher = TimeStretcher()
    val reverb = Reverb()

    /**
     * Continuous across buffers. The pitch shift used to resample each buffer on
     * its own with the fingerprinting filter, which was both slow and seamed at
     * every buffer boundary.
     */
    private val pitchResampler = StreamingResampler(2)
    private var pitchActive = false

    @Volatile
    var speed: Float = 1f

    @Volatile
    var pitchSemitones: Float = 0f

    @Volatile
    var volume: Float = 1f

    fun reset() {
        stretcher.reset()
        reverb.reset()
        pitchResampler.reset()
        pitchActive = false
    }

    fun process(input: FloatArray): FloatArray {
        val pitch = 2f.pow(pitchSemitones / 12f)
        var buffer = input

        if (pitch !in 0.999f..1.001f) {
            // History from an earlier stretch of pitched audio is stale by now.
            if (!pitchActive) {
                pitchResampler.reset()
                pitchActive = true
            }
            buffer = pitchResampler.process(buffer, buffer.size / 2, pitch.toDouble())
        } else {
            pitchActive = false
        }
        stretcher.factor = speed / pitch
        buffer = stretcher.process(buffer)

        reverb.process(buffer)

        if (volume !in 0.999f..1.001f) {
            for (i in buffer.indices) buffer[i] *= volume
        }
        return buffer
    }
}
