package com.exo.musicplayer.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.delay

/**
 * The small pieces every screen shares.
 *
 * Hand-built rather than Material's buttons and text fields: Material's desktop
 * defaults are sized for touch, and mixing 56dp-tall inputs into a dense desktop
 * layout is exactly what makes a port look like a port.
 */

/**
 * Buttons and text fields share one height, so a button beside a field lines up
 * with it instead of sitting a few pixels short.
 */
private val CONTROL_HEIGHT = 34.dp

@Composable
fun AccentButton(
    label: String,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        !enabled -> Palette.Hover
        hovered -> Palette.Accent
        else -> Palette.Button
    }
    Row(
        Modifier
            .heightIn(min = CONTROL_HEIGHT)
            .clip(RoundedCornerShape(7.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = when {
            !enabled -> Palette.TextFaint
            hovered -> Palette.OnAccent
            else -> Palette.OnButton
        }
        if (icon != null) {
            Icon(icon, null, Modifier.size(15.dp), tint = tint)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}

@Composable
fun GhostButton(
    label: String,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .heightIn(min = CONTROL_HEIGHT)
            .clip(RoundedCornerShape(7.dp))
            .background(if (hovered && enabled) Palette.Hover else Color.Transparent)
            .border(1.dp, Palette.Line, RoundedCornerShape(7.dp))
            .hoverable(interaction)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (enabled) Palette.TextDim else Palette.TextFaint
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = tint)
            Spacer(Modifier.width(7.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            // A squeezed Row would otherwise break this one character per line.
            maxLines = 1,
            softWrap = false
        )
    }
}

/** A bordered panel — the desktop equivalent of the phone's cards. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Palette.Raised)
            .border(1.dp, Palette.Line, RoundedCornerShape(10.dp))
            .padding(18.dp),
        content = content
    )
}

@Composable
fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Text,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextDim,
        modifier = modifier
    )
}

/** Single-line input styled for a dense layout. */
/**
 * Whether a text box currently holds the keyboard.
 *
 * The window claims a few keys before anything else sees them, which is right
 * for Ctrl+A over the song list and wrong the moment someone is typing in a
 * box: there, Ctrl+A means the text in it. The window stands back while this
 * is set, which also hands back Ctrl+C, Ctrl+V, Ctrl+X, Ctrl+Z, Home, End and
 * shift-selection, all of which the text field has always known how to do.
 */
object Typing {
    var active by mutableStateOf(false)
}

@Composable
fun TextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    singleLine: Boolean = true,
    onSubmit: (() -> Unit)? = null
) {
    Row(
        modifier
            .heightIn(min = CONTROL_HEIGHT)
            .clip(RoundedCornerShape(7.dp))
            .background(Palette.Content)
            .border(1.dp, Palette.Line, RoundedCornerShape(7.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Icon(leading, null, Modifier.size(15.dp), tint = Palette.TextFaint)
            Spacer(Modifier.width(8.dp))
        }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextFaint
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.Text),
                cursorBrush = SolidColor(Palette.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { Typing.active = it.isFocused }
                    .then(
                        if (onSubmit == null) {
                            Modifier
                        } else {
                            Modifier.onEnter(onSubmit)
                        }
                    )
            )
        }
    }
}

/** Enter submits — expected on desktop, and absent from BasicTextField. */
private fun Modifier.onEnter(action: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown &&
        (event.key == Key.Enter || event.key == Key.NumPadEnter)
    ) {
        action()
        true
    } else {
        false
    }
}

@Composable
fun CheckRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    note: String? = null,
    onToggle: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onToggle(!checked) }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(15.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) Palette.Accent else Color.Transparent)
                .border(
                    1.5.dp,
                    // The line colour disappeared into the panel; an empty box has
                    // to be seen to be clicked.
                    if (checked) Palette.Accent else Palette.TextFaint,
                    RoundedCornerShape(4.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Default.Check, null, Modifier.size(11.dp), tint = Palette.OnAccent)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) Palette.Text else Palette.TextFaint
            )
            if (note != null) {
                Text(note, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            }
        }
    }
}

/** Segmented row of choices — the desktop idiom for a small enum. */
@Composable
fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Palette.Content)
            .border(1.dp, Palette.Line, RoundedCornerShape(7.dp))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isSelected) Palette.Selected else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Palette.Text else Palette.TextDim
                )
            }
        }
    }
}

@Composable
fun ThinProgress(progress: Float?, modifier: Modifier = Modifier) {
    if (progress == null) {
        LinearProgressIndicator(
            modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
            color = Palette.Accent,
            trackColor = Palette.Line
        )
    } else {
        Box(
            modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Palette.Line)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Palette.Accent)
            )
        }
    }
}

/** Centred "nothing here yet" block, used by several screens. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, Modifier.size(40.dp), tint = Palette.TextFaint)
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextDim,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/**
 * A thin line with a ball on it: the seek bar and the volume.
 *
 * Drawn rather than laid out. The seek bar before this put its ball inside a
 * box as wide as the played part of the song, so at the very start that box
 * was nothing wide and squeezed the ball down to a sliver, and the ball's layer
 * was inset where the line was not, so at the end the line carried on past it.
 * Here the line and the ball come from the same two numbers - where the travel
 * starts and where it ends - so the ball sits exactly on the end of the played
 * part all the way from nothing to everything.
 *
 * The travel is inset by the ball's largest radius, so the ball never leaves
 * the control however far it goes, and the pointer is mapped through the same
 * inset, so what is under the mouse is what gets chosen.
 *
 * A press is followed wherever the mouse goes until the button comes up, well
 * off the control included, and meanwhile the line shows the pointer rather
 * than the thing it controls. After letting go it holds the chosen value until
 * that thing has caught up - a seek takes the playback thread a moment - rather
 * than showing the old value for a frame and then the new one, which is what
 * read as the bar bugging in and out.
 */
@Composable
fun LineSlider(
    value: Float,
    onSet: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    live: Boolean = false,
    wheelStep: Float = 0f,
    restColor: Color = Palette.Accent,
    activeColor: Color = restColor,
    onPreview: (Float?) -> Unit = {}
) {
    // The gesture and wheel handlers are started once and outlive any single
    // composition, so they read these rather than whatever was current when
    // they began. Without it the wheel steps from a stale value every time.
    val latestValue by rememberUpdatedState(value)
    val latestSet by rememberUpdatedState(onSet)
    val latestPreview by rememberUpdatedState(onPreview)

    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf<Float?>(null) }
    var holding by remember { mutableStateOf<Float?>(null) }

    // Let go of the held value once the real one arrives - or after a moment
    // regardless, because a seek clamped short of the end never lands exactly
    // where it was dropped, and the line must not stay pinned there for ever.
    LaunchedEffect(value, holding) {
        val held = holding ?: return@LaunchedEffect
        if (abs(value - held) < SLIDER_CATCH_UP) holding = null
    }
    LaunchedEffect(holding) {
        if (holding != null) {
            delay(SLIDER_HOLD_MS)
            holding = null
        }
    }
    val preview = dragging ?: holding
    LaunchedEffect(preview) { latestPreview(preview) }

    val shown = (preview ?: value).coerceIn(0f, 1f)
    val engaged = enabled && (hovered || dragging != null)
    // Eased rather than switched, so crossing the edge of the control does not
    // make the ball flick between two sizes.
    val emphasis by animateFloatAsState(if (engaged) 1f else 0f, tween(140))
    val colour = lerp(restColor, activeColor, emphasis)
    val line = Palette.Line

    Box(
        modifier
            .height(20.dp)
            .hoverable(interaction, enabled)
            .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            .pointerInput(enabled, live) {
                if (!enabled) return@pointerInput
                val inset = SLIDER_INSET.toPx()
                fun at(x: Float): Float =
                    ((x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val buttons = currentEvent.buttons
                    if (buttons.isSecondaryPressed && !buttons.isPrimaryPressed) return@awaitEachGesture
                    down.consume()
                    var last = at(down.position.x)
                    dragging = last
                    if (live) latestSet(last)
                    var released = false
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                released = true
                                break
                            }
                            val next = at(change.position.x)
                            if (next != last) {
                                last = next
                                dragging = next
                                if (live) latestSet(next)
                            }
                            change.consume()
                        }
                    } finally {
                        // A cancelled gesture sets nothing; only a real release
                        // commits, and a click with no movement is a release too.
                        if (released) {
                            holding = last
                            latestSet(last)
                        }
                        dragging = null
                    }
                }
            }
            .pointerInput(enabled, wheelStep) {
                if (!enabled || wheelStep <= 0f) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val dy = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                        if (dy == 0f) continue
                        latestSet((latestValue - sign(dy) * wheelStep).coerceIn(0f, 1f))
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = SLIDER_INSET.toPx()
            val thickness = SLIDER_TRACK.toPx()
            val y = size.height / 2f
            val start = inset
            val end = size.width - inset
            val x = start + shown * (end - start)
            drawLine(line, Offset(start, y), Offset(end, y), thickness, StrokeCap.Round)
            if (!enabled) return@Canvas
            drawLine(colour, Offset(start, y), Offset(x, y), thickness, StrokeCap.Round)
            val rest = SLIDER_REST_RADIUS.toPx()
            val radius = rest + (inset - rest) * emphasis
            if (emphasis > 0f) {
                drawCircle(colour.copy(alpha = 0.16f * emphasis), radius + 4.dp.toPx() * emphasis, Offset(x, y))
            }
            drawCircle(colour, radius, Offset(x, y))
        }
    }
}

/** The largest the ball gets, and so how far the travel is kept from the edges. */
private val SLIDER_INSET = 6.dp
private val SLIDER_TRACK = 4.dp
private val SLIDER_REST_RADIUS = 4.5.dp
private const val SLIDER_CATCH_UP = 0.025f
private const val SLIDER_HOLD_MS = 900L
