package com.chris.whisperloom

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.api.Http
import com.chris.whisperloom.api.NetworkCheck
import com.chris.whisperloom.api.ProviderCatalog
import com.chris.whisperloom.api.WavUpload
import com.chris.whisperloom.llm.FakeTextModel
import com.chris.whisperloom.llm.LocalTextEngine
import com.chris.whisperloom.llm.deviceRam
import com.chris.whisperloom.llm.fakeTextModels
import com.chris.whisperloom.llm.installSparse
import com.chris.whisperloom.llm.resetTextEngine
import com.chris.whisperloom.whisper.ModelCatalog
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.TextModelCatalog
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Die Textverbesserung nach der Entscheidungstabelle, end-to-end ueber [TranscriptionEngine.transcribe]
 * und [SharedRefine.run]: Offline-Erkennung als Fake-Backend, Netz als Fake, das lokale Textmodell
 * als Fake — und der eigene Online-Zugang als echter lokaler HttpServer, der jede Anfrage zaehlt.
 * Was hier "0 Anfragen" heisst, ist also wirklich nicht rausgegangen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RefinePipelineTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs
    private lateinit var server: HttpServer

    private val originalBackend = TranscriptionEngine.backendFactory
    private val originalNetwork = RefinePlan.networkCheck
    private val originalModel = LocalTextEngine.factory
    private val e2b = TextModelCatalog.GEMMA4_E2B

    private val chatRequests = AtomicInteger()
    private val sttRequests = AtomicInteger()
    @Volatile private var chatStatus = 200
    @Volatile private var chatHold: CountDownLatch? = null
    private val chatArrived = CountDownLatch(1)

    /** Fake-Netz: [net] = aktives, validiertes Netz. */
    @Volatile private var net = true
    private var localAnswer: (String, String) -> String = { _, _ -> "Lokal verbessert." }
    private lateinit var made: MutableList<FakeTextModel>

    private val speech = FloatArray(AudioUtils.SAMPLE_RATE) { 0.3f }

    /** Erkennt offline (simuliert) — die echte whisper-Bibliothek gibt es in der JVM nicht. */
    private class OfflineFake : TranscriptionBackend {
        override val label = "Offline · Small"
        override fun transcribe(upload: WavUpload, language: String) = TranscriptResult("also ähm hallo welt")
    }

    @Before fun aufbau() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { ex ->
            chatRequests.incrementAndGet()
            ex.requestBody.readBytes()
            chatArrived.countDown()
            chatHold?.await(10, TimeUnit.SECONDS)
            val out = (if (chatStatus == 200) """{"choices":[{"message":{"content":"Online verbessert."}}]}""" else """{"error":{"message":"kaputt"}}""").toByteArray()
            ex.sendResponseHeaders(chatStatus, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.createContext("/v1/audio/transcriptions") { ex ->
            sttRequests.incrementAndGet()
            ex.requestBody.readBytes()
            val out = """{"text":"also ähm hallo welt"}""".toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()

        TranscriptionEngine.backendFactory = { p, prompt ->
            if (p.engine == Engine.OFFLINE) OfflineFake() else TranscriptionEngine.backend(p, prompt)
        }
        RefinePlan.networkCheck = { NetworkCheck { _, _ -> net } }
        LocalTextEngine.init(ctx)
        made = fakeTextModels { system, user -> localAnswer(system, user) }

        prefs.language = "de"
        prefs.refineMode = RefineMode.POLISH
    }

    @After fun abbau() {
        chatHold?.countDown()
        server.stop(0)
        TranscriptionEngine.backendFactory = originalBackend
        RefinePlan.networkCheck = originalNetwork
        resetTextEngine(originalModel)
        ModelStore(ctx).dir.deleteRecursively()
    }

    // --- Aufbau ------------------------------------------------------------------------------

    /** Offline-Erkennung eingerichtet (whisper-Modell da), Regel [rule]. */
    private fun offline(rule: OfflineRefineRule) {
        installSparse(ctx, ModelCatalog.SMALL)
        prefs.engine = Engine.OFFLINE
        prefs.offlineRefine = rule
    }

    /** Lokales Textmodell geladen, Geraet mit 8 GB RAM. */
    private fun localModel() {
        installSparse(ctx, e2b)
        deviceRam(ctx, 8)
    }

    /** Eigener Online-Zugang fuer die Textverbesserung: der lokale HttpServer. */
    private fun ownAccess() {
        prefs.llmProviderId = "custom"
        prefs.llmUrl = "http://127.0.0.1:${server.address.port}/v1"
        prefs.llmModel = "qwen3:8b"
    }

    private class Run(val text: String, val skipped: String?, val note: String?, val started: Boolean)

    private fun transcribe(skip: RefineSkip? = null, onStart: () -> Unit = {}): Run {
        var skipped: String? = null
        var note: String? = null
        var started = false
        val text = TranscriptionEngine.transcribe(
            ctx,
            speech,
            skip = skip,
            onRefineStart = {
                started = true
                onStart()
            },
            onRefineNote = { note = it },
        ) { skipped = it }
        return Run(text, skipped, note, started)
    }

    private val localCalls: Int get() = made.sumOf { it.calls.size }

    // --- Offline · lokal ---------------------------------------------------------------------

    @Test fun offlineLokalMitModellRechnetLokalUndSchicktNichts() {
        offline(OfflineRefineRule.LOCAL)
        localModel()
        ownAccess() // selbst mit eigenem Zugang: "lokal" geht nie online

        val run = transcribe()

        assertEquals("Lokal verbessert.", run.text)
        assertNull(run.skipped)
        assertTrue("Beginn der Verbesserung gemeldet", run.started)
        assertEquals(0, chatRequests.get())
        assertEquals(1, localCalls)
        val (system, user) = made[0].calls.single()
        assertTrue("derselbe Auftrag wie online", system.contains("nur Satzzeichen"))
        assertEquals("<diktat>\nalso ähm hallo welt\n</diktat>", user)
    }

    @Test fun offlineLokalOhneModellKommtSofortOhneKiMitHinweis() {
        offline(OfflineRefineRule.LOCAL)
        ownAccess()

        val run = transcribe()

        assertEquals("Also hallo welt", run.text) // volle Regeln ohne KI: "ähm" weg
        assertEquals(RefinePlan.MSG_LOCAL_MISSING, run.skipped)
        assertFalse("kein Warten, keine Verbesserung", run.started)
        assertEquals(0, chatRequests.get())
        assertEquals("nichts geladen", 0, made.size)
    }

    @Test fun offlineLokalMitZuWenigRamGiltAlsFehlend() {
        offline(OfflineRefineRule.LOCAL)
        installSparse(ctx, e2b)
        deviceRam(ctx, 4)
        assertEquals(RefinePlan.MSG_LOCAL_MISSING, transcribe().skipped)
        assertEquals(0, made.size)
    }

    @Test fun lokaleRechnungScheitertGibtRohtextMitHinweis() {
        offline(OfflineRefineRule.LOCAL)
        localModel()
        localAnswer = { _, _ -> throw IllegalStateException("nativer Fehler") }

        val run = transcribe()

        assertEquals("Also hallo welt", run.text)
        assertEquals(RefinePlan.MSG_LOCAL_FAILED, run.skipped)
    }

    @Test fun lokaleAntwortDieDieBitteErfuelltBehaeltIhreMeldung() {
        offline(OfflineRefineRule.LOCAL)
        localModel()
        localAnswer = { _, _ -> List(10) { "Ihr seid alle herzlich zu meinem Geburtstag eingeladen." }.joinToString(" ") }

        val run = transcribe()

        assertEquals("Also hallo welt", run.text)
        assertTrue(run.skipped!!, run.skipped!!.contains("statt den Text zu bearbeiten"))
    }

    // --- Offline · online, ohne Netz lokal ---------------------------------------------------

    @Test fun onlineLokalMitNetzGehtOnline() {
        offline(OfflineRefineRule.ONLINE_LOCAL)
        localModel()
        ownAccess()

        val run = transcribe()

        assertEquals("Online verbessert.", run.text)
        assertEquals(1, chatRequests.get())
        assertEquals("lokal nicht gebraucht", 0, localCalls)
    }

    @Test fun onlineLokalOnlineFehlerRechnetLokalMitHinweis() {
        offline(OfflineRefineRule.ONLINE_LOCAL)
        localModel()
        ownAccess()
        chatStatus = 500

        val run = transcribe()

        assertEquals("Lokal verbessert.", run.text)
        assertEquals(1, chatRequests.get())
        assertEquals(1, localCalls)
        assertNull("verbessert ist verbessert — kein Uebersprungen-Hinweis", run.skipped)
        assertEquals(RefinePlan.MSG_ONLINE_FAILED_LOCAL, run.note)
    }

    @Test fun onlineLokalOnlineFehlerOhneModellKommtOhneKi() {
        offline(OfflineRefineRule.ONLINE_LOCAL)
        ownAccess()
        chatStatus = 500

        val run = transcribe()

        assertEquals("Also hallo welt", run.text)
        assertTrue(run.skipped!!, run.skipped!!.contains("500"))
        assertNull(run.note)
    }

    @Test fun onlineLokalOhneNetzRechnetLokalOhneAnfrage() {
        offline(OfflineRefineRule.ONLINE_LOCAL)
        localModel()
        ownAccess()
        net = false

        assertEquals("Lokal verbessert.", transcribe().text)
        assertEquals(0, chatRequests.get())
    }

    @Test fun onlineLokalOhneEigenenZugangRechnetLokal() {
        offline(OfflineRefineRule.ONLINE_LOCAL)
        localModel()

        assertEquals("Lokal verbessert.", transcribe().text)
        assertEquals(0, chatRequests.get())
    }

    @Test fun onlineLokalOhneNetzUndOhneModellKommtOhneKiMitHinweis() {
        offline(OfflineRefineRule.ONLINE_LOCAL)
        ownAccess()
        net = false

        val run = transcribe()

        assertEquals(RefinePlan.MSG_LOCAL_MISSING, run.skipped)
        assertEquals(0, chatRequests.get())
    }

    @Test fun mitAusweichloesungNurAchtSekundenConnectTimeout() {
        assertEquals(8_000, RefinePlan.connectTimeoutMs(RefineRoute.Online(fallbackLocal = true)))
        assertEquals(Http.CONNECT_TIMEOUT_MS, RefinePlan.connectTimeoutMs(RefineRoute.Online(fallbackLocal = false)))
        assertEquals(15_000, Http.CONNECT_TIMEOUT_MS)
    }

    // --- Offline · ueberspringen -------------------------------------------------------------

    @Test fun ueberspringenMitNetzGehtOnline() {
        offline(OfflineRefineRule.SKIP)
        localModel()
        ownAccess()

        assertEquals("Online verbessert.", transcribe().text)
        assertEquals(1, chatRequests.get())
        assertEquals(0, localCalls)
    }

    @Test fun ueberspringenOnlineFehlerKommtOhneKiNieLokal() {
        offline(OfflineRefineRule.SKIP)
        localModel()
        ownAccess()
        chatStatus = 500

        val run = transcribe()

        assertEquals("Also hallo welt", run.text)
        assertTrue(run.skipped!!.contains("500"))
        assertEquals(0, localCalls)
    }

    /** Regression Haenger: Offline-Erkennung, eigener Zugang, kein Netz — es darf nichts rausgehen. */
    @Test fun ueberspringenOhneNetzSchicktKeineAnfrage() {
        offline(OfflineRefineRule.SKIP)
        ownAccess()
        net = false

        val run = transcribe()

        assertEquals("Also hallo welt", run.text)
        assertEquals(RefinePlan.MSG_NO_NET, run.skipped)
        assertFalse(run.started)
        assertEquals("ohne Netz keine Anfrage", 0, chatRequests.get())
    }

    @Test fun ueberspringenOhneEigenenZugangKommtOhneKiUndOhneHinweis() {
        offline(OfflineRefineRule.SKIP)
        localModel()

        val run = transcribe()

        assertEquals("Also hallo welt", run.text)
        assertNull("so gewaehlt", run.skipped)
        assertEquals(0, chatRequests.get())
        assertEquals(0, localCalls)
    }

    // --- Online-Erkennung --------------------------------------------------------------------

    private fun onlineRecognition() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "http://127.0.0.1:${server.address.port}/v1"
        prefs.llmModel = "qwen3:8b"
    }

    @Test fun onlineErkennungVerbessertOnlineWieBisher() {
        onlineRecognition()
        localModel()
        assertEquals("Online verbessert.", transcribe().text)
        assertEquals(1, chatRequests.get())
        assertEquals("die Offline-Regel gilt hier nicht", 0, localCalls)
    }

    /** Regression Haenger: faellt das Netz nach der Erkennung weg, geht keine Verbesserung mehr raus. */
    @Test fun onlineErkennungOhneNetzDanachKommtOhneKiOhneAnfrage() {
        onlineRecognition()
        net = false

        val run = transcribe()

        assertEquals(1, sttRequests.get())
        assertEquals("Also hallo welt", run.text)
        assertEquals(RefinePlan.MSG_NO_NET, run.skipped)
        assertEquals(0, chatRequests.get())
    }

    /**
     * Review c6: WLAN, das Android nicht validiert (Pruef-URL gesperrt), ohne mobile Daten. Die
     * Online-Erkennung lief gerade darueber — die Verbesserung darf es auch; nur die Offline-Erkennung
     * verlangt VALIDATED. Mit der echten Netzpruefung, nicht dem Fake.
     */
    @Test fun onlineErkennungBrauchtKeinValidiertesNetz() {
        RefinePlan.networkCheck = originalNetwork
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        shadowOf(cm).setNetworkCapabilities(
            cm.activeNetwork,
            ShadowNetworkCapabilities.newInstance().also { shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) },
        )
        prefs.sttProviderId = ProviderCatalog.OPENAI_ID // Text "wie Erkennung": api.openai.com, nicht im eigenen Netz
        prefs.apiKey = "sk-test"

        prefs.engine = Engine.ONLINE
        assertEquals(RefineRoute.Online(fallbackLocal = false), RefinePlan.of(ctx, prefs, RefineMode.POLISH).route)

        offline(OfflineRefineRule.SKIP)
        prefs.llmProviderId = ProviderCatalog.OPENAI_ID
        prefs.llmKey = "sk-test"
        assertEquals(RefineRoute.Raw(RefineHint.NO_NET), RefinePlan.of(ctx, prefs, RefineMode.POLISH).route)
    }

    @Test fun stufeAusFragtWederNetzNochModell() {
        offline(OfflineRefineRule.LOCAL)
        localModel()
        prefs.refineMode = RefineMode.OFF
        RefinePlan.networkCheck = { NetworkCheck { _, _ -> throw AssertionError("Netz gefragt") } }

        val run = transcribe()

        assertEquals("Also hallo welt", run.text)
        assertNull(run.skipped)
        assertFalse(run.started)
        assertEquals(0, made.size)
    }

    // --- Ausweg: Tipp "ohne KI" --------------------------------------------------------------

    @Test fun abbruchTippLiefertSofortDenRohtextUndBrichtLokalAb() {
        offline(OfflineRefineRule.LOCAL)
        localModel()
        LocalTextEngine.generate(e2b.id, "s", "laden") // geladen, damit der Fake schon existiert
        val model = made[0]
        model.holdNext()
        val skip = RefineSkip()
        var tipp: Thread? = null

        val run = transcribe(skip) {
            tipp = thread {
                model.started.await(5, TimeUnit.SECONDS)
                skip.skip()
            }
        }

        assertEquals("Also hallo welt", run.text)
        assertNull("so gewollt — kein Hinweis", run.skipped)
        tipp!!.join(5_000)
        assertEquals("lokal abgebrochen (cancelProcess)", 1, model.cancels)
        assertEquals("nie close mitten in der Rechnung", 0, model.closes)
    }

    @Test fun abbruchTippVerwirftDieLaufendeOnlineAnfrage() {
        offline(OfflineRefineRule.SKIP)
        ownAccess()
        chatHold = CountDownLatch(1) // der Server antwortet erst, wenn der Test es erlaubt
        val skip = RefineSkip()

        val run = transcribe(skip) {
            thread {
                chatArrived.await(5, TimeUnit.SECONDS)
                skip.skip()
            }
        }

        assertEquals("Rohtext, obwohl die Anfrage noch laeuft", "Also hallo welt", run.text)
        assertEquals(1, chatRequests.get())
        assertNull(run.skipped)
    }

    @Test fun ohneAbbruchLiefertDerAuswegDasErgebnis() {
        offline(OfflineRefineRule.LOCAL)
        localModel()
        assertEquals("Lokal verbessert.", transcribe(RefineSkip()).text)
    }

    @Test fun fehlerKommtAuchMitAuswegAlsHinweis() {
        offline(OfflineRefineRule.LOCAL)
        localModel()
        localAnswer = { _, _ -> throw IllegalStateException("nativer Fehler") }
        val run = transcribe(RefineSkip())
        assertEquals("Also hallo welt", run.text)
        assertEquals(RefinePlan.MSG_LOCAL_FAILED, run.skipped)
    }

    // --- Vorwaermen beim Aufnahmestart -------------------------------------------------------

    private fun waitLoaded() {
        val until = System.currentTimeMillis() + 5_000
        while (!LocalTextEngine.isLoaded && System.currentTimeMillis() < until) Thread.sleep(10)
    }

    @Test fun vorwaermenNurWennLokalVerbessertWird() {
        offline(OfflineRefineRule.LOCAL)
        localModel()
        TranscriptionEngine.warmUp(ctx)
        waitLoaded()
        assertTrue(LocalTextEngine.isLoaded)
    }

    @Test fun keinVorwaermenBeiOnlineUeberspringenOderAus() {
        localModel()
        ownAccess()

        onlineRecognition()
        TranscriptionEngine.warmUp(ctx)
        offline(OfflineRefineRule.SKIP)
        TranscriptionEngine.warmUp(ctx)
        offline(OfflineRefineRule.ONLINE_LOCAL) // mit Netz geht es online
        TranscriptionEngine.warmUp(ctx)
        offline(OfflineRefineRule.LOCAL)
        prefs.refineMode = RefineMode.OFF
        TranscriptionEngine.warmUp(ctx)

        Thread.sleep(200)
        assertFalse(LocalTextEngine.isLoaded)
        assertEquals(0, made.size)
    }

    // --- Geteilte Audios ---------------------------------------------------------------------

    /** Was onStart gemeldet hat: true = lokal, false = online, null = nie gerufen. */
    private var startedLocal: Boolean? = null

    private fun share(parts: List<String> = listOf("Erstes Stück.", "Zweites Stück.")) =
        SharedRefine.run(ctx, prefs, parts, "de", onStart = { startedLocal = it })

    @Test fun geteilteAudiosOfflineLokal() {
        offline(OfflineRefineRule.LOCAL)
        localModel()
        ownAccess()
        prefs.shareRefineMode = RefineMode.POLISH

        val result = share()

        assertEquals(listOf("Lokal verbessert.", "Lokal verbessert."), result.paragraphs)
        assertNull(result.skipped)
        assertEquals("je Stueck eine Rechnung", 2, localCalls)
        assertEquals(0, chatRequests.get())
        assertEquals("Fortschritt: \"Text wird lokal verbessert …\"", true, startedLocal)
        assertEquals("kein Ausweg noetig", false, result.localFallback)
    }

    @Test fun geteilteAudiosOnlineMeldenDenStartAlsOnline() {
        offline(OfflineRefineRule.ONLINE_LOCAL)
        localModel()
        ownAccess()
        prefs.shareRefineMode = RefineMode.POLISH

        val result = share()

        assertEquals(listOf("Online verbessert.", "Online verbessert."), result.paragraphs)
        assertEquals(false, startedLocal)
        assertEquals(false, result.localFallback)
    }

    @Test fun geteilteAudiosOhneModellOhneKiMitHinweis() {
        offline(OfflineRefineRule.LOCAL)
        prefs.shareRefineMode = RefineMode.POLISH

        val result = share()

        assertEquals(RefineMode.POLISH, result.mode)
        assertNull(result.paragraphs)
        assertEquals(RefinePlan.MSG_LOCAL_MISSING, result.skipped)
    }

    @Test fun geteilteAudiosOhneNetzSchickenNichts() {
        offline(OfflineRefineRule.SKIP)
        ownAccess()
        net = false
        prefs.shareRefineMode = RefineMode.POLISH

        val result = share()

        assertEquals(RefinePlan.MSG_NO_NET, result.skipped)
        assertEquals(0, chatRequests.get())
    }

    @Test fun geteilteAudiosUeberspringenOhneZugangWieAus() {
        offline(OfflineRefineRule.SKIP)
        prefs.shareRefineMode = RefineMode.POLISH
        assertEquals(SharedRefine.Result(RefineMode.OFF, null, null), share())
    }

    @Test fun geteilteAudiosNachOnlineFehlerDieRestlichenStueckeGleichLokal() {
        offline(OfflineRefineRule.ONLINE_LOCAL)
        localModel()
        ownAccess()
        chatStatus = 500
        prefs.shareRefineMode = RefineMode.POLISH

        val result = share()

        assertEquals(listOf("Lokal verbessert.", "Lokal verbessert."), result.paragraphs)
        assertEquals("nur ein Timeout, nicht je Stueck", 1, chatRequests.get())
        assertEquals(2, localCalls)
        assertEquals("Share-Ansicht: online fehlgeschlagen, lokal verbessert", true, result.localFallback)
        assertNull(result.skipped)
    }
}
