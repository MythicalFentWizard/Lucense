package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalWindowInfo
import com.exo.musicplayer.data.audio.SpectrumAnalyser
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** What is drawn behind the sidebar, the library and the lyrics. */
enum class BackdropStyle(val label: String, val note: String) {
    NONE("None", "A plain background."),
    STARS("Stars", "A slow twinkle, as on the phone."),
    AURORA("Aurora", "Curtains of light sweeping slowly across the window."),
    FIREFLIES("Fireflies", "Warm specks of light wandering and glowing."),
    SNOW("Snow", "Flakes falling gently."),
    WAVES("Waves", "A ribbon of light that swells and ripples with the music."),
    REACTIVE("Reactive", "Glows, pulses and sends out rings on the beat.");

    companion object {
        fun fromName(name: String?): BackdropStyle = entries.firstOrNull { it.name == name } ?: STARS
    }
}

private const val TAU = (2 * PI).toFloat()
private const val REACTIVE_BANDS = 24

internal class Mote(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float)

internal fun motes(count: Int, seed: Int = 11): List<Mote> {
    val random = Random(seed)
    return List(count) {
        Mote(
            random.nextFloat(), random.nextFloat(), random.nextFloat(),
            random.nextFloat() * TAU, 0.5f + random.nextFloat()
        )
    }
}

/** Where this surface sits in its window, so effects run on across neighbouring surfaces. */
internal class Placement {
    var x = 0f
    var width = 0f
}

/**
 * The music, as the reactive effects read it: band levels that rise at once
 * and fall away gently, bass, mids and treble, a kick taken from the raw bass
 * jumping above its recent average, rings sent out on those kicks, and a
 * distance travelled that runs faster the louder it gets.
 */
internal class Pulse(bands: Int = REACTIVE_BANDS) {
    val levels = FloatArray(bands)
    var bass = 0f
    var mid = 0f
    var treble = 0f
    var energy = 0f
    var kick = 0f
    var travel = 0f

    /** When each ring was sent out, in effect seconds. */
    val rings = FloatArray(6) { -10f }

    private var slowBass = 0f
    private var lastRing = -10f
    private var lastTime = -1f

    fun update(snapshot: FloatArray, t: Float) {
        val dt = if (lastTime < 0f) 0f else (t - lastTime).coerceIn(0f, 0.2f)
        lastTime = t
        for (i in levels.indices) {
            val value = if (snapshot.isEmpty()) {
                0f
            } else {
                snapshot[(i * snapshot.size / levels.size).coerceAtMost(snapshot.size - 1)].coerceIn(0f, 1f)
            }
            levels[i] = if (value > levels[i]) value else levels[i] * 0.86f + value * 0.14f
        }
        bass = average(0, 4)
        mid = average(4, 12)
        treble = average(12, levels.size)
        energy = average(0, levels.size)

        var rawBass = 0f
        val low = (snapshot.size / 6).coerceAtLeast(1).coerceAtMost(snapshot.size)
        for (i in 0 until low) rawBass += snapshot[i].coerceIn(0f, 1f)
        rawBass /= low
        kick = max(kick * 0.8f, ((rawBass - slowBass) * 4f).coerceIn(0f, 1f))
        slowBass = slowBass * 0.92f + rawBass * 0.08f

        travel += dt * (0.015f + energy * 0.1f)
        if (kick > 0.45f && t - lastRing > 0.28f) {
            var oldest = 0
            for (s in rings.indices) if (rings[s] < rings[oldest]) oldest = s
            rings[oldest] = t
            lastRing = t
        }
    }

    /** Mean level of bands [from] until [to]. */
    fun average(from: Int, to: Int): Float {
        val end = min(to, levels.size)
        if (end <= from) return 0f
        var sum = 0f
        for (i in from until end) sum += levels[i]
        return sum / (end - from)
    }
}

/**
 * The chosen background effect, in [color].
 *
 * Every style follows Starfield's rules: a graphics layer of its own, the clock
 * read inside the draw call so a tick repaints without recomposing anything in
 * front, and no ticking while the window isn't focused - unless
 * [pauseWhenUnfocused] is false, as for the lyrics window, which mostly sits
 * beside whatever has focus. Waves and Reactive read the playback spectrum, so
 * they keep the analyser switched on for as long as they are shown. Aurora,
 * Waves and Reactive are laid out across the whole window rather than each
 * surface, so they carry on unbroken from the sidebar into the library.
 */
@Composable
fun Backdrop(
    style: BackdropStyle,
    color: Color,
    spectrum: SpectrumAnalyser?,
    modifier: Modifier = Modifier,
    count: Int = 90,
    pauseWhenUnfocused: Boolean = true
) {
    when (style) {
        BackdropStyle.NONE -> Unit
        BackdropStyle.STARS -> Starfield(
            enabled = true,
            color = color,
            modifier = modifier,
            count = count,
            pauseWhenUnfocused = pauseWhenUnfocused
        )
        else -> Animated(style, color, spectrum, modifier, count, pauseWhenUnfocused)
    }
}

@Composable
private fun Animated(
    style: BackdropStyle,
    color: Color,
    spectrum: SpectrumAnalyser?,
    modifier: Modifier,
    count: Int,
    pauseWhenUnfocused: Boolean
) {
    val motes = remember(count) { motes(count) }
    val seconds = remember { mutableFloatStateOf(0f) }
    val pulse = remember { Pulse() }
    val place = remember { Placement() }
    val focused = LocalWindowInfo.current.isWindowFocused || !pauseWhenUnfocused
    val listening = (style == BackdropStyle.REACTIVE || style == BackdropStyle.WAVES) && spectrum != null

    LaunchedEffect(focused, style) {
        if (!focused) return@LaunchedEffect
        val origin = System.nanoTime() - (seconds.floatValue * 1e9f).toLong()
        val raw = FloatArray(spectrum?.bands() ?: 1)
        while (isActive) {
            val now = (System.nanoTime() - origin) / 1e9f
            if (listening && spectrum != null) {
                // Re-asserted every tick: the effects panel's meter switches the
                // analyser off when it closes, and this still needs it.
                spectrum.enabled = true
                pulse.update(spectrum.snapshot(raw), now)
            }
            seconds.floatValue = now
            delay(if (listening) 33L else 50L)
        }
    }
    DisposableEffect(listening) {
        onDispose { if (listening) spectrum?.enabled = false }
    }

    Spacer(
        modifier
            .onGloballyPositioned { coordinates ->
                place.x = coordinates.positionInRoot().x
                place.width = coordinates.findRootCoordinates().size.width.toFloat()
            }
            .graphicsLayer()
            .drawBehind {
                val t = seconds.floatValue
                clipRect {
                    when (style) {
                        BackdropStyle.AURORA -> aurora(t, color, place)
                        BackdropStyle.FIREFLIES -> fireflies(t, color, motes)
                        BackdropStyle.SNOW -> snow(t, color, motes)
                        BackdropStyle.WAVES -> waves(t, color, if (listening) pulse else null, place)
                        BackdropStyle.REACTIVE -> reactive(t, color, pulse, motes, place)
                        else -> Unit
                    }
                }
            }
    )
}

private fun wrap(value: Float) = ((value % 1f) + 1f) % 1f

// Reused on the UI thread, where every backdrop draws.
private val glowPaint = Paint()
private val softPaint = Paint()

/**
 * A glow whose brightness falls away along a bell curve rather than a straight
 * line, dithered, so it fades into the background without a visible edge or
 * banding on a dark theme.
 */
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
        glowPaint.asFrameworkPaint().maskFilter = MaskFilter.makeBlur(FilterBlurMode.NORMAL, blur)
        canvas.drawPath(path, glowPaint)
    }
}

// ---- Aurora --------------------------------------------------------------------

/**
 * Curtains of light computed per pixel on the graphics card. Each hangs from a
 * lower edge that sweeps across the window in a slow S-curve, comes and goes
 * along the width, glows green along that edge and turns towards the theme's
 * colour as it fades upward, and is broken into drifting vertical rays.
 */
internal class AuroraShader private constructor(effect: RuntimeEffect) {

    private val builder = RuntimeShaderBuilder(effect)

    fun brush(t: Float, windowWidth: Float, height: Float, offsetX: Float, color: Color): ShaderBrush {
        val edge = lerp(color, Color(0xFF3DF2A6), 0.55f)
        val violet = lerp(color, Color(0xFFB36BFF), 0.45f)
        builder.uniform("iResolution", windowWidth, height)
        builder.uniform("iOffset", offsetX)
        builder.uniform("iTime", t)
        builder.uniform("c1", color.red, color.green, color.blue)
        builder.uniform("c2", edge.red, edge.green, edge.blue)
        builder.uniform("c3", violet.red, violet.green, violet.blue)
        builder.uniform("strength", 0.9f)
        return ShaderBrush(builder.makeShader())
    }

    companion object {
        /** Null where runtime shaders can't be compiled; the drawn aurora stands in. */
        val instance: AuroraShader? by lazy {
            runCatching { AuroraShader(RuntimeEffect.makeForShader(AURORA_SKSL)) }.getOrNull()
        }
    }
}

private const val AURORA_SKSL = """
uniform float2 iResolution;
uniform float iOffset;
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
    float2 uv = float2((fragCoord.x + iOffset) / max(iResolution.x, 1.0), fragCoord.y / max(iResolution.y, 1.0));
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
    // A whisper of noise, below one step of 8-bit colour, so the fades never band.
    light = max(light + (hash(fragCoord + fract(iTime * 7.0)) - 0.5) / 255.0, float3(0.0));
    float alpha = clamp(max(light.r, max(light.g, light.b)), 0.0, 1.0);
    light = min(light, float3(alpha));
    return half4(half3(light), half(alpha));
}
"""

internal fun DrawScope.aurora(t: Float, color: Color, place: Placement) {
    val shader = AuroraShader.instance
    if (shader == null) {
        drawnAurora(t, color)
        return
    }
    drawRect(shader.brush(t, max(place.width, size.width), size.height, place.x, color))
}

/** Soft pools of light on slow orbits, for renderers without runtime shaders. */
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
        drawCircle(
            Brush.radialGradient(listOf(tint.copy(alpha = 0.13f), Color.Transparent), center, radius),
            radius,
            center
        )
    }
}

// ---- Fireflies and snow ----------------------------------------------------------

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

// ---- Waves ---------------------------------------------------------------------

/**
 * A ribbon of seven nearly parallel lines rolling along the lower part of the
 * window, twisting a little from one edge of the ribbon to the other. The bass
 * swells the whole ribbon, each line ripples with its own part of the range,
 * the middle lines brighten and glow with the music, and the ribbon is pushed
 * along faster the louder it gets. Without music it just rolls.
 */
internal fun DrawScope.waves(t: Float, color: Color, pulse: Pulse?, place: Placement) {
    val w = size.width
    val h = size.height
    val windowWidth = max(place.width, w)
    val step = 4f * density
    val lines = 7
    val half = (lines - 1) / 2f
    val hot = lerp(color, Color.White, 0.5f)
    val bass = pulse?.bass ?: 0f
    val energy = pulse?.energy ?: 0f
    val kick = pulse?.kick ?: 0f
    val swell = h * (0.055f + bass * 0.09f + kick * 0.03f)
    val spread = h * (0.034f + energy * 0.02f)
    val frequency = TAU * 1.15f / windowWidth
    val ripple = TAU * 3.1f / windowWidth
    val drift = t * 0.28f + (pulse?.travel ?: 0f) * 7f
    for (k in 0 until lines) {
        val offset = k - half
        val level = pulse?.let { it.average(k * it.levels.size / lines, (k + 1) * it.levels.size / lines) } ?: 0f
        val base = h * 0.64f + offset * spread
        val twist = offset * 0.28f
        val path = Path()
        var x = -step
        var first = true
        while (x <= w + step) {
            val along = x + place.x
            val y = base + sin(along * frequency + drift + twist) * swell +
                sin(along * ripple - t * 0.55f + k * 0.9f) * h * 0.010f * (1f + level * 2.5f)
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            x += step
        }
        val centre = 1f - abs(offset) / half
        val tint = lerp(color, hot, (0.15f + level * 0.6f) * centre)
        val alpha = ((0.10f + 0.16f * centre) * (0.8f + energy * 1.4f + level * 0.6f)).coerceAtMost(0.6f)
        if (centre > 0.6f) {
            glowPath(path, tint.copy(alpha = alpha * 0.55f), (3f + bass * 5f) * density, (5f + bass * 7f) * density)
        }
        drawPath(path, tint.copy(alpha = alpha), style = Stroke(width = (1.1f + centre * 0.8f + level * 1.2f) * density))
    }
}

// ---- Reactive ------------------------------------------------------------------

/**
 * A glow rising from the bottom that swells with the bass, a hot core that
 * flashes and sends out rings on each kick, a smooth mirrored outline of the
 * spectrum with the bass in the middle, and sparks that rise faster and
 * brighter the louder it gets. With nothing playing, the glow just breathes.
 */
internal fun DrawScope.reactive(t: Float, color: Color, pulse: Pulse, motes: List<Mote>, place: Placement) {
    val w = size.width
    val h = size.height
    val windowWidth = max(place.width, w)
    val centreX = windowWidth / 2f - place.x
    val hot = lerp(color, Color.White, 0.4f)
    val breathe = 0.5f + 0.5f * sin(t * 0.6f)
    val bottom = Offset(centreX, h)

    val glowRadius = max(windowWidth, h) * (0.6f + pulse.bass * 0.35f)
    val glowCentre = Offset(centreX, h * 1.08f)
    softCircle(color, 0.22f + 0.07f * breathe + pulse.bass * 0.30f, glowCentre, glowRadius)

    val coreRadius = windowWidth * (0.22f + pulse.kick * 0.10f)
    softCircle(hot, 0.10f + pulse.kick * 0.35f, bottom, coreRadius)

    for (birth in pulse.rings) {
        val age = t - birth
        if (age < 0f || age > 1.6f) continue
        val progress = age / 1.6f
        val fade = (1f - progress) * (1f - progress)
        drawCircle(
            hot.copy(alpha = 0.5f * fade),
            windowWidth * (0.05f + progress * 0.5f),
            bottom,
            style = Stroke(width = (2.2f - 1.2f * progress) * density)
        )
    }

    val bands = pulse.levels.size
    val smoothed = FloatArray(bands) { i ->
        (pulse.levels[max(i - 1, 0)] + 2f * pulse.levels[i] + pulse.levels[min(i + 1, bands - 1)]) / 4f
    }
    val halfSpan = windowWidth / 2f
    val tallest = h * 0.28f
    val count = bands * 2 + 1
    val xs = FloatArray(count)
    val ys = FloatArray(count)
    for (i in 0 until count) {
        val fromCentre = i - bands
        val level = smoothed[abs(fromCentre).coerceAtMost(bands - 1)].coerceIn(0f, 1f)
        xs[i] = centreX + fromCentre.toFloat() / bands * halfSpan
        ys[i] = h - tallest * (0.03f + level.pow(0.8f) * 0.97f) * (1f - 0.35f * abs(fromCentre).toFloat() / bands)
    }
    val outline = Path().also { smoothThrough(it, xs, ys, moveFirst = true) }
    val fill = Path().apply {
        moveTo(xs[0], h)
        lineTo(xs[0], ys[0])
        smoothThrough(this, xs, ys, moveFirst = false)
        lineTo(xs[count - 1], h)
        close()
    }
    drawPath(
        fill,
        Brush.verticalGradient(
            listOf(color.copy(alpha = 0.0f), color.copy(alpha = 0.34f)),
            startY = h - tallest,
            endY = h
        )
    )
    glowPath(outline, hot.copy(alpha = 0.4f), 3f * density, 7f * density)
    drawPath(outline, hot.copy(alpha = 0.6f), style = Stroke(width = 1.5f * density))

    for (m in motes) {
        val xw = m.x * windowWidth - place.x
        if (xw < -10f || xw > w + 10f) continue
        val yFraction = wrap(m.y - pulse.travel * m.speed * 2f - t * 0.004f * m.speed)
        val fadeTop = (yFraction * 1.6f).coerceAtMost(1f)
        val alpha = ((0.08f + pulse.energy * 0.8f * m.size) * fadeTop).coerceAtMost(0.8f)
        val radius = (0.6f + m.size * 1.1f + pulse.kick * 1.2f) * density
        val centre = Offset(xw, yFraction * h)
        if (m.size > 0.7f) softCircle(hot, alpha * 0.35f, centre, radius * 4f)
        drawCircle(hot.copy(alpha = alpha), radius, centre)
    }
}

/** A smooth curve through the points: quadratic segments meeting at each midpoint. */
private fun smoothThrough(path: Path, xs: FloatArray, ys: FloatArray, moveFirst: Boolean) {
    if (moveFirst) path.moveTo(xs[0], ys[0])
    for (i in 1 until xs.size) {
        path.quadraticBezierTo(xs[i - 1], ys[i - 1], (xs[i - 1] + xs[i]) / 2f, (ys[i - 1] + ys[i]) / 2f)
    }
    path.lineTo(xs[xs.size - 1], ys[ys.size - 1])
}
