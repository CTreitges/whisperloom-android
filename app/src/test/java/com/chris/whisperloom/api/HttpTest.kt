package com.chris.whisperloom.api

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/**
 * Tests gegen einen lokalen JDK-HttpServer (keine Abhaengigkeit). Robolectric nur, weil
 * Http fuer die Fehlermeldung org.json braucht.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HttpTest {

    private lateinit var server: HttpServer
    private var lastAuth: String? = "unset"
    private var lastAgent: String? = null
    private var lastBody = ""

    @Before fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/ok") { ex ->
            lastAuth = ex.requestHeaders.getFirst("Authorization")
            lastAgent = ex.requestHeaders.getFirst("User-Agent")
            lastBody = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val out = """{"text":"hi"}""".toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.createContext("/unauthorized") { ex ->
            ex.requestBody.readBytes()
            val out = """{"error":{"message":"invalid api key"}}""".toByteArray()
            ex.sendResponseHeaders(401, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.createContext("/missing") { ex ->
            ex.requestBody.readBytes()
            val out = "not here".toByteArray()
            ex.sendResponseHeaders(404, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.createContext("/slow") { ex ->
            ex.requestBody.readBytes()
            Thread.sleep(1_500)
            runCatching {
                ex.sendResponseHeaders(200, 2)
                ex.responseBody.use { it.write("{}".toByteArray()) }
            }
        }
        server.start()
    }

    @After fun stopServer() {
        server.stop(0)
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    @Test fun ohneKeyKeinAuthorizationHeader() {
        val body = Http.post(url("/ok"), apiKey = "", contentType = "text/plain") { it.write("x".toByteArray()) }
        assertEquals("""{"text":"hi"}""", body)
        assertNull(lastAuth)
        assertEquals("x", lastBody)
    }

    @Test fun mitKeyBearerHeader() {
        Http.post(url("/ok"), apiKey = "sk-test", contentType = "text/plain") { it.write("x".toByteArray()) }
        assertEquals("Bearer sk-test", lastAuth)
    }

    @Test fun leerzeichenKeyZaehltAlsKeinKey() {
        Http.post(url("/ok"), apiKey = "   ", contentType = "text/plain") { it.write("x".toByteArray()) }
        assertNull(lastAuth)
    }

    /** ollama.com sperrt "Dalvik/…" mit 403 — jeder Request traegt deshalb die App-Kennung. */
    @Test fun eigenerUserAgentStattDalvik() {
        Http.post(url("/ok"), apiKey = "", contentType = "text/plain") { it.write("x".toByteArray()) }
        assertTrue(lastAgent!!, lastAgent!!.startsWith("WhisperLoom/"))
        lastAgent = null
        Http.get(url("/ok"), apiKey = "")
        assertTrue(lastAgent!!, lastAgent!!.startsWith("WhisperLoom/"))
    }

    @Test fun ollamaFehlerformWirdLesbar() {
        assertEquals("Unauthorized", Http.errorDetail("""{"error":"Unauthorized"}"""))
        assertEquals("invalid api key", Http.errorDetail("""{"error":{"message":"invalid api key"}}"""))
        assertEquals("keine Antwort", Http.errorDetail(""))
    }

    @Test fun fehlerstatusWirdLesbar() {
        try {
            Http.post(url("/unauthorized"), apiKey = "falsch", contentType = "text/plain") { it.write("x".toByteArray()) }
            fail("401 erwartet")
        } catch (e: ApiHttpException) {
            assertEquals(401, e.code)
            assertEquals("invalid api key", e.detail)
            assertTrue(e.message!!.contains("API-Key"))
        }
    }

    @Test fun nichtJsonFehlerBodyBleibtAlsText() {
        try {
            Http.post(url("/missing"), apiKey = "", contentType = "text/plain") { it.write("x".toByteArray()) }
            fail("404 erwartet")
        } catch (e: ApiHttpException) {
            assertEquals(404, e.code)
            assertEquals("not here", e.detail)
            assertTrue(e.message!!.contains("/v1"))
        }
    }

    @Test fun readTimeoutWirdZurZeitueberschreitung() {
        try {
            Http.post(url("/slow"), apiKey = "", contentType = "text/plain", readTimeoutMs = 200) {
                it.write("x".toByteArray())
            }
            fail("Timeout erwartet")
        } catch (e: ApiNetworkException) {
            assertTrue(e.message, e.message!!.contains("Zeitüberschreitung"))
            assertTrue(e.cause is java.net.SocketTimeoutException)
        }
    }

    @Test fun verbindungVerweigertWirdErklaert() {
        // Port 1 lauscht niemand.
        try {
            Http.post("http://127.0.0.1:1/x", apiKey = "", contentType = "text/plain") { it.write("x".toByteArray()) }
            fail("ConnectException erwartet")
        } catch (e: ApiNetworkException) {
            assertTrue(e.message, e.message!!.contains("nicht erreichbar"))
        }
    }

    @Test fun ungueltigeUrlWirdZumNetzfehler() {
        try {
            Http.post("", apiKey = "", contentType = "text/plain") { }
            fail("Netzfehler erwartet")
        } catch (e: ApiNetworkException) {
            // MalformedURLException -> lesbare Meldung statt Absturz
            assertTrue(e.cause is java.net.MalformedURLException)
        }
    }

    @Test fun endpunktTrimmtSchraegstriche() {
        assertEquals("https://x/v1/audio/transcriptions", Http.endpoint(" https://x/v1/ ", "/audio/transcriptions"))
        assertEquals("https://x/v1/chat/completions", Http.endpoint("https://x/v1", "/chat/completions"))
    }
}
