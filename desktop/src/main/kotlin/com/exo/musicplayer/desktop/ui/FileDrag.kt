package com.exo.musicplayer.desktop.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.DragAndDropSourceScope
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.geometry.CornerRadius
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File

/**
 * Songs dragged out of Lucense as their files: onto the desktop or into a
 * folder, a chat, an editor. Always a copy - the library's own files never
 * move because of a drag.
 */
object DragOut {
    /** While one of Lucense's own drags is under way, so the window doesn't take it back as an import. */
    @Volatile
    var active: Boolean = false
}

/**
 * Makes this a place songs can be dragged out from. [files] is asked for the
 * moment a drag starts, so a row in a selection can hand over the whole
 * selection. A plain click still clicks: the drag begins only once the mouse
 * has moved a little with the button held.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
fun Modifier.dragOutFiles(files: () -> List<File>): Modifier = composed {
    // The same gesture from one frame to the next, so a place that redraws
    // all the time - the playing bar - doesn't drop a drag as it begins.
    val latest by rememberUpdatedState(files)
    val gesture: suspend DragAndDropSourceScope.() -> Unit = remember {
        {
            detectDragGestures(
                onDragStart = { at ->
                    val list = latest().filter { it.isFile }
                    if (list.isNotEmpty()) {
                        DragOut.active = true
                        startTransfer(
                            DragAndDropTransferData(
                                transferable = DragAndDropTransferable(FileList(list)),
                                supportedActions = listOf(DragAndDropTransferAction.Copy),
                                dragDecorationOffset = at,
                                onTransferCompleted = { DragOut.active = false }
                            )
                        )
                    }
                },
                onDrag = { change, _ -> change.consume() }
            )
        }
    }
    dragAndDropSource(
        drawDragDecoration = {
            drawRoundRect(Palette.Accent.copy(alpha = 0.22f), cornerRadius = CornerRadius(12f, 12f))
        },
        block = gesture
    )
}

/** Files, as Explorer and every other drop target on Windows and Linux reads them. */
private class FileList(private val files: List<File>) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

    override fun getTransferData(flavor: DataFlavor): Any {
        if (!isDataFlavorSupported(flavor)) throw UnsupportedFlavorException(flavor)
        return files
    }
}
