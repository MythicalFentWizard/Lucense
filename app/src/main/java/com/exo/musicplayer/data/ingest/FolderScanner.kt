package com.exo.musicplayer.data.ingest

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.exo.musicplayer.util.AudioTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Walks a folder the user picked with the system tree picker and lists every
 * audio file inside it.
 *
 * Uses DocumentsContract directly rather than androidx.documentfile: DocumentFile
 * issues a separate query per file, which is painfully slow on a folder with
 * hundreds of songs, and avoiding it also avoids adding a dependency.
 */
object FolderScanner {

    private const val TAG = "FolderScanner"
    private const val MAX_DEPTH = 8
    private const val MAX_FILES = 2000

    data class Found(val uri: Uri, val name: String)

    suspend fun findAudio(context: Context, treeUri: Uri): List<Found> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<Found>()
            val rootId = runCatching {
                DocumentsContract.getTreeDocumentId(treeUri)
            }.getOrNull() ?: return@withContext emptyList()

            walk(context, treeUri, rootId, results, 0)
            results
        }

    private fun walk(
        context: Context,
        treeUri: Uri,
        documentId: String,
        results: MutableList<Found>,
        depth: Int
    ) {
        if (depth > MAX_DEPTH || results.size >= MAX_FILES) return

        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        // Subdirectories are collected first and recursed into after the cursor
        // is closed, so a deep tree doesn't hold many cursors open at once.
        val subdirectories = mutableListOf<String>()

        try {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { c ->
                val idIndex = c.getColumnIndexOrThrow(projection[0])
                val nameIndex = c.getColumnIndexOrThrow(projection[1])
                val mimeIndex = c.getColumnIndexOrThrow(projection[2])
                while (c.moveToNext()) {
                    if (results.size >= MAX_FILES) return@use
                    val childId = c.getString(idIndex) ?: continue
                    val name = c.getString(nameIndex).orEmpty()
                    val mime = c.getString(mimeIndex).orEmpty()

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        subdirectories += childId
                    } else if (AudioTypes.isProbablyAudio(mime, name)) {
                        results += Found(
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, childId),
                            name
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not list $documentId", t)
        }

        for (childId in subdirectories) {
            walk(context, treeUri, childId, results, depth + 1)
        }
    }
}
