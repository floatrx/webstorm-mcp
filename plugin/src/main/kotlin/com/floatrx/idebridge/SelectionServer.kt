package com.floatrx.idebridge

import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

/**
 * HTTP server that exposes IDE editor selection via REST endpoint.
 * Uses Java's built-in HTTP server to avoid dependency conflicts with IntelliJ.
 */
class SelectionServer {
    private val logger = Logger.getInstance(SelectionServer::class.java)
    private var server: HttpServer? = null
    private val port = 63343

    fun start() {
        try {
            server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0).apply {
                createContext("/api/selection") { exchange ->
                    val selection = SelectionService.getSelection()
                    val json = selection?.toJson() ?: emptySelectionJson()

                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
                    exchange.sendResponseHeaders(200, json.toByteArray().size.toLong())
                    exchange.responseBody.use { it.write(json.toByteArray()) }
                }

                createContext("/api/health") { exchange ->
                    val json = """{"status":"ok","plugin":"ide-bridge","version":"1.0.0"}"""
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, json.toByteArray().size.toLong())
                    exchange.responseBody.use { it.write(json.toByteArray()) }
                }

                executor = null
                start()
            }
            logger.info("IDE Bridge: Server started on http://127.0.0.1:$port")
        } catch (e: Exception) {
            logger.error("IDE Bridge: Failed to start server", e)
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
        logger.info("IDE Bridge: Server stopped")
    }

    private fun emptySelectionJson(): String = """
        {"text":"","filePath":"","startLine":0,"endLine":0,"startColumn":0,"endColumn":0,"language":"","projectName":""}
    """.trimIndent()
}
