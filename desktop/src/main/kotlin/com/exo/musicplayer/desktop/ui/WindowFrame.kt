package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import java.awt.EventQueue
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.Frame
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Lucense's own window frame, in place of Windows' title bar and border.
 *
 * The window is undecorated, so what Windows used to draw is drawn here: a
 * strip along the top to drag it by, and the minimise, maximise and close
 * buttons. On Windows the moving, sizing, maximising and snapping are then
 * handed back to Windows itself ([NativeFrame]), which knows where the strip
 * and the maximise button are from [dragArea] and [maximizeButton]. Only if
 * that can't be set up does the strip move the window by hand, and maximise
 * to the screen's working area.
 */
class WindowFrame(val window: Window, private val onClose: () -> Unit) {

    var maximized by mutableStateOf(false)
        private set

    /** Windows itself moves, sizes and snaps the window ([NativeFrame]). */
    var native by mutableStateOf(false)
        private set

    /** The strip to drag the window by, in the window's pixels from its top left corner. Read on Windows' own thread. */
    @Volatile
    var dragArea: Rectangle? = null

    /** The maximise button, likewise: Windows 11 shows its snap layouts over it. */
    @Volatile
    var maximizeButton: Rectangle? = null

    /**
     * The pointer is over the maximise button, or pressing it. While [native]
     * the button is Windows' (for the snap layouts), so Windows says.
     */
    var maximizeHovered by mutableStateOf(false)
        private set
    var maximizePressed by mutableStateOf(false)
        private set

    private val hovering = AtomicBoolean(false)
    private val pressing = AtomicBoolean(false)
    private val zoomed = AtomicBoolean(false)

    private var restore: Rectangle? = null
    private var nativeMinimize: (() -> Unit)? = null
    private var nativeToggle: (() -> Unit)? = null

    fun minimize() {
        nativeMinimize?.let { return it() }
        (window as? Frame)?.extendedState = Frame.ICONIFIED
    }

    fun close() = onClose()

    fun toggleMaximize() {
        nativeToggle?.let { return it() }
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

    internal fun attachNative(minimize: () -> Unit, toggleMaximize: () -> Unit, maximized: Boolean) {
        nativeMinimize = minimize
        nativeToggle = toggleMaximize
        zoomed.set(maximized)
        this.maximized = maximized
        native = true
    }

    /** From Windows' thread, on every size it takes: passed on only when maximised or not changes. */
    internal fun nativeSized(maximized: Boolean) {
        if (zoomed.getAndSet(maximized) != maximized) EventQueue.invokeLater { this.maximized = maximized }
    }

    internal fun hoverMaximize(on: Boolean) {
        if (hovering.getAndSet(on) != on) EventQueue.invokeLater { maximizeHovered = on }
    }

    internal fun pressMaximize(on: Boolean) {
        if (pressing.getAndSet(on) != on) EventQueue.invokeLater { maximizePressed = on }
    }

    /** Dragged while maximised, by hand: back to its own size, under the pointer, as Windows does. */
    fun unmaximizeUnder(pointer: Point) {
        if (!maximized) return
        val size = restore ?: return
        val across = (pointer.x - window.x).toDouble() / window.width.coerceAtLeast(1)
        // Kept on the screen: grabbed near the very top, it would otherwise start above it.
        val top = (pointer.y - 16).coerceAtLeast(window.graphicsConfiguration.bounds.y)
        window.bounds = Rectangle(pointer.x - (size.width * across).toInt(), top, size.width, size.height)
        maximized = false
        WindowCorners.round(window, true)
    }

    private fun workingArea(config: java.awt.GraphicsConfiguration = window.graphicsConfiguration): Rectangle {
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
                .then(
                    if (frame != null) {
                        // From the window's left edge: with glass the strip starts a gap in,
                        // and that gap is the top of the window too.
                        Modifier.onGloballyPositioned { at ->
                            val strip = at.pixelBounds()
                            frame.dragArea = Rectangle(0, strip.y, strip.x + strip.width, strip.height)
                        }.dragsWindow(frame)
                    } else {
                        Modifier
                    }
                )
        )
        CaptionButton(Icons.Default.Remove, "Minimise") { frame?.minimize() }
        CaptionButton(
            if (frame?.maximized == true) Icons.Default.FilterNone else Icons.Default.CropSquare,
            if (frame?.maximized == true) "Restore" else "Maximise",
            modifier = if (frame != null) Modifier.onGloballyPositioned { frame.maximizeButton = it.pixelBounds() } else Modifier,
            // Windows' own while it handles the frame, for its snap layouts: it says when the pointer is over it.
            hoveredByWindows = frame?.takeIf { it.native }?.let { it.maximizeHovered || it.maximizePressed }
        ) { frame?.toggleMaximize() }
        CaptionButton(Icons.Default.Close, "Close", danger = true) { frame?.close() }
    }
}

/** Where a piece of the layout is in the window, in the window's pixels. */
private fun LayoutCoordinates.pixelBounds(): Rectangle {
    val bounds = boundsInWindow()
    return Rectangle(
        bounds.left.roundToInt(),
        bounds.top.roundToInt(),
        bounds.width.roundToInt(),
        bounds.height.roundToInt()
    )
}

@Composable
private fun CaptionButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    hoveredByWindows: Boolean? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hoveredHere by interaction.collectIsHoveredAsState()
    val hovered = hoveredByWindows ?: hoveredHere
    Box(
        modifier
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
 * Moves the window with the pointer, when Windows isn't doing it: by the
 * screen's own coordinates rather than the drawing's, which are scaled. A
 * second press soon after the first maximises instead.
 *
 * While Windows handles the frame a press never arrives here - Windows takes
 * it as one on its own title bar - unless Skia's window was replaced and not
 * yet taught that; then Windows is asked to start the move itself.
 */
private fun Modifier.dragsWindow(frame: WindowFrame): Modifier = pointerInput(frame) {
    var lastPress = 0L
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        if (frame.native) {
            NativeFrame.startMove(frame.window)
            return@awaitEachGesture
        }
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
