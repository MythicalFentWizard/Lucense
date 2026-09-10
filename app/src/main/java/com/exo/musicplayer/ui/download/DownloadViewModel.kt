package com.exo.musicplayer.ui.download

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exo.musicplayer.data.download.DownloadOutcome
import com.exo.musicplayer.data.download.LinkResolver
import com.exo.musicplayer.data.download.ResolvedLink
import com.exo.musicplayer.data.download.YtDlpDownloader
import com.exo.musicplayer.data.ingest.ImportResult
import com.exo.musicplayer.musicApp
import kotlinx.coroutines.Job
import com.exo.musicplayer.data.download.DownloadQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadUiState(
    val busy: Boolean = false,
    val stage: String = "",
    val percent: Float = 0f,
    val etaSeconds: Long = 0L,
    val title: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
    /** Shown while downloading, e.g. the Spotify search caveat. */
    val note: String? = null
)

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val downloader = YtDlpDownloader(application)
    private val importer = application.musicApp.importer

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()

    private var job: Job? = null

    fun setUrl(value: String) { _url.value = value.trim() }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = DownloadUiState(message = "Cancelled", isError = false)
    }

    fun update() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = DownloadUiState(busy = true, stage = "Updating yt-dlp…")
            val message = downloader.update()
            _state.value = DownloadUiState(
                message = message,
                isError = message.startsWith("Update failed")
            )
        }
    }

    /**
     * Starts a download from an Identify result. Services that host audio
     * (YouTube, Audius, Internet Archive) carry a real URL; the rest fall back
     * to a name search, which is the same path a Spotify link takes.
     */
    private val _quality = MutableStateFlow(DownloadQuality.DEFAULT)
    val quality: StateFlow<DownloadQuality> = _quality.asStateFlow()

    fun setQuality(value: DownloadQuality) { _quality.value = value }

    fun downloadMatch(downloadUrl: String?, query: String) {
        _url.value = downloadUrl ?: LinkResolver.searchTarget(query)
        download()
    }

    fun download() {
        val link = _url.value
        if (link.isBlank()) return
        // A ytsearch: target is not a URL but is valid input to yt-dlp.
        if (!link.startsWith("ytsearch") && !downloader.looksLikeUrl(link)) {
            _state.value = DownloadUiState(message = "That doesn't look like a link.", isError = true)
            return
        }

        job?.cancel()
        job = viewModelScope.launch {
            _state.value = DownloadUiState(busy = true, stage = "Starting yt-dlp…")

            // First run unpacks the Python runtime, which takes a few seconds.
            if (!downloader.ensureReady()) {
                _state.value = DownloadUiState(
                    message = downloader.lastInitError
                        ?: "yt-dlp couldn't start. This build ships arm64 libraries only.",
                    isError = true
                )
                return@launch
            }

            _state.value = _state.value.copy(stage = "Reading the link…")

            // Spotify can't be fetched directly, so a Spotify link becomes a
            // search for the same track. Everything else goes straight to yt-dlp.
            val target: String
            val label: String?
            when (val resolved = downloader.resolve(link)) {
                is ResolvedLink.Direct -> {
                    target = resolved.url
                    label = downloader.peekTitle(resolved.url)
                }
                is ResolvedLink.Search -> {
                    target = LinkResolver.searchTarget(resolved.query)
                    label = resolved.display
                    _state.value = _state.value.copy(note = resolved.note)
                }
                is ResolvedLink.Unsupported -> {
                    _state.value = DownloadUiState(message = resolved.reason, isError = true)
                    return@launch
                }
            }

            _state.value = _state.value.copy(stage = "Downloading…", title = label)

            val outcome = downloader.downloadAudio(target, _quality.value) { percent, eta, line ->
                _state.value = _state.value.copy(
                    percent = percent.coerceIn(0f, 100f),
                    etaSeconds = eta,
                    stage = if (line.contains("ExtractAudio", true) ||
                        line.contains("ffmpeg", true)
                    ) {
                        "Converting to mp3…"
                    } else {
                        "Downloading…"
                    }
                )
            }

            when (outcome) {
                is DownloadOutcome.Done -> {
                    _state.value = _state.value.copy(stage = "Adding to library…", percent = 100f)
                    // Same pipeline as a Telegram share: hashed, deduplicated, tagged.
                    val result = importer.import(
                        Uri.fromFile(outcome.file),
                        sourceApp = "Download"
                    )
                    // The cache copy is redundant once the importer has its own.
                    runCatching { outcome.file.parentFile?.deleteRecursively() }

                    _state.value = when (result) {
                        is ImportResult.Imported ->
                            DownloadUiState(message = "Added \"${result.track.title}\"")
                        is ImportResult.Duplicate ->
                            DownloadUiState(message = "Already in your library")
                        else ->
                            DownloadUiState(
                                message = "Downloaded, but the file couldn't be imported.",
                                isError = true
                            )
                    }
                    _url.value = ""
                }

                is DownloadOutcome.Failed ->
                    _state.value = DownloadUiState(message = outcome.message, isError = true)

                DownloadOutcome.NotReady ->
                    _state.value = DownloadUiState(
                        message = downloader.lastInitError ?: "yt-dlp is not available.",
                        isError = true
                    )
            }
        }
    }
}
