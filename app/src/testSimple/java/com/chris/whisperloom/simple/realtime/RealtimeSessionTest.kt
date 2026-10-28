package com.chris.whisperloom.simple.realtime

import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [RealtimeSession] gegen einen Fake-WebSocket-Server (OkHttp MockWebServer, Context7: square.github.io/okhttp —
 * MockResponse.withWebSocketUpgrade). Kein Aufruf von ElevenLabs, kein Key.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RealtimeSessionTest {

    private val server = MockWebServer()
    private val client = OkHttpClient()

    /** Die Server-Seite der Verbindung: zeichnet Nachrichten auf, kann senden und abbrechen. */
    private class ServerSide : WebSocketListener() {
        val opened = CountDownLatch(1)
        val received = CopyOnWriteArrayList<String>()
        @Volatile var socket: WebSocket? = null
        override fun onOpen(webSocket: WebSocket, response: Response) { socket = webSocket; opened.countDown() }
        override fun onMessage(webSocket: WebSocket, text: String) { received += text }
        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        fun send(json: String) = socket!!.send(json)
        fun awaitMessages(n: Int) {
            val until = System.currentTimeMillis() + 5_000
            while (received.size < n && System.currentTimeMillis() < until) Thread.sleep(10)
            assertTrue("nur ${received.size} von $n Nachrichten angekommen", received.size >= n)
        }
    }

    private class Spy : RealtimeSession.Listener {
        val opened = CountDownLatch(1)
        val partials = CopyOnWriteArrayList<String>()
        val committed = CopyOnWriteArrayList<String>()
        val errors = CopyOnWriteArrayList<String>()
        val closed = CountDownLatch(1)
        @Volatile var finished: Boolean? = null
        @Volatile var pending: ByteArray? = null
        override fun onOpen() = opened.countDown()
        override fun onPartial(text: String) { partials += text }
        override fun onCommitted(text: String) { committed += text }
        override fun onError(code: String) { errors += code }
        override fun onClosed(finished: Boolean, pending: ByteArray) {
            this.finished = finished
            this.pending = pending
            closed.countDown()
        }
        fun awaitClosed() = assertTrue("Verbindung nicht geschlossen", closed.await(5, TimeUnit.SECONDS))
    }

    private class FakeTokens(private vararg val tokens: SessionToken) : TokenSource {
        var calls = 0
        override fun fetch(): SessionToken {
            if (calls >= tokens.size) throw IOException("keine Tokens mehr")
            return tokens[calls++]
        }
    }

    private lateinit var serverSide: ServerSide
    private lateinit var spy: Spy

    @Before fun aufbau() {
        server.start()
        serverSide = ServerSide()
        spy = Spy()
        server.enqueue(MockResponse().withWebSocketUpgrade(serverSide))
    }

    @After fun abbau() {
        try {
            server.shutdown()
        } catch (_: Exception) {
            // schon im Test heruntergefahren (Verbindungsabbruch)
        }
    }

    // 127.0.0.1 statt server.url(): der Hostname des Rechners gilt nicht als lokaler Fake-Server (RealtimeSession.secure).
    private fun token(name: String = "tok", expiresInMs: Long = 600_000) =
        SessionToken(name, "http://127.0.0.1:${server.port}", System.currentTimeMillis() + expiresInMs)

    private fun session(tokens: TokenCache = TokenCache(FakeTokens(token())), finishTimeoutMs: Long = 3_000) =
        RealtimeSession(client, ElevenLabsRealtime, tokens, RealtimeOptions(), spy, finishTimeoutMs = finishTimeoutMs)

    private fun verbunden(s: RealtimeSession) {
        assertTrue(s.start())
        assertTrue(serverSide.opened.await(5, TimeUnit.SECONDS))
        assertTrue(spy.opened.await(5, TimeUnit.SECONDS))
    }

    private fun warteAuf(liste: List<String>, n: Int) {
        val until = System.currentTimeMillis() + 5_000
        while (liste.size < n && System.currentTimeMillis() < until) Thread.sleep(10)
        assertEquals(n, liste.size)
    }

    @Test fun partialWirdAlsVorlaeufigerTextGemeldet() {
        val s = session()
        verbunden(s)
        serverSide.send("""{"message_type":"partial_transcript","text":"Hallo"}""")
        serverSide.send("""{"message_type":"partial_transcript","text":"Hallo Anna"}""")
        warteAuf(spy.partials, 2)
        assertEquals(listOf("Hallo", "Hallo Anna"), spy.partials)
        s.cancel()
    }

    @Test fun committedWirdAlsFesterTextGemeldet() {
        val s = session()
        verbunden(s)
        serverSide.send("""{"message_type":"committed_transcript","text":"Hallo Anna."}""")
        warteAuf(spy.committed, 1)
        assertEquals(listOf("Hallo Anna."), spy.committed)
        s.cancel()
    }

    @Test fun audioGehtAlsChunkUndDerStoppSendetCommitTrue() {
        val s = session()
        verbunden(s)
        val audio = ByteArray(3_200) { it.toByte() }
        s.sendAudio(audio)
        s.finish()
        serverSide.awaitMessages(2)
        val chunk = JSONObject(serverSide.received[0])
        assertEquals("input_audio_chunk", chunk.getString("message_type"))
        assertFalse(chunk.getBoolean("commit"))
        assertEquals(16_000, chunk.getInt("sample_rate"))
        val stop = JSONObject(serverSide.received[1])
        assertTrue("commit: true beim Stopp", stop.getBoolean("commit"))
        // Der letzte feste Text kommt, dann geht die Verbindung regulaer zu.
        serverSide.send("""{"message_type":"committed_transcript","text":"Hallo."}""")
        spy.awaitClosed()
        assertEquals(listOf("Hallo."), spy.committed)
        assertEquals(true, spy.finished)
        assertEquals("nach dem festen Text ist der Puffer leer", 0, spy.pending!!.size)
    }

    @Test fun ohneFestenTextNachDemStoppSchliesstDieZeitgrenzeAlsNichtRegulaer() {
        // Review K2: die Zeitgrenze ist kein regulaeres Ende — der offene Text ist dann nur als Partial bekannt.
        val s = session(finishTimeoutMs = 300)
        verbunden(s)
        s.finish()
        spy.awaitClosed()
        assertEquals(false, spy.finished)
    }

    @Test fun abbruchWaehrendDesTokenAbrufsVerbindetNichtMehr() {
        // Review K3: Loeschen waehrend des (langsamen) Token-Abrufs — start() darf danach keinen Socket oeffnen.
        val langsam = object : TokenSource {
            override fun fetch(): SessionToken { Thread.sleep(300); return token() }
        }
        val s = session(TokenCache(langsam))
        // Ergebnis aus dem Neben-Thread einsammeln: ein assert dort saehe JUnit nicht.
        var started: Boolean? = null
        val t = Thread { started = s.start() }
        t.start()
        Thread.sleep(50)
        s.cancel()
        t.join(5_000)
        assertEquals("nach cancel() kein Start", false, started)
        assertEquals("kein Verbindungsaufbau", null, server.takeRequest(500, TimeUnit.MILLISECONDS))
        assertEquals("onOpen kam nie", 1L, spy.opened.count)
    }

    @Test fun unverschluesselteAdresseWirdAbgelehnt() {
        // Review W7: Audio und Token nur per wss; Klartext nur zum lokalen Fake-Server.
        assertTrue(RealtimeSession.secure("wss://api.elevenlabs.io"))
        assertTrue(RealtimeSession.secure("http://127.0.0.1:8080"))
        assertFalse(RealtimeSession.secure("ws://api.elevenlabs.io"))
        assertFalse(RealtimeSession.secure("http://evil.example"))
        val s = session(TokenCache(FakeTokens(SessionToken("t", "ws://api.example", System.currentTimeMillis() + 600_000))))
        assertFalse(s.start())
        assertEquals(listOf(RealtimeSession.ERROR_INSECURE), spy.errors)
        spy.awaitClosed()
    }

    @Test fun verbindungsabbruchFesterTextBleibtRestImPuffer() {
        val s = session()
        verbunden(s)
        val vorher = ByteArray(3_200) { 1 }
        s.sendAudio(vorher)
        serverSide.awaitMessages(1)
        serverSide.send("""{"message_type":"committed_transcript","text":"Hallo Anna."}""")
        warteAuf(spy.committed, 1)
        val danach = ByteArray(3_200) { 2 }
        s.sendAudio(danach)
        serverSide.awaitMessages(2)
        // Netz weg: der Server verschwindet ohne Close-Handshake (OkHttp meldet onFailure).
        // (socket.cancel() geht serverseitig nicht: MockWebServer-Sockets haben keinen Call.)
        server.shutdown()
        spy.awaitClosed()
        assertEquals(false, spy.finished)
        assertEquals("fester Text wurde geliefert", listOf("Hallo Anna."), spy.committed)
        assertArrayEquals("Puffer = Audio seit dem letzten festen Text", danach, spy.pending)
    }

    @Test fun anbieterFehlerWirdGemeldetUndSchliesst() {
        val s = session()
        verbunden(s)
        serverSide.send("""{"message_type":"quota_exceeded","error":"leer"}""")
        spy.awaitClosed()
        assertEquals(listOf("quota_exceeded"), spy.errors)
        assertEquals(false, spy.finished)
    }

    @Test fun abgelaufenesTokenWirdVorDemVerbindenErneuert() {
        val tokens = FakeTokens(token("alt", expiresInMs = -1_000), token("neu"))
        val cache = TokenCache(tokens)
        cache.prefetch() // beim Einblenden der Tastatur geholt — inzwischen abgelaufen
        assertEquals(1, tokens.calls)
        val s = session(cache)
        verbunden(s)
        assertEquals("ein zweites Token geholt", 2, tokens.calls)
        val request = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue("verbunden mit dem neuen Token: ${request.path}", request.path!!.contains("token=neu"))
        assertFalse(request.path!!.contains("token=alt"))
        s.cancel()
    }

    @Test fun ohneTokenKeinStartUndFehlerNoToken() {
        val s = session(TokenCache(NoTokenSource))
        assertFalse(s.start())
        assertEquals(listOf(RealtimeSession.ERROR_NO_TOKEN), spy.errors)
        // Regression (Emulator 2026-10-28): ohne onClosed blieb die Tastatur bei "Moment …" haengen.
        spy.awaitClosed()
        assertEquals(false, spy.finished)
    }

    @Test fun einmalTokenIstNachDemVerbindenVerbraucht() {
        val tokens = FakeTokens(token("eins"), token("zwei"))
        val cache = TokenCache(tokens)
        val s = session(cache)
        verbunden(s)
        s.cancel()
        assertEquals("zwei", cache.current().token)
        assertEquals(2, tokens.calls)
    }
}
