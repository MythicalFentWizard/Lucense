package com.exo.musicplayer.data.trim

import android.content.Context
import android.net.Uri
import com.exo.musicplayer.data.audio.Trim
import com.exo.musicplayer.data.db.Track
import com.exo.musicplayer.data.ingest.ImportResult
import com.exo.musicplayer.musicApp
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves a stretch of a song as a new song, cut rather than encoded again:
 * Riff's lossless trimmer (github.com/rootscripts/riff, MIT).
 *
 * The ffmpeg that comes with the downloader does the cutting. It is run
 * directly, the way the downloader's own runner starts it: the program is in
 * the app's native library folder, and the libraries it needs (its own, and
 * the C++ runtime that comes with Python) are unpacked under no_backup when
 * the packages are set up. The piece is written to the
 * cache and handed to the importer, which gives it a place in the library like
 * any other song, and then the cache copy goes.
 */
class TrackTrimmer(private val context: Context) {

    suspend fun save(track: Track, startMs: Long, endMs: Long): Result<Track> = withContext(Dispatchers.IO) {
        runCatching {
            // Both packages, as the downloader sets them up: ffmpeg's own
            // libraries need the C++ runtime, which comes in Python's.
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            val binary = File(context.applicationInfo.nativeLibraryDir, "libffmpeg.so")
            check(binary.isFile) { "ffmpeg isn't part of this build." }
            val packages = File(context.noBackupFilesDir, "youtubedl-android/packages")
            val libraries = listOf("ffmpeg", "python").map { File(packages, "$it/usr/lib") }.filter { it.isDirectory }

            val source = File(track.filePath)
            check(source.isFile) { "The song's file is missing." }
            val work = File(context.cacheDir, "trim").apply {
                deleteRecursively()
                mkdirs()
            }
            // Named after the song rather than the stored file, whose name is a hash.
            val safe = track.title.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_").take(80).ifBlank { "Song" }
            val out = File(work, Trim.outputName("$safe.${source.extension}") { false })
            try {
                val process = ProcessBuilder(
                    listOf(binary.path) + Trim.ffmpegArgs(source.path, out.path, startMs, endMs, Trim.title(track.title))
                )
                    .redirectErrorStream(true)
                    .apply { environment()["LD_LIBRARY_PATH"] = libraries.joinToString(":") }
                    .start()
                val log = process.inputStream.bufferedReader().readText()
                val code = process.waitFor()
                if (code != 0 || !out.isFile || out.length() == 0L) {
                    error(log.lines().lastOrNull { it.isNotBlank() } ?: "ffmpeg stopped with code $code.")
                }
                when (val result = context.musicApp.importer.import(Uri.fromFile(out), sourceApp = "Trim")) {
                    is ImportResult.Imported -> result.track
                    is ImportResult.Duplicate -> result.existing
                    is ImportResult.Rejected -> error(result.reason)
                    is ImportResult.Failed -> throw result.error
                }
            } finally {
                work.deleteRecursively()
            }
        }
    }
}
