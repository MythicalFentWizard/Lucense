package com.exo.musicplayer.data.recognition

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Minimal DSP needed for fingerprinting. Hand-rolled rather than pulled from a
 * library: it is two well-understood routines, and every added artifact is a
 * real download cost on this project's connection.
 */
object Dsp {

    /**
     * In-place iterative radix-2 Cooley-Tukey FFT. [re]/[im] must be a power of
     * two in length.
     */
    fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        require(n and (n - 1) == 0) { "FFT size must be a power of two" }

        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val angle = -2.0 * PI / len
            val wr = cos(angle).toFloat()
            val wi = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curR = 1f
                var curI = 0f
                for (k in 0 until len / 2) {
                    val uR = re[i + k]
                    val uI = im[i + k]
                    val vR = re[i + k + len / 2] * curR - im[i + k + len / 2] * curI
                    val vI = re[i + k + len / 2] * curI + im[i + k + len / 2] * curR
                    re[i + k] = uR + vR
                    im[i + k] = uI + vI
                    re[i + k + len / 2] = uR - vR
                    im[i + k + len / 2] = uI - vI
                    val nextR = curR * wr - curI * wi
                    curI = curR * wi + curI * wr
                    curR = nextR
                }
                i += len
            }
            len = len shl 1
        }
    }

    /**
     * Windowed-sinc resampler.
     *
     * Linear interpolation would be simpler, but going from 44.1 kHz to 16 kHz
     * without an anti-aliasing filter folds everything above 8 kHz back into the
     * band the fingerprint is computed from, which measurably hurts matching.
     */
    fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate || input.isEmpty()) return input

        val ratio = toRate.toDouble() / fromRate
        val outLength = (input.size * ratio).toInt()
        if (outLength <= 0) return FloatArray(0)

        // Cut off at the lower of the two Nyquist limits.
        val cutoff = min(1.0, ratio)
        val halfWidth = 16
        val out = FloatArray(outLength)

        for (i in 0 until outLength) {
            val center = i / ratio
            val start = kotlin.math.floor(center).toInt() - halfWidth + 1
            var acc = 0.0
            var norm = 0.0
            for (k in 0 until halfWidth * 2) {
                val index = start + k
                if (index < 0 || index >= input.size) continue
                val x = center - index
                val h = sinc(cutoff * x) * cutoff * hannTaper(x, halfWidth)
                acc += input[index] * h
                norm += h
            }
            out[i] = if (norm != 0.0) (acc / norm).toFloat() else 0f
        }
        return out
    }

    private fun sinc(x: Double): Double =
        if (kotlin.math.abs(x) < 1e-9) 1.0 else sin(PI * x) / (PI * x)

    private fun hannTaper(x: Double, halfWidth: Int): Double {
        val a = kotlin.math.abs(x)
        if (a >= halfWidth) return 0.0
        return 0.5 * (1.0 + cos(PI * a / halfWidth))
    }
}
