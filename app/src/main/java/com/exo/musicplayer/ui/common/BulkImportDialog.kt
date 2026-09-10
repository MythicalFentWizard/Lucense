package com.exo.musicplayer.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exo.musicplayer.ui.MainViewModel

/** Progress for a folder import, which can run to hundreds of files. */
@Composable
fun BulkImportDialog(
    progress: MainViewModel.BulkProgress,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* deliberate: cancelling is explicit */ },
        title = { Text(if (progress.scanning) "Scanning folder…" else "Importing songs") },
        text = {
            Column {
                if (progress.scanning) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Looking for audio files…",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Text(
                        "${progress.done} of ${progress.total}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    progress.currentName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = buildString {
                            append("${progress.added} added")
                            if (progress.duplicates > 0) {
                                append(" · ${progress.duplicates} already saved")
                            }
                            if (progress.failed > 0) append(" · ${progress.failed} skipped")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCancel) { Text("Stop") }
        }
    )
}
