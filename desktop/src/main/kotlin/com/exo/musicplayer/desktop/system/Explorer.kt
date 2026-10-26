package com.exo.musicplayer.desktop.system

import java.util.concurrent.TimeUnit
import java.awt.Desktop
import java.io.File

/** Opens the file manager on a file, with the file selected where it can be. */
object Explorer {

    /**
     * explorer.exe's arguments for [file]: `/select,` and the path as two
     * arguments, which Java turns into `explorer.exe /select, "G:\Music\a song.mp3"`.
     *
     * As one argument, `/select,G:\Music\a song.mp3`, Java quotes all of it
     * whenever the path has a space, and Explorer doesn't read a quoted
     * `"/select,..."` as /select: it opened Documents instead of the song's
     * folder, for nearly every song. Split in two, only the path is quoted.
     */
    fun arguments(file: File): List<String> {
        val target = file.absoluteFile
        return when {
            target.isFile -> listOf("explorer.exe", "/select,", target.path)
            target.isDirectory -> listOf("explorer.exe", target.path)
            else -> listOf("explorer.exe", (target.parentFile ?: target).path)
        }
    }

    fun reveal(file: File) {
        if (!Platform.isWindows) {
            revealOnLinux(file.absoluteFile)
            return
        }
        val started = runCatching { ProcessBuilder(arguments(file)).start() }.isSuccess
        if (!started) {
            runCatching { Desktop.getDesktop().open(file.absoluteFile.parentFile) }
        }
    }

    /**
     * The file selected in the file manager, by the request Linux file
     * managers share (Files, Dolphin, Nemo, Caja and Thunar answer it); where
     * none answers, its folder opened as any folder would be.
     */
    private fun revealOnLinux(target: File) {
        Thread {
            val selected = target.isFile && runCatching {
                val dbus = Platform.onPath("dbus-send") ?: return@runCatching false
                val process = ProcessBuilder(
                    dbus.path, "--session", "--print-reply", "--dest=org.freedesktop.FileManager1",
                    "/org/freedesktop/FileManager1", "org.freedesktop.FileManager1.ShowItems",
                    "array:string:" + target.toURI().toASCIIString(), "string:"
                ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
            }.getOrDefault(false)
            if (selected) return@Thread
            val folder = if (target.isDirectory) target else (target.parentFile ?: target)
            val opened = Platform.onPath("xdg-open")?.let { runCatching { ProcessBuilder(it.path, folder.path).start() }.isSuccess } ?: false
            if (!opened) runCatching { Desktop.getDesktop().open(folder) }
        }.apply { isDaemon = true; start() }
    }
}
