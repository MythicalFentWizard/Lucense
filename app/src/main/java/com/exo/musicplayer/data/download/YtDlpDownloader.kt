package com.exo.musicplayer.data.download

import android.content.Context
import android.net.Uri
import android.util.Log
import com.exo.musicplayer.data.download.LinkResolver
import com.exo.musicplayer.data.download.ResolvedLink
import com.exo.musicplayer.data.youtube.YouTubeVideo
import com.exo.musicplayer.data.youtube.YtDlpFlatSearch
import com.exo.musicplayer.musicApp
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface DownloadOutcome {
    data class Done(val file: File, val title: String?) : DownloadOutcome
    data class Failed(
        val message: String,
        /** YouTube refused the connection itself, so another source is worth a try. */
        val refusedByYouTube: Boolean = false
    ) : DownloadOutcome
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
            checked()
            "yt-dlp updated"
        }.getOrElse { "Update failed: ${it.message}" }
    }

    private val marks by lazy { context.getSharedPreferences("ytdlp", Context.MODE_PRIVATE) }

    private fun checked() = marks.edit().putLong(KEY_CHECKED, System.currentTimeMillis()).apply()

    /** Whether yt-dlp hasn't been looked at for a couple of days, or ever. */
    val stale: Boolean
        get() = System.currentTimeMillis() - marks.getLong(KEY_CHECKED, 0L) > STALE_AFTER_MS

    /**
     * Brings yt-dlp up to date if it hasn't been checked lately.
     *
     * The copy inside the app is whatever was current when the library it
     * comes in was built: 2025.11.12, a year behind YouTube, which answers it
     * with "HTTP Error 403" on song after song. Nobody should have to find the
     * update button to make a fresh install work, so the first download does
     * it, and it is looked at again every couple of days. A check that fails -
     * no network, GitHub out of reach - is simply tried again next time.
     */
    suspend fun updateIfStale(): Boolean = withContext(Dispatchers.IO) {
        if (!stale || !ensureReady()) return@withContext false
        updateNow()
    }

    /** True when a newer yt-dlp was installed. */
    private fun updateNow(): Boolean = runCatching {
        val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        checked()
        status == YoutubeDL.UpdateStatus.DONE
    }.getOrElse {
        Log.w(TAG, "yt-dlp update failed", it)
        false
    }

    /** Set once a failed download has made this run look for a newer yt-dlp. */
    @Volatile
    private var updatedAfterFailure = false

    /** Works out whether a link is downloadable directly or needs a lookup. */
    suspend fun resolve(url: String): ResolvedLink = withContext(Dispatchers.IO) {
        LinkResolver.resolve(url)
    }

    /**
     * Best-effort title lookup so the UI can show what's about to download.
     *
     * A YouTube link is read from its oEmbed card: one light request instead of
     * a full extraction, which doubled what YouTube saw per download - the kind
     * of traffic that gets an address flagged - and which it refuses on a
     * flagged VPN anyway. Other sites ask yt-dlp for just the title.
     */
    suspend fun peekTitle(url: String): String? = withContext(Dispatchers.IO) {
        if (LinkResolver.isYouTube(url)) {
            return@withContext YouTubeTrouble.title(url, lookupProxy)
        }
        if (!ensureReady()) return@withContext null
        runCatching {
            val request = newRequest(url).apply {
                addOption("--print", "title")
                addOption("--skip-download")
                addOption("--no-playlist")
                addOption("--no-warnings")
            }
            YoutubeDL.getInstance().execute(request).out.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        }.getOrNull()
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
            val request = newRequest(url).apply {
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
            // A 403 on the audio is what an out-of-date yt-dlp gets. Once a
            // run, that is answered by looking for a newer one and trying the
            // song again, rather than by reporting a failure an update fixes.
            val message = t.message.orEmpty()
            if (!updatedAfterFailure && OUTDATED_SIGNS.any { message.contains(it, ignoreCase = true) }) {
                updatedAfterFailure = true
                if (updateNow()) return@withContext downloadAudio(url, quality, onProgress)
            }
            DownloadOutcome.Failed(friendly(t.message), YouTubeTrouble.refused(t.message))
        }
    }

    /** The download proxy from Settings, if one is set and makes sense. */
    private val proxy: String?
        get() = DownloadProxy.normalize(context.musicApp.prefs.downloadProxy.value)

    /** The same proxy, for the app's own lookups that go with a download. */
    val lookupProxy: java.net.Proxy?
        get() = DownloadProxy.javaProxy(context.musicApp.prefs.downloadProxy.value)

    /** Every yt-dlp call starts here, so a download proxy applies to all of them. */
    fun newRequest(target: String): YoutubeDLRequest =
        YoutubeDLRequest(target).apply { proxy?.let { addOption("--proxy", it) } }

    /**
     * What a playlist link holds, without downloading any of it; with why
     * not, when it can't be read (a private playlist, a dead link).
     */
    suspend fun playlist(url: String): Pair<YtDlpFlatSearch.Playlist?, String?> = withContext(Dispatchers.IO) {
        if (!ensureReady()) return@withContext null to lastInitError
        runCatching {
            val request = newRequest(url)
            YtDlpFlatSearch.PLAYLIST_ARGUMENTS.forEach { request.addOption(it) }
            YtDlpFlatSearch.parsePlaylist(YoutubeDL.getInstance().execute(request).out) to null as String?
        }.getOrElse { null to friendly(it.message) }
    }

    private fun friendly(raw: String?): String {
        val message = raw.orEmpty()
        return when {
            message.contains("not a bot", true) ||
                message.contains("HTTP Error 429", true) ||
                message.contains("Too Many Requests", true) ->
                "YouTube refused this connection. ${YouTubeTrouble.WHY_REFUSED}"
            message.contains("confirm your age", true) ->
                "YouTube only plays this video for signed-in adults."
            message.contains("Video unavailable", true) ->
                "That video is unavailable, private, or region blocked."
            message.contains("Unsupported URL", true) ->
                "That link isn't supported."
            NETWORK_TROUBLE.any { message.contains(it, true) } ->
                "Couldn't reach the site. Check your connection; if YouTube is " +
                    "blocked where you are, downloads need a VPN."
            message.contains("HTTP Error 403", true) ->
                "The site refused the download. Updating yt-dlp usually fixes this."
            message.isBlank() -> "Download failed."
            // yt-dlp's own verdict is the line that starts with ERROR. Warnings
            // come first and can mention errors too ("TransportError"), so a
            // line merely containing the word isn't enough.
            else -> message.lines()
                .firstOrNull { it.trimStart().startsWith("ERROR:") }
                ?.substringAfter("ERROR:")
                ?.replace(Regex("""^\s*\[[^\]]+]\s*([\w-]+:\s*)?"""), "")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: message.lines().lastOrNull { it.isNotBlank() }?.take(300)
                ?: "Download failed."
        }
    }

    fun looksLikeUrl(text: String): Boolean = runCatching {
        val uri = Uri.parse(text.trim())
        uri.scheme?.startsWith("http") == true && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private companion object {
        /** How a connection that never got through reads in yt-dlp's output. */
        val NETWORK_TROUBLE = listOf(
            "Connection refused", "timed out", "Network is unreachable",
            "Temporary failure in name resolution", "Failed to resolve",
            "getaddrinfo failed", "Connection reset", "No route to host"
        )
        const val TAG = "YtDlpDownloader"

        const val KEY_CHECKED = "checked_at"

        /** How long a yt-dlp is trusted before it is checked for a newer one. */
        const val STALE_AFTER_MS = 2L * 24 * 60 * 60 * 1000

        /** What yt-dlp says when the trouble is its own age. */
        val OUTDATED_SIGNS = listOf("HTTP Error 403", "older than 90 days")
    }
}
