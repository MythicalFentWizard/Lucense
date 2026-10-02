package com.exo.musicplayer.data.library

import android.net.Uri
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.exo.musicplayer.data.recognition.AudioSampler
import com.exo.musicplayer.data.recognition.Dsp
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Listening to songs for the duplicate finder, on real songs from the library
 * on the device it runs on: how long it takes per song, that the faster
 * resampler gives the same audio as the per-sample one it replaced, and that
 * prints taken the new way still match prints saved the old way.
 */
@RunWith(AndroidJUnit4::class)
class PrintSpeedTest {

    @Test
    fun printsRealSongs(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val music = File(context.getExternalFilesDir(null), "Music")
        val songs = music.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.take(12)
        assumeTrue("no songs in ${music.path}", songs.isNotEmpty())

        var oldWay = 0L
        var newWay = 0L
        var worstBer = 0f
        var berSum = 0f
        var compared = 0
        for (song in songs) {
            val uri = Uri.fromFile(song)
            // As prints were taken before: 16 kHz first, then down to the print rate.
            val t0 = System.nanoTime()
            val before = AudioSampler.sampleMono16k(context, uri, AudioPrint.SECONDS)
                ?.let { AudioPrint.of(it, 16_000) }
            val t1 = System.nanoTime()
            // As they are taken now: straight to the print rate.
            val after = AudioSampler.sampleMono(context, uri, AudioPrint.SECONDS, AudioPrint.RATE)
                ?.let { AudioPrint.of(it, AudioPrint.RATE) }
            val t2 = System.nanoTime()
            oldWay += t1 - t0
            newWay += t2 - t1
            if (before != null && after != null && before.isNotEmpty() && after.isNotEmpty()) {
                val ber = AudioPrint.bitErrorRate(before, after, 0)
                worstBer = max(worstBer, ber)
                berSum += ber
                compared++
            }
        }
        val n = songs.size
        Log.i(
            "PrintSpeedTest",
            "per song: two-step ${oldWay / 1_000_000 / n} ms, direct ${newWay / 1_000_000 / n} ms; " +
                "prints compared $compared, mean BER ${"%.3f".format(berSum / compared)}, worst ${"%.3f".format(worstBer)}"
        )
        // Copies of one song differ by at most 0.185, different songs by at least
        // 0.476 (measured on a 1,102-song library); the threshold is 0.30.
        assertTrue("new prints drift from old ones: worst BER $worstBer", worstBer < 0.10f)
    }

    @Test
    fun resamplerMatchesThePerSampleFormula() {
        val rnd = java.util.Random(7)
        val input = FloatArray(44_100 * 3) { (sin(it * 0.031) * 0.5 + rnd.nextGaussian() * 0.1).toFloat() }
        for ((from, to) in listOf(44_100 to 16_000, 44_100 to 5_512, 48_000 to 5_512, 16_000 to 5_512, 22_050 to 44_100)) {
            val fast = Dsp.resample(input, from, to)
            val slow = reference(input, from, to)
            var worst = 0f
            for (i in fast.indices) worst = max(worst, abs(fast[i] - slow[i]))
            Log.i("PrintSpeedTest", "$from -> $to: ${fast.size} samples, worst difference $worst")
            assertTrue("$from -> $to differs by $worst", fast.size == slow.size && worst < 1e-5f)
        }
    }

    /** The resampler as it was: every weight worked out for every sample. */
    private fun reference(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        val ratio = toRate.toDouble() / fromRate
        val out = FloatArray((input.size * ratio).toInt())
        val cutoff = min(1.0, ratio)
        val halfWidth = 16
        fun sinc(x: Double) = if (abs(x) < 1e-9) 1.0 else sin(PI * x) / (PI * x)
        fun taper(x: Double) = if (abs(x) >= halfWidth) 0.0 else 0.5 * (1.0 + cos(PI * abs(x) / halfWidth))
        for (i in out.indices) {
            val center = i / ratio
            val start = floor(center).toInt() - halfWidth + 1
            var acc = 0.0
            var norm = 0.0
            for (k in 0 until halfWidth * 2) {
                val index = start + k
                if (index < 0 || index >= input.size) continue
                val x = center - index
                val h = sinc(cutoff * x) * cutoff * taper(x)
                acc += input[index] * h
                norm += h
            }
            out[i] = if (norm != 0.0) (acc / norm).toFloat() else 0f
        }
        return out
    }
}
