package com.exo.musicplayer.data.library

import com.exo.musicplayer.data.recognition.Dsp
import java.nio.ByteBuffer
import java.util.Base64
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * What a song sounds like, reduced to something two copies of the same
 * recording share and two different songs don't - for finding duplicates by
 * sound rather than by name.
 *
 * Names are a poor way to tell: the same song arrives as "Track 03.mp3",
 * "Artist - Song (Official Video).m4a" and an id from a Telegram bot, while two
 * different songs can share a title. Matching on the audio sees through both.
 *
 * The print is the Philips scheme (Haitsma & Kalker): thirty seconds from the
 * middle of the song at 5.5 kHz, cut into overlapping frames; each frame
 * becomes 32 bits saying, for 33 bands from 300 Hz to 2 kHz, whether the
 * difference in energy between neighbouring bands grew or shrank since the
 * frame before. That pattern survives re-encoding, a different bitrate or
 * format, and a change of volume, because all of those move every band the
 * same way. Two prints of the same recording disagree in a few percent of
 * their bits; prints of different songs disagree in about half.
 *
 * Shared, so the phone and the PC find the same duplicates by the same rule.
 */
object AudioPrint {

    /** Seconds of audio a print is taken from, centred on the middle of the song. */
    const val SECONDS = 30

    private const val RATE = 5_512
    private const val FRAME = 2_048
    /** About 46 ms, so 30 seconds is around 640 frames. */
    private const val HOP = 256
    private const val BANDS = 33
    private const val LOW_HZ = 300.0
    private const val HIGH_HZ = 2_000.0

    /** Where a window of [SECONDS] centred on the middle starts, in milliseconds. */
    fun windowStartMs(durationMs: Long): Long =
        if (durationMs <= SECONDS * 1_000L) 0L else durationMs / 2 - SECONDS * 500L

    /** The print of mono [samples] at [sampleRate]; empty when there is too little audio. */
    fun of(samples: FloatArray, sampleRate: Int): IntArray {
        val audio = if (sampleRate == RATE) samples else Dsp.resample(samples, sampleRate, RATE)
        if (audio.size < FRAME * 2) return IntArray(0)

        val window = FloatArray(FRAME) { (0.5 - 0.5 * cos(2.0 * Math.PI * it / (FRAME - 1))).toFloat() }
        // Band edges spaced evenly on a log scale, as FFT bin indices.
        val edges = IntArray(BANDS + 1) { band ->
            val hz = exp(ln(LOW_HZ) + (ln(HIGH_HZ) - ln(LOW_HZ)) * band / BANDS)
            (hz * FRAME / RATE).toInt().coerceIn(1, FRAME / 2 - 1)
        }

        val frames = (audio.size - FRAME) / HOP + 1
        val re = FloatArray(FRAME)
        val im = FloatArray(FRAME)
        var previous = DoubleArray(BANDS)
        val energy = DoubleArray(BANDS)
        val print = IntArray(frames - 1)

        for (frame in 0 until frames) {
            val start = frame * HOP
            for (i in 0 until FRAME) {
                re[i] = audio[start + i] * window[i]
                im[i] = 0f
            }
            Dsp.fft(re, im)
            for (band in 0 until BANDS) {
                var sum = 0.0
                for (bin in edges[band] until max(edges[band] + 1, edges[band + 1])) {
                    sum += re[bin].toDouble() * re[bin] + im[bin].toDouble() * im[bin]
                }
                energy[band] = sum
            }
            if (frame > 0) {
                var bits = 0
                for (bit in 0 until 32) {
                    val now = energy[bit] - energy[bit + 1]
                    val before = previous[bit] - previous[bit + 1]
                    if (now - before > 0) bits = bits or (1 shl bit)
                }
                print[frame - 1] = bits
            }
            previous = energy.copyOf()
        }
        return print
    }

    fun encode(print: IntArray): String {
        val buffer = ByteBuffer.allocate(print.size * 4)
        print.forEach { buffer.putInt(it) }
        return Base64.getEncoder().encodeToString(buffer.array())
    }

    fun decode(text: String?): IntArray? = runCatching {
        val bytes = Base64.getDecoder().decode(text ?: return null)
        val buffer = ByteBuffer.wrap(bytes)
        IntArray(bytes.size / 4) { buffer.getInt() }
    }.getOrNull()

    /** The fraction of bits that differ where the two line up best near [offset], and that offset. */
    fun bitErrorRate(a: IntArray, b: IntArray, offset: Int): Float {
        // a[i] lines up with b[i - offset].
        val from = max(0, offset)
        val to = min(a.size, b.size + offset)
        val overlap = to - from
        if (overlap < MIN_OVERLAP) return 1f
        var differing = 0L
        for (i in from until to) differing += Integer.bitCount(a[i] xor b[i - offset])
        return differing.toFloat() / (overlap * 32L)
    }

    /** One copy, as the matcher sees it. */
    class Entry<K>(val key: K, val print: IntArray, val durationMs: Long)

    /**
     * Which songs are the same recording, as groups of two or more.
     *
     * Comparing every song with every other at every possible alignment would
     * take far too long on a big library, so candidates come from an index:
     * frames that agree in all 32 bits, which copies of a song have plenty of,
     * each voting for how far apart the two prints sit. A pair with enough
     * votes for one alignment is then checked properly at it.
     *
     * Two copies also have to be about the same length - an extended mix is
     * not a duplicate of the radio edit, even where their middles agree.
     */
    fun <K> groups(entries: List<Entry<K>>): List<List<K>> {
        val usable = entries.filter { it.print.size >= MIN_OVERLAP }
        if (usable.size < 2) return emptyList()

        // value -> (song << 32 | frame), skipping values so common they say nothing.
        val index = HashMap<Int, MutableList<Long>>()
        usable.forEachIndexed { song, entry ->
            entry.print.forEachIndexed { frame, value ->
                index.getOrPut(value) { ArrayList(2) }.add((song.toLong() shl 32) or frame.toLong())
            }
        }
        index.values.removeIf { it.size > MAX_COMMON }

        val parent = IntArray(usable.size) { it }
        fun root(i: Int): Int {
            var node = i
            while (parent[node] != node) {
                parent[node] = parent[parent[node]]
                node = parent[node]
            }
            return node
        }

        for (song in usable.indices) {
            val entry = usable[song]
            // other song -> (offset -> votes)
            val votes = HashMap<Int, HashMap<Int, Int>>()
            entry.print.forEachIndexed { frame, value ->
                index[value]?.forEach { packed ->
                    val other = (packed ushr 32).toInt()
                    if (other >= song) return@forEach
                    val offset = frame - (packed and 0xFFFFFFFFL).toInt()
                    val byOffset = votes.getOrPut(other) { HashMap() }
                    byOffset[offset] = (byOffset[offset] ?: 0) + 1
                }
            }
            for ((other, byOffset) in votes) {
                if (root(song) == root(other)) continue
                val (offset, count) = byOffset.maxByOrNull { it.value }?.toPair() ?: continue
                if (count < MIN_VOTES) continue
                val that = usable[other]
                if (!similarLength(entry.durationMs, that.durationMs)) continue
                val best = (-1..1).minOf { bitErrorRate(entry.print, that.print, offset + it) }
                if (best <= MAX_BIT_ERROR) parent[root(song)] = root(other)
            }
        }

        return usable.indices.groupBy { root(it) }.values
            .filter { it.size > 1 }
            .map { members -> members.map { usable[it].key } }
    }

    private fun similarLength(a: Long, b: Long): Boolean {
        if (a <= 0 || b <= 0) return true
        return abs(a - b) <= max(10_000L, max(a, b) / 10)
    }

    /** About seven seconds in common at the least. */
    private const val MIN_OVERLAP = 150
    private const val MIN_VOTES = 4
    private const val MAX_COMMON = 48
    /** Re-encodes of one recording sit well under this; different songs sit near 0.5. */
    const val MAX_BIT_ERROR = 0.30f
}
