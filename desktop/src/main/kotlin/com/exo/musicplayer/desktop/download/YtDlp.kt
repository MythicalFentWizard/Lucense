package com.exo.musicplayer.desktop.download

import com.exo.musicplayer.desktop.system.Platform
import com.exo.musicplayer.data.download.DownloadFormat
import com.exo.musicplayer.data.download.DownloadQuality
import com.exo.musicplayer.data.download.YtDlpTidy
import com.exo.musicplayer.desktop.data.AppDirs
import com.exo.musicplayer.desktop.data.NetworkProxy
import com.exo.musicplayer.desktop.data.ToolPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Passed to every yt-dlp call. Many VPNs carry only IPv4, and yt-dlp trying
 * IPv6 first behind one fails outright; tunnels also stall, so the socket
 * timeout is longer than yt-dlp's 20 s.
 */
internal val YT_DLP_NETWORK = listOf("--force-ipv4", "--socket-timeout", "30")

/**
 * Downloads only: a flaky tunnel drops connections mid-file, and yt-dlp's
 * retries otherwise fire back to back and are all spent within the same
 * second. These back off, so a short dropout is ridden out. Kept off search,
 * where waiting half a minute to report failure would be worse.
 */
internal val YT_DLP_RETRIES = listOf(
    "--extractor-retries", "5",
    "--retry-sleep", "exp=1:20",
    "--retry-sleep", "fragment:exp=1:20",
    "--retry-sleep", "extractor:exp=1:10"
)

/**
 * The JavaScript engine yt-dlp is to use, for any call that reads a video.
 *
 * YouTube hands out most of its streams behind a JavaScript challenge, and
 * yt-dlp solves it by running an engine that sits beside it. With none it is
 * left with the one kind of request that needs no solving, which yt-dlp calls
 * deprecated. Measured on 2026-10-04 that one request was still enough: eight
 * videos of eight downloaded with and without an engine, the same 44 formats
 * offered either way. So this changes nothing today. It is here for the day
 * that request stops being answered, so that an installed copy keeps working
 * through it. yt-dlp looks for Deno by default; QuickJS does the same job at
 * two megabytes instead of a hundred, and has to be named.
 *
 * Signed in, every video needs the challenge solved, and QuickJS took about
 * five seconds of each preview and download preparing YouTube's player for it,
 * every time. [SolverCache] keeps the prepared player, so that is paid once per
 * player instead (measured: 4.8 s to 0.3 s).
 */
internal fun ytDlpJsEngine(): List<String> {
    val qjs = ToolPaths.qjs.takeIf { it.isFile } ?: return emptyList()
    return listOf("--js-runtimes", "quickjs:${qjs.absolutePath}") + SolverCache.arguments()
}

/**
 * yt-dlp's QuickJS solver with its cache of prepared players switched on: a
 * small yt-dlp plugin (resources/yt-dlp/lucense_solver.py, which says how),
 * written out where yt-dlp is pointed at it, and a cache folder of Lucense's
 * own. yt-dlp ships the cache switched off because the files are large (about
 * four megabytes a player) and it never clears them away; here the old ones
 * are cleared. Should a yt-dlp update break the plugin, yt-dlp's own solver
 * takes over, as slow as before but working.
 */
internal object SolverCache {
    private val plugins: File by lazy { File(AppDirs.root, "yt-dlp-plugins") }
    private val cache: File by lazy { File(AppDirs.root, "yt-dlp-cache") }

    @Volatile private var prepared = false

    fun arguments(): List<String> {
        prepare()
        return listOf("--plugin-dirs", plugins.absolutePath, "--cache-dir", cache.absolutePath)
    }

    @Synchronized
    private fun prepare() {
        if (prepared) return
        prepared = true
        runCatching {
            val text = SolverCache::class.java.getResource("/yt-dlp/lucense_solver.py")?.readText() ?: return@runCatching
            val file = File(plugins, "lucense/yt_dlp_plugins/extractor/lucense_solver.py")
            if (!file.isFile || file.readText() != text) {
                file.parentFile.mkdirs()
                file.writeText(text)
            }
        }
        // Players YouTube has moved on from: it changes every week or two.
        runCatching {
            val old = System.currentTimeMillis() - PLAYER_KEPT_MS
            cache.listFiles { dir -> dir.isDirectory && dir.name.startsWith("lucense-solver-") }?.forEach { section ->
                section.listFiles()?.filter { it.lastModified() < old }?.forEach { it.delete() }
                if (section.list()?.isEmpty() == true) section.delete()
            }
        }
    }

    private const val PLAYER_KEPT_MS = 14L * 24 * 60 * 60 * 1000
}

/** What the downloader can do right now. */
data class ToolStatus(
    val ytDlp: Boolean = false,
    val ffmpeg: Boolean = false,
    val spotdl: Boolean = false,
    val jsEngine: Boolean = false,
    val ytDlpVersion: String? = null
) {
    val ready: Boolean get() = ytDlp
}

/** A line of progress from a running job. */
data class DownloadProgress(
    val percent: Float?,
    val line: String
)

/**
 * The bundled command-line downloaders.
 *
 * yt-dlp, ffmpeg and spotdl all ship inside the app image, so there is no first
 * -use install step and no window where the feature exists but does not work.
 * That is most of the installer's size, and it is the right trade: the tools are
 * the feature.
 *
 * yt-dlp still updates itself — extractors break whenever a site changes, often
 * faster than Lucense ships — but it cannot rewrite a binary under Program
 * Files, so updating copies it somewhere writable first.
 */
object YtDlp {

    fun status(): ToolStatus = ToolStatus(
        ytDlp = ToolPaths.ytDlp.isFile,
        ffmpeg = ToolPaths.ffmpeg.isFile,
        spotdl = ToolPaths.spotdl.isFile,
        jsEngine = ToolPaths.qjs.isFile,
        ytDlpVersion = null
    )

    suspend fun version(): String? = withContext(Dispatchers.IO) {
        val exe = ToolPaths.ytDlp
        if (!exe.isFile) return@withContext null
        runCatching {
            val process = ProcessBuilder(exe.absolutePath, "--version")
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
            text.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * Updates yt-dlp in place, after moving it somewhere it can be written.
     */
    suspend fun update(onLine: (String) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val exe = ToolPaths.makeUpdatable(Platform.program("yt-dlp"))
                ?: error("Couldn't find yt-dlp to update.")
            onLine("Updating ${exe.absolutePath}")
            val process = ProcessBuilder(listOf(exe.absolutePath, "-U") + NetworkProxy.ytDlpArgs())
                .redirectErrorStream(true)
                .also(NetworkProxy::configure)
                .start()
            process.inputStream.bufferedReader().forEachLine(onLine)
            check(process.waitFor() == 0) { "Update failed." }
        }
    }

    /**
     * Runs one download.
     *
     * Output lands in a private empty directory first and is moved out on
     * success. That is how the finished files are identified: yt-dlp's console
     * output changes between versions and between extractors, whereas "whatever
     * appeared in this empty folder" is always right — and a failed or cancelled
     * job leaves no half-written file in the user's music folder.
     */
    suspend fun download(
        target: String,
        destination: File,
        /** What to convert to; null keeps whatever the site served. */
        format: DownloadFormat?,
        embedThumbnail: Boolean,
        wholePlaylist: Boolean,
        quality: DownloadQuality = DownloadQuality.DEFAULT,
        /** Cut what SponsorBlock marks as not music in a music video. */
        cutNonMusic: Boolean = false,
        onProgress: (DownloadProgress) -> Unit,
        registerProcess: (Process) -> Unit = {}
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val exe = ToolPaths.ytDlp
            check(exe.isFile) { "yt-dlp is missing from this installation." }
            destination.mkdirs()

            val ffmpeg = ToolPaths.ffmpeg
            // A video already read for its preview, or read ahead, isn't read again.
            val kept = if (wholePlaylist) null else YouTubeAhead.videoId(target)?.let(YouTubeAhead::kept)
            inStaging(destination) { staging ->
                val command = mutableListOf(
                    exe.absolutePath,
                    "--newline",
                    "--no-warnings",
                    "--ignore-config",
                    // Filters what gets pulled down, not just what it is
                    // re-encoded to, so a lower setting saves bandwidth too.
                    "-f", quality.formatSelector,
                    "-o", File(staging, "%(title).180B.%(ext)s").absolutePath
                )
                if (!wholePlaylist) command += "--no-playlist"
                if (format != null) {
                    check(ffmpeg.isFile) { "Converting to ${format.label} needs ffmpeg." }
                    command += listOf("-x", "--audio-format", format.ytDlp)
                    if (format.lossy) command += listOf("--audio-quality", quality.audioQuality)
                }
                if (ffmpeg.isFile) {
                    command += listOf("--ffmpeg-location", ffmpeg.absolutePath)
                    // Metadata and cover art only survive the container with
                    // ffmpeg available to rewrite it.
                    command += "--embed-metadata"
                    command += YtDlpTidy.TAGS
                    if (embedThumbnail && format?.holdsCover != false) {
                        command += "--embed-thumbnail"
                        command += YtDlpTidy.squareCover(windows = true)
                    }
                    if (cutNonMusic) command += YtDlpTidy.NON_MUSIC_CUT
                }
                command += ytDlpJsEngine()
                command += NetworkProxy.ytDlpArgs()
                command += YouTubeSignIn.ytDlpArgs()
                command += YT_DLP_NETWORK
                command += YT_DLP_RETRIES

                fun run(source: List<String>): Int {
                    val process = ProcessBuilder(command + source).redirectErrorStream(true)
                        .also(NetworkProxy::configure)
                        .start()
                    registerProcess(process)
                    process.inputStream.bufferedReader().forEachLine { line ->
                        onProgress(DownloadProgress(percentOf(line), line.trim()))
                    }
                    return process.waitFor()
                }

                var code = if (kept != null) run(listOf("--load-info-json", kept.info.absolutePath)) else run(listOf(target))
                // What was kept may have stopped working (YouTube moved on, or the
                // connection did): read afresh, the way it always was.
                if (code != 0 && kept != null && staging.walkTopDown().none { it.isFile && it.extension.lowercase() !in LEFTOVERS }) {
                    YouTubeAhead.forget(kept.id)
                    staging.listFiles()?.forEach { it.deleteRecursively() }
                    onProgress(DownloadProgress(null, "Reading the video again"))
                    code = run(listOf(target))
                }
                code to "yt-dlp exited with code $code."
            }
        }
    }

    /**
     * Downloads a Spotify link with spotdl.
     *
     * spotdl reads Spotify's public metadata and then fetches the matching
     * recording from YouTube — Spotify's own audio is encrypted and nothing can
     * download it. The result is therefore a *match*: nearly always the same
     * recording, occasionally a live take or a remaster. Doing it this way
     * rather than scraping the page for a title gets exact track, album and
     * artwork metadata, and handles whole albums and playlists.
     */
    suspend fun downloadSpotify(
        url: String,
        destination: File,
        format: DownloadFormat?,
        quality: DownloadQuality = DownloadQuality.DEFAULT,
        onProgress: (DownloadProgress) -> Unit,
        registerProcess: (Process) -> Unit = {}
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            val exe = ToolPaths.spotdl
            check(exe.isFile) { "spotdl is missing from this installation." }
            destination.mkdirs()

            inStaging(destination) { staging ->
                val command = mutableListOf(
                    exe.absolutePath,
                    "download", url,
                    "--output", File(staging, "{artists} - {title}.{output-ext}").absolutePath
                )
                if (format != null) command += listOf("--format", format.spotdl)
                command += listOf("--bitrate", quality.spotdlBitrate)
                command += YouTubeSignIn.spotdlArgs()
                if (ToolPaths.ffmpeg.isFile) {
                    command += listOf("--ffmpeg", ToolPaths.ffmpeg.absolutePath)
                }

                // spotdl has no SOCKS option of its own; it gets the environment.
                val process = ProcessBuilder(command).redirectErrorStream(true)
                    .also(NetworkProxy::configure)
                    .start()
                registerProcess(process)
                process.inputStream.bufferedReader().forEachLine { line ->
                    onProgress(DownloadProgress(null, line.trim()))
                }
                val code = process.waitFor()
                code to "spotdl exited with code $code."
            }
        }
    }

    /**
     * Runs [block] against a scratch directory and moves whatever it produced
     * into [destination].
     */
    private inline fun inStaging(
        destination: File,
        block: (File) -> Pair<Int, String>
    ): List<File> {
        val staging = File(AppDirs.tools, "staging-${System.nanoTime()}")
        staging.mkdirs()
        try {
            val (code, failure) = block(staging)
            // Only what can be played counts. A download the site cuts off
            // part-way leaves the cover picture it had already fetched, and
            // that alone used to pass for a finished song: "Saved 1 file", a
            // .webp in the music folder, and no second try anywhere else.
            val produced = staging.walkTopDown().filter { it.isFile && it.extension.lowercase() !in LEFTOVERS }.toList()
            if (code != 0 && produced.isEmpty()) error(failure)

            return produced.map { file ->
                val moved = uniqueIn(destination, file.name)
                if (!file.renameTo(moved)) {
                    file.copyTo(moved, overwrite = true)
                    file.delete()
                }
                moved
            }
        } finally {
            staging.deleteRecursively()
        }
    }

    private val PERCENT = Regex("""(\d{1,3}(?:\.\d+)?)%""")

    /** What a download leaves in its folder besides the song: cover pictures and unfinished parts. */
    private val LEFTOVERS = setOf("webp", "jpg", "jpeg", "png", "part", "ytdl", "temp", "tmp", "json", "description")

    private fun percentOf(line: String): Float? {
        if (!line.startsWith("[download]")) return null
        return PERCENT.find(line)?.groupValues?.get(1)?.toFloatOrNull()?.div(100f)
    }

    private fun uniqueIn(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
        var index = 2
        while (candidate.exists()) {
            val suffix = if (extension.isEmpty()) "" else ".$extension"
            candidate = File(dir, "$base ($index)$suffix")
            index++
        }
        return candidate
    }
}
