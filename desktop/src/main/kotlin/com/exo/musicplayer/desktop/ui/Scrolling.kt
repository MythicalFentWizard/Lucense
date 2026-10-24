package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.awt.Cursor
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * A long list as Windows does it: a scrollbar along the right that can be
 * dragged, and middle-click scrolling as in a browser - press the wheel, move
 * the mouse up or down, and the list runs that way, faster the further the
 * mouse goes from where it was pressed. Pressing again, or any other button,
 * stops it; held and dragged, it stops when the wheel is let go.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScrollingList(state: LazyListState, modifier: Modifier = Modifier, content: @Composable BoxScope.(Modifier) -> Unit) {
    var anchor by remember { mutableStateOf<Offset?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }

    // While anchored, the list moves every frame by how far the pointer is from the anchor.
    LaunchedEffect(anchor) {
        val from = anchor ?: return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1e9f
            last = now
            val distance = pointer.y - from.y
            val beyond = abs(distance) - DEAD_ZONE
            if (beyond > 0f) {
                val speed = (beyond * PIXELS_PER_SECOND_PER_PIXEL).coerceAtMost(MAX_SPEED) * sign(distance)
                state.scrollBy(speed * seconds)
            }
        }
    }

    Box(
        modifier
            .pointerInput(Unit) {
                var pressedAt = 0L
                var travelled = false
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val position = event.changes.firstOrNull()?.position ?: continue
                        when (event.type) {
                            PointerEventType.Press -> {
                                if (event.button == PointerButton.Tertiary) {
                                    if (anchor == null) {
                                        anchor = position
                                        pointer = position
                                        pressedAt = System.currentTimeMillis()
                                        travelled = false
                                    } else {
                                        anchor = null
                                    }
                                    event.changes.forEach { it.consume() }
                                } else if (anchor != null) {
                                    // Any other click just stops it, without clicking what's under it.
                                    anchor = null
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            PointerEventType.Move -> {
                                pointer = position
                                anchor?.let { if (abs(position.y - it.y) > DEAD_ZONE) travelled = true }
                            }
                            PointerEventType.Release -> {
                                // Held and dragged rather than clicked: letting go stops it.
                                if (event.button == PointerButton.Tertiary && anchor != null && travelled &&
                                    System.currentTimeMillis() - pressedAt > 250
                                ) {
                                    anchor = null
                                }
                            }
                            PointerEventType.Scroll -> anchor = null
                        }
                    }
                }
            }
            .then(if (anchor != null) Modifier.pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR))) else Modifier)
    ) {
        content(Modifier.fillMaxSize())
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            style = listScrollbarStyle(),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 3.dp)
        )
        anchor?.let { at -> AnchorMark(at) }
    }
}

/** The mark left where the wheel was pressed: a ring with an arrow up and an arrow down. */
@Composable
private fun AnchorMark(at: Offset) {
    val size = 30.dp
    Canvas(
        Modifier
            .offset { IntOffset((at.x - size.toPx() / 2).roundToInt(), (at.y - size.toPx() / 2).roundToInt()) }
            .size(size)
    ) {
        val r = this.size.minDimension / 2
        drawCircle(Palette.Raised.copy(alpha = 0.9f), r)
        drawCircle(Palette.TextDim, r - 1f, style = Stroke(1.5f))
        drawCircle(Palette.Text, 2.5f)
        fun arrow(up: Boolean) {
            val cx = this.size.width / 2
            val tip = if (up) r * 0.28f else this.size.height - r * 0.28f
            val base = if (up) tip + r * 0.36f else tip - r * 0.36f
            drawPath(
                Path().apply {
                    moveTo(cx, tip)
                    lineTo(cx - r * 0.3f, base)
                    lineTo(cx + r * 0.3f, base)
                    close()
                },
                Palette.Text
            )
        }
        arrow(up = true)
        arrow(up = false)
    }
}

/** A thin bar in the theme's colours, thicker and brighter under the mouse. */
@Composable
fun listScrollbarStyle(): ScrollbarStyle = ScrollbarStyle(
    minimalHeight = 32.dp,
    thickness = 8.dp,
    shape = RoundedCornerShape(4.dp),
    hoverDurationMillis = 250,
    unhoverColor = Palette.TextFaint.copy(alpha = 0.35f),
    hoverColor = Palette.TextDim.copy(alpha = 0.75f)
)

/** Within this many pixels of the anchor the list holds still. */
private const val DEAD_ZONE = 10f

/** Each pixel further from the anchor adds this much speed. */
private const val PIXELS_PER_SECOND_PER_PIXEL = 11f

private const val MAX_SPEED = 14_000f
