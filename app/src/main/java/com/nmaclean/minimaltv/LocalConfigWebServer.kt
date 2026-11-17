package com.nmaclean.minimaltv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LocalConfigWebServer(
    private val preferences: HomeAssistantPreferences,
    private val onConfigSaved: (String) -> Unit,
    private val preferredPort: Int = DEFAULT_PORT,
    private val mainThreadPoster: (() -> Unit) -> Unit = { action ->
        Handler(Looper.getMainLooper()).post(action)
    }
) {
    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var running = false

    private var acceptThread: Thread? = null
    private var clientExecutor: ExecutorService? = null

    val listeningPort: Int
        get() = serverSocket?.localPort ?: -1

    fun start(): Int {
        if (running) return listeningPort
        val socket = bindServerSocket(preferredPort)
        serverSocket = socket
        running = true
        clientExecutor = Executors.newCachedThreadPool()

        acceptThread = Thread({
            while (running) {
                try {
                    val client = socket.accept()
                    clientExecutor?.execute { handleClient(client) }
                } catch (_: IOException) {
                    if (!running) break
                }
            }
        }, "LocalConfigWebServer-Accept").apply {
            isDaemon = true
            start()
        }

        return socket.localPort
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: IOException) {
            // ignore
        }
        serverSocket = null
        acceptThread?.interrupt()
        acceptThread = null
        clientExecutor?.shutdownNow()
        clientExecutor = null
    }

    private fun bindServerSocket(port: Int): ServerSocket {
        return try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        } catch (_: IOException) {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(0))
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 10_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            try {
                val requestLine = readLine(input) ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    writeHtmlResponse(output, 400, "Bad Request", errorHtml("Malformed HTTP request."))
                    return
                }

                val method = parts[0].uppercase()
                val rawPath = parts[1].substringBefore("?")

                var contentLength = 0
                while (true) {
                    val headerLine = readLine(input) ?: break
                    if (headerLine.isEmpty()) break
                    val colonIndex = headerLine.indexOf(':')
                    if (colonIndex > 0) {
                        val name = headerLine.substring(0, colonIndex).trim()
                        val value = headerLine.substring(colonIndex + 1).trim()
                        if (name.equals("Content-Length", ignoreCase = true)) {
                            contentLength = value.toIntOrNull()?.coerceIn(0, MAX_BODY_BYTES) ?: 0
                        }
                    }
                }

                val body = if (contentLength > 0) {
                    val buffer = ByteArray(contentLength)
                    var totalRead = 0
                    while (totalRead < contentLength) {
                        val read = input.read(buffer, totalRead, contentLength - totalRead)
                        if (read == -1) break
                        totalRead += read
                    }
                    String(buffer, 0, totalRead, StandardCharsets.UTF_8)
                } else {
                    ""
                }

                when {
                    method == "GET" && (rawPath == "/" || rawPath == "/index.html") -> {
                        writeHtmlResponse(output, 200, "OK", buildFormHtml(preferences.getUrl()))
                    }
                    method == "POST" && rawPath == "/save" -> {
                        val result = handleSavePost(body, preferences)
                        if (result.success) {
                            mainThreadPoster { onConfigSaved(result.savedUrl) }
                            writeHtmlResponse(output, 200, "OK", savedHtml(result.savedUrl))
                        } else {
                            writeHtmlResponse(output, 400, "Bad Request", errorHtml(result.message))
                        }
                    }
                    else -> {
                        writeHtmlResponse(output, 404, "Not Found", errorHtml("Page not found."))
                    }
                }
            } catch (e: Exception) {
                runCatching {
                    writeHtmlResponse(output, 500, "Internal Server Error", errorHtml(e.message ?: "Unexpected server error."))
                }
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        var b = input.read()
        if (b == -1) return null
        while (b != -1) {
            if (b == '\n'.code) break
            if (b != '\r'.code) {
                bytes.write(b)
            }
            if (bytes.size() >= MAX_HEADER_LINE_BYTES) break
            b = input.read()
        }
        return bytes.toString(StandardCharsets.UTF_8.name())
    }

    private fun writeHtmlResponse(output: OutputStream, statusCode: Int, statusText: String, html: String) {
        val bodyBytes = html.toByteArray(StandardCharsets.UTF_8)
        val headers = buildString {
            append("HTTP/1.1 ").append(statusCode).append(' ').append(statusText).append("\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ").append(bodyBytes.size).append("\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            append("\r\n")
        }
        output.write(headers.toByteArray(StandardCharsets.US_ASCII))
        output.write(bodyBytes)
        output.flush()
    }

    data class SaveResult(
        val success: Boolean,
        val savedUrl: String = "",
        val message: String = ""
    )

    companion object {
        const val DEFAULT_PORT = 8080
        private const val MAX_HEADER_LINE_BYTES = 8192
        private const val MAX_BODY_BYTES = 65536

        fun parseFormUrlEncoded(body: String): Map<String, String> {
            if (body.isBlank()) return emptyMap()
            val result = linkedMapOf<String, String>()
            for (pair in body.split("&")) {
                if (pair.isEmpty()) continue
                val eqIndex = pair.indexOf('=')
                val rawKey = if (eqIndex >= 0) pair.substring(0, eqIndex) else pair
                val rawVal = if (eqIndex >= 0) pair.substring(eqIndex + 1) else ""
                val key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8.name())
                val value = URLDecoder.decode(rawVal, StandardCharsets.UTF_8.name())
                result[key] = value
            }
            return result
        }

        fun handleSavePost(body: String, preferences: HomeAssistantPreferences): SaveResult {
            val params = parseFormUrlEncoded(body)
            val rawUrl = params["url"] ?: params["ha_url"] ?: ""
            val rawToken = params["token"] ?: params["ha_token"] ?: ""

            val normalizedUrl = HomeAssistantPreferences.normalizeUrl(rawUrl)
            val trimmedToken = rawToken.trim()

            if (normalizedUrl.isEmpty() || trimmedToken.isEmpty()) {
                return SaveResult(
                    success = false,
                    message = "Both Home Assistant URL and Long-Lived Access Token are required."
                )
            }

            preferences.save(normalizedUrl, trimmedToken)
            return SaveResult(
                success = true,
                savedUrl = normalizedUrl,
                message = "Saved! You can close this page."
            )
        }

        fun getLocalIpv4Address(context: Context): String? {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            if (activeNetwork != null) {
                val caps = cm.getNetworkCapabilities(activeNetwork)
                val isLocalTransport = caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    )
                val linkProps = cm.getLinkProperties(activeNetwork)
                val activeIp = linkProps?.linkAddresses
                    ?.map { it.address }
                    ?.filterIsInstance<Inet4Address>()
                    ?.firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    ?.hostAddress
                if (activeIp != null && (isLocalTransport || caps == null)) {
                    return activeIp
                }
            }

            return findInterfaceIpv4Address()
        }

        private fun findInterfaceIpv4Address(): String? {
            return try {
                val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                val candidates = mutableListOf<Inet4Address>()
                for (iface in interfaces) {
                    if (!iface.isUp || iface.isLoopback) continue
                    for (addr in iface.inetAddresses.toList()) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                            candidates.add(addr)
                        }
                    }
                }
                candidates.firstOrNull { it.isSiteLocalAddress }?.hostAddress
                    ?: candidates.firstOrNull()?.hostAddress
            } catch (_: Exception) {
                null
            }
        }

        private fun escapeHtml(text: String): String {
            return buildString(text.length) {
                for (ch in text) {
                    when (ch) {
                        '&' -> append("&amp;")
                        '<' -> append("&lt;")
                        '>' -> append("&gt;")
                        '"' -> append("&quot;")
                        '\'' -> append("&#39;")
                        else -> append(ch)
                    }
                }
            }
        }

        fun buildFormHtml(currentUrl: String): String {
            val escapedUrl = escapeHtml(currentUrl)
            return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>MinimalTV — Home Assistant Setup</title>
                  <style>
                    body {
                      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                      background: #0B0F17;
                      color: #F8FAFC;
                      margin: 0;
                      padding: 24px 16px;
                    }
                    .card {
                      max-width: 480px;
                      margin: 0 auto;
                      background: #131A26;
                      border: 1px solid #24324D;
                      border-radius: 16px;
                      padding: 24px;
                    }
                    h1 {
                      font-size: 20px;
                      margin: 0 0 8px 0;
                    }
                    p {
                      color: #94A3B8;
                      font-size: 14px;
                      margin: 0 0 20px 0;
                    }
                    label {
                      display: block;
                      font-size: 13px;
                      font-weight: 600;
                      margin: 14px 0 6px 0;
                      color: #CBD5E1;
                    }
                    input[type="url"], input[type="text"], textarea {
                      width: 100%;
                      box-sizing: border-box;
                      background: #0F172A;
                      color: #FFFFFF;
                      border: 1px solid #334155;
                      border-radius: 10px;
                      padding: 12px;
                      font-size: 15px;
                    }
                    textarea {
                      min-height: 110px;
                      resize: vertical;
                      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
                    }
                    button {
                      margin-top: 20px;
                      width: 100%;
                      background: #38BDF8;
                      color: #0B0F17;
                      font-weight: 700;
                      font-size: 16px;
                      border: none;
                      border-radius: 10px;
                      padding: 13px;
                      cursor: pointer;
                    }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>MinimalTV Setup</h1>
                    <p>Paste your Home Assistant URL and Long-Lived Access Token below.</p>
                    <form method="POST" action="/save">
                      <label for="url">Home Assistant URL</label>
                      <input id="url" name="url" type="text" value="$escapedUrl" placeholder="" required />

                      <label for="token">Long-Lived Access Token</label>
                      <textarea id="token" name="token" placeholder="Paste your Home Assistant Long-Lived Access Token here" required></textarea>

                      <button type="submit">Save Configuration</button>
                    </form>
                  </div>
                </body>
                </html>
            """.trimIndent()
        }

        private fun savedHtml(savedUrl: String): String {
            val escapedUrl = escapeHtml(savedUrl)
            return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>MinimalTV — Saved</title>
                  <style>
                    body {
                      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                      background: #0B0F17;
                      color: #F8FAFC;
                      margin: 0;
                      padding: 24px 16px;
                    }
                    .card {
                      max-width: 480px;
                      margin: 0 auto;
                      background: #131A26;
                      border: 1px solid #22C55E;
                      border-radius: 16px;
                      padding: 24px;
                    }
                    h1 { color: #22C55E; font-size: 20px; margin: 0 0 10px 0; }
                    p { color: #CBD5E1; font-size: 14px; line-height: 1.5; }
                    a { color: #38BDF8; text-decoration: none; font-size: 14px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>Saved! You can close this page.</h1>
                    <p>Configured Home Assistant URL: <strong>$escapedUrl</strong></p>
                    <p><a href="/">← Back to form</a></p>
                  </div>
                </body>
                </html>
            """.trimIndent()
        }

        private fun errorHtml(message: String): String {
            val escapedMsg = escapeHtml(message)
            return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1" />
                  <title>MinimalTV — Error</title>
                  <style>
                    body {
                      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                      background: #0B0F17;
                      color: #F8FAFC;
                      margin: 0;
                      padding: 24px 16px;
                    }
                    .card {
                      max-width: 480px;
                      margin: 0 auto;
                      background: #131A26;
                      border: 1px solid #F87171;
                      border-radius: 16px;
                      padding: 24px;
                    }
                    h1 { color: #F87171; font-size: 20px; margin: 0 0 10px 0; }
                    p { color: #CBD5E1; font-size: 14px; }
                    a { color: #38BDF8; text-decoration: none; font-size: 14px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>Could not save configuration</h1>
                    <p>$escapedMsg</p>
                    <p><a href="/">← Try again</a></p>
                  </div>
                </body>
                </html>
            """.trimIndent()
        }
    }
}
