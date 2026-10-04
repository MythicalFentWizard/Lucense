package com.exo.musicplayer.ui.discover

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exo.musicplayer.data.discover.DiscoverBrowser
import com.exo.musicplayer.data.discover.Discovery
import com.exo.musicplayer.data.discover.LibrarySong
import com.exo.musicplayer.musicApp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File

/**
 * Keeps Discover's pages across a rotation and hands them the library.
 *
 * The browsing itself is [DiscoverBrowser], which Windows runs too.
 */
@OptIn(FlowPreview::class)
class DiscoverViewModel(application: Application) : AndroidViewModel(application) {

    val browser = DiscoverBrowser(viewModelScope, Discovery(File(application.cacheDir, "discover")))

    init {
        viewModelScope.launch {
            // The library changes a song at a time during an import; Discover
            // is told once it has settled.
            application.musicApp.library.observeAllTracks().debounce(600).collect { tracks ->
                browser.setLibrary(tracks.map { LibrarySong(it.title, it.artist, it.genre) })
            }
        }
    }
}
