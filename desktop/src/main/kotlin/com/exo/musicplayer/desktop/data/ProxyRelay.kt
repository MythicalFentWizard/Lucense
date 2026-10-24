package com.exo.musicplayer.desktop.data

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URI

/**
 * An HTTP proxy on this PC only, passing each connection on along Lucense's
 * own route - for ffmpeg, which can use an HTTP proxy but can't speak SOCKS.
 *
 * Most of what ffmpeg plays is a single file, fetched here and handed to it
 * on stdin. A segmented stream (HLS) can't be: its playlist names the pieces,
 * and ffmpeg goes and gets each one itself. Pointed at this relay, it gets
 * them through the same proxy as everything else, from the same address the
 * playlist was issued to - YouTube's stream addresses only work from there.
 */
object ProxyRelay {

    private var server: ServerSocket? = null

    /**
     * The address to give ffmpeg's `-http_proxy` for [target], or null when
     * Lucense goes direct to it. An HTTP proxy is given as it is; a SOCKS one
     * through the relay, which is started the first time it is needed.
     */
    fun forFfmpeg(target: String): String? {
        val route = routeFor(target) ?: return null
        if (route.type() == Proxy.Type.HTTP) {
            val address = route.address() as? InetSocketAddress ?: return null
            return "http://${address.hostString}:${address.port}"
        }
        return "http://127.0.0.1:${start()}"
    }

    private fun routeFor(target: String): Proxy? =
        runCatching { NetworkProxy.proxiesFor(URI(target)) }.getOrNull()?.firstOrNull { it.type() != Proxy.Type.DIRECT }

    @Synchronized
    private fun start(): Int {
        server?.takeIf { !it.isClosed }?.let { return it.localPort }
        val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        server = socket
        Thread({
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: continue
                Thread({ runCatching { serve(client) }; runCatching { client.close() } }, "lucense-relay").apply {
                    isDaemon = true
                    start()
                }
            }
        }, "lucense-relay-accept").apply {
            isDaemon = true
            start()
        }
        return socket.localPort
    }

    /** One connection: a CONNECT tunnel for https, or a plain request forwarded for http. */
    private fun serve(client: Socket) {
        client.soTimeout = TIMEOUT_MS
        val fromClient = client.getInputStream()
        val head = readHead(fromClient) ?: return
        val requestLine = head.substringBefore("\r\n")
        val parts = requestLine.split(' ')
        if (parts.size < 3) return

        if (parts[0].equals("CONNECT", ignoreCase = true)) {
            val host = parts[1].substringBeforeLast(':')
            val port = parts[1].substringAfterLast(':').toIntOrNull() ?: 443
            val upstream = open(host, port, "https://$host/") ?: run {
                client.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                return
            }
            upstream.use {
                client.getOutputStream().apply {
                    write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray())
                    flush()
                }
                splice(fromClient, client.getOutputStream(), upstream)
            }
        } else {
            // "GET http://host/path HTTP/1.1": sent on as "GET /path", the rest as it came.
            val uri = runCatching { URI(parts[1]) }.getOrNull() ?: return
            val host = uri.host ?: return
            val port = if (uri.port > 0) uri.port else 80
            val path = (uri.rawPath?.ifEmpty { "/" } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
            val upstream = open(host, port, parts[1]) ?: run {
                client.getOutputStream().write("HTTP/1.1 502 Bad Gateway\r\n\r\n".toByteArray())
                return
            }
            upstream.use {
                val rest = head.substringAfter("\r\n")
                upstream.getOutputStream().apply {
                    write("${parts[0]} $path ${parts[2]}\r\n$rest".toByteArray())
                    flush()
                }
                splice(fromClient, client.getOutputStream(), upstream)
            }
        }
    }

    /** A connection to [host]:[port] along Lucense's route for [target]. */
    private fun open(host: String, port: Int, target: String): Socket? = runCatching {
        val route = routeFor(target) ?: Proxy.NO_PROXY
        Socket(if (route.type() == Proxy.Type.SOCKS) route else Proxy.NO_PROXY).apply {
            soTimeout = TIMEOUT_MS
            connect(InetSocketAddress.createUnresolved(host, port).takeIf { route.type() == Proxy.Type.SOCKS } ?: InetSocketAddress(host, port), CONNECT_MS)
        }
    }.getOrNull()

    /** Both ways at once until either side closes. */
    private fun splice(fromClient: InputStream, toClient: OutputStream, upstream: Socket) {
        val up = Thread({
            runCatching { fromClient.copyTo(upstream.getOutputStream(), BUFFER) }
            runCatching { upstream.shutdownOutput() }
        }, "lucense-relay-up").apply {
            isDaemon = true
            start()
        }
        runCatching { upstream.getInputStream().copyTo(toClient, BUFFER) }
        runCatching { toClient.flush() }
        up.join(1_000)
    }

    /** The request line and headers, up to the blank line; null if the client gave up first. */
    private fun readHead(input: InputStream): String? {
        val bytes = java.io.ByteArrayOutputStream()
        var matched = 0
        val end = byteArrayOf(13, 10, 13, 10)
        while (bytes.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            bytes.write(b)
            matched = if (b.toByte() == end[matched]) matched + 1 else if (b.toByte() == end[0]) 1 else 0
            if (matched == end.size) return bytes.toString(Charsets.ISO_8859_1.name())
        }
        return null
    }

    private const val TIMEOUT_MS = 30_000
    private const val CONNECT_MS = 15_000
    private const val BUFFER = 1 shl 16
    private const val MAX_HEAD = 64 * 1024
}
