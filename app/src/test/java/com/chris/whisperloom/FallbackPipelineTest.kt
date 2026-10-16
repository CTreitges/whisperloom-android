package com.chris.whisperloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.api.ApiHttpException
import com.chris.whisperloom.api.NetworkCheck
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
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * Fallback (3.9.1) end-to-end ueber [TranscriptionEngine.transcribe] und [SharedRefine.run]: zwei
 * "eigene Server" auf einem lokalen HttpServer — der erste unter /erst/v1, der Fallback unter
 * /fallback/v1, jeder mit eigenem Zaehler und Status. Was "0 Anfragen" heisst, ist nicht rausgegangen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FallbackPipelineTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs
    private lateinit var server: HttpServer

    private val originalBackend = TranscriptionEngine.backendFactory
    private val originalNetwork = RefinePlan.networkCheck
    private val originalModel = LocalTextEngine.factory
    private lateinit var made: MutableList<FakeTextModel>

    /** Zaehler und Status je Server und Endpunkt. */
    private class Endpoint(@Volatile var status: Int, val answer: String) {
        val hits = AtomicInteger()
    }

    private val firstStt = Endpoint(200, """{"text":"erster anbieter"}""")
    private val fallbackStt = Endpoint(200, """{"text":"also ähm hallo welt"}""")
    private val firstChat = Endpoint(200, """{"choices":[{"message":{"content":"Vom ersten verbessert."}}]}""")
    private val fallbackChat = Endpoint(200, """{"choices":[{"message":{"content":"Vom Fallback verbessert."}}]}""")

    private val speech = FloatArray(AudioUtils.SAMPLE_RATE) { 0.3f }
    private val base get() = "http://127.0.0.1:${server.address.port}"

    @Before fun aufbau() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/erst/v1/audio/transcriptions") { answer(it, firstStt) }
        server.createContext("/fallback/v1/audio/transcriptions") { answer(it, fallbackStt) }
        server.createContext("/erst/v1/chat/completions") { answer(it, firstChat) }
        server.createContext("/fallback/v1/chat/completions") { answer(it, fallbackChat) }
        server.start()
        RefinePlan.networkCheck = { NetworkCheck { _, _ -> true } }
        LocalTextEngine.init(ctx)
        made = fakeTextModels { _, _ -> "Lokal verbessert." }
        deviceRam(ctx, 8)

        prefs.language = "de"
        prefs.refineMode = RefineMode.POLISH
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "$base/erst/v1"
        prefs.apiModel = "whisper-1"
        prefs.llmProviderId = "custom"
        prefs.llmUrl = "$base/erst/v1"
        prefs.llmModel = "qwen3:8b"
    }

    @After fun abbau() {
        server.stop(0)
        TranscriptionEngine.backendFactory = originalBackend
        RefinePlan.networkCheck = originalNetwork
        resetTextEngine(originalModel)
        ModelStore(ctx).dir.deleteRecursively()
    }

    private fun answer(ex: HttpExchange, endpoint: Endpoint) {
        endpoint.hits.incrementAndGet()
        ex.requestBody.readBytes()
        val out = (if (endpoint.status == 200) endpoint.answer else """{"error":{"message":"kaputt ${endpoint.status}"}}""").toByteArray()
        ex.sendResponseHeaders(endpoint.status, out.size.toLong())
        ex.responseBody.use { it.write(out) }
    }

    private fun sttFallback(model: String = "whisper-large-v3") {
        prefs.sttFallbackEnabled = true
        prefs.sttFallbackProviderId = "custom"
        prefs.sttFallbackUrl = "$base/fallback/v1"
        prefs.sttFallbackModel = model
    }

    private fun llmFallback(model: String = "gemma4:12b") {
        prefs.llmFallbackEnabled = true
        prefs.llmFallbackProviderId = "custom"
        prefs.llmFallbackUrl = "$base/fallback/v1"
        prefs.llmFallbackModel = model
    }

    private fun transcribe() = TranscriptionEngine.transcribe(ctx, speech)

    // --- Erkennung ---------------------------------------------------------------------------

    @Test fun ersterAnbieterErkenntFallbackBleibtStill() {
        sttFallback()
        val d = transcribe()
        assertEquals("erster anbieter", d.raw)
        assertEquals(0, fallbackStt.hits.get())
        assertNull(d.result.note)
    }

    @Test fun ersterScheitertFallbackErkenntMitHinweis() {
        sttFallback()
        firstStt.status = 503
        val d = transcribe()
        assertEquals(1, firstStt.hits.get())
        assertEquals(1, fallbackStt.hits.get())
        assertEquals("also ähm hallo welt", d.raw)
        assertEquals("Vom ersten verbessert.", d.text)
        assertEquals(TranscriptionEngine.sttFallbackMessage("Eigener Server (OpenAI-kompatibel)"), d.result.note)
    }

    @Test fun ohneFallbackBleibtEsBeimFehler() {
        firstStt.status = 503
        try {
            transcribe()
            fail("ohne Fallback kommt der Fehler wie bisher")
        } catch (e: ApiHttpException) {
            assertEquals(503, e.code)
        }
        assertEquals(0, fallbackStt.hits.get())
    }

    @Test fun ausgeschalteterFallbackSpringtNichtEin() {
        sttFallback()
        prefs.sttFallbackEnabled = false
        firstStt.status = 503
        runCatching { transcribe() }
        assertEquals(0, fallbackStt.hits.get())
    }

    @Test fun unvollstaendigerFallbackSpringtNichtEin() {
        sttFallback(model = "") // eigener Server ohne Modell: speaches/LocalAI lehnen ab
        firstStt.status = 503
        assertTrue(runCatching { transcribe() }.exceptionOrNull() is ApiHttpException)
        assertEquals(0, fallbackStt.hits.get())
    }

    @Test fun scheiternBeideKommtDerFehlerDesErsten() {
        sttFallback()
        firstStt.status = 401
        fallbackStt.status = 500
        try {
            transcribe()
            fail("beide gescheitert")
        } catch (e: ApiHttpException) {
            assertEquals(401, e.code)
            assertEquals(500, (e.suppressed.single() as ApiHttpException).code)
        }
    }

    // --- Textverbesserung --------------------------------------------------------------------

    @Test fun kiZugangScheitertFallbackVerbessertMitHinweis() {
        llmFallback()
        firstChat.status = 500
        val d = transcribe()
        assertEquals("Vom Fallback verbessert.", d.text)
        assertNull(d.result.skipped)
        assertEquals(RefinePlan.fallbackMessage("Eigener Server (OpenAI-kompatibel)"), d.result.note)
        assertEquals("der Fallback hat gerechnet", "gemma4:12b", d.result.model)
        assertEquals(1, firstChat.hits.get())
        assertEquals(1, fallbackChat.hits.get())
    }

    @Test fun kiZugangKlapptFallbackWirdNichtGefragt() {
        llmFallback()
        val d = transcribe()
        assertEquals("Vom ersten verbessert.", d.text)
        assertEquals("qwen3:8b", d.result.model)
        assertNull(d.result.note)
        assertEquals(0, fallbackChat.hits.get())
    }

    @Test fun scheiternBeideKommtDerTextOhneKiMitBeidenGruenden() {
        llmFallback()
        firstChat.status = 500
        fallbackChat.status = 401
        val d = transcribe()
        assertEquals("Erster anbieter", d.text) // Rohtext mit den vollen Regeln
        val skipped = requireNotNull(d.result.skipped)
        assertTrue(skipped, skipped.contains("API-Fehler 500"))
        assertTrue(skipped, skipped.contains("Fallback: API-Fehler 401"))
        assertNull(d.result.model)
    }

    @Test fun ohneFallbackKommtDerTextOhneKiWieBisher() {
        firstChat.status = 500
        val d = transcribe()
        assertEquals("Erster anbieter", d.text)
        assertTrue(requireNotNull(d.result.skipped).startsWith("API-Fehler 500"))
        assertEquals(0, fallbackChat.hits.get())
    }

    @Test fun unvollstaendigerFallbackDerTextverbesserungSpringtNichtEin() {
        llmFallback(model = "") // eigener Server ohne Modell: nicht bereit
        firstChat.status = 500
        assertTrue(requireNotNull(transcribe().result.skipped).startsWith("API-Fehler 500"))
        assertEquals(0, fallbackChat.hits.get())
    }

    @Test fun beideFallbacksInEinemDiktatNennenBeideHinweise() {
        sttFallback()
        llmFallback()
        firstStt.status = 503
        firstChat.status = 500
        val d = transcribe()
        assertEquals("Vom Fallback verbessert.", d.text)
        assertEquals(
            TranscriptionEngine.sttFallbackMessage("Eigener Server (OpenAI-kompatibel)") + " · " +
                RefinePlan.fallbackMessage("Eigener Server (OpenAI-kompatibel)"),
            d.result.note,
        )
    }

    /** Offline erkannt, Regel "Online, ohne Netz lokal": der Fallback kommt vor dem lokalen Textmodell. */
    @Test fun offlineOnlineLokalErstFallbackDannLokal() {
        TranscriptionEngine.backendFactory = { _, _ ->
            object : TranscriptionBackend {
                override val label = "Offline · Small"
                override fun transcribe(upload: WavUpload, language: String) = TranscriptResult("also ähm hallo welt")
            }
        }
        installSparse(ctx, ModelCatalog.SMALL)
        installSparse(ctx, TextModelCatalog.GEMMA4_E2B)
        prefs.engine = Engine.OFFLINE
        prefs.offlineRefine = OfflineRefineRule.ONLINE_LOCAL
        llmFallback()
        firstChat.status = 500

        assertEquals("Vom Fallback verbessert.", transcribe().text)
        assertEquals("kein lokaler Aufruf", 0, made.sumOf { it.calls.size })

        fallbackChat.status = 500
        val d = transcribe()
        assertEquals("Lokal verbessert.", d.text)
        assertEquals(RefinePlan.MSG_ONLINE_FAILED_LOCAL, d.result.note)
    }

    // --- Geteilte Audios ---------------------------------------------------------------------

    @Test fun geteilteAudiosNachAusfallDieRestlichenStueckeGleichZumFallback() {
        llmFallback()
        firstChat.status = 500
        prefs.shareRefineMode = RefineMode.POLISH

        val result = SharedRefine.run(ctx, prefs, listOf("Erstes Stück.", "Zweites Stück."), "de")

        assertEquals(listOf("Vom Fallback verbessert.", "Vom Fallback verbessert."), result.paragraphs)
        assertEquals("nur ein Fehlversuch, nicht je Stueck", 1, firstChat.hits.get())
        assertEquals(2, fallbackChat.hits.get())
        assertFalse("kein Hinweis „lokal verbessert“", result.localFallback)
        assertNull(result.skipped)
    }
}
