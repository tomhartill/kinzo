package com.tomhartill.kinzo.account

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI

/**
 * Browser sign-in for the desktop development build: opens the default browser and listens on
 * a loopback port for the API to send it back.
 */
class DesktopBrowserSignIn : BrowserSignIn {
    override suspend fun open(startUrl: (redirectUri: String) -> String): String? = withContext(Dispatchers.IO) {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        val redirect = "http://127.0.0.1:${server.address.port}/callback"
        val result = CompletableDeferred<String>()
        server.createContext("/callback") { exchange ->
            val page = "<!doctype html><meta charset=utf-8><title>Kinzo</title>" +
                "<body style='font-family:sans-serif;padding:40px'><h2>You can close this tab and go back to Kinzo.</h2>"
            val bytes = page.encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            result.complete(redirect + (exchange.requestURI.rawQuery?.let { "?$it" } ?: ""))
        }
        server.start()
        try {
            Desktop.getDesktop().browse(URI(startUrl(redirect)))
            withTimeoutOrNull(5 * 60_000L) { result.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Kinzo: couldn't open the browser to sign in: $e")
            null
        } finally {
            server.stop(0)
        }
    }
}

class DesktopSharer : Sharer {
    override fun share(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }
}
