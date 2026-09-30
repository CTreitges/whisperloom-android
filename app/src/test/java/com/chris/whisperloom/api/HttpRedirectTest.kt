package com.chris.whisperloom.api

import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/**
 * Weiterleitungen und der Authorization-Header. Robolectric nur wegen org.json in der
 * Fehlerauswertung; die Verbindung ist eine echte auf 127.0.0.1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HttpRedirectTest {

    private lateinit var ziel: HttpServer
    private lateinit var start: HttpServer
    private var tokenBeimZiel: String? = null
    private var xiKeyBeimZiel: String? = null
    private var zielAufrufe = 0

    @Before fun server() {
        ziel = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        ziel.createContext("/") { ex ->
            zielAufrufe++
            tokenBeimZiel = ex.requestHeaders.getFirst("Authorization")
            xiKeyBeimZiel = ex.requestHeaders.getFirst("xi-api-key")
            val body = """{"status":"accepted"}""".toByteArray()
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        ziel.start()

        start = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        start.createContext("/") { ex ->
            ex.responseHeaders.add("Location", "http://127.0.0.1:${ziel.address.port}/woanders")
            ex.sendResponseHeaders(302, -1)
            ex.close()
        }
        start.start()
    }

    @After fun stoppen() {
        start.stop(0)
        ziel.stop(0)
    }

    private fun url() = "http://127.0.0.1:${start.address.port}/v1/task"

    @Test fun eineWeiterleitungWirdVerfolgtAberOhneDasGeheimnis() {
        // Beobachtet auf dieser JVM: der fremde Host wird angefragt, der Authorization-Header
        // aber abgestreift. Android benutzt einen anderen HTTP-Unterbau — darauf verlaesst
        // sich die Bridge nicht, sie schaltet Weiterleitungen ganz ab (siehe AgentBridge).
        Http.post(url(), "geheim", "application/json", followRedirects = true) { it.write("{}".toByteArray()) }
        assertEquals(1, zielAufrufe)
        assertNull(tokenBeimZiel)
    }

    @Test fun ohneWeiterleitungBleibtDasGeheimnisHier() {
        val e = assertThrows(ApiHttpException::class.java) {
            Http.post(url(), "geheim", "application/json", followRedirects = false) { it.write("{}".toByteArray()) }
        }
        assertEquals(302, e.code)
        assertNull("Das Token darf den fremden Host nie erreichen", tokenBeimZiel)
    }

    @Test fun eigenerKeyHeaderWuerdeBeiWeiterleitungMitwandern() {
        // Beobachtet auf dieser JVM: bei GET streift die Weiterleitung nur Authorization ab, ein
        // eigener Header wie xi-api-key landet beim fremden Host. Deshalb followRedirects=false.
        Http.get(url(), "geheim", followRedirects = true, authHeader = "xi-api-key")
        assertEquals(1, zielAufrufe)
        assertEquals("geheim", xiKeyBeimZiel)
    }

    @Test fun eigenerKeyHeaderBleibtOhneWeiterleitungHier() {
        val post = assertThrows(ApiHttpException::class.java) {
            Http.post(url(), "geheim", "application/json", followRedirects = false, authHeader = "xi-api-key") {
                it.write("{}".toByteArray())
            }
        }
        assertEquals(302, post.code)
        val get = assertThrows(ApiHttpException::class.java) {
            Http.get(url(), "geheim", followRedirects = false, authHeader = "xi-api-key")
        }
        assertEquals(302, get.code)
        assertEquals("Der fremde Host wird nie angefragt", 0, zielAufrufe)
        assertNull(xiKeyBeimZiel)
    }

    @Test fun ohneTokenGehtGarKeinHeaderRaus() {
        Http.post("http://127.0.0.1:${ziel.address.port}/", "", "application/json") { it.write("{}".toByteArray()) }
        assertNull(tokenBeimZiel)
    }
}
