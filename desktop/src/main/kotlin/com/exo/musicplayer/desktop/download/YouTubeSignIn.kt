package com.exo.musicplayer.desktop.download

import com.exo.musicplayer.desktop.data.AppDirs
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Downloads as the user's own YouTube account, when they choose to.
 *
 * YouTube judges a download by where it comes from, and refuses many VPN
 * addresses outright with "Sign in to confirm you're not a bot". A signed-in
 * request is judged by the account instead, which gets through. yt-dlp can't
 * sign in with a password any more; it takes the sign-in cookies.
 *
 * Lucense keeps its own copy of them ([kept]) and gives yt-dlp that, from
 * whichever source:
 *
 *  - **Sign in here** ([Source.OWN]): the default browser, in a window with a
 *    profile of its own, signed in to once ([OwnSignIn]). Nothing else ever uses that
 *    session, so YouTube never cycles it out the way it does a browser's
 *    in daily use; it lasts until signed out of.
 *  - **A browser**: its sign-in copied once and renewed every few days, or
 *    when YouTube stops taking it - rather than read afresh on every run,
 *    which failed whenever the browser held its cookies locked.
 *  - **cookies.txt**: a file exported from a browser.
 *
 * Nothing leaves the PC except to YouTube, and only YouTube's and Google's
 * cookies are kept.
 */
object YouTubeSignIn {

    enum class Source(val label: String, val browser: String?) {
        OFF("Off", null),
        OWN("Sign in here", null),
        FIREFOX("Firefox", "firefox"),
        CHROME("Chrome", "chrome"),
        EDGE("Edge", "edge"),
        BRAVE("Brave", "brave"),
        OPERA("Opera", "opera"),
        // yt-dlp knows Opera GX only as Opera with its own profile folder.
        OPERA_GX("Opera GX", "opera"),
        VIVALDI("Vivaldi", "vivaldi"),
        FILE("cookies.txt", null);

        /** A browser whose sign-in is copied. */
        val copied: Boolean get() = browser != null

        companion object {
            fun of(name: String?): Source = entries.firstOrNull { it.name == name } ?: OFF
        }
    }

    @Volatile var source: Source = Source.OFF

    /** The cookies.txt file, for [Source.FILE]. */
    @Volatile var cookieFile: File? = null

    private val dir: File by lazy { File(AppDirs.root, "youtube-sign-in").also { it.mkdirs() } }

    /** Lucense's own copy of the sign-in: YouTube's and Google's cookies, in yt-dlp's cookies.txt format. */
    val kept: File get() = File(dir, "cookies.txt")
    private val keptFrom: File get() = File(dir, "from.txt")

    /** Where a sign-in window keeps its profile while it is open. */
    val windowProfile: File get() = File(dir, "window")

    /** Which source the kept copy came from, or null when there is none. */
    fun keptSource(): Source? =
        if (kept.isFile && keptFrom.isFile) runCatching { Source.of(keptFrom.readText().trim()) }.getOrNull() else null

    /**
     * When the kept copy was made or renewed, or null: signed in, or copied
     * from a browser. Not when YouTube last renewed its cookies, which
     * [takeBackRuns] keeps up with as it goes.
     */
    fun keptAt(): Long? = keptFrom.takeIf { it.isFile && kept.isFile }?.lastModified()

    /** Whether yt-dlp is being given a sign-in at all. */
    val active: Boolean
        get() = when (val chosen = source) {
            Source.OFF -> false
            Source.FILE -> cookieFile?.isFile == true
            Source.OWN -> keptSource() == Source.OWN
            else -> chosen.copied
        }

    /** What every yt-dlp run is given, after the proxy. */
    fun ytDlpArgs(): List<String> = when (val chosen = source) {
        Source.OFF -> emptyList()
        Source.FILE -> cookieFile?.takeIf { it.isFile }?.let { listOf("--cookies", runCopy(it, ours = false).path) } ?: emptyList()
        Source.OWN -> if (keptSource() == Source.OWN) listOf("--cookies", runCopy(kept, ours = true).path) else emptyList()
        // Not copied yet, or the copy is of another browser: read the browser, as before.
        else -> if (keptSource() == chosen) listOf("--cookies", runCopy(kept, ours = true).path) else browserArgs(chosen)
    }

    /** Reads the sign-in from [chosen] itself. */
    private fun browserArgs(chosen: Source): List<String> = when (chosen) {
        Source.OPERA_GX -> listOf(
            "--cookies-from-browser",
            "opera:" + File(System.getenv("APPDATA").orEmpty(), "Opera Software/Opera GX Stable").path
        )
        else -> listOf("--cookies-from-browser", chosen.browser!!)
    }

    /** spotdl takes a cookies file and nothing else: the kept copy, or the chosen file. */
    fun spotdlArgs(): List<String> {
        val file = when (val chosen = source) {
            Source.OFF -> null
            Source.FILE -> cookieFile?.takeIf { it.isFile }
            else -> kept.takeIf { keptSource() == chosen }
        } ?: return emptyList()
        return listOf("--cookie-file", runCopy(file, ours = file == kept).path)
    }

    /**
     * A copy for one run. yt-dlp writes its cookies back into the file when
     * it finishes, and runs side by side writing one file could leave it torn;
     * each gets its own, and copies from runs long over are cleared away.
     * [ours] marks a copy of the kept sign-in, whose renewed cookies are taken
     * back; a cookies.txt the user chose is never written to.
     */
    @Synchronized
    private fun runCopy(from: File, ours: Boolean): File {
        val runs = File(dir, "runs").also { it.mkdirs() }
        runCatching { takeBackRuns(runs) }
        val now = System.currentTimeMillis()
        runs.listFiles()?.filter { now - it.lastModified() > RUN_COPY_KEPT_MS }?.forEach { it.delete() }
        val copy = File(runs, "${if (ours) KEPT_RUN else OTHER_RUN}${System.nanoTime()}.txt")
        from.copyTo(copy, overwrite = true)
        return copy
    }

    /**
     * Takes back what YouTube renewed. A signed-in session's cookies are
     * renewed by YouTube as they are used, and yt-dlp writes the renewed ones
     * into its run's copy as it finishes. Thrown away, every run went on
     * showing YouTube the cookies as first kept, older by the hour, which it
     * can take for a stolen session and end. So the newest finished run's are
     * kept - only while it is still signed in: a copy YouTube signed out of is
     * never taken back.
     */
    private fun takeBackRuns(runs: File) {
        val now = System.currentTimeMillis()
        val finished = runs.listFiles { file -> file.name.startsWith(KEPT_RUN) }
            ?.filter { written(it) && now - it.lastModified() > SETTLED_MS }
            .orEmpty()
        if (finished.isEmpty()) return
        val newest = finished.maxBy { it.lastModified() }
        val source = keptSource()
        if (source != null && newest.lastModified() > kept.lastModified()) {
            store(runCatching { newest.readLines() }.getOrDefault(emptyList()), from = null)
        }
        finished.forEach { it.delete() }
    }

    /** Whether yt-dlp wrote [run] back after it was copied: changed well after it was made. */
    private fun written(run: File): Boolean = runCatching {
        val made = Files.readAttributes(run.toPath(), BasicFileAttributes::class.java).creationTime().toMillis()
        run.lastModified() - made > 1_000
    }.getOrDefault(false)

    /**
     * yt-dlp arguments that read [chosen]'s sign-in and write the whole
     * browser jar into [into], for [keep] to take YouTube's part of.
     */
    fun copyArguments(chosen: Source, into: File): List<String> = browserArgs(chosen) + listOf("--cookies", into.path)

    /** A scratch file for [copyArguments], beside the kept copy. */
    fun scratch(): File = File(dir, "copying-${System.nanoTime()}.txt")

    /**
     * Keeps YouTube's and Google's cookies from [jar] - a cookies.txt as
     * yt-dlp writes it, which holds everything the browser had - as the kept
     * sign-in from [from]. False, and nothing changed, when there is no
     * YouTube sign-in in it.
     */
    fun keep(jar: File, from: Source): Boolean = keepLines(runCatching { jar.readLines() }.getOrDefault(emptyList()), from)

    /** As [keep], from cookies.txt lines. */
    fun keepLines(lines: List<String>, from: Source): Boolean = store(lines, from)

    /** As [keepLines]; with [from] null, the cookies of the sign-in already kept, renewed. */
    @Synchronized
    private fun store(lines: List<String>, from: Source?): Boolean {
        val ours = lines.filter { line ->
            val domain = domainOf(line) ?: return@filter false
            KEPT_DOMAINS.any { domain == it || domain.endsWith(".$it") }
        }
        val signedIn = ours.any { line ->
            val fields = line.split('\t')
            fields.size >= 7 && domainOf(line)?.endsWith("youtube.com") == true && fields[5] in SIGN_IN_COOKIES
        }
        if (!signedIn) return false
        val text = buildString {
            appendLine("# Netscape HTTP Cookie File")
            appendLine("# Lucense's YouTube sign-in. It signs in as your account: keep it to yourself.")
            ours.forEach { appendLine(it) }
        }
        val next = File(dir, "cookies.next")
        next.writeText(text)
        Files.move(next.toPath(), kept.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        if (from != null) keptFrom.writeText(from.name)
        return true
    }

    /** The domain a cookies.txt line is for, or null for a comment or a blank line. */
    private fun domainOf(line: String): String? {
        val text = line.removePrefix("#HttpOnly_")
        if (text.isBlank() || text.startsWith("#")) return null
        return text.substringBefore('\t').trim().trimStart('.').lowercase().takeIf { it.isNotEmpty() && '\t' in text }
    }

    /** Drops the kept sign-in. YouTube itself isn't told; the session simply goes unused. */
    fun forget() {
        kept.delete()
        keptFrom.delete()
        File(dir, "runs").deleteRecursively()
    }

    /** A browser's copy is old enough to renew, or is of another browser, or there is none. */
    fun needsRenewal(): Boolean {
        val chosen = source
        if (!chosen.copied) return false
        val at = keptAt() ?: return true
        return keptSource() != chosen || System.currentTimeMillis() - at > RENEW_AFTER_MS
    }

    /** A page only a signed-in account can see: the subscriptions feed, one entry of it. */
    val CHECK_ARGUMENTS = listOf("--flat-playlist", "-I", "1", "--print", "%(id)s", "--no-warnings", "--ignore-config", ":ytsubs")

    /** The cookies that say a sign-in is there; the same set the phone looks for. */
    val SIGN_IN_COOKIES = setOf("SAPISID", "__Secure-3PAPISID", "__Secure-1PAPISID", "LOGIN_INFO", "SID", "__Secure-3PSID")

    private val KEPT_DOMAINS = listOf("youtube.com", "google.com")

    /** A browser's copy is renewed after this long, in case the browser has moved its sign-in on. */
    const val RENEW_AFTER_MS = 3L * 24 * 60 * 60 * 1000

    private const val RUN_COPY_KEPT_MS = 24L * 60 * 60 * 1000

    /** Run copies of the kept sign-in, whose renewed cookies are taken back, and of anything else. */
    private const val KEPT_RUN = "kept-"
    private const val OTHER_RUN = "other-"

    /** A finished run's copy is left this long first, in case yt-dlp is still writing it. */
    private const val SETTLED_MS = 3_000L

    /** What a check's error says, in words that fit the setting. */
    fun explain(error: String?, chosen: Source): String {
        val said = error.orEmpty()
        val browser = chosen.label
        return when {
            said.isBlank() -> when {
                chosen.copied -> "Signed in. Lucense keeps a copy of $browser's sign-in and renews it every few days, " +
                    "so $browser only needs to be closed now and then, when a copy is made."
                chosen == Source.OWN -> "Signed in: YouTube shows this account's pages."
                else -> "Signed in: YouTube shows this account's pages."
            }
            said.contains("Login details are needed", true) || said.contains("requires authentication", true) ->
                when (chosen) {
                    Source.FILE -> "That file has no YouTube sign-in in it. Export it again from a browser that is signed in to YouTube."
                    Source.OWN -> "That sign-in has run out or was signed out of. Sign in again."
                    else -> "$browser isn't signed in to YouTube. Sign in at youtube.com in $browser, then check again."
                }
            said.contains("could not find", true) && said.contains("cookies", true) ->
                "$browser doesn't seem to be installed for this Windows user."
            said.contains("DPAPI", true) || said.contains("decrypt", true) ->
                "$browser keeps its cookies encrypted so other programs can't read them. Use Sign in here, Firefox, or a cookies.txt file."
            said.contains("Could not copy", true) || said.contains("database is locked", true) || said.contains("Permission denied", true) ->
                "$browser locks its cookies while it is open. Close $browser and try again - Lucense keeps a copy, so " +
                    "this is only needed now and then - or use Sign in here."
            said.contains("not a bot", true) ->
                "YouTube still asked to confirm this isn't a bot, so the sign-in didn't take. Sign in again."
            else -> said.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("ERROR") }
                ?.removePrefix("ERROR:")?.trim()?.take(200) ?: "The check didn't finish."
        }
    }
}
