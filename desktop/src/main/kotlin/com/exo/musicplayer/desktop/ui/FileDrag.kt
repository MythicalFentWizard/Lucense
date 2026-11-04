package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragGestureEvent
import java.awt.dnd.DragGestureRecognizer
import java.awt.dnd.DragSource
import java.awt.dnd.DragSourceAdapter
import java.awt.dnd.DragSourceDropEvent
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.SwingUtilities

/**
 * Songs dragged out of Lucense as their files: onto the desktop or into a
 * folder, a chat, an editor. Always a copy - the library's own files never
 * move because of a drag.
 *
 * Started through AWT rather than Compose's dragAndDropSource: Compose 1.7.3
 * always asks Swing for a *move*, and Swing quietly drops a drag whose source
 * offers only a copy - so nothing happened at all. Offering a move as well
 * would let Explorer move the song out of the music folder.
 */
object DragOut {
    /** While one of Lucense's own drags is under way, so the window doesn't take it back as an import. */
    @Volatile
    var active: Boolean = false

    /** Starts the OS drag; replaced in a test, which can't let one start on a live desktop. */
    internal var start: (DragGestureEvent, Transferable) -> Unit = { gesture, files ->
        gesture.startDrag(DragSource.DefaultCopyDrop, files, object : DragSourceAdapter() {
            override fun dragDropEnd(event: DragSourceDropEvent) {
                active = false
            }
        })
    }

    /** Begins dragging [files] out of the window under the mouse, from where the mouse is now. */
    internal fun begin(files: List<File>) {
        val screen = MouseInfo.getPointerInfo()?.location ?: return
        // The mini player stays on top, so it comes first when the two overlap.
        val window = Window.getWindows().filter { it.isShowing && it.bounds.contains(screen) }
            .maxByOrNull { if (it.isAlwaysOnTop) 1 else 0 }
            ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            ?: Window.getWindows().firstOrNull { it.isShowing }
            ?: return
        val inWindow = Point(screen).also { SwingUtilities.convertPointFromScreen(it, window) }
        val component = SwingUtilities.getDeepestComponentAt(window, inWindow.x, inWindow.y) ?: window
        val at = SwingUtilities.convertPoint(window, inWindow, component)
        val press = MouseEvent(
            component, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
            at.x, at.y, 1, false, MouseEvent.BUTTON1
        )
        val gesture = DragGestureEvent(Gesture(component, press), DnDConstants.ACTION_COPY, at, listOf(press))
        active = true
        runCatching { start(gesture, FileList(files)) }.onFailure { active = false }
    }

    /** What AWT needs to have recognised a drag: the drag itself was recognised by Compose. */
    private class Gesture(component: Component, event: MouseEvent) :
        DragGestureRecognizer(DragSource.getDefaultDragSource(), component, DnDConstants.ACTION_COPY) {
        init {
            appendEvent(event)
        }

        override fun registerListeners() {}
        override fun unregisterListeners() {}
    }
}

/**
 * Makes this a place songs can be dragged out from. [files] is asked for the
 * moment a drag starts, so a row in a selection can hand over the whole
 * selection. A plain click still clicks: the drag begins only once the mouse
 * has moved a little with the button held.
 */
fun Modifier.dragOutFiles(files: () -> List<File>): Modifier = composed {
    // The same gesture from one frame to the next, so a place that redraws
    // all the time - the playing bar - doesn't drop a drag as it begins.
    val latest by rememberUpdatedState(files)
    pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                val list = latest().filter { it.isFile }
                if (list.isNotEmpty()) DragOut.begin(list)
            },
            onDrag = { change, _ -> change.consume() }
        )
    }
}

/** Files, as Explorer and every other drop target on Windows and Linux reads them. */
internal class FileList(private val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return files
    }
}
