package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.Frame

/**
 * Lucense's own window frame, in place of Windows' title bar and border.
 *
 * The window is undecorated, so everything Windows used to draw is drawn
 * here: a strip along the top to drag it by (double-click maximises), the
 * minimise, maximise and close buttons, and edges to resize it from. Windows
 * 11 rounds its corners, asked to through DWM; on Windows 10 they stay square.
 *
 * Maximising an undecorated window the usual way covers the taskbar, so it is
 * done by hand: the window takes the screen's working area, and gives it back
 * on the second press, or when it is dragged away from the top.
 */
class WindowFrame(val window: Window, private val onClose: () -> Unit) {

    var maximized by mutableStateOf(false)
        private set

    private var restore: Rectangle? = null

    fun minimize() {
        (window as? Frame)?.extendedState = Frame.ICONIFIED
    }

    fun close() = onClose()

    fun toggleMaximize() {
        if (maximized) {
            restore?.let { window.bounds = it }
            maximized = false
            WindowCorners.round(window, true)
        } else {
            restore = window.bounds
            window.bounds = workingArea()
            maximized = true
            WindowCorners.round(window, false)
        }
    }

    /** Dragged off the top while maximised: back to its own size, under the pointer. */
    fun unmaximizeUnder(pointer: Point) {
        if (!maximized) return
        val size = restore ?: return
        val across = (pointer.x - window.x).toDouble() / window.width.coerceAtLeast(1)
        window.bounds = Rectangle(pointer.x - (size.width * across).toInt(), pointer.y - 16, size.width, size.height)
        maximized = false
        WindowCorners.round(window, true)
    }

    private fun workingArea(): Rectangle {
        val config = window.graphicsConfiguration
        val screen = config.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)
        return Rectangle(
            screen.x + insets.left,
            screen.y + insets.top,
            screen.width - insets.left - insets.right,
            screen.height - insets.top - insets.bottom
        )
    }
}

/** The frame of the window being drawn in, or null where there isn't one (a picture drawn offscreen). */
val LocalWindowFrame = staticCompositionLocalOf<WindowFrame?> { null }

/** Height of the strip along the top. */
val TITLE_BAR = 34.dp

/**
 * The strip along the top: drag it to move the window, double-click it to
 * maximise, and the three buttons at the right. Draws nothing of its own
 * beneath, so it sits over whatever the window shows.
 */
@Composable
fun WindowTitleBar(modifier: Modifier = Modifier, background: Color = Color.Transparent) {
    val frame = LocalWindowFrame.current
    Row(
        modifier.fillMaxWidth().height(TITLE_BAR).background(background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(if (frame != null) Modifier.dragsWindow(frame) else Modifier)
        )
        CaptionButton(Icons.Default.Remove, "Minimise") { frame?.minimize() }
        CaptionButton(
            if (frame?.maximized == true) Icons.Default.FilterNone else Icons.Default.CropSquare,
            if (frame?.maximized == true) "Restore" else "Maximise"
        ) { frame?.toggleMaximize() }
        CaptionButton(Icons.Default.Close, "Close", danger = true) { frame?.close() }
    }
}

@Composable
private fun CaptionButton(icon: ImageVector, description: String, danger: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier
            .width(46.dp)
            .fillMaxHeight()
            .background(
                when {
                    hovered && danger -> Color(0xFFE81123)
                    hovered -> Palette.Text.copy(alpha = 0.09f)
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val up = waitForUpOrCancellation()
                    if (up != null) onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            description,
            Modifier.size(if (icon == Icons.Default.Remove) 16.dp else 14.dp),
            tint = if (hovered && danger) Color.White else Palette.TextDim
        )
    }
}

/**
 * Moves the window with the pointer, by the screen's own coordinates rather
 * than the drawing's, which are scaled. A second press soon after the first
 * maximises instead.
 */
private fun Modifier.dragsWindow(frame: WindowFrame): Modifier = pointerInput(frame) {
    var lastPress = 0L
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val now = System.currentTimeMillis()
        if (now - lastPress < 400) {
            lastPress = 0L
            frame.toggleMaximize()
            return@awaitEachGesture
        }
        lastPress = now
        val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
        var from = frame.window.location
        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.none { it.pressed }) break
            val at = MouseInfo.getPointerInfo()?.location ?: break
            if (!moved && (kotlin.math.abs(at.x - start.x) > 3 || kotlin.math.abs(at.y - start.y) > 3)) {
                moved = true
                if (frame.maximized) {
                    frame.unmaximizeUnder(at)
                    from = frame.window.location
                    start.setLocation(at)
                }
            }
            if (moved) frame.window.setLocation(from.x + at.x - start.x, from.y + at.y - start.y)
            event.changes.forEach { it.consume() }
        }
    }
}

/**
 * The edges to resize the window from, laid over everything: a few pixels
 * along each side and a little more at each corner, with the matching
 * pointer. Gone while maximised.
 */
@Composable
fun BoxScope.ResizeEdges(thickness: Dp = 5.dp) {
    val frame = LocalWindowFrame.current ?: return
    if (frame.maximized) return
    val corner = thickness * 3
    // Left, right, top, bottom, then the four corners on top of them.
    Edge(frame, Modifier.align(Alignment.CenterStart).width(thickness).fillMaxHeight(), Cursor.W_RESIZE_CURSOR, left = true)
    Edge(frame, Modifier.align(Alignment.CenterEnd).width(thickness).fillMaxHeight(), Cursor.E_RESIZE_CURSOR, right = true)
    Edge(frame, Modifier.align(Alignment.TopCenter).height(thickness).fillMaxWidth(), Cursor.N_RESIZE_CURSOR, top = true)
    Edge(frame, Modifier.align(Alignment.BottomCenter).height(thickness).fillMaxWidth(), Cursor.S_RESIZE_CURSOR, bottom = true)
    Edge(frame, Modifier.align(Alignment.TopStart).size(corner), Cursor.NW_RESIZE_CURSOR, left = true, top = true)
    Edge(frame, Modifier.align(Alignment.TopEnd).size(corner), Cursor.NE_RESIZE_CURSOR, right = true, top = true)
    Edge(frame, Modifier.align(Alignment.BottomStart).size(corner), Cursor.SW_RESIZE_CURSOR, left = true, bottom = true)
    Edge(frame, Modifier.align(Alignment.BottomEnd).size(corner), Cursor.SE_RESIZE_CURSOR, right = true, bottom = true)
}

@Composable
private fun Edge(
    frame: WindowFrame,
    modifier: Modifier,
    cursor: Int,
    left: Boolean = false,
    right: Boolean = false,
    top: Boolean = false,
    bottom: Boolean = false
) {
    Spacer(
        modifier
            .pointerHoverIcon(PointerIcon(Cursor(cursor)))
            .pointerInput(frame) {
                awaitEachGesture {
                    awaitFirstDown()
                    val start = MouseInfo.getPointerInfo()?.location ?: return@awaitEachGesture
                    val from = frame.window.bounds
                    val least = frame.window.minimumSize
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                        val at = MouseInfo.getPointerInfo()?.location ?: break
                        val dx = at.x - start.x
                        val dy = at.y - start.y
                        var x = from.x
                        var y = from.y
                        var w = from.width
                        var h = from.height
                        if (right) w = (from.width + dx).coerceAtLeast(least.width)
                        if (bottom) h = (from.height + dy).coerceAtLeast(least.height)
                        if (left) {
                            w = (from.width - dx).coerceAtLeast(least.width)
                            x = from.x + from.width - w
                        }
                        if (top) {
                            h = (from.height - dy).coerceAtLeast(least.height)
                            y = from.y + from.height - h
                        }
                        frame.window.setBounds(x, y, w, h)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    )
}

/**
 * Asks Windows 11 to round the window's corners, which it does for windows
 * without its own frame only when asked. Windows 10 doesn't know the
 * attribute and leaves them square; nothing else changes.
 */
object WindowCorners {

    private interface Dwm : Library {
        fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: IntByReference, size: Int): Int
    }

    private val dwm: Dwm? by lazy {
        runCatching { Native.load("dwmapi", Dwm::class.java) }.getOrNull()
    }

    private const val CORNER_PREFERENCE = 33
    private const val ROUND = 2
    private const val SQUARE = 1

    /** True when Windows took it. */
    fun round(window: Window, rounded: Boolean = true): Boolean {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return false
        return runCatching {
            val hwnd = WinDef.HWND(Native.getComponentPointer(window))
            dwm?.DwmSetWindowAttribute(hwnd, CORNER_PREFERENCE, IntByReference(if (rounded) ROUND else SQUARE), 4) == 0
        }.getOrDefault(false)
    }
}
