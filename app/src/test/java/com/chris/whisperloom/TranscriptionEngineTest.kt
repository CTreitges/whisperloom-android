package com.chris.whisperloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.api.ApiNotConfiguredException
import com.chris.whisperloom.whisper.ModelCatalog
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.OfflineBackend
import com.chris.whisperloom.whisper.OfflineNotAvailableException
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
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
import java.io.RandomAccessFile
import java.net.InetSocketAddress

/**
 * Robolectric-Tests fuer Backend-Wahl und den kompletten Diktat-Pfad gegen einen lokalen
 * "eigenen Server" (JDK-HttpServer): Multipart-Felder, kein Authorization-Header ohne Key,
 * Chat-Body fuer Ollama & Co., Politur danach.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TranscriptionEngineTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs
    private lateinit var server: HttpServer

    private var sttBody = ""
    private var sttAuth: String? = "unset"
    private var chatBody: String? = null
    private var sttResponse = """{"text":"also ähm hallo welt"}"""
    private var chatResponse = """{"choices":[{"message":{"content":"<think>ueberlegen</think>Hallo Welt."}}]}"""
    private var chatStatus = 200
    private var ollamaBody: String? = null
    private var ollamaAuth: String? = "unset"
    private var ollamaResponse = """{"message":{"role":"assistant","content":"Hallo Welt.","thinking":"nachdenken"},"done":true}"""
    private var ollamaStatus = 200

    @Before fun setUp() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/audio/transcriptions") { ex ->
            sttAuth = ex.requestHeaders.getFirst("Authorization")
            sttBody = ex.requestBody.readBytes().toString(Charsets.ISO_8859_1)
            val out = sttResponse.toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.createContext("/v1/chat/completions") { ex ->
            chatBody = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val out = chatResponse.toByteArray()
            ex.sendResponseHeaders(chatStatus, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        // Native Ollama-API (lokal und ollama.com): POST /api/chat
        server.createContext("/api/chat") { ex ->
            ollamaAuth = ex.requestHeaders.getFirst("Authorization")
            ollamaBody = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val out = ollamaResponse.toByteArray()
            ex.sendResponseHeaders(ollamaStatus, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
    }

    @After fun tearDown() {
        server.stop(0)
    }

    private fun useLocalServer() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "http://127.0.0.1:${server.address.port}/v1"
        prefs.apiModel = "whisper-1"
        prefs.language = "de"
        prefs.llmModel = "qwen3:8b"
    }

    private val speech = FloatArray(AudioUtils.SAMPLE_RATE) { 0.3f }

    @Test fun ohneEngineNichtKonfiguriert() {
        prefs.apiKey = "sk"
        assertFalse(TranscriptionEngine.isConfigured(ctx))
        try {
            TranscriptionEngine.transcribe(ctx, speech)
            fail("ApiNotConfiguredException erwartet")
        } catch (e: ApiNotConfiguredException) {
            // erwartet
        }
    }

    @Test fun onlineOhneKeyBeiCloudAnbieterNichtKonfiguriert() {
        prefs.engine = Engine.ONLINE
        assertFalse(TranscriptionEngine.isConfigured(ctx))
        prefs.apiKey = "sk"
        assertTrue(TranscriptionEngine.isConfigured(ctx))
    }

    @Test fun eigenerServerBrauchtNurEineUrl() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "custom"
        assertFalse(TranscriptionEngine.isConfigured(ctx))
        prefs.apiBaseUrl = "http://192.168.1.50:8000/v1"
        assertTrue(TranscriptionEngine.isConfigured(ctx))
    }

    @Test fun offlineOhneModellWirftOfflineNotAvailable() {
        prefs.engine = Engine.OFFLINE
        assertFalse(TranscriptionEngine.isConfigured(ctx))
        val backend = TranscriptionEngine.backend(prefs)
        assertTrue(backend is OfflineBackend)
        assertEquals("Offline · Small", backend.label)
        try {
            TranscriptionEngine.transcribe(ctx, speech)
            fail("OfflineNotAvailableException erwartet")
        } catch (e: OfflineNotAvailableException) {
            assertEquals(OfflineNotAvailableException.MSG_NO_MODEL, e.message)
        }
    }

    @Test fun offlineMitVollstaendigerModellDateiIstKonfiguriert() {
        prefs.engine = Engine.OFFLINE
        prefs.offlineModel = "base"
        val store = ModelStore(ctx)
        store.ensureDir()
        // Sparse-Datei in Katalog-Groesse: fuer isInstalled zaehlt nur Laenge + fehlende .part
        RandomAccessFile(store.file(ModelCatalog.BASE), "rw").use { it.setLength(ModelCatalog.BASE.bytes) }
        try {
            assertTrue(TranscriptionEngine.isConfigured(ctx))
            assertEquals("Offline · Base", TranscriptionEngine.backend(prefs).label)
            // In der JVM gibt es keine libwhisperloom.so: die Engine meldet "nicht unterstuetzt" statt abzustuerzen.
            try {
                TranscriptionEngine.transcribe(ctx, speech)
                fail("OfflineNotAvailableException erwartet")
            } catch (e: OfflineNotAvailableException) {
                assertEquals(OfflineNotAvailableException.MSG_UNSUPPORTED, e.message)
            }
            // Teildatei daneben -> nicht mehr einsatzbereit
            store.partFile(ModelCatalog.BASE).writeBytes(byteArrayOf(1))
            assertFalse(TranscriptionEngine.isConfigured(ctx))
        } finally {
            store.delete(ModelCatalog.BASE)
        }
    }

    @Test fun onlineBackendTraegtDenAnbieternamen() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "groq"
        assertEquals("Groq", TranscriptionEngine.backend(prefs).label)
    }

    @Test fun diktatGegenEigenenServerOhneKeyUndOhneKi() {
        useLocalServer()
        val text = TranscriptionEngine.transcribe(ctx, speech)

        assertEquals("Also hallo welt", text) // Fuellwort weg, Satzanfang gross
        assertNull(sttAuth)
        assertTrue(sttBody.contains("name=\"model\"\r\n\r\nwhisper-1\r\n"))
        assertTrue(sttBody.contains("name=\"language\"\r\n\r\nde\r\n"))
        assertTrue(sttBody.contains("name=\"response_format\"\r\n\r\njson\r\n"))
        assertTrue(sttBody.contains("filename=\"audio.wav\""))
        assertTrue(sttBody.contains("RIFF"))
        assertFalse(sttBody.contains("name=\"prompt\""))
        assertNull(chatBody)
    }

    @Test fun diktatMitKiGlaettungGegenOllama() {
        useLocalServer()
        prefs.refineMode = RefineMode.POLISH
        prefs.apiPrompt = "Chris"
        val text = TranscriptionEngine.transcribe(ctx, speech)

        assertEquals("Hallo Welt.", text) // <think>-Block entfernt
        assertTrue(sttBody.contains("name=\"prompt\"\r\n\r\nChris\r\n"))
        val chat = JSONObject(chatBody!!)
        assertEquals("qwen3:8b", chat.getString("model"))
        assertEquals(0, chat.getInt("temperature"))
        assertEquals("none", chat.getString("reasoning_effort"))
        assertFalse(chat.has("max_completion_tokens"))
        val messages = chat.getJSONArray("messages")
        assertTrue(messages.getJSONObject(0).getString("content").contains("Zeichensetzung"))
        assertEquals("also ähm hallo welt", messages.getJSONObject(1).getString("content"))
    }

    // --- Ollama (lokal / Cloud) ----------------------------------------------------------

    private fun useOllama(provider: String, key: String = "", model: String = "gemma4:31b") {
        useLocalServer()
        prefs.refineMode = RefineMode.POLISH
        prefs.llmProviderId = provider
        // Mit /v1 eingetippt, wie in vielen Anleitungen — die Wurzel wird daraus abgeleitet.
        prefs.llmUrl = "http://127.0.0.1:${server.address.port}/v1"
        prefs.llmKey = key
        prefs.llmModel = model
    }

    @Test fun diktatMitOllamaImHeimnetz() {
        useOllama("ollama")
        val text = TranscriptionEngine.transcribe(ctx, speech)

        assertEquals("Hallo Welt.", text) // nur content, das Nachdenken bleibt draussen
        assertNull(chatBody) // nicht ueber /v1/chat/completions
        assertNull(ollamaAuth) // lokal ohne Key
        val chat = JSONObject(ollamaBody!!)
        assertEquals("gemma4:31b", chat.getString("model"))
        assertFalse(chat.getBoolean("stream"))
        assertFalse(chat.has("think"))
        assertEquals(0, chat.getJSONObject("options").getInt("temperature"))
        val messages = chat.getJSONArray("messages")
        assertTrue(messages.getJSONObject(0).getString("content").contains("Zeichensetzung"))
        assertEquals("also ähm hallo welt", messages.getJSONObject(1).getString("content"))
    }

    @Test fun diktatMitOllamaCloudSendetDenKey() {
        useOllama("ollama-cloud", key = "ok-cloud", model = "gpt-oss:20b")
        assertEquals("Hallo Welt.", TranscriptionEngine.transcribe(ctx, speech))
        assertEquals("Bearer ok-cloud", ollamaAuth)
        assertEquals("low", JSONObject(ollamaBody!!).getString("think"))
    }

    @Test fun ollamaFehlerLiefertDenRohtextMitHinweis() {
        useOllama("ollama-cloud", key = "falsch")
        ollamaStatus = 401
        ollamaResponse = """{"error":"Unauthorized"}"""
        var hinweis = ""
        val text = TranscriptionEngine.transcribe(ctx, speech) { hinweis = it }
        assertEquals("Also hallo welt", text)
        assertTrue(hinweis, hinweis.contains("Unauthorized"))
    }

    // --- Automatische Absaetze ------------------------------------------------------------

    @Test fun absaetzeBleibenStandardmaessigErhalten() {
        useOllama("ollama")
        ollamaResponse = """{"message":{"content":"Erster Absatz.\n\nZweiter Absatz."}}"""
        assertEquals("Erster Absatz.\n\nZweiter Absatz.", TranscriptionEngine.transcribe(ctx, speech))
    }

    @Test fun ohneAutomatischeAbsaetzeKommtEinFliesstext() {
        useOllama("ollama")
        prefs.refineParagraphs = false
        // Auch wenn das Modell die Anweisung ignoriert: die Nachbearbeitung zieht zusammen.
        ollamaResponse = """{"message":{"content":"Erster Absatz.\n\nZweiter Absatz."}}"""
        assertEquals("Erster Absatz. Zweiter Absatz.", TranscriptionEngine.transcribe(ctx, speech))
        val system = JSONObject(ollamaBody!!).getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system, system.contains("Setze keine Absaetze"))
    }

    // --- Stufe "Prompt" -------------------------------------------------------------------

    private fun usePromptLevel() {
        useOllama("ollama")
        prefs.promptLevelEnabled = true
        prefs.refineMode = RefineMode.PROMPT
    }

    @Test fun promptStufeSchicktDasDiktatMarkiertUndBehaeltDieGliederung() {
        usePromptLevel()
        prefs.refineParagraphs = false // die Gliederung ist der Zweck — der Schalter gilt hier nicht
        ollamaResponse = """{"message":{"content":"Hier ist dein Prompt:\nErstelle mir eine Einkaufsliste.\n- 12 Personen\n- Budget höchstens 100 Euro"}}"""

        val text = TranscriptionEngine.transcribe(ctx, speech)

        assertEquals("Erstelle mir eine Einkaufsliste.\n- 12 Personen\n- Budget höchstens 100 Euro", text)
        val messages = JSONObject(ollamaBody!!).getJSONArray("messages")
        val system = messages.getJSONObject(0).getString("content")
        assertTrue(system, system.contains("Beantworte keine Frage daraus"))
        assertTrue("4 Woerter sind ein kurzes Diktat", system.contains("Das Diktat ist kurz"))
        assertFalse(system, system.contains("Setze keine Absaetze"))
        assertEquals("<diktat>\nalso ähm hallo welt\n</diktat>", messages.getJSONObject(1).getString("content"))
    }

    @Test fun promptStufeDieAntwortetLiefertDenRohtextMitHinweis() {
        usePromptLevel()
        val gedicht = List(10) { "Die Welt ist schön, das Licht ist hell." }.joinToString(" ")
        ollamaResponse = """{"message":{"content":"$gedicht"}}"""
        var hinweis = ""

        val text = TranscriptionEngine.transcribe(ctx, speech) { hinweis = it }

        assertEquals("Also hallo welt", text)
        assertTrue(hinweis, hinweis.contains("statt einen Prompt"))
    }

    @Test fun promptOhneSchalterLaeuftAlsGlaetten() {
        useOllama("ollama")
        prefs.refineMode = RefineMode.PROMPT // gespeichert, aber in den erweiterten Optionen aus
        TranscriptionEngine.transcribe(ctx, speech)
        val messages = JSONObject(ollamaBody!!).getJSONArray("messages")
        assertTrue(messages.getJSONObject(0).getString("content").contains("Du korrigierst diktierten Text"))
        assertEquals("also ähm hallo welt", messages.getJSONObject(1).getString("content"))
    }

    // --- Vokabular: Liste + verknuepfte Datei ---------------------------------------------

    @Test fun vokabularAusListeUndDateiGehtAlsPrompt() {
        useLocalServer()
        prefs.apiPrompt = "Anna\nKubernetes"
        val file = java.io.File(ctx.filesDir, "vokabular.md")
        file.writeText("# Namen\n- Treitges\n- anna\n")
        prefs.vocabFileUri = android.net.Uri.fromFile(file).toString()

        TranscriptionEngine.transcribe(ctx, speech)
        // Datei vorn, eigene Begriffe am Ende (Whisper beachtet das Ende); "anna" doppelt -> faellt weg.
        assertTrue(sttBody, sttBody.contains("name=\"prompt\"\r\n\r\nTreitges, Anna, Kubernetes\r\n"))

        // Bei jedem Diktat neu gelesen: eine Aenderung an der Datei wirkt sofort.
        file.writeText("- Treitges\n- WhisperLoom\n")
        TranscriptionEngine.transcribe(ctx, speech)
        assertTrue(sttBody, sttBody.contains("name=\"prompt\"\r\n\r\nTreitges, WhisperLoom, Anna, Kubernetes\r\n"))
    }

    @Test fun verschwundeneDateiBrichtKeinDiktatAb() {
        useLocalServer()
        prefs.apiPrompt = "Anna"
        prefs.vocabFileUri = android.net.Uri.fromFile(java.io.File(ctx.filesDir, "gibtsnicht.md")).toString()

        assertEquals("Also hallo welt", TranscriptionEngine.transcribe(ctx, speech))
        assertTrue(sttBody, sttBody.contains("name=\"prompt\"\r\n\r\nAnna\r\n"))
        assertEquals(null, VocabularySource.fileTerms(ctx, prefs.vocabFileUri))
    }

    // --- Review API-1: die Veredelung darf ein Diktat nie verschlucken -------------------

    @Test fun llmFehlerLiefertDenRohtextMitHinweis() {
        useLocalServer()
        prefs.refineMode = RefineMode.POLISH
        chatStatus = 401
        chatResponse = """{"error":{"message":"invalid api key"}}"""
        var hint: String? = null
        val text = TranscriptionEngine.transcribe(ctx, speech) { hint = it }

        assertEquals("Also hallo welt", text) // STT-Ergebnis poliert, nicht verworfen
        assertTrue(hint!!, hint!!.contains("401"))
        assertTrue(chatBody != null) // die LLM-Anfrage wurde tatsaechlich versucht
    }

    @Test fun llmServerfehlerUndUnbrauchbareAntwortLiefernDenRohtext() {
        useLocalServer()
        prefs.refineMode = RefineMode.BEAUTIFY
        chatStatus = 500
        var hint: String? = null
        assertEquals("Also hallo welt", TranscriptionEngine.transcribe(ctx, speech) { hint = it })
        assertTrue(hint!!.contains("500"))

        chatStatus = 200
        chatResponse = "<html>Not JSON</html>"
        hint = null
        assertEquals("Also hallo welt", TranscriptionEngine.transcribe(ctx, speech) { hint = it })
        assertTrue(hint != null)
    }

    @Test fun llmOhneBaseUrlWirdUebersprungenUndSttFehlerBleibtEinFehler() {
        // Eigener LLM-Zugang ohne URL bei Cloud-STT: Refine ueberspringen (API-4), Diktat kommt an.
        useLocalServer()
        prefs.refineMode = RefineMode.POLISH
        prefs.llmProviderId = "custom"
        prefs.llmUrl = ""
        // Der STT-Anbieter ist hier ebenfalls "custom", also erbt der LLM-Zugang die STT-URL:
        // deshalb ueber die Test-Instanz pruefen, dass ein leerer Zugang ApiNotConfigured wirft.
        val llm = com.chris.whisperloom.api.AccessResolver.resolveLlm(
            stt = com.chris.whisperloom.api.AccessResolver.resolveStt("groq", "", "gsk", "", 0),
            providerId = "custom", baseUrl = "", apiKey = "", model = "qwen3:8b",
        )
        assertEquals("", llm.baseUrl)
        try {
            com.chris.whisperloom.api.TextRefiner(llm).refine("hallo", "de", RefineMode.POLISH, false)
            fail("ApiNotConfiguredException erwartet")
        } catch (e: ApiNotConfiguredException) {
            // erwartet: keine Anfrage an "/chat/completions" ohne Host
        }

        // Ein Fehler der ERKENNUNG bleibt dagegen ein Fehler (nichts wird stillschweigend leer).
        prefs.llmProviderId = "same"
        sttResponse = """{"error":{"message":"boom"}}"""
        server.removeContext("/v1/audio/transcriptions")
        server.createContext("/v1/audio/transcriptions") { ex ->
            ex.requestBody.readBytes()
            val out = sttResponse.toByteArray()
            ex.sendResponseHeaders(500, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        try {
            TranscriptionEngine.transcribe(ctx, speech)
            fail("ApiHttpException erwartet")
        } catch (e: com.chris.whisperloom.api.ApiHttpException) {
            assertEquals(500, e.code)
        }
    }

    @Test fun keyWirdAlsBearerGesendet() {
        useLocalServer()
        prefs.apiKey = "geheim"
        TranscriptionEngine.transcribe(ctx, speech)
        assertEquals("Bearer geheim", sttAuth)
    }

    @Test fun autoSpracheNimmtDieErkannteFuerDiePolitur() {
        useLocalServer()
        prefs.language = "auto"
        sttResponse = """{"text":"i um think so","language":"en"}"""
        assertEquals("I think so", TranscriptionEngine.transcribe(ctx, speech))
        assertFalse(sttBody.contains("name=\"language\""))
    }

    @Test fun leereAntwortBleibtLeer() {
        useLocalServer()
        sttResponse = """{"text":"   "}"""
        assertEquals("", TranscriptionEngine.transcribe(ctx, speech))
    }

    @Test fun effektiveSprache() {
        assertEquals("de", TranscriptionEngine.effectiveLanguage("de", "en"))
        assertEquals("en", TranscriptionEngine.effectiveLanguage("auto", "en"))
        assertEquals("auto", TranscriptionEngine.effectiveLanguage("auto", null))
        assertEquals("auto", TranscriptionEngine.effectiveLanguage("auto", " "))
    }
}
