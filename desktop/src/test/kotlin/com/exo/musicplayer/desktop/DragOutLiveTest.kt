package com.exo.musicplayer.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import com.exo.musicplayer.desktop.ui.DragOut
import com.exo.musicplayer.desktop.ui.dragOutFiles
import androidx.compose.foundation.onClick
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragGestureEvent
import kotlin.test.assertTrue

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Container
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.io.File
import java.nio.file.Files
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.TransferHandler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A drag out of a real Compose window, up to the moment AWT would hand it to
 * Windows: DragOut.start is swapped for one that records what would be
 * dragged, so no OS drag starts and the mouse is never touched. Off-screen; only with LUCENSE_DND set.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
class DragOutLiveTest {

    @Test
    fun aRealWindowHandsTheFileToSwing() = runBlocking {
        if (System.getenv("LUCENSE_DND") == null) return@runBlocking
        val file = Files.createTempFile("lucense-drag", ".mp3").toFile().also { it.deleteOnExit() }
        var dragged: Transferable? = null
        var gesture: DragGestureEvent? = null
        var clicks = 0
        val real = DragOut.start
        DragOut.start = { g, t -> gesture = g; dragged = t }
        withContext(Edt) {
            val window = ComposeWindow()
            try {
                window.isUndecorated = true
                window.focusableWindowState = false
                window.setBounds(-3000, -3000, 400, 200)
                // The library row's gestures, in its order: a drag out, and three kinds of click.
                window.setContent {
                    Box(
                        Modifier.fillMaxSize()
                            .dragOutFiles { listOf(file) }
                            .onClick(keyboardModifiers = { isCtrlPressed }) {}
                            .onClick(keyboardModifiers = { isShiftPressed && !isCtrlPressed }) {}
                            .onClick(keyboardModifiers = { !isCtrlPressed && !isShiftPressed }) { clicks++ }
                    )
                }
                window.isVisible = true
                delay(1500)

                val target = SwingUtilities.getDeepestComponentAt(window.contentPane, 200, 100)
                    ?: window.contentPane
                fun send(id: Int, x: Int) {
                    val at = SwingUtilities.convertPoint(window.contentPane, x, 100, target)
                    target.dispatchEvent(
                        MouseEvent(target, id, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK,
                            at.x, at.y, 1, false, MouseEvent.BUTTON1)
                    )
                }
                send(MouseEvent.MOUSE_PRESSED, 100)
                for (x in 104..220 step 4) { delay(16); send(MouseEvent.MOUSE_DRAGGED, x) }
                send(MouseEvent.MOUSE_RELEASED, 220)
                delay(500)
                val got = assertNotNull(dragged, "the drag never reached AWT")
                assertEquals(listOf(file), got.getTransferData(DataFlavor.javaFileListFlavor))
                // A copy and nothing else, so Explorer can never move the song out of the library.
                assertEquals(DnDConstants.ACTION_COPY, gesture!!.dragAction)
                assertEquals(DnDConstants.ACTION_COPY, gesture!!.sourceAsDragGestureRecognizer.sourceActions)
                assertTrue(DragOut.active)
                // And a click with no drag in it is still a click, not a drag.
                dragged = null
                send(MouseEvent.MOUSE_PRESSED, 100)
                send(MouseEvent.MOUSE_RELEASED, 100)
                delay(300)
                assertEquals(1, clicks)
                assertEquals(null, dragged)
            } finally {
                window.dispose()
                DragOut.start = real
                DragOut.active = false
            }
        }
    }

    private fun components(c: Component): List<Component> =
        listOf(c) + ((c as? Container)?.components?.flatMap { components(it) } ?: emptyList())
}

/** The AWT event thread, as a coroutine dispatcher. */
private object Edt : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = SwingUtilities.invokeLater(block)
}
