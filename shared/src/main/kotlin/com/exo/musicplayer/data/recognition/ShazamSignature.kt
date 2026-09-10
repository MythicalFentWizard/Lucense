package com.exo.musicplayer.data.recognition

import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max

/**
 * Shazam's client-side audio fingerprint.
 *
 * Ported from SongRec's Rust implementation and validated before this file was
 * written: the same algorithm in Python produced a signature that the live
 * endpoint matched to "Frank Sinatra — My Way" from a local mp3, which is what
 * pinned down the Hann window convention, the peak-spreading order and the
 * exact byte layout below.
 *
 * The client only ever transmits frequency peaks — tuples of
 * (fft pass, magnitude, frequency bin) — never the audio itself.
 */
object ShazamSignature {

    const val SAMPLE_RATE = 16000
    private const val SAMPLE_RATE_ID = 3      // 3 == 16 kHz
    private const val FFT_SIZE = 2048
    private const val BINS = 1025
    private const val RING = 256
    const val WINDOW_SECONDS = 12

    /**
     * SongRec's table begins 0.0000023508, which is numpy.hanning(2050)[1:2049]:
     * a symmetric Hann of length 2050 with its two zero endpoints trimmed.
     */
    private val HANN = FloatArray(FFT_SIZE) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * (i + 1) / 2049.0)).toFloat()
    }

    private val NEAR_OFFSETS = intArrayOf(-10, -7, -4, -3, 1, 2, 5, 8)
    private val FAR_OFFSETS = intArrayOf(
        -53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249
    )

    private class Peak(val fftPass: Int, val magnitude: Int, val bin: Int)

    /** @param samples mono PCM at [SAMPLE_RATE], nominally [WINDOW_SECONDS] long. */
    fun generate(samples: FloatArray): ByteArray {
        val ring = FloatArray(FFT_SIZE)
        var ringIndex = 0
        val fftOutputs = Array(RING) { FloatArray(BINS) }
        var fftIndex = 0
        val spread = Array(RING) { FloatArray(BINS) }
        var spreadIndex = 0
        var numSpread = 0
        val bands = Array(4) { mutableListOf<Peak>() }

        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        val reordered = FloatArray(FFT_SIZE)

        val chunks = samples.size / 128
        for (chunk in 0 until chunks) {
            // --- do_fft ---
            for (k in 0 until 128) {
                // Samples are handled as 16-bit integers, as in the reference.
                val s = samples[chunk * 128 + k] * 32768f
                ring[ringIndex + k] = s.coerceIn(-32768f, 32767f).toInt().toFloat()
            }
            ringIndex = (ringIndex + 128) and 2047

            for (i in 0 until FFT_SIZE) {
                reordered[i] = ring[(i + ringIndex) and 2047] * HANN[i]
            }
            System.arraycopy(reordered, 0, re, 0, FFT_SIZE)
            java.util.Arrays.fill(im, 0f)
            Dsp.fft(re, im)

            val out = fftOutputs[fftIndex]
            for (i in 0 until BINS) {
                val mag = (re[i] * re[i] + im[i] * im[i]) / (1 shl 17).toFloat()
                out[i] = max(mag, 1e-10f)
            }
            fftIndex = (fftIndex + 1) and 255

            // --- do_peak_spreading ---
            val src = fftOutputs[(fftIndex - 1) and 255]
            val dst = spread[spreadIndex]
            System.arraycopy(src, 0, dst, 0, BINS)
            // Reads look ahead at values this loop has not yet written, so this
            // is a plain 3-tap running maximum over the original spectrum.
            for (p in 0..1022) {
                dst[p] = max(dst[p], max(src[p + 1], src[p + 2]))
            }
            val copy = dst.copyOf()
            for (back in intArrayOf(1, 3, 6)) {
                val prev = spread[(spreadIndex - back) and 255]
                for (p in 0 until BINS) prev[p] = max(prev[p], copy[p])
            }
            spreadIndex = (spreadIndex + 1) and 255
            numSpread++

            // --- do_peak_recognition ---
            if (numSpread < 46) continue
            val f46 = fftOutputs[(fftIndex - 46) and 255]
            val s49 = spread[(spreadIndex - 49) and 255]

            for (b in 10..1014) {
                val v = f46[b]
                if (v < 1f / 64f || v < s49[b - 1]) continue

                var maxNear = 0f
                for (o in NEAR_OFFSETS) maxNear = max(maxNear, s49[b + o])
                if (v <= maxNear) continue

                var maxFar = maxNear
                for (o in FAR_OFFSETS) {
                    maxFar = max(maxFar, spread[(spreadIndex + o) and 255][b - 1])
                }
                if (v <= maxFar) continue

                val mag = magnitude(f46[b])
                val before = magnitude(f46[b - 1])
                val after = magnitude(f46[b + 1])
                val variation1 = mag * 2f - before - after
                if (variation1 <= 0f) continue
                val variation2 = (after - before) * 32f / variation1

                val correctedBin = (b * 64 + variation2.toInt()) and 0xFFFF
                val frequencyHz = correctedBin * (SAMPLE_RATE / 2f / 1024f / 64f)
                val band = when (frequencyHz.toInt()) {
                    in 250..519 -> 0
                    in 520..1449 -> 1
                    in 1450..3499 -> 2
                    in 3500..5500 -> 3
                    else -> continue
                }
                bands[band].add(Peak(numSpread - 46, mag.toInt() and 0xFFFF, correctedBin))
            }
        }

        return encode(bands, samples.size)
    }

    /**
     * Single-precision throughout, matching the reference client. Computing the
     * log in Double and narrowing afterwards shifts a handful of magnitudes by
     * one unit, which is harmless but needlessly divergent.
     */
    private fun magnitude(value: Float): Float =
        max(ln(value), 1f / 64f) * 1477.3f + 6144.0f

    private fun encode(bands: Array<MutableList<Peak>>, sampleCount: Int): ByteArray {
        val out = ByteArrayOutputStream()
        fun u32(value: Int) {
            out.write(value and 0xFF)
            out.write((value ushr 8) and 0xFF)
            out.write((value ushr 16) and 0xFF)
            out.write((value ushr 24) and 0xFF)
        }

        u32(0xcafe2580.toInt())               // magic1
        u32(0)                                // crc32, patched below
        u32(0)                                // size, patched below
        u32(0x94119c00.toInt())               // magic2
        u32(0); u32(0); u32(0)                // void1
        u32(SAMPLE_RATE_ID shl 27)
        u32(0); u32(0)                        // void2
        u32(sampleCount + (SAMPLE_RATE * 0.24).toInt())
        u32((15 shl 19) + 0x40000)
        u32(0x40000000)
        u32(0)                                // size repeated, patched below

        for (band in 0 until 4) {
            val peaks = bands[band]
            if (peaks.isEmpty()) continue
            val payload = ByteArrayOutputStream()
            var pass = 0
            for (peak in peaks) {
                if (peak.fftPass - pass >= 255) {
                    payload.write(0xFF)
                    payload.write(peak.fftPass and 0xFF)
                    payload.write((peak.fftPass ushr 8) and 0xFF)
                    payload.write((peak.fftPass ushr 16) and 0xFF)
                    payload.write((peak.fftPass ushr 24) and 0xFF)
                    pass = peak.fftPass
                }
                payload.write((peak.fftPass - pass) and 0xFF)
                payload.write(peak.magnitude and 0xFF)
                payload.write((peak.magnitude ushr 8) and 0xFF)
                payload.write(peak.bin and 0xFF)
                payload.write((peak.bin ushr 8) and 0xFF)
                pass = peak.fftPass
            }
            val bytes = payload.toByteArray()
            u32(0x60030040 + band)
            u32(bytes.size)
            out.write(bytes)
            repeat((4 - bytes.size % 4) % 4) { out.write(0) }
        }

        val raw = out.toByteArray()
        val total = raw.size
        writeLe(raw, 8, total - 48)
        writeLe(raw, 48 + 4, total - 48)
        val crc = CRC32().apply { update(raw, 8, raw.size - 8) }.value
        writeLe(raw, 4, crc.toInt())
        return raw
    }

    private fun writeLe(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }
}
