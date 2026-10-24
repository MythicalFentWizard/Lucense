package com.exo.musicplayer.data.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The effects with character, in the spirit of Riff's distortion
 * (github.com/rootscripts/riff, MIT): each one a slider from 0 (off) to 1.
 *
 * - **Distortion** pushes the sound through a soft-clipping curve. Squashing
 *   the peaks raises the average level, so the result is turned back down to
 *   the loudness that went in: grit, not volume.
 * - **Lo-fi** takes bits and samples away, down to an old sampler's crunch.
 * - **Tremolo** pulses the level, five and a half times a second.
 * - **8D** sends the sound round the head, once every eight seconds or so.
 * - **Echo** repeats it a third of a second later, fading.
 *
 * One of these per player, since each keeps a little state between buffers:
 * where the tremolo and the turn have got to, the echo's memory, the
 * distortion's level. Buffers are interleaved PCM floats in -1..1, run after
 * the EQ and before any reverb, so the room is of the effected sound.
 */
class CharacterFx {

    @Volatile var drive = 0f
    @Volatile var lofi = 0f
    @Volatile var tremolo = 0f
    @Volatile var rotate = 0f
    @Volatile var echo = 0f

    val active: Boolean
        get() = on(drive) || on(lofi) || on(tremolo) || on(rotate) || on(echo)

    private var makeUp = 1f
    private var held = FloatArray(2)
    private var holdCount = 0
    private var tremoloPhase = 0.0
    private var turnPhase = 0.0
    private var echoLine = FloatArray(0)
    private var echoAt = 0
    private var echoShape = 0L

    fun reset() {
        makeUp = 1f
        holdCount = 0
        tremoloPhase = 0.0
        turnPhase = 0.0
        echoLine.fill(0f)
        echoAt = 0
    }

    fun process(buffer: FloatArray, channels: Int, sampleRate: Int, from: Int = 0, to: Int = buffer.size) {
        if (to <= from || channels < 1) return
        val ch = channels
        val rate = sampleRate.coerceAtLeast(8_000)
        if (on(drive)) distort(buffer, from, to, ch, rate) else makeUp = 1f
        if (on(lofi)) crush(buffer, from, to, ch)
        if (on(tremolo)) pulse(buffer, from, to, ch, rate)
        if (on(echo)) repeat(buffer, from, to, ch, rate) else if (echoLine.isNotEmpty()) echoLine.fill(0f)
        if (on(rotate) && ch >= 2) turn(buffer, from, to, ch, rate)
    }

    private fun distort(buffer: FloatArray, from: Int, to: Int, ch: Int, rate: Int) {
        val amount = drive.coerceIn(0f, 1f)
        val k = 1f + amount * MAX_DRIVE
        val norm = 1f / tanh(k)
        var inSq = 0.0
        var outSq = 0.0
        for (i in from until to) {
            val x = buffer[i]
            inSq += x * x
            val y = tanh(x * k) * norm
            outSq += y * y
            buffer[i] = y
        }
        // Back down to the level that went in, eased over about a sixth of a
        // second so it follows the song without pumping. Silence leaves it be.
        val previous = makeUp
        if (inSq > SILENCE * (to - from)) {
            val wanted = sqrt(inSq / outSq.coerceAtLeast(1e-12)).toFloat().coerceIn(0.05f, 1.5f)
            val seconds = (to - from).toDouble() / ch / rate
            val follow = (1.0 - exp(-seconds / 0.16)).toFloat()
            makeUp = previous + (wanted - previous) * follow
        }
        val frames = ((to - from) / ch).coerceAtLeast(1)
        var i = from
        var frame = 0
        while (i < to) {
            // Moved smoothly across the buffer, so there is no step at its edge.
            val g = previous + (makeUp - previous) * frame / frames
            for (c in 0 until ch) if (i + c < to) buffer[i + c] *= g
            i += ch
            frame++
        }
    }

    private fun crush(buffer: FloatArray, from: Int, to: Int, ch: Int) {
        val amount = lofi.coerceIn(0f, 1f)
        val levels = Math.pow(2.0, (15.0 - amount * 11.0)).toFloat()
        val hold = 1 + (amount * 7f).roundToInt()
        if (held.size != ch) held = FloatArray(ch)
        var i = from
        while (i + ch <= to) {
            if (holdCount <= 0) {
                for (c in 0 until ch) held[c] = (buffer[i + c] * levels).roundToInt() / levels
                holdCount = hold
            }
            holdCount--
            for (c in 0 until ch) buffer[i + c] = held[c]
            i += ch
        }
    }

    private fun pulse(buffer: FloatArray, from: Int, to: Int, ch: Int, rate: Int) {
        val depth = tremolo.coerceIn(0f, 1f) * 0.85f
        val step = TREMOLO_HZ / rate
        var i = from
        while (i + ch <= to) {
            val g = 1f - depth * (0.5f - 0.5f * cos(2 * PI * tremoloPhase).toFloat())
            for (c in 0 until ch) buffer[i + c] *= g
            tremoloPhase = (tremoloPhase + step) % 1.0
            i += ch
        }
    }

    private fun repeat(buffer: FloatArray, from: Int, to: Int, ch: Int, rate: Int) {
        val amount = echo.coerceIn(0f, 1f)
        val shape = rate.toLong() * 16 + ch
        if (shape != echoShape) {
            echoLine = FloatArray((rate * ECHO_SECONDS).toInt().coerceAtLeast(1) * ch)
            echoAt = 0
            echoShape = shape
        }
        val feedback = 0.25f + amount * 0.35f
        val mix = amount * 0.55f
        for (i in from until to) {
            val past = echoLine[echoAt]
            val x = buffer[i]
            echoLine[echoAt] = x + past * feedback
            // The dry sound steps back a little as the echo comes in, so it doesn't just get louder.
            buffer[i] = x * (1f - amount * 0.25f) + past * mix
            echoAt++
            if (echoAt >= echoLine.size) echoAt = 0
        }
    }

    private fun turn(buffer: FloatArray, from: Int, to: Int, ch: Int, rate: Int) {
        val amount = rotate.coerceIn(0f, 1f)
        val step = TURN_HZ / rate
        var i = from
        while (i + ch <= to) {
            val left = buffer[i]
            val right = buffer[i + 1]
            val mono = (left + right) * 0.5f
            // Equal power across the arc, so it doesn't dip as it passes the middle.
            val angle = (sin(2 * PI * turnPhase).toFloat() + 1f) * (PI / 4).toFloat()
            val l = mono * cos(angle) * SQRT2
            val r = mono * sin(angle) * SQRT2
            buffer[i] = left + (l - left) * amount
            buffer[i + 1] = right + (r - right) * amount
            turnPhase = (turnPhase + step) % 1.0
            i += ch
        }
    }

    companion object {
        /** Below this a slider is off, and its effect is skipped entirely. */
        const val OFF = 0.005f

        fun on(amount: Float): Boolean = amount > OFF

        private const val MAX_DRIVE = 24f
        private const val TREMOLO_HZ = 5.5
        private const val TURN_HZ = 0.125
        private const val ECHO_SECONDS = 0.33
        private const val SILENCE = 1e-8
        private val SQRT2 = sqrt(2f)
    }
}

/** The four effects beside distortion, as the effects panels list them. */
enum class CharacterEffect(val label: String, val note: String) {
    LOFI("Lo-fi", "Fewer bits and samples, like an old sampler"),
    TREMOLO("Tremolo", "The level pulses, five and a half times a second"),
    ROTATE("8D", "The sound goes round your head. Best on headphones"),
    ECHO("Echo", "Repeats a third of a second later, fading");

    /** "off", or how far it's turned up. */
    fun describe(amount: Float): String =
        if (!CharacterFx.on(amount)) "off" else "${(amount * 100).toInt()}%"
}
