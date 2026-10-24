package com.exo.musicplayer.data.download

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Downloads as the user's own YouTube account, when they choose to.
 *
 * YouTube judges a download by where it comes from, and refuses many VPN
 * addresses with "Sign in to confirm you're not a bot". A signed-in request is
 * judged by the account instead. yt-dlp can't sign in with a password; it
 * takes the sign-in cookies, as a cookies.txt file. They come either from
 * YouTube's own sign-in page shown in the app, or from a cookies.txt file the
 * user exported from a browser. They stay in the app's private storage and go
 * nowhere but to YouTube.
 */
class YouTubeCookies(context: Context) {

    private val appContext = context.applicationContext

    /** What yt-dlp is given with `--cookies`. It writes refreshed values back as it goes. */
    val file: File get() = File(appContext.filesDir, "youtube-cookies.txt")

    private val _signedIn = MutableStateFlow(file.isFile && holdsSignIn(file.readText()))
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    /**
     * Keeps YouTube's cookies from the in-app sign-in page. False when they
     * hold no sign-in yet, and then nothing is written.
     */
    fun saveFromWebView(): Boolean {
        val manager = CookieManager.getInstance()
        manager.flush()
        val header = manager.getCookie("https://www.youtube.com").orEmpty()
        val pairs = header.split(';').mapNotNull { part ->
            val at = part.indexOf('=')
            if (at <= 0) null else part.substring(0, at).trim() to part.substring(at + 1).trim()
        }.filter { (name, _) -> name.isNotBlank() }
        if (pairs.none { (name, _) -> name in SIGN_IN_COOKIES }) return false
        // A year on: the cookie manager doesn't say when each runs out, and
        // YouTube refreshes the ones that matter as yt-dlp uses them.
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 60 * 60
        val text = buildString {
            appendLine("# Netscape HTTP Cookie File")
            appendLine("# YouTube's sign-in, from Lucense's sign-in page. Only yt-dlp reads it.")
            pairs.forEach { (name, value) -> appendLine(".youtube.com\tTRUE\t/\tTRUE\t$expiry\t$name\t$value") }
        }
        file.writeText(text)
        _signedIn.value = true
        return true
    }

    /**
     * Takes a cookies.txt file the user exported from a browser. Null when it
     * was kept; otherwise why not.
     */
    fun saveFromFile(uri: Uri): String? {
        val text = runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return "That file couldn't be read."
        if (!text.lineSequence().any { it.contains("youtube.com") }) return "That file has no YouTube cookies in it."
        if (!holdsSignIn(text)) return "That file has YouTube cookies but no sign-in. Export it from a browser signed in to YouTube."
        file.writeText(text)
        _signedIn.value = true
        return null
    }

    fun signOut() {
        runCatching { file.delete() }
        runCatching {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
        _signedIn.value = false
    }

    private fun holdsSignIn(text: String): Boolean =
        text.lineSequence().any { line ->
            val fields = line.split('\t')
            fields.size >= 7 && fields[0].contains("youtube.com") && fields[5] in SIGN_IN_COOKIES
        } || SIGN_IN_COOKIES.any { "$it=" in text && "youtube.com" in text && !text.contains('\t') }

    companion object {
        /** Cookies YouTube sets only for a signed-in account. */
        val SIGN_IN_COOKIES = setOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID", "LOGIN_INFO", "SID", "__Secure-3PSID")

        /** Google's sign-in, sent on to YouTube's mobile site once done. */
        const val SIGN_IN_URL =
            "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=" +
                "https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26app%3Dm%26next%3Dhttps%253A%252F%252Fm.youtube.com%252F"
    }
}
