package com.exo.musicplayer.data.download

import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

/**
 * A proxy for downloads only, as typed in Settings.
 *
 * YouTube judges the address a download comes from, and refuses many cheap VPN
 * servers outright. A phone's VPN app often also offers a local proxy -
 * v2rayNG and Hiddify listen on 127.0.0.1:10808 by default - which can point at
 * a different, cleaner server than the one everything else goes through. This
 * sends yt-dlp, and nothing else, through that.
 *
 * Accepts socks5, socks5h, socks4, socks4a, http and https, or a bare
 * host:port, which is taken as SOCKS5. SOCKS5 is handed to yt-dlp as socks5h,
 * which looks site names up at the proxy: on a network that blocks YouTube by
 * DNS, looking them up locally fails before the proxy is reached.
 */
object DownloadProxy {

    private val SCHEMES = setOf("socks5", "socks5h", "socks4", "socks4a", "http", "https")

    /** The proxy as yt-dlp wants it, or null for none or for something that isn't one. */
    fun normalize(text: String?): String? {
        val raw = text?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val withScheme = if ("://" in raw) raw else "socks5h://$raw"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme !in SCHEMES || uri.host.isNullOrBlank() || uri.port !in 1..65535) return null
        val wire = if (scheme == "socks5") "socks5h" else scheme
        return "$wire://${uri.host}:${uri.port}"
    }

    /** The same proxy for the app's own lookups, such as reading a link's title. */
    fun javaProxy(text: String?): Proxy? {
        val url = normalize(text) ?: return null
        val uri = URI(url)
        // Unresolved, so the proxy looks the name up, as socks5h does for yt-dlp.
        val address = InetSocketAddress.createUnresolved(uri.host, uri.port)
        return if (uri.scheme.startsWith("socks")) Proxy(Proxy.Type.SOCKS, address) else Proxy(Proxy.Type.HTTP, address)
    }
}
