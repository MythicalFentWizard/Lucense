package com.exo.musicplayer.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Arc gauge for pitch shift.
 *
 * Shows semitones rather than a raw multiplier because semitones are the unit
 * that means something musically: +12 is an octave, and the tick marks let you
 * land on a whole number by eye. The frequency ratio is shown underneath for
 * anyone who wants it.
 */
@Composable
fun PitchMeter(
    semitones: Float,
    modifier: Modifier = Modifier,
    minSemitones: Float = -12f,
    maxSemitones: Float = 12f
) {
    val animated by animateFloatAsState(
        targetValue = semitones,
        animationSpec = tween(220),
        label = "pitch"
    )
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(118.dp)
            ) {
                val sweep = 220f
                val startAngle = 90f + (360f - sweep) / 2f
                val stroke = 12.dp.toPx()
                val inset = stroke / 2 + 14.dp.toPx()
                val diameter = minOf(size.width, size.height * 2f) - inset * 2
                val topLeft = Offset((size.width - diameter) / 2f, inset)
                val arcSize = Size(diameter, diameter)

                drawArc(
                    color = track,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )

                // Fill outward from the centre, so "no shift" reads as neutral
                // and any change is visible as a wing to one side.
                val fraction = ((animated - minSemitones) / (maxSemitones - minSemitones))
                    .coerceIn(0f, 1f)
                val centreFraction = ((0f - minSemitones) / (maxSemitones - minSemitones))
                val from = minOf(fraction, centreFraction)
                val to = maxOf(fraction, centreFraction)
                if (to - from > 0.001f) {
                    drawArc(
                        color = primary,
                        startAngle = startAngle + sweep * from,
                        sweepAngle = sweep * (to - from),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }

                // Ticks every 3 semitones, brighter at the octaves.
                val centre = Offset(size.width / 2f, inset + diameter / 2f)
                val radius = diameter / 2f
                var st = minSemitones
                while (st <= maxSemitones) {
                    val f = (st - minSemitones) / (maxSemitones - minSemitones)
                    val angle = Math.toRadians((startAngle + sweep * f).toDouble())
                    val major = abs(st) % 12f == 0f
                    val inner = radius - stroke / 2 - (if (major) 10.dp.toPx() else 6.dp.toPx())
                    val outer = radius - stroke / 2 - 2.dp.toPx()
                    drawLine(
                        color = if (major) onSurfaceVariant else onSurfaceVariant.copy(alpha = 0.45f),
                        start = Offset(
                            centre.x + cos(angle).toFloat() * inner,
                            centre.y + sin(angle).toFloat() * inner
                        ),
                        end = Offset(
                            centre.x + cos(angle).toFloat() * outer,
                            centre.y + sin(angle).toFloat() * outer
                        ),
                        strokeWidth = if (major) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    st += 3f
                }

                // Needle.
                val needleAngle = Math.toRadians(
                    (startAngle + sweep * fraction).toDouble()
                )
                val needleEnd = Offset(
                    centre.x + cos(needleAngle).toFloat() * (radius - stroke - 4.dp.toPx()),
                    centre.y + sin(needleAngle).toFloat() * (radius - stroke - 4.dp.toPx())
                )
                drawLine(
                    color = primary,
                    start = centre,
                    end = needleEnd,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(color = primary, radius = 5.dp.toPx(), center = centre)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.size(width = 140.dp, height = 70.dp)
            ) {
                Spacer(Modifier.height(30.dp))
                Text(
                    text = formatSemitones(animated),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (abs(animated) < 0.05f) onSurfaceVariant else primary
                )
                Text(
                    text = "×%.3f".format(2f.pow(animated / 12f)),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )
            }
        }
    }
}

private fun formatSemitones(value: Float): String {
    val rounded = (value * 10).roundToInt() / 10f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    return when {
        rounded > 0f -> "+$text st"
        else -> "$text st"
    }
}
