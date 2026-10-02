package com.exo.musicplayer.ui.theme

import android.graphics.BlurMaskFilter
import android.graphics.RuntimeShader
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.exo.musicplayer.data.audio.SpectrumAnalyser
import com.exo.musicplayer.playback.Spectrum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The backgrounds Windows offers, on the phone: the same seven, the same names,
 * drawn the same way where Android allows it.
 */
enum class BackdropStyle(val label: String, val note: String) {
    NONE("None", "A plain background."),
    STARS("Stars", "A slow twinkle."),
    AURORA("Aurora", "Curtains of light sweeping slowly across the screen."),
    FIREFLIES("Fireflies", "Warm specks of light wandering and glowing."),
    SNOW("Snow", "Flakes falling gently."),
    WAVES("Waves", "A graph of the song scrolling past: what has played to the left, what is coming to the right."),
    REACTIVE("Reactive", "Moves with whatever is playing, in one of two looks.");

    companion object {
        fun fromName(name: String?): BackdropStyle? = entries.firstOrNull { it.name == name }
    }
}

/** Reactive's two looks. */
enum class ReactiveMode(val label: String, val note: String) {
    BALL("Ball", "A glowing ball in the middle, ringed by the spectrum, that swells and shakes on the beat."),
    BARS("Bars", "The output meter, across the bottom of the screen.");

    companion object {
        fun fromName(name: String?): ReactiveMode = entries.firstOrNull { it.name == name } ?: BALL
    }
}

private const val TAU = (2 * PI).toFloat()
private const val GRAPH_SPAN_MS = 12_000.0
private const val SLICE_MS = 10.0

private class Mote(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float)

private fun motes(count: Int, seed: Int = 11): List<Mote> {
    val random = Random(seed)
    return List(count) {
        Mote(random.nextFloat(), random.nextFloat(), random.nextFloat(), random.nextFloat() * TAU, 0.5f + random.nextFloat())
    }
}

/**
 * The music, as the reactive effects read it, stretched so that it visibly
 * moves - the same reading as Windows. Each band is measured against its own
 * recent floor and ceiling, so a bass-heavy song swings the whole way on every
 * beat instead of sitting near the top; [kick] jumps on a beat and dies away.
 */
class Pulse(bands: Int = 14) {
    val levels = FloatArray(bands.coerceAtLeast(4))
    var bass = 0f
        private set
    var energy = 0f
        private set
    var punch = 0f
        private set
    var kick = 0f
        private set
    var travel = 0f
        private set
    val rings = FloatArray(6) { -10f }

    private val floors = FloatArray(levels.size)
    private val ceilings = FloatArray(levels.size)
    private var heardFloor = 1f
    private var heardCeiling = 0f
    private var heard = 0f
    private var heardSlow = 0f
    private var kickPeak = 0f
    private var lastRing = -10f
    private var lastTime = -1f

    fun update(snapshot: FloatArray, t: Float) {
        val dt = if (lastTime < 0f) 0f else (t - lastTime).coerceIn(0f, 0.1f)
        lastTime = t
        val reading = FloatArray(levels.size) { i ->
            if (snapshot.isEmpty()) 0f
            else snapshot[(i * snapshot.size / levels.size).coerceAtMost(snapshot.size - 1)].coerceIn(0f, 1f)
        }
        val attack = ease(dt, 0.03f)
        val release = ease(dt, 0.12f)
        val settle = ease(dt, 1.6f)
        for (i in levels.indices) {
            val value = reading[i]
            floors[i] = if (value < floors[i]) value else floors[i] + (value - floors[i]) * settle
            ceilings[i] = if (value > ceilings[i]) value else ceilings[i] + (value - ceilings[i]) * settle
            val swing = ((value - floors[i]) / max(ceilings[i] - floors[i], MIN_SPAN)).coerceIn(0f, 1f)
            val target = (value * 0.3f + swing * 0.7f) * (value / 0.08f).coerceAtMost(1f)
            levels[i] += (target - levels[i]) * (if (target > levels[i]) attack else release)
        }
        val lowEnd = (levels.size * 0.22f).toInt().coerceAtLeast(1)
        bass = average(0, lowEnd)
        energy = average(0, levels.size)

        var raw = 0f
        for (i in 0 until lowEnd) raw += reading[i]
        val loud = raw / lowEnd
        heardFloor = if (loud < heardFloor) loud else heardFloor + (loud - heardFloor) * ease(dt, 1.6f)
        heardCeiling = if (loud > heardCeiling) loud else heardCeiling + (loud - heardCeiling) * ease(dt, 1.6f)
        val stretched = ((loud - heardFloor) / max(heardCeiling - heardFloor, MIN_HEARD_SPAN)).coerceIn(0f, 1f)
        val plain = ((loud - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val target = (stretched * 0.8f + plain * 0.2f) * (loud / 0.1f).coerceAtMost(1f)
        heard += (target - heard) * (if (target > heard) ease(dt, 0.012f) else ease(dt, 0.11f))
        punch = heard

        heardSlow += (heard - heardSlow) * ease(dt, 0.25f)
        val onset = ((heard - heardSlow) * 2.4f).coerceIn(0f, 1f)
        kickPeak = max(kickPeak * exp(-dt / 0.14f), onset)
        kick += (kickPeak - kick) * ease(dt, 0.015f)
        travel += dt * (0.015f + energy * 0.1f)
        if (onset > 0.45f && t - lastRing > 0.22f) {
            var oldest = 0
            for (s in rings.indices) if (rings[s] < rings[oldest]) oldest = s
            rings[oldest] = t
            lastRing = t
        }
    }

    fun average(from: Int, to: Int): Float {
        val end = min(to, levels.size)
        if (end <= from) return 0f
        var sum = 0f
        for (i in from until end) sum += levels[i]
        return sum / (end - from)
    }

    private fun ease(dt: Float, seconds: Float): Float = 1f - exp(-dt / seconds)

    private companion object {
        const val MIN_SPAN = 0.12f
        const val MIN_HEARD_SPAN = 0.10f
    }
}

/**
 * The song as Waves draws it: its loudness in 10 ms slices, read from the file
 * ahead of time in the background, and the position it has reached.
 */
class SongShape(val trackId: Long) {
    @Volatile var slices = FloatArray(0)
        private set
    @Volatile var filled = 0
        private set
    @Volatile var loudest = 0.02f
        private set

    /** Where the song is now, in milliseconds; set by whoever knows. */
    var positionMs: () -> Double = { 0.0 }

    suspend fun load(path: String) = withContext(Dispatchers.Default) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        runCatching {
            extractor.setDataSource(path)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")
            } ?: return@runCatching
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val durationUs = runCatching { format.getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val out = FloatArray(((durationUs / 1000.0) / SLICE_MS).toInt().coerceIn(1, 120_000))
            slices = out
            codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).apply {
                configure(format, null, null, 0)
                start()
            }
            val decoder = codec!!
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var squares = 0.0
            var count = 0
            var slice = 0
            var perSlice = (rate * channels * SLICE_MS / 1000).toInt().coerceAtLeast(1)
            while (isActive && slice < out.size) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val read = extractor.readSampleData(buffer, 0)
                        if (read < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, read, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    rate = decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    perSlice = (rate * channels * SLICE_MS / 1000).toInt().coerceAtLeast(1)
                } else if (outIndex >= 0) {
                    val buffer = decoder.getOutputBuffer(outIndex)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.nativeOrder())
                        while (buffer.remaining() >= 2 && slice < out.size) {
                            val v = buffer.getShort() / 32_768f
                            squares += v.toDouble() * v
                            count++
                            if (count >= perSlice) {
                                val rms = sqrt(squares / count).toFloat()
                                out[slice++] = rms
                                if (rms > loudest) loudest = rms
                                squares = 0.0
                                count = 0
                                filled = slice
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        }
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        extractor.release()
    }
}

/**
 * The chosen background, in [color]. Waves and Reactive redraw every frame
 * and keep the analyser on while shown; the others tick twenty times a second.
 */
@Composable
fun AppBackdrop(
    style: BackdropStyle,
    color: Color,
    spectrum: SpectrumAnalyser?,
    reactiveMode: ReactiveMode,
    song: SongShape?,
    modifier: Modifier = Modifier,
    count: Int = 70
) {
    if (style == BackdropStyle.NONE) return
    if (style == BackdropStyle.STARS) {
        Starfield(modifier = modifier, starColor = color, starCount = count + 20)
        return
    }
    val field = remember(count) { motes(count) }
    val seconds = remember { mutableFloatStateOf(0f) }
    val pulse = remember(spectrum) { Pulse(spectrum?.bands() ?: 14) }
    val moving = style == BackdropStyle.REACTIVE || style == BackdropStyle.WAVES
    val analyser = if (moving) spectrum else null

    SpectrumWhileVisible(active = analyser != null)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(style, lifecycle) {
        val raw = FloatArray(spectrum?.bands() ?: 1)
        // Only while the app is on screen: music plays for hours with the app
        // in the background, and a hidden background is time spent on nothing.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Picks up where it left off rather than jumping ahead by the time away.
            val origin = System.nanoTime() - (seconds.floatValue * 1e9f).toLong()
            while (isActive) {
                if (moving) withFrameNanos { } else delay(50L)
                val now = (System.nanoTime() - origin) / 1e9f
                if (analyser != null) pulse.update(analyser.snapshot(raw), now)
                seconds.floatValue = now
            }
        }
    }

    Spacer(
        modifier
            .graphicsLayer()
            .drawBehind {
                val t = seconds.floatValue
                clipRect {
                    when (style) {
                        BackdropStyle.AURORA -> aurora(t, color)
                        BackdropStyle.FIREFLIES -> fireflies(t, color, field)
                        BackdropStyle.SNOW -> snow(t, color, field)
                        BackdropStyle.WAVES ->
                            if (song != null && song.filled > 0) songGraph(color, song, pulse)
                            else ribbon(t, color, pulse)
                        BackdropStyle.REACTIVE -> when (reactiveMode) {
                            ReactiveMode.BALL -> reactiveBall(t, color, pulse)
                            ReactiveMode.BARS -> reactiveBars(color, pulse)
                        }
                        else -> Unit
                    }
                }
            }
    )
}

/**
 * Keeps the shared spectrum analyser running while this is composed and the
 * app is on screen, and not otherwise. It costs an FFT on every audio buffer,
 * and with the app in the background nothing is there to see it.
 */
@Composable
fun SpectrumWhileVisible(active: Boolean = true) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, active) {
        var holding = false
        fun follow() {
            val wanted = active && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            if (wanted && !holding) Spectrum.acquire()
            if (!wanted && holding) Spectrum.release()
            holding = wanted
        }
        val observer = LifecycleEventObserver { _, _ -> follow() }
        lifecycle.addObserver(observer)
        follow()
        onDispose {
            lifecycle.removeObserver(observer)
            if (holding) Spectrum.release()
        }
    }
}

private fun wrap(value: Float) = ((value % 1f) + 1f) % 1f

private val glowPaint = Paint()
private val softPaint = Paint()

/** A glow falling away along a bell curve, dithered, so it fades without an edge. */
private fun DrawScope.softCircle(color: Color, alpha: Float, center: Offset, radius: Float) {
    if (radius <= 0f || alpha <= 0.002f) return
    val brush = Brush.radialGradient(
        0f to color.copy(alpha = alpha),
        0.2f to color.copy(alpha = alpha * 0.82f),
        0.4f to color.copy(alpha = alpha * 0.52f),
        0.6f to color.copy(alpha = alpha * 0.24f),
        0.8f to color.copy(alpha = alpha * 0.07f),
        1f to Color.Transparent,
        center = center,
        radius = radius
    )
    drawIntoCanvas { canvas ->
        brush.applyTo(size, softPaint, 1f)
        softPaint.asFrameworkPaint().isDither = true
        canvas.drawCircle(center, radius, softPaint)
    }
}

/** A line blurred into a soft halo, for the glow under a crisp stroke. */
private fun DrawScope.glowPath(path: Path, color: Color, width: Float, blur: Float) {
    drawIntoCanvas { canvas ->
        glowPaint.color = color
        glowPaint.style = PaintingStyle.Stroke
        glowPaint.strokeWidth = width
        glowPaint.strokeCap = StrokeCap.Round
        glowPaint.asFrameworkPaint().maskFilter = BlurMaskFilter(blur.coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
        canvas.drawPath(path, glowPaint)
    }
}

private fun smoothThrough(path: Path, xs: FloatArray, ys: FloatArray, moveFirst: Boolean) {
    if (moveFirst) path.moveTo(xs[0], ys[0])
    for (i in 1 until xs.size) {
        path.quadraticTo(xs[i - 1], ys[i - 1], (xs[i - 1] + xs[i]) / 2f, (ys[i - 1] + ys[i]) / 2f)
    }
    path.lineTo(xs[xs.size - 1], ys[ys.size - 1])
}

// ---- Aurora ----------------------------------------------------------------------

/** The same curtains of light as Windows, as an Android runtime shader (13 and up). */
private object AuroraShader {
    val shader: RuntimeShader? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) null
        else runCatching { RuntimeShader(AURORA_AGSL) }.getOrNull()
    }
}

private const val AURORA_AGSL = """
uniform float2 iResolution;
uniform float iTime;
uniform float3 c1;
uniform float3 c2;
uniform float3 c3;
uniform float strength;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
               mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
}

float fbm(float2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p);
        p = p * 2.03 + float2(3.1, 1.7);
        amplitude *= 0.5;
    }
    return value;
}

half4 main(float2 fragCoord) {
    float aspect = iResolution.x / max(iResolution.y, 1.0);
    float2 uv = float2(fragCoord.x / max(iResolution.x, 1.0), fragCoord.y / max(iResolution.y, 1.0));
    float t = iTime;
    float x = uv.x * aspect;
    float3 light = float3(0.0);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float edge = 0.44 - 0.12 * fi
            + 0.13 * sin(x * (1.1 + 0.4 * fi) + t * (0.045 + 0.02 * fi) + fi * 2.1)
            + 0.14 * (fbm(float2(x * 1.7 + fi * 5.0, t * 0.04)) - 0.5);
        float d = uv.y - edge;
        float presence = 0.35 + 0.65 * smoothstep(0.25, 0.60, fbm(float2(x * 0.55 + fi * 9.0, t * 0.015 + fi)));
        float fall = 0.24 + 0.08 * fi;
        float body = d < 0.0 ? exp(d / fall * 1.8) : exp(-d * 30.0);
        float core = d < 0.0 ? exp(d / 0.045) : exp(-d * 30.0);
        float rays = smoothstep(0.25, 0.85, fbm(float2(x * 10.0 + t * 0.06 * (1.0 + fi), t * 0.10 + fi * 3.7)));
        float shimmer = 0.85 + 0.15 * sin(x * 13.0 + t * (0.8 + 0.3 * fi));
        float3 top = i == 1 ? c3 : c1;
        float3 tint = mix(c2, top, clamp(-d / fall * 1.4, 0.0, 1.0));
        float layer = i == 2 ? 0.6 : 1.0;
        light += tint * presence * layer * (body * 0.55 + core * 0.45) * (0.35 + 0.65 * rays) * shimmer;
    }
    light += c1 * 0.035 * (1.0 - uv.y);
    light *= strength;
    light = max(light + (hash(fragCoord + fract(iTime * 7.0)) - 0.5) / 255.0, float3(0.0));
    float alpha = clamp(max(light.r, max(light.g, light.b)), 0.0, 1.0);
    light = min(light, float3(alpha));
    return half4(half3(light), half(alpha));
}
"""

private fun DrawScope.aurora(t: Float, color: Color) {
    val shader = AuroraShader.shader
    if (shader == null) {
        drawnAurora(t, color)
        return
    }
    val edge = lerp(color, Color(0xFF3DF2A6), 0.55f)
    val violet = lerp(color, Color(0xFFB36BFF), 0.45f)
    shader.setFloatUniform("iResolution", size.width, size.height)
    shader.setFloatUniform("iTime", t)
    shader.setFloatUniform("c1", color.red, color.green, color.blue)
    shader.setFloatUniform("c2", edge.red, edge.green, edge.blue)
    shader.setFloatUniform("c3", violet.red, violet.green, violet.blue)
    shader.setFloatUniform("strength", 0.9f)
    drawRect(ShaderBrush(shader))
}

/** Soft pools of light on slow orbits, for phones older than Android 13. */
private fun DrawScope.drawnAurora(t: Float, color: Color) {
    val w = size.width
    val h = size.height
    val tints = listOf(color, lerp(color, Color(0xFF3FE0C8), 0.5f), lerp(color, Color(0xFFFF6FD8), 0.4f))
    for (i in 0 until 4) {
        val tint = tints[i % tints.size]
        val center = Offset(
            w * (0.5f + 0.38f * sin(t * 0.045f * (i + 1) + i * 1.7f)),
            h * (0.4f + 0.3f * cos(t * 0.035f * (i + 1) + i * 0.9f))
        )
        val radius = max(w, h) * (0.38f + 0.08f * sin(t * 0.08f + i))
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.13f), Color.Transparent), center, radius), radius, center)
    }
}

// ---- Fireflies and snow --------------------------------------------------------------

private fun DrawScope.fireflies(t: Float, color: Color, motes: List<Mote>) {
    val glow = lerp(color, Color(0xFFFFE08A), 0.4f)
    for (m in motes) {
        val center = Offset(
            wrap(m.x + 0.03f * sin(t * 0.25f * m.speed + m.phase)) * size.width,
            wrap(m.y + 0.03f * cos(t * 0.19f * m.speed + m.phase * 1.3f) - t * 0.004f * m.speed) * size.height
        )
        val pulse = 0.5f + 0.5f * sin(t * 1.1f * m.speed + m.phase)
        val core = (1.2f + m.size * 1.4f) * density
        softCircle(glow, 0.18f * pulse, center, core * 6f)
        drawCircle(glow.copy(alpha = 0.25f + 0.6f * pulse), core, center)
    }
}

private fun DrawScope.snow(t: Float, color: Color, motes: List<Mote>) {
    val flake = lerp(color, Color.White, 0.65f)
    for (m in motes) {
        val center = Offset(
            wrap(m.x + 0.012f * sin(t * 0.7f * m.speed + m.phase)) * size.width,
            wrap(m.y + t * 0.025f * m.speed) * size.height
        )
        drawCircle(flake.copy(alpha = 0.18f + m.size * 0.45f), (0.7f + m.size * 1.9f) * density, center)
    }
}

// ---- Waves --------------------------------------------------------------------------

private fun DrawScope.songGraph(color: Color, song: SongShape, pulse: Pulse?) {
    val w = size.width
    val h = size.height
    val nowX = w * 0.3f
    val msPerPx = GRAPH_SPAN_MS / w
    val centre = h * 0.62f
    val reach = h * 0.16f
    val position = song.positionMs()
    val hot = lerp(color, Color.White, 0.45f)
    val step = 5f * density
    val slices = song.slices
    val filled = min(song.filled, slices.size)
    val scale = 1f / max(song.loudest, 0.02f)

    fun loudness(ms: Double): Float {
        var sum = 0f
        var n = 0
        for (k in -3..3) {
            val s = ms / SLICE_MS + k
            val i = floor(s).toInt()
            if (i < 0 || i + 1 >= filled) continue
            val f = (s - i).toFloat()
            sum += slices[i] + (slices[i + 1] - slices[i]) * f
            n++
        }
        if (n == 0) return 0f
        return ((sum / n) * scale).coerceIn(0f, 1f).pow(0.75f)
    }

    val count = (w / step).toInt() + 3
    val xs = FloatArray(count)
    val tops = FloatArray(count)
    val bottoms = FloatArray(count)
    for (c in 0 until count) {
        val x = (c - 1) * step
        val amplitude = loudness(position + (x - nowX) * msPerPx) * reach + density
        xs[c] = x
        tops[c] = centre - amplitude
        bottoms[c] = centre + amplitude
    }
    val nowFraction = (nowX / w).coerceIn(0.002f, 0.998f)
    fun split(tint: Color, past: Float, future: Float, tail: Float) = Brush.horizontalGradient(
        0f to tint.copy(alpha = past),
        (nowFraction - 0.001f) to tint.copy(alpha = past),
        (nowFraction + 0.001f) to tint.copy(alpha = future),
        1f to tint.copy(alpha = tail),
        startX = 0f,
        endX = w
    )
    val top = Path().also { smoothThrough(it, xs, tops, moveFirst = true) }
    val bottom = Path().also { smoothThrough(it, xs, bottoms, moveFirst = true) }
    val body = Path().apply {
        smoothThrough(this, xs, tops, moveFirst = true)
        lineTo(xs[count - 1], bottoms[count - 1])
        smoothThrough(this, xs.reversedArray(), bottoms.reversedArray(), moveFirst = false)
        close()
    }
    drawLine(color.copy(alpha = 0.10f), Offset(0f, centre), Offset(w, centre), density)
    drawPath(body, split(color, 0.08f, 0.24f, 0.12f))
    glowPath(top, hot.copy(alpha = 0.22f), 2f * density, 6f * density)
    glowPath(bottom, hot.copy(alpha = 0.22f), 2f * density, 6f * density)
    drawPath(top, split(hot, 0.30f, 0.80f, 0.35f), style = Stroke(width = 1.4f * density))
    drawPath(bottom, split(hot, 0.30f, 0.80f, 0.35f), style = Stroke(width = 1.4f * density))

    val level = loudness(position)
    val live = pulse?.energy ?: 0f
    val lineTop = centre - reach * 1.2f
    val lineBottom = centre + reach * 1.2f
    val line = Path().apply {
        moveTo(nowX, lineTop)
        lineTo(nowX, lineBottom)
    }
    glowPath(line, hot.copy(alpha = 0.30f + live * 0.40f), 3f * density, (6f + live * 8f) * density)
    drawLine(hot.copy(alpha = 0.85f), Offset(nowX, lineTop), Offset(nowX, lineBottom), 1.5f * density)
    val reachNow = level * reach + density
    for (y in listOf(centre - reachNow, centre + reachNow)) {
        softCircle(hot, 0.45f + live * 0.35f, Offset(nowX, y), (10f + level * 14f) * density)
        drawCircle(hot, (2.4f + level * 2f) * density, Offset(nowX, y))
    }
}

/** Waves before anything is playing: lines rolling along, swelling with the bass. */
private fun DrawScope.ribbon(t: Float, color: Color, pulse: Pulse?) {
    val w = size.width
    val h = size.height
    val step = 4f * density
    val lines = 7
    val half = (lines - 1) / 2f
    val hot = lerp(color, Color.White, 0.5f)
    val bass = pulse?.bass ?: 0f
    val energy = pulse?.energy ?: 0f
    val kick = pulse?.kick ?: 0f
    val swell = h * (0.05f + bass * 0.10f + kick * 0.03f)
    val spread = h * (0.028f + energy * 0.022f)
    val frequency = TAU * 1.0f / w
    val ripple = TAU * 2.6f / w
    val drift = t * 0.32f + (pulse?.travel ?: 0f) * 9f
    val breathe = sin(t * 0.37f) * h * 0.02f
    for (k in 0 until lines) {
        val offset = k - half
        val level = pulse?.let { it.average(k * it.levels.size / lines, (k + 1) * it.levels.size / lines) } ?: 0f
        val base = h * 0.62f + breathe + offset * spread
        val twist = offset * 0.32f
        val path = Path()
        var x = -step
        var first = true
        while (x <= w + step) {
            val y = base + sin(x * frequency + drift + twist) * swell +
                sin(x * ripple - t * 0.55f + k * 0.9f) * h * 0.010f * (1f + level * 3.2f)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            x += step
        }
        val centreWeight = 1f - abs(offset) / half
        val tint = lerp(color, hot, (0.15f + level * 0.6f) * centreWeight)
        val alpha = ((0.10f + 0.16f * centreWeight) * (0.8f + energy * 1.4f + level * 0.6f)).coerceAtMost(0.6f)
        if (centreWeight > 0.6f) glowPath(path, tint.copy(alpha = alpha * 0.55f), (3f + bass * 5f) * density, (5f + bass * 7f) * density)
        drawPath(path, tint.copy(alpha = alpha), style = Stroke(width = (1.1f + centreWeight * 0.8f + level * 1.2f) * density))
    }
}

// ---- Reactive ---------------------------------------------------------------------

private fun DrawScope.reactiveBall(t: Float, color: Color, pulse: Pulse) {
    val w = size.width
    val h = size.height
    val hot = lerp(color, Color.White, 0.4f)
    val punch = pulse.punch
    val kick = pulse.kick
    val shake = (kick * 26f + punch * 4f) * density
    val jitter = Offset(
        (sin(t * 47f) * 0.6f + sin(t * 83f + 1.3f) * 0.4f) * shake,
        (cos(t * 53f) * 0.6f + sin(t * 71f + 2.1f) * 0.4f) * shake
    )
    val centre = Offset(w / 2f, h * 0.42f) + jitter
    val radius = min(w, h) * 0.13f * (0.78f + punch * 0.68f + kick * 0.18f)
    softCircle(color, 0.10f + punch * 0.30f + kick * 0.08f, centre, radius * 4.2f)
    for (birth in pulse.rings) {
        val age = t - birth
        if (age < 0f || age > 1.2f) continue
        val progress = age / 1.2f
        val fade = (1f - progress) * (1f - progress)
        drawCircle(hot.copy(alpha = 0.55f * fade), radius * (1.15f + progress * 3.2f), centre, style = Stroke(width = (2.8f - 1.8f * progress) * density))
    }
    val bars = 72
    val half = bars / 2
    val bands = pulse.levels.size
    val ring = Path()
    for (i in 0 until bars) {
        val j = if (i < half) i else bars - 1 - i
        val level = pulse.levels[(j * bands / half).coerceAtMost(bands - 1)].coerceIn(0f, 1f)
        val angle = PI / 2 + i.toDouble() / bars * 2 * PI
        val inner = radius * 1.12f
        val outer = inner + radius * (0.05f + level.pow(0.85f) * 1.25f)
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        ring.moveTo(centre.x + dx * inner, centre.y + dy * inner)
        ring.lineTo(centre.x + dx * outer, centre.y + dy * outer)
    }
    glowPath(ring, color.copy(alpha = 0.45f + punch * 0.25f), 3f * density, 7f * density)
    drawPath(ring, hot.copy(alpha = 0.9f), style = Stroke(width = 2.6f * density, cap = StrokeCap.Round))
    drawCircle(
        Brush.radialGradient(
            listOf(hot.copy(alpha = 0.35f + punch * 0.35f), color.copy(alpha = 0.30f), color.copy(alpha = 0.14f)),
            centre,
            radius
        ),
        radius,
        centre
    )
    drawCircle(hot.copy(alpha = 0.85f), radius, centre, style = Stroke(width = (2f + punch * 1.5f) * density))
}

private fun DrawScope.reactiveBars(color: Color, pulse: Pulse) {
    val w = size.width
    val h = size.height
    val count = pulse.levels.size
    val margin = 16f * density
    val gap = 5f * density
    val barWidth = ((w - margin * 2 - gap * (count - 1)) / count).coerceAtLeast(2f)
    val floorY = h - 8f * density
    val quiet = lerp(color, Color.Black, 0.55f).copy(alpha = 0.55f)
    val lit = lerp(color, Color.White, 0.25f)
    val corner = CornerRadius(min(barWidth / 2f, 4f * density))
    for (i in 0 until count) {
        val level = pulse.levels[i].coerceIn(0f, 1f)
        val low = (1f - i / (count * 0.35f)).coerceIn(0f, 1f)
        val lift = pulse.kick * low * 0.16f + pulse.punch * low * 0.10f
        val tall = 4f * density + (level.pow(0.9f) + lift).coerceAtMost(1.15f) * h * 0.26f
        val left = margin + i * (barWidth + gap)
        val top = floorY - tall
        if (level > 0.02f) {
            softCircle(color, 0.04f + level * 0.13f, Offset(left + barWidth / 2f, top), barWidth * 1.7f)
            drawRoundRect(
                Brush.verticalGradient(
                    0f to color.copy(alpha = 0.05f),
                    0.4f to color.copy(alpha = 0.15f),
                    1f to color.copy(alpha = 0.30f),
                    startY = top,
                    endY = floorY
                ),
                Offset(left, top),
                Size(barWidth, tall),
                corner
            )
            drawRoundRect(lit.copy(alpha = 0.40f), Offset(left, top), Size(barWidth, min(3f * density, tall)), corner)
        } else {
            drawRoundRect(quiet.copy(alpha = quiet.alpha * 0.45f), Offset(left, top), Size(barWidth, tall), corner)
        }
    }
}
