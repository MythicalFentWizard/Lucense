package com.exo.musicplayer.desktop.data

import com.exo.musicplayer.desktop.system.Platform
import java.io.File

/**
 * Where the bundled command-line tools live.
 *
 * All three ship inside the app image, so downloading works the moment Lucense
 * is installed. A copy under [AppDirs.tools] takes precedence when one exists,
 * which is what makes updating possible: the app image sits in Program Files and
 * is not writable, so `yt-dlp -U` would fail in place. Updating copies the
 * binary somewhere writable first and updates it there, and from then on the
 * newer copy is the one that runs.
 */
object ToolPaths {

    /** Set by the Compose launcher; absent when running from a plain classpath. */
    private val bundledDir: File?
        get() = System.getProperty("compose.application.resources.dir")
            ?.let(::File)
            ?.takeIf { it.isDirectory }

    private fun resolve(tool: String): File {
        val name = Platform.program(tool)
        val updated = File(AppDirs.tools, name)
        if (updated.isFile) return updated.also(::runnable)
        bundledDir?.let { File(it, name) }?.takeIf { it.isFile }?.let { bundled ->
            // On Linux a program can arrive without its permission to run, and the
            // installed copy belongs to root; a copy that can run is made instead.
            if (Platform.isWindows || bundled.canExecute()) return bundled
            return makeUpdatable(name) ?: bundled
        }
        // Returned even when missing so callers can report a path that makes
        // sense; every caller checks isFile first.
        return updated
    }

    /** Lets [file] run, where that is a permission rather than a file name. */
    private fun runnable(file: File) {
        if (!Platform.isWindows && !file.canExecute()) file.setExecutable(true)
    }

    val ytDlp: File get() = resolve("yt-dlp")
    val ffmpeg: File get() = resolve("ffmpeg")
    val spotdl: File get() = resolve("spotdl")

    /** QuickJS, the JavaScript engine yt-dlp runs YouTube's challenges in. */
    val qjs: File get() = resolve("qjs")

    /** True once a writable copy exists, i.e. the tool can update itself. */
    fun isUpdatable(name: String): Boolean = File(AppDirs.tools, name).isFile

    /**
     * Copies a bundled binary somewhere writable so it can replace itself.
     *
     * @return the writable copy, or null if there was nothing to copy.
     */
    fun makeUpdatable(name: String): File? {
        val target = File(AppDirs.tools, name)
        if (target.isFile) return target
        val source = bundledDir?.let { File(it, name) }?.takeIf { it.isFile } ?: return null
        return runCatching {
            AppDirs.tools.mkdirs()
            source.copyTo(target, overwrite = true)
            runnable(target)
            target
        }.getOrNull()
    }
}
