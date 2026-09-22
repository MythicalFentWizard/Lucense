package com.exo.musicplayer.desktop.data

import com.exo.musicplayer.data.net.Http

/** The newest build published on GitHub, and whether this one is behind it. */
object Updates {

    data class Release(val version: String, val url: String)

    /**
     * Every release, newest first, rather than /releases/latest: these builds go
     * out as pre-releases, which that endpoint leaves out entirely.
     */
    private const val RELEASES =
        "https://api.github.com/repos/MythicalFentWizard/Resonate/releases?per_page=5"

    private val TAG = Regex("\"tag_name\"\\s*:\\s*\"v?([0-9]+(?:\\.[0-9]+)*)\"")
    private val PAGE = Regex("\"html_url\"\\s*:\\s*\"(https://github\\.com/[^\"]*/releases/tag/[^\"]+)\"")

    fun newest(): Release? {
        val body = Http.get(RELEASES, mapOf("Accept" to "application/vnd.github+json")) ?: return null
        val version = TAG.find(body)?.groupValues?.get(1) ?: return null
        val page = PAGE.find(body)?.groupValues?.get(1)
            ?: "https://github.com/MythicalFentWizard/Resonate/releases"
        return Release(version, page)
    }

    /**
     * Compared number by number, so 1.20.0 is newer than 1.9.3 where a string
     * comparison would have it the other way about. A build whose version is not
     * a number - a developer build - is never told it is behind.
     */
    fun isNewer(candidate: String, current: String): Boolean {
        val theirs = candidate.split(".").mapNotNull { it.toIntOrNull() }
        val ours = current.split(".").mapNotNull { it.toIntOrNull() }
        if (theirs.isEmpty() || ours.isEmpty()) return false
        for (index in 0 until maxOf(theirs.size, ours.size)) {
            val a = theirs.getOrElse(index) { 0 }
            val b = ours.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
