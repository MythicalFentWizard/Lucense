package com.exo.musicplayer.share

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.ingest.ImportResult
import com.exo.musicplayer.musicApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportUiState(
    val total: Int = 0,
    val completed: Int = 0,
    val currentName: String? = null,
    val results: List<ImportResult> = emptyList(),
    val finished: Boolean = false
) {
    val imported: List<Track> get() = results.filterIsInstance<ImportResult.Imported>().map { it.track }
    val duplicates: List<Track> get() = results.filterIsInstance<ImportResult.Duplicate>().map { it.existing }
    val problems: List<ImportResult>
        get() = results.filter { it is ImportResult.Rejected || it is ImportResult.Failed }

    /** Everything that ended up in the library this run, new or already there. */
    val playable: List<Track> get() = imported + duplicates
    val progress: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var started = false

    /**
     * Copies each URI in turn. Runs on the ViewModel scope so a rotation mid-import
     * doesn't restart it, but it must finish while the Activity is alive: the read
     * grant the sharing app gave us dies with this task.
     */
    fun start(uris: List<Uri>, sourceApp: String?, mimeHint: String? = null) {
        if (started) return
        started = true

        _state.value = ImportUiState(total = uris.size)
        viewModelScope.launch {
            val importer = getApplication<Application>().musicApp.importer
            for (uri in uris) {
                val result = importer.import(uri, sourceApp, mimeHint)
                _state.update {
                    it.copy(
                        completed = it.completed + 1,
                        currentName = result.displayName,
                        results = it.results + result
                    )
                }
            }
            _state.update { it.copy(finished = true, currentName = null) }
        }
    }
}
