package com.exo.musicplayer.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exo.musicplayer.MainActivity
import com.exo.musicplayer.musicApp
import com.exo.musicplayer.ui.theme.MusicPlayerTheme

/**
 * The share-sheet entry point.
 *
 * This screen exists to hold the process alive while the bytes are copied. The
 * sharing app grants us read access to its content:// URI only for the lifetime
 * of *this task*, and that grant cannot be persisted, so the copy has to finish
 * before we call finish(). Handing the URI to a background service instead would
 * be racing a revocation we cannot see.
 */
class ShareReceiverActivity : ComponentActivity() {

    private val viewModel: ImportViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uris = SharedUris.from(intent)
        if (uris.isEmpty()) {
            Toast.makeText(this, "Nothing to add.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Warm up the player so "Play now" is instant when the import lands.
        musicApp.playback.connect()
        viewModel.start(uris, SharedUris.sourceLabel(this), intent.type)

        setContent {
            MusicPlayerTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                ImportCard(
                    state = state,
                    onPlayNow = {
                        musicApp.playback.play(state.playable)
                        openLibrary(alsoOpenPlayer = true)
                    },
                    onOpenLibrary = { openLibrary(alsoOpenPlayer = false) },
                    onDismiss = { finish() }
                )
            }
        }
    }

    /** A second share while one is on screen should not stack another card. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun openLibrary(alsoOpenPlayer: Boolean) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_PLAYER, alsoOpenPlayer)
            }
        )
        finish()
    }

}

@Composable
private fun ImportCard(
    state: ImportUiState,
    onPlayNow: () -> Unit,
    onOpenLibrary: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(24.dp)) {
            if (!state.finished) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (state.total > 1) {
                                "Adding ${state.completed + 1} of ${state.total}"
                            } else {
                                "Adding to your library"
                            },
                            style = MaterialTheme.typography.titleMedium
                        )
                        state.currentName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (state.total > 1) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = summaryLine(state),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    // The card waits for you now, so it needs an explicit way out.
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val firstTitle = state.playable.firstOrNull()?.title
                if (state.total == 1 && firstTitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = firstTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                state.problems.forEach { problem ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = problemLine(problem),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                    if (state.playable.isNotEmpty()) {
                        TextButton(onClick = onOpenLibrary) {
                            Icon(Icons.Default.LibraryMusic, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Library")
                        }
                        TextButton(onClick = onPlayNow) {
                            Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Play")
                        }
                    }
                }
            }
        }
    }
}

private fun summaryLine(state: ImportUiState): String {
    val added = state.imported.size
    val dupes = state.duplicates.size
    return when {
        added > 0 && dupes > 0 -> "Added $added, $dupes already saved"
        added > 1 -> "Added $added songs"
        added == 1 -> "Added to your library"
        dupes > 0 -> if (dupes == 1) "Already in your library" else "$dupes already saved"
        else -> "Nothing was added"
    }
}

private fun problemLine(result: com.exo.musicplayer.data.ingest.ImportResult): String {
    val name = result.displayName ?: "That file"
    return when (result) {
        is com.exo.musicplayer.data.ingest.ImportResult.Rejected -> "$name — ${result.reason}"
        is com.exo.musicplayer.data.ingest.ImportResult.Failed ->
            "$name — could not be saved."
        else -> name
    }
}
