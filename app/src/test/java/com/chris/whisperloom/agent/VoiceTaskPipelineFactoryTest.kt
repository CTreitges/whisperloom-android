package com.chris.whisperloom.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.ProviderCatalog
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/**
 * Die ECHTE Verdrahtung des Auftrags — ohne Attrappe zwischen Worker und
 * [com.chris.whisperloom.TranscriptionEngine].
 *
 * Noetig geworden, weil der Worker-Test die Fabrik komplett ersetzt: eine Mutation, die
 * das Melden der ausgefallenen Textverbesserung entfernte, blieb dort unbemerkt. Hier
 * laeuft der gesamte Weg gegen einen echten kleinen HTTP-Server auf 127.0.0.1 — Erkennung,
 * Textverbesserung und Versand an die Bridge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceTaskPipelineFactoryTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: HttpServer
    private lateinit var store: VoiceTaskStore

    /** Antwort auf /chat/completions: null = Fehler 500 (Textverbesserung faellt aus). */
    private var veredelung: String? = """{"choices":[{"message":{"content":"Kauf bitte Milch."}}]}"""
    private val gesendet = mutableListOf<String>()

    @Before fun aufbauen() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        store = VoiceTaskStore(ctx)
        store.clear()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/audio/transcriptions") { ex ->
            ex.requestBody.readBytes()
            antwort(ex, 200, """{"text":"kauf milch"}""")
        }
        server.createContext("/v1/chat/completions") { ex ->
            ex.requestBody.readBytes()
            val body = veredelung
            if (body == null) antwort(ex, 500, """{"error":{"message":"Modell ueberlastet"}}""")
            else antwort(ex, 200, body)
        }
        server.createContext("/v1/task") { ex ->
            gesendet += org.json.JSONObject(String(ex.requestBody.readBytes())).getString("transcript")
            antwort(ex, 202, """{"status":"accepted"}""")
        }
        server.start()

        Prefs(ctx).apply {
            engine = Engine.ONLINE
            sttProviderId = ProviderCatalog.CUSTOM_ID
            apiBaseUrl = "http://127.0.0.1:${server.address.port}/v1"
            apiKey = "egal"
            apiModel = "whisper-1"
            refineMode = RefineMode.POLISH
            agentUrl = "http://127.0.0.1:${server.address.port}"
            agentToken = "geheim"
            agentEnabled = true
        }
    }

    @After fun abbauen() {
        server.stop(0)
    }

    private fun antwort(ex: com.sun.net.httpserver.HttpExchange, code: Int, body: String) {
        val b = body.toByteArray()
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(code, b.size.toLong())
        ex.responseBody.use { it.write(b) }
    }

    /** Die ECHTE Fabrik, unveraendert — auch der Versand geht an den Testserver. */
    private fun lauf(): TaskOutcome {
        store.begin(FloatArray(16_000) { i -> if (i % 2 == 0) 0.3f else -0.3f }, 4000, "2026-09-22T00:00:00Z")
        return VoiceTaskWorker.pipelineFactory(ctx, store).run(null)
    }

    @Test fun mitFunktionierenderVeredelungKommtDerVerbesserteTextAn() {
        val outcome = lauf()
        assertTrue(outcome is TaskOutcome.Sent)
        assertEquals("Kauf bitte Milch.", gesendet.single())
        assertEquals("Glaetten hat gegriffen — kein Hinweis noetig", "", store.refineSkipped)
    }

    @Test fun faelltDieVeredelungAusGehtDerRohtextRausUndEsWirdVermerkt() {
        veredelung = null
        val outcome = lauf()
        assertTrue("Ein bezahltes Diktat darf die Veredelung nie verschlucken", outcome is TaskOutcome.Sent)
        // Roh erkannt war "kauf milch" — die Nachbearbeitung (Gross-Schreibung) laeuft
        // trotzdem, nur die KI-Stufe faellt aus.
        assertEquals("Kauf milch", gesendet.single())
        assertTrue("Der Ausfall muss vermerkt sein", store.refineSkipped.isNotBlank())
        assertTrue(store.refineSkipped.contains("500"))
    }
}
