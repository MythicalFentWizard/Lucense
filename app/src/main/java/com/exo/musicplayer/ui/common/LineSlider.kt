package com.exo.musicplayer.ui.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A slider drawn the way the Windows app draws its own: a thin line, the set
 * part in the accent, and a round handle that grows while it's held. No tick
 * dots and no gap in the line, which Material's newer slider insists on.
 *
 * Tap anywhere on it to jump there, or drag sideways. A vertical drag is left
 * alone, so it still scrolls the page it sits in.
 *
 * @param from where the coloured part starts: the low end by default, or a
 *   value such as 0 for a control that goes both ways from a middle, like
 *   pitch or an equalizer band.
 * @param steps how many stops between the ends, as Material counts them; the
 *   value snaps to them, but they aren't drawn.
 */
@Composable
fun LineSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    from: Float? = null,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    description: String? = null
) {
    val accent = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    val mark = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    var held by remember { mutableStateOf(false) }
    var width by remember { mutableFloatStateOf(1f) }
    var dragX by remember { mutableFloatStateOf(0f) }
    val handle by animateDpAsState(if (held) 9.dp else 6.dp, label = "handle")
    // The line stops short of the ends by the held handle's size, so the
    // handle is never cut off at either end.
    val inset = with(LocalDensity.current) { 9.dp.toPx() }
    val span = valueRange.endInclusive - valueRange.start

    fun snap(v: Float): Float {
        val f = ((v - valueRange.start) / span).coerceIn(0f, 1f)
        val stops = steps + 1
        val snapped = if (steps > 0) (f * stops).roundToInt() / stops.toFloat() else f
        return valueRange.start + snapped * span
    }
    fun at(x: Float) = snap(valueRange.start + ((x - inset) / (width - 2 * inset)) * span)

    val drag = rememberDraggableState { delta ->
        dragX += delta
        change(at(dragX))
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(2 * inset + 1f) }
            .semantics {
                description?.let { contentDescription = it }
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                if (enabled) {
                    setProgress { target ->
                        change(snap(target))
                        finished?.invoke()
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        held = true
                        tryAwaitRelease()
                        held = false
                    },
                    onTap = { position ->
                        change(at(position.x))
                        finished?.invoke()
                    }
                )
            }
            .draggable(
                state = drag,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStarted = { position ->
                    held = true
                    dragX = position.x
                    change(at(position.x))
                },
                onDragStopped = {
                    held = false
                    finished?.invoke()
                }
            )
    ) {
        val y = size.height / 2f
        val stroke = 4.dp.toPx()
        val left = inset
        val right = size.width - inset
        fun xOf(v: Float) = left + (right - left) * ((v - valueRange.start) / span).coerceIn(0f, 1f)
        val x = xOf(value)
        val start = xOf(from ?: valueRange.start)
        drawLine(rest, Offset(left, y), Offset(right, y), stroke, StrokeCap.Round)
        if (from != null) {
            // The middle a two-way control returns to.
            drawLine(mark, Offset(start, y - 7.dp.toPx()), Offset(start, y + 7.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
        }
        if (kotlin.math.abs(x - start) > 0.5f) {
            drawLine(accent.copy(alpha = if (enabled) 1f else 0.4f), Offset(start, y), Offset(x, y), stroke, StrokeCap.Round)
        }
        if (enabled) drawCircle(accent, handle.toPx(), Offset(x, y))
    }
}
