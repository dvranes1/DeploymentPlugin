package com.github.dvranes1.deploymentplugin.auth

import com.intellij.openapi.diagnostic.thisLogger
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import com.sun.net.httpserver.HttpServer

class LocalCallbackServer(
    private val port: Int,
    private val path: String = "/callback"
) {
    private var server: HttpServer? = null
    val result: CompletableFuture<Map<String, String>> = CompletableFuture()

    fun start() {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        http.executor = Executors.newSingleThreadExecutor()

        http.createContext(path) { exchange ->
            try {
                val query = exchange.requestURI.rawQuery.orEmpty()
                val params = parseQuery(query)

                val html = """
                    <html><body style="font-family: sans-serif">
                      <h3>Authentication completed</h3>
                      <p>You can return to WebStorm now.</p>
                    </body></html>
                """.trimIndent()

                exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                exchange.sendResponseHeaders(200, html.toByteArray().size.toLong())
                exchange.responseBody.use { it.write(html.toByteArray()) }

                result.complete(params)
            } catch (t: Throwable) {
                thisLogger().warn("Callback handling failed", t)
                result.completeExceptionally(t)
            }
        }

        server = http
        http.start()
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    fun redirectUri(): String = "http://127.0.0.1:$port$path"

    private fun parseQuery(q: String): Map<String, String> {
        if (q.isBlank()) return emptyMap()
        return q.split("&")
            .mapNotNull { part ->
                val idx = part.indexOf("=")
                if (idx <= 0) return@mapNotNull null
                val k = java.net.URLDecoder.decode(part.substring(0, idx), "UTF-8")
                val v = java.net.URLDecoder.decode(part.substring(idx + 1), "UTF-8")
                k to v
            }.toMap()
    }
}
