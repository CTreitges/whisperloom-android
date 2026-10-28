package com.chris.whisperloom.simple.realtime

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Timer
import java.util.TimerTask

/**
 * Eine Echtzeit-Sitzung (Konzept §6.1, §11.2): Token holen, WebSocket oeffnen, Audio-Stuecke senden, vorlaeufigen
 * und festen Text melden, beim Fertig-Tipp `commit: true` senden und auf den letzten festen Text warten.
 *
 * Rueckrufe kommen auf OkHttp-Threads; der Aufrufer (Tastatur) schiebt sie auf main. Android-frei, damit die
 * Tests mit einem Fake-WebSocket-Server (MockWebServer) laufen.
 *
 * Context7: OkHttp (square.github.io/okhttp) — OkHttpClient.newWebSocket(Request, WebSocketListener),
 * WebSocketListener.onMessage/onClosing/onClosed/onFailure; `wss:`-URLs werden von Request.Builder.url angenommen.
 */
class RealtimeSession(
    private val client: OkHttpClient,
    private val adapter: RealtimeAdapter,
    private val tokens: TokenCache,
    private val options: RealtimeOptions,
    private val listener: Listener,
    private val sampleRate: Int = 16_000,
    /** Kommt nach dem Fertig-Commit kein fester Text, schliesst die Sitzung nach dieser Zeit trotzdem. */
    private val finishTimeoutMs: Long = 3_000,
) : RealtimeLink {

    interface Listener {
        fun onOpen()
        fun onPartial(text: String)
        fun onCommitted(text: String)
        /** Anbieter-Fehlercode ([ElevenLabsRealtime.ERROR_TYPES]) oder [ERROR_NO_TOKEN]; danach folgt [onClosed]. */
        fun onError(code: String)
        /**
         * Verbindung zu. [finished] = regulaer nach dem Fertig-Commit; sonst Abbruch (Netz, Fehler, Zeitlimit).
         * [pending] = Audio seit dem letzten festen Text — der Puffer fuer den Batch-Rueckfall (Konzept §6.4).
         */
        fun onClosed(finished: Boolean, pending: ByteArray)
    }

    private var ws: WebSocket? = null
    private val pending = ByteArrayOutputStream()
    private val timer = Timer("loom-simple-finish", true)

    @Volatile private var finishing = false
    @Volatile private var done = false

    override fun start(): Boolean {
        val token = try {
            tokens.current()
        } catch (e: IOException) {
            // Kein Token = keine Verbindung: Fehler melden UND schliessen, damit die Tastatur nicht im
            // Verbinden haengen bleibt (Emulator-Test 2026-10-28: "Moment …" blieb stehen).
            listener.onError(ERROR_NO_TOKEN)
            deliverClosed(finished = false)
            return false
        }
        // Einmal-Token: mit dem Verbinden verbraucht; die naechste Sitzung holt ein neues.
        tokens.invalidate()
        val request = Request.Builder().url(adapter.url(token.wsBase, token.token, options)).build()
        ws = client.newWebSocket(request, socketListener)
        return true
    }

    override fun sendAudio(pcm: ByteArray) {
        val socket = ws ?: return
        if (finishing || done) return
        synchronized(pending) { pending.write(pcm) }
        socket.send(adapter.audioMessage(pcm, sampleRate, commit = false))
    }

    override fun finish() {
        val socket = ws ?: return
        if (finishing || done) return
        finishing = true
        socket.send(adapter.audioMessage(ByteArray(0), sampleRate, commit = true))
        timer.schedule(object : TimerTask() {
            override fun run() { socket.close(CLOSE_NORMAL, "timeout") }
        }, finishTimeoutMs)
    }

    override fun cancel() {
        if (done) return
        done = true
        timer.cancel()
        ws?.cancel()
    }

    private val socketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            listener.onOpen()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            when (val event = adapter.parse(text)) {
                is RealtimeEvent.Partial -> listener.onPartial(event.text)
                is RealtimeEvent.Committed -> {
                    synchronized(pending) { pending.reset() }
                    listener.onCommitted(event.text)
                    if (finishing) webSocket.close(CLOSE_NORMAL, "done")
                }
                is RealtimeEvent.Error -> {
                    listener.onError(event.code)
                    webSocket.close(CLOSE_NORMAL, event.code)
                }
                RealtimeEvent.Started, RealtimeEvent.Ignored -> Unit
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(CLOSE_NORMAL, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            deliverClosed(finished = finishing)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            deliverClosed(finished = false)
        }
    }

    private fun deliverClosed(finished: Boolean) {
        if (done) return
        done = true
        timer.cancel()
        listener.onClosed(finished, synchronized(pending) { pending.toByteArray() })
    }

    companion object {
        const val ERROR_NO_TOKEN = "no_token"
        private const val CLOSE_NORMAL = 1000
    }
}
