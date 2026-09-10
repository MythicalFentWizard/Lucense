package com.exo.musicplayer.data.download

import android.content.Context
import android.net.Uri
import android.util.Log
import com.exo.musicplayer.data.download.LinkResolver
import com.exo.musicplayer.data.download.ResolvedLink
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface DownloadOutcome {
    data class Done(val file: File, val title: String?) : DownloadOutcome
    data class Failed(val message: String) : DownloadOutcome
    data object NotReady : DownloadOutcome
}

/**
 * Wraps yt-dlp, which ships here as a bundled Python runtime plus ffmpeg.
 *
 * Only arm64 native libraries are packaged — every other ABI would roughly
 * triple the APK for hardware that has not shipped in years.
 *
 * yt-dlp breaks whenever YouTube changes something, which is often, so
 * [update] is exposed to the UI: a stale binary is by far the most common cause
 * of a download that used to work suddenly failing.
 */
class YtDlpDownloader(private val context: Context) {

    @Volatile
    private var ready = false

    @Volatile
    private var initError: String? = null

    val lastInitError: String? get() = initError

    /** Unpacks the runtime. Safe to call repeatedly; only the first does work. */
    suspend fun ensureReady(): Boolean = withContext(Dispatchers.IO) {
        if (ready) return@withContext true
        try {
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            ready = true
            initError = null
            true
        } catch (t: Throwable) {
            Log.w(TAG, "yt-dlp init failed", t)
            initError = t.message ?: "Could not start yt-dlp on this device."
            false
        }
    }

    suspend fun update(): String = withContext(Dispatchers.IO) {
        if (!ensureReady()) return@withContext initError ?: "yt-dlp is not available."
        runCatching {
            YoutubeDL.getInstance()
                .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            "yt-dlp updated"
        }.getOrElse { "Update failed: ${it.message}" }
    }

    /** Works out whether a link is downloadable directly or needs a lookup. */
    suspend fun resolve(url: String): ResolvedLink = withContext(Dispatchers.IO) {
        LinkResolver.resolve(url)
    }

    /** Best-effort title lookup so the UI can show what's about to download. */
    suspend fun peekTitle(url: String): String? = withContext(Dispatchers.IO) {
        if (!ensureReady()) return@withContext null
        runCatching { YoutubeDL.getInstance().getInfo(url).title }.getOrNull()
    }

    /**
     * Downloads [url] as an mp3 into the app cache and hands back the file.
     * The caller imports it through the normal pipeline so it is hashed,
     * deduplicated and tagged like any other track.
     */
    suspend fun downloadAudio(
        url: String,
        quality: DownloadQuality = DownloadQuality.DEFAULT,
        onProgress: (percent: Float, etaSeconds: Long, line: String) -> Unit
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        if (!ensureReady()) {
            return@withContext DownloadOutcome.NotReady
        }

        val workDir = File(context.cacheDir, "ytdlp/${UUID.randomUUID()}").apply { mkdirs() }
        return@withContext try {
            val request = YoutubeDLRequest(url).apply {
                addOption("-x")                              // audio only
                addOption("--audio-format", "mp3")
                addOption("--audio-quality", quality.audioQuality)
                // Narrows what is fetched, not just what it is re-encoded to,
                // which matters more on a phone than on a desktop.
                addOption("-f", quality.formatSelector)
                addOption("--embed-thumbnail")               // cover art
                addOption("--add-metadata")                  // title/artist tags
                addOption("--no-playlist")                   // a link inside a playlist
                addOption("--no-mtime")
                addOption("-o", "${workDir.absolutePath}/%(title)s.%(ext)s")
            }

            YoutubeDL.getInstance().execute(request, null) { progress, eta, line ->
                onProgress(progress, eta, line)
            }

            val produced = workDir.listFiles()
                ?.filter { it.isFile && it.length() > 0 }
                ?.maxByOrNull { it.length() }

            if (produced == null) {
                workDir.deleteRecursively()
                DownloadOutcome.Failed("yt-dlp finished but produced no audio file.")
            } else {
                DownloadOutcome.Done(produced, produced.nameWithoutExtension)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "download failed", t)
            workDir.deleteRecursively()
            DownloadOutcome.Failed(friendly(t.message))
        }
    }

    private fun friendly(raw: String?): String {
        val message = raw.orEmpty()
        return when {
            message.contains("Sign in to confirm", true) ||
                message.contains("bot", true) ->
                "YouTube asked for sign-in verification. Try again later, or update " +
                    "yt-dlp — this usually means the bundled version is stale."
            message.contains("Video unavailable", true) ->
                "That video is unavailable, private, or region blocked."
            message.contains("Unsupported URL", true) ->
                "That link isn't supported."
            message.isBlank() -> "Download failed."
            else -> message.lines().firstOrNull { it.contains("ERROR", true) } ?: message.take(300)
        }
    }

    fun looksLikeUrl(text: String): Boolean = runCatching {
        val uri = Uri.parse(text.trim())
        uri.scheme?.startsWith("http") == true && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private companion object {
        const val TAG = "YtDlpDownloader"
    }
}
