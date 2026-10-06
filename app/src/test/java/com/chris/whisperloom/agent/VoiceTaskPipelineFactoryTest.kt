package com.chris.whisperloom.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.ProviderCatalog
import com.chris.whisperloom.llm.OfflineRefineFixture
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
    private val kennungen = mutableListOf<Pair<String, String>>()

    /** Welcher Endpunkt den Auftrag bekam — je Widget ein eigener Pfad vor /v1/task. */
    private val pfade = mutableListOf<String>()
    private val basis get() = "http://127.0.0.1:${server.address.port}"

    @Before fun aufbauen() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
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
        listOf("/v1/task", "/a/v1/task", "/b/v1/task").forEach { pfad ->
            server.createContext(pfad) { ex ->
                val json = org.json.JSONObject(String(ex.requestBody.readBytes()))
                gesendet += json.getString("transcript")
                kennungen += json.getString("request_id") to json.getString("recorded_at")
                pfade += ex.requestURI.path
                antwort(ex, 202, """{"status":"accepted"}""")
            }
        }
        server.start()

        Prefs(ctx).apply {
            engine = Engine.ONLINE
            sttProviderId = ProviderCatalog.CUSTOM_ID
            apiBaseUrl = "http://127.0.0.1:${server.address.port}/v1"
            apiKey = "egal"
            apiModel = "whisper-1"
            refineMode = RefineMode.POLISH
        }
        // Das Standardprofil sendet an /v1/task — jeder Rueckfall darauf faellt in den Tests auf.
        serverEinrichten(ctx, url = basis)
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

    private fun auftrag(recordedAt: String = "2026-09-22T00:00:00Z", profileId: String = WidgetProfile.DEFAULT_ID) =
        store.begin(FloatArray(16_000) { i -> if (i % 2 == 0) 0.3f else -0.3f }, 4000, recordedAt, profileId)

    /** Ein weiteres Widget-Profil; [pfad] = eigener Server ("" = keiner). */
    private fun widget(name: String, pfad: String): WidgetProfile {
        val profiles = WidgetProfileStore(ctx)
        val p = profiles.create(name)
        return if (pfad.isEmpty()) p else serverEinrichten(ctx, p.id, url = basis + pfad)
    }

    /** Die ECHTE Fabrik, unveraendert — auch der Versand geht an den Testserver. */
    private fun lauf(): TaskOutcome {
        auftrag()
        return VoiceTaskWorker.pipelineFactory(ctx, store) { true }.run(null)
    }

    @Test fun kennungUndZeitpunktSindBeimBauEingefroren() {
        // #10: ein abgeloester Lauf haengt in der Erkennung, derweil entsteht ein neuer Auftrag.
        // Liest der alte Lauf die Kennung erst beim Senden, schickt er alten Text unter der
        // NEUEN Kennung — die Bridge claimt sie, der echte Auftrag geht als Duplikat verloren.
        veredelung = null // damit der Ausfall-Hinweis geschrieben wuerde
        val alt = auftrag("2026-09-22T00:00:00Z")
        val pipeline = VoiceTaskWorker.pipelineFactory(ctx, store) { true }
        val neu = auftrag("2026-09-22T00:05:00Z", widget("Arbeit", "/b").id)

        assertTrue(pipeline.run(null) is TaskOutcome.Sent)

        assertEquals(alt to "2026-09-22T00:00:00Z", kennungen.single())
        assertEquals("Auch der Server gehoert zum alten Auftrag", listOf("/v1/task"), pfade)
        assertEquals(neu, store.requestId)
        assertEquals("Der Hinweis des alten Laufs gehoert nicht zum neuen Auftrag", "", store.refineSkipped)
    }

    @Test fun einNichtMehrZustaendigerLaufErreichtDieBridgeNicht() {
        auftrag()
        val outcome = VoiceTaskWorker.pipelineFactory(ctx, store) { false }.run(null)
        assertTrue(outcome is TaskOutcome.Superseded)
        assertTrue("Nichts darf an die Bridge gegangen sein", kennungen.isEmpty())
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

    /**
     * Review c1: ein abgeloester Auftrag darf das lokale Textmodell nicht weiter belegen — seine
     * Verbesserung bricht ab, statt den neuen Auftrag dahinter warten zu lassen.
     */
    @Test fun einAbgeloesterAuftragRechnetNichtLokalWeiter() {
        val offline = OfflineRefineFixture(ctx)
        offline.setUp() // Offline-Erkennung (Fake), Regel "lokal", Glaetten, Textmodell als Fake
        try {
            auftrag("2026-09-22T00:00:00Z")
            val alt = VoiceTaskWorker.pipelineFactory(ctx, store) { false }
            auftrag("2026-09-22T00:05:00Z") // der neue Auftrag loest den alten ab

            assertTrue(alt.run(null) is TaskOutcome.Superseded)

            assertEquals("die verworfene Verbesserung rechnet nicht", 0, offline.made.sumOf { it.calls.size })
            assertEquals("kein Hinweis am neuen Auftrag", "", store.refineSkipped)
        } finally {
            offline.tearDown()
        }
    }

    // --- Server je Widget (3.7.1) --------------------------------------------------

    @Test fun derAuftragGehtAnDenServerSeinesWidgets() {
        widget("Einkauf", "/a")
        auftrag(profileId = widget("Arbeit", "/b").id)
        assertTrue(VoiceTaskWorker.pipelineFactory(ctx, store) { true }.run(null) is TaskOutcome.Sent)
        assertEquals(listOf("/b/v1/task"), pfade)
    }

    @Test fun einAuftragVonVorDemUpdateGehtAnsStandardprofil() {
        // Die Migration hat den alten, gemeinsamen Server ins Standardprofil kopiert.
        auftrag(profileId = "")
        assertTrue(VoiceTaskWorker.pipelineFactory(ctx, store) { true }.run(null) is TaskOutcome.Sent)
        assertEquals(listOf("/v1/task"), pfade)
    }

    @Test fun einGeloeschtesWidgetErreichtKeinenServer() {
        // R1: forWidget oder ein Rueckfall aufs Standardprofil schickte den Auftrag an einen fremden Server.
        val arbeit = widget("Arbeit", "/b")
        auftrag(profileId = arbeit.id)
        WidgetProfileStore(ctx).delete(arbeit.id)

        val outcome = VoiceTaskWorker.pipelineFactory(ctx, store) { true }.run(null)

        assertEquals(ctx.getString(R.string.widget_task_profile_gone), (outcome as TaskOutcome.Failed).reason)
        assertTrue("Der Poster darf nie aufgerufen werden: $pfade", pfade.isEmpty())
    }

    @Test fun mitProWidgetsAusGehtNichtsRaus() {
        Prefs(ctx).proWidgetsEnabled = false
        auftrag()

        val outcome = VoiceTaskWorker.pipelineFactory(ctx, store) { true }.run(null)

        assertEquals(ctx.getString(R.string.widget_task_pro_off), (outcome as TaskOutcome.Failed).reason)
        assertEquals("Der bezahlte Text kommt zum Cachen zurueck", "Kauf bitte Milch.", outcome.text)
        assertTrue(pfade.isEmpty())
    }

    @Test fun ohneServerImWidgetGehtNichtsRaus() {
        auftrag(profileId = widget("Einkauf", "").id)

        val outcome = VoiceTaskWorker.pipelineFactory(ctx, store) { true }.run(null)

        assertEquals(ctx.getString(R.string.widget_task_no_server, "Einkauf"), (outcome as TaskOutcome.Failed).reason)
        assertTrue("Statt des Standardprofils: gar nichts", pfade.isEmpty())
    }

    @Test fun eineKorrekturDesServersWirktBeimNaechstenVersuch() {
        val einkauf = widget("Einkauf", "/a")
        auftrag(profileId = einkauf.id)
        val pipeline = VoiceTaskWorker.pipelineFactory(ctx, store) { true }
        serverEinrichten(ctx, einkauf.id, url = "$basis/b")

        assertTrue(pipeline.run(null) is TaskOutcome.Sent)
        assertEquals(listOf("/b/v1/task"), pfade)
    }
}
