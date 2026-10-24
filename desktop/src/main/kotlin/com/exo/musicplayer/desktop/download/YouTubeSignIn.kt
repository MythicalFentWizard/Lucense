package com.exo.musicplayer.desktop.download

import java.io.File

/**
 * Downloads as the user's own YouTube account, when they choose to.
 *
 * YouTube judges a download by where it comes from, and refuses many VPN
 * addresses outright with "Sign in to confirm you're not a bot". A signed-in
 * request is judged by the account instead, which gets through. yt-dlp can't
 * sign in with a password any more; it takes the sign-in cookies, either read
 * fresh from a browser the user is signed in to each time it runs, or from a
 * cookies.txt file exported from one.
 *
 * Firefox is the one to use on Windows: Chrome and Edge now lock their
 * cookies so that no other program can read them while they run, and encrypt
 * them so that none can read them at all. Nothing is sent anywhere but to
 * YouTube, and nothing is stored here but the choice.
 */
object YouTubeSignIn {

    enum class Source(val label: String, val browser: String?) {
        OFF("Off", null),
        FIREFOX("Firefox", "firefox"),
        CHROME("Chrome", "chrome"),
        EDGE("Edge", "edge"),
        BRAVE("Brave", "brave"),
        OPERA("Opera", "opera"),
        // yt-dlp knows Opera GX only as Opera with its own profile folder.
        OPERA_GX("Opera GX", "opera"),
        VIVALDI("Vivaldi", "vivaldi"),
        FILE("cookies.txt", null);

        companion object {
            fun of(name: String?): Source = entries.firstOrNull { it.name == name } ?: OFF
        }
    }

    @Volatile var source: Source = Source.OFF

    /** The cookies.txt file, for [Source.FILE]. */
    @Volatile var cookieFile: File? = null

    /** Whether yt-dlp is being given a sign-in at all. */
    val active: Boolean
        get() = when (source) {
            Source.OFF -> false
            Source.FILE -> cookieFile?.isFile == true
            else -> true
        }

    /** What every yt-dlp run is given, after the proxy. */
    fun ytDlpArgs(): List<String> = when (val chosen = source) {
        Source.OFF -> emptyList()
        Source.FILE -> cookieFile?.takeIf { it.isFile }?.let { listOf("--cookies", it.absolutePath) } ?: emptyList()
        Source.OPERA_GX -> listOf(
            "--cookies-from-browser",
            "opera:" + File(System.getenv("APPDATA").orEmpty(), "Opera Software/Opera GX Stable").path
        )
        else -> listOf("--cookies-from-browser", chosen.browser!!)
    }

    /** spotdl takes a cookies file and nothing else. */
    fun spotdlArgs(): List<String> =
        if (source == Source.FILE) cookieFile?.takeIf { it.isFile }?.let { listOf("--cookie-file", it.absolutePath) } ?: emptyList()
        else emptyList()

    /** A page only a signed-in account can see: the subscriptions feed, one entry of it. */
    val CHECK_ARGUMENTS = listOf("--flat-playlist", "-I", "1", "--print", "%(id)s", "--no-warnings", "--ignore-config", ":ytsubs")

    /** What a check's error says, in words that fit the setting. */
    fun explain(error: String?, chosen: Source): String {
        val said = error.orEmpty()
        val browser = chosen.label
        return when {
            said.isBlank() -> "Signed in: YouTube shows this account's pages."
            said.contains("Login details are needed", true) || said.contains("requires authentication", true) ->
                if (chosen == Source.FILE) {
                    "That file has no YouTube sign-in in it. Export it again from a browser that is signed in to YouTube."
                } else {
                    "$browser isn't signed in to YouTube. Sign in at youtube.com in $browser, then check again."
                }
            said.contains("could not find", true) && said.contains("cookies", true) ->
                "$browser doesn't seem to be installed for this Windows user."
            said.contains("DPAPI", true) || said.contains("decrypt", true) ->
                "$browser keeps its cookies encrypted so other programs can't read them. Use Firefox, or a cookies.txt file."
            said.contains("Could not copy", true) || said.contains("database is locked", true) || said.contains("Permission denied", true) ->
                "$browser locks its cookies while it is open. Close $browser and check again, or use Firefox."
            said.contains("not a bot", true) ->
                "YouTube still asked to confirm this isn't a bot, so the sign-in didn't take. Sign in again in $browser."
            else -> said.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("ERROR") }
                ?.removePrefix("ERROR:")?.trim()?.take(200) ?: "The check didn't finish."
        }
    }
}
