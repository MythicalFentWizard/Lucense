package com.exo.musicplayer.desktop.system

import java.io.File

/** Which system this is, for the few things done differently on each. */
object Platform {
    private val name = System.getProperty("os.name").orEmpty()

    val isWindows: Boolean = name.startsWith("Windows")
    val isLinux: Boolean = name.startsWith("Linux")

    /** A program's file name here: `yt-dlp.exe` on Windows, `yt-dlp` elsewhere. */
    fun program(name: String): String = if (isWindows) "$name.exe" else name

    /** [command] as found on the PATH, or null; for programs a Linux system may or may not have. */
    fun onPath(command: String): File? =
        System.getenv("PATH").orEmpty().split(File.pathSeparator)
            .asSequence()
            .filter { it.isNotBlank() }
            .map { File(it, command) }
            .firstOrNull { it.isFile && it.canExecute() }
}
