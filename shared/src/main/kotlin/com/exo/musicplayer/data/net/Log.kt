package com.exo.musicplayer.data.net

/**
 * Minimal logging shim.
 *
 * The shared module must not touch android.util.Log — it also compiles for the
 * desktop app, where that class does not exist. Each platform can point this at
 * its own sink; stderr is a reasonable default for both.
 */
object Log {
    fun warn(tag: String, message: String, error: Throwable? = null) {
        System.err.println("W/$tag: $message")
        error?.printStackTrace()
    }

    fun info(tag: String, message: String) = println("I/$tag: $message")
}
