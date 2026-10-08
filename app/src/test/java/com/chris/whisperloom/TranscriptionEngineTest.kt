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
    private var elevenBody = ""
    private var elevenKey: String? = "unset"
    private var elevenAuth: String? = "unset"
    private var elevenResponse = """{"language_code":"deu","language_probability":0.98,"text":"also ähm hallo welt"}"""
    private var elevenStatus = 200

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
        // ElevenLabs Speech-to-Text: eigener Pfad, Key im Header xi-api-key
        server.createContext("/v1/speech-to-text") { ex ->
            elevenKey = ex.requestHeaders.getFirst("xi-api-key")
            elevenAuth = ex.requestHeaders.getFirst("Authorization")
            elevenBody = ex.requestBody.readBytes().toString(Charsets.ISO_8859_1)
            val out = elevenResponse.toByteArray()
            ex.sendResponseHeaders(elevenStatus, out.size.toLong())
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
        assertTrue(messages.getJSONObject(0).getString("content").contains("nur Satzzeichen"))
        // Markiert: ein diktiertes "schreib mir …" soll bearbeitet, nicht erfuellt werden.
        assertEquals("<diktat>\nalso ähm hallo welt\n</diktat>", messages.getJSONObject(1).getString("content"))
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
        assertTrue(messages.getJSONObject(0).getString("content").contains("nur Satzzeichen"))
        // Markiert: ein diktiertes "schreib mir …" soll bearbeitet, nicht erfuellt werden.
        assertEquals("<diktat>\nalso ähm hallo welt\n</diktat>", messages.getJSONObject(1).getString("content"))
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

    /** Review-Befund: die KI sollte die Fuellwoerter entfernen — gescheitert, tat es niemand. */
    @Test fun kiFehlerMitIntelligentenFuellwoerternRaeumtTrotzdemAuf() {
        useOllama("ollama")
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.CLEAN)
        ollamaStatus = 500
        assertEquals("Also hallo welt", TranscriptionEngine.transcribe(ctx, speech))
    }

    // --- Fuellwort-Netz und leere Antwort (3.9.0) -------------------------------------------

    /** Plan §4: "Ohne Fuellwoerter" schickt den Glaetten-Zweig mit Fuellwort-Regel, die Liste faengt danach Reste. */
    @Test fun ohneFuellwoerterSchicktDenZweigUndDieListeFaengtReste() {
        useOllama("ollama")
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.CLEAN)
        prefs.customFillers = setOf("sozusagen")
        // Ein kleines Modell uebersieht ein "ähm" — bis 3.8.6 pausierte die Liste dann und es blieb stehen.
        ollamaResponse = """{"message":{"content":"Also, ähm, hallo Welt, sozusagen."}}"""

        assertEquals("Also, hallo Welt.", TranscriptionEngine.transcribe(ctx, speech))
        val system = JSONObject(ollamaBody!!).getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system, system.contains("nie Wörter außer Füllwörtern"))
    }

    /** N7: eine leere Antwort galt als "verbessert" — ohne Hinweis, mit "intelligent" samt jedem "ähm". */
    @Test fun leereKiAntwortGiltAlsOhneKiMitHinweis() {
        useOllama("ollama")
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.CLEAN)
        prefs.setParagraphsFor(RefineMode.POLISH, true)
        ollamaResponse = """{"message":{"content":""},"done":true}"""
        var hinweis: String? = null

        val text = TranscriptionEngine.transcribe(ctx, speech) { hinweis = it }

        assertEquals("Rohtext mit den vollen Regeln", "Also hallo welt", text)
        assertEquals(com.chris.whisperloom.api.TextRefiner.MSG_EMPTY, hinweis)
    }

    @Test fun leereAntwortUeberChatCompletionsGiltAlsOhneKi() {
        useLocalServer()
        prefs.refineMode = RefineMode.BEAUTIFY
        chatResponse = """{"choices":[{"message":{"content":"<think>hm</think>  "},"finish_reason":"stop"}]}"""
        var hinweis: String? = null
        assertEquals("Also hallo welt", TranscriptionEngine.transcribe(ctx, speech) { hinweis = it })
        assertEquals(com.chris.whisperloom.api.TextRefiner.MSG_EMPTY, hinweis)
    }

    /** Plan §3.2: Verschoenern behaelt den Standard-Prompt — die Fuellwort-Regel gehoert zum Glaetten. */
    @Test fun verschoenernBekommtKeinenFuellwortZusatz() {
        useOllama("ollama")
        prefs.refineMode = RefineMode.BEAUTIFY
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.CLEAN)
        ollamaResponse = """{"message":{"content":"Also, ähm, hallo Welt."}}"""

        assertEquals("die Liste raeumt auf", "Also, hallo Welt.", TranscriptionEngine.transcribe(ctx, speech))
        val system = JSONObject(ollamaBody!!).getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system, system.contains("Du überarbeitest diktierten Text"))
        assertEquals(system, com.chris.whisperloom.api.RefinePrompt.build(RefineMode.BEAUTIFY, german = true, smartFillers = false, short = true))
    }

    /** Diktat und Sprachnachrichten getrennt: die Bereinigung der Sprachnachrichten aendert das Diktat nicht. */
    @Test fun diktatIgnoriertDieBereinigungDerSprachnachrichten() {
        useOllama("ollama")
        prefs.shareRefineMode = RefineMode.POLISH
        prefs.setPolishCleanupFor(RefineWay.SHARE, PolishCleanup.CLEAN)
        TranscriptionEngine.transcribe(ctx, speech)
        val system = JSONObject(ollamaBody!!).getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system, system.contains("Du korrigierst in diktiertem Text nur Satzzeichen"))
        assertFalse(system, system.contains("Füllwörtern"))
    }

    // --- Automatische Absaetze ------------------------------------------------------------

    @Test fun absaetzeBleibenStandardmaessigErhalten() {
        useOllama("ollama")
        ollamaResponse = """{"message":{"content":"Erster Absatz.\n\nZweiter Absatz."}}"""
        assertEquals("Erster Absatz.\n\nZweiter Absatz.", TranscriptionEngine.transcribe(ctx, speech))
    }

    @Test fun ohneAbsaetzeKommtEinFliesstext() {
        useOllama("ollama")
        prefs.setParagraphsFor(RefineMode.POLISH, false)
        // Auch wenn das Modell die Anweisung ignoriert: die Nachbearbeitung zieht zusammen.
        ollamaResponse = """{"message":{"content":"Erster Absatz.\n\nZweiter Absatz."}}"""
        assertEquals("Erster Absatz. Zweiter Absatz.", TranscriptionEngine.transcribe(ctx, speech))
        val system = JSONObject(ollamaBody!!).getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system, system.contains("einen einzigen durchgehenden Absatz"))
    }

    @Test fun absaetzeGeltenJeStufe() {
        useOllama("ollama")
        prefs.setParagraphsFor(RefineMode.BEAUTIFY, false)
        ollamaResponse = """{"message":{"content":"Erster Absatz.\n\nZweiter Absatz."}}"""
        assertEquals("Glaetten behaelt sie", "Erster Absatz.\n\nZweiter Absatz.", TranscriptionEngine.transcribe(ctx, speech))

        prefs.refineMode = RefineMode.BEAUTIFY
        assertEquals("Erster Absatz. Zweiter Absatz.", TranscriptionEngine.transcribe(ctx, speech))
        val system = JSONObject(ollamaBody!!).getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system, system.contains("einen einzigen durchgehenden Absatz"))
    }

    @Test fun zusammenfassenAlsFliesstextOhneListe() {
        useOllama("ollama")
        prefs.refineMode = RefineMode.SUMMARIZE
        prefs.setSummarizeFormFor(RefineWay.DICTATION, SummarizeForm.PROSE)
        ollamaResponse = """{"message":{"content":"Kurz gesagt.\n- Eins.\n- Zwei."}}"""
        assertEquals("Kurz gesagt. - Eins. - Zwei.", TranscriptionEngine.transcribe(ctx, speech))
        val system = JSONObject(ollamaBody!!).getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system, system.contains("ohne Liste und ohne Zeilenumbruch"))

        // Automatisch: die Liste bleibt.
        prefs.setSummarizeFormFor(RefineWay.DICTATION, SummarizeForm.AUTO)
        assertEquals("Kurz gesagt.\n- Eins.\n- Zwei.", TranscriptionEngine.transcribe(ctx, speech))
    }

    @Test fun lesbarerGlaettenSchicktDenLesbarPromptUndDieWortlisteFaengtReste() {
        useOllama("ollama")
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.READABLE)
        // Ein kleines Modell laesst ein "ähm" stehen — die Wortliste raeumt es danach weg.
        ollamaResponse = """{"message":{"content":"Also, ähm, hallo Welt."}}"""

        assertEquals("Also, hallo Welt.", TranscriptionEngine.transcribe(ctx, speech))
        val messages = JSONObject(ollamaBody!!).getJSONArray("messages")
        val system = messages.getJSONObject(0).getString("content")
        assertTrue(system, system.contains("Du machst diktierten Text lesbar"))
        assertEquals("<diktat>\nalso ähm hallo welt\n</diktat>", messages.getJSONObject(1).getString("content"))
        assertEquals("gespeichert bleibt Glaetten", RefineMode.POLISH, Prefs(ctx).refineMode)
    }

    @Test fun ohneLesbarBleibtGlaettenKorrektorat() {
        useOllama("ollama")
        TranscriptionEngine.transcribe(ctx, speech)
        val system = JSONObject(ollamaBody!!).getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system, system.contains("Du korrigierst in diktiertem Text nur Satzzeichen"))
        assertFalse(system, system.contains("lesbar"))
    }

    /** 3.8.6: die Bereinigung der Sprachnachrichten gilt nicht fuers Diktat. */
    @Test fun diktatIgnoriertDenLesbarSchalterDerSprachnachrichten() {
        useOllama("ollama")
        prefs.shareRefineMode = RefineMode.POLISH
        prefs.setPolishCleanupFor(RefineWay.SHARE, PolishCleanup.READABLE)
        TranscriptionEngine.transcribe(ctx, speech)
        val system = JSONObject(ollamaBody!!).getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system, system.contains("Du korrigierst in diktiertem Text nur Satzzeichen"))
        assertFalse(system, system.contains("lesbar"))
    }

    @Test fun kiDieEineBitteErfuelltLiefertDenRohtextMitHinweis() {
        useOllama("ollama")
        val einladung = List(10) { "Ihr seid alle herzlich zu meinem Geburtstag eingeladen." }.joinToString(" ")
        ollamaResponse = """{"message":{"content":"$einladung"}}"""
        var hinweis = ""
        assertEquals("Also hallo welt", TranscriptionEngine.transcribe(ctx, speech) { hinweis = it })
        assertTrue(hinweis, hinweis.contains("statt den Text zu bearbeiten"))
    }

    @Test fun markierungUndVorredeKommenNieInsTextfeld() {
        useOllama("ollama")
        ollamaResponse = """{"message":{"content":"Hier ist der geglättete Text:\n<diktat>\nAlso, hallo Welt.\n</diktat>"}}"""
        assertEquals("Also, hallo Welt.", TranscriptionEngine.transcribe(ctx, speech))
    }

    // --- Stufe "Prompt" -------------------------------------------------------------------

    private fun usePromptLevel() {
        useOllama("ollama")
        prefs.promptLevelEnabled = true
        prefs.refineMode = RefineMode.PROMPT
    }

    @Test fun promptStufeSchicktDasDiktatMarkiertUndBehaeltDieGliederung() {
        usePromptLevel()
        prefs.setParagraphsFor(RefineMode.POLISH, false) // die Gliederung ist der Zweck — kein Schalter hier
        ollamaResponse = """{"message":{"content":"Hier ist dein Prompt:\nErstelle mir eine Einkaufsliste.\n- 12 Personen\n- Budget höchstens 100 Euro"}}"""

        val text = TranscriptionEngine.transcribe(ctx, speech)

        assertEquals("Erstelle mir eine Einkaufsliste.\n- 12 Personen\n- Budget höchstens 100 Euro", text)
        val messages = JSONObject(ollamaBody!!).getJSONArray("messages")
        val system = messages.getJSONObject(0).getString("content")
        assertTrue(system, system.contains("Beantworte keine Frage daraus"))
        assertTrue("4 Woerter sind ein kurzes Diktat", system.contains("Das Diktat ist kurz"))
        assertFalse(system, system.contains("durchgehenden Absatz"))
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
        assertTrue(messages.getJSONObject(0).getString("content").contains("Du korrigierst in diktiertem Text"))
        assertEquals("<diktat>\nalso ähm hallo welt\n</diktat>", messages.getJSONObject(1).getString("content"))
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

    /** Review Share-Stufe HOCH: eine an der Laengengrenze abgebrochene Antwort ist nur der Anfang. */
    @Test fun abgeschnitteneKiAntwortLiefertDenRohtextMitHinweis() {
        useLocalServer()
        prefs.refineMode = RefineMode.POLISH
        chatResponse = """{"choices":[{"message":{"content":"Also hallo"},"finish_reason":"length"}]}"""
        var hint: String? = null
        assertEquals("Also hallo welt", TranscriptionEngine.transcribe(ctx, speech) { hint = it })
        assertEquals(com.chris.whisperloom.api.TextRefiner.MSG_TRUNCATED, hint)

        chatResponse = """{"choices":[{"message":{"content":"Hallo Welt."},"finish_reason":"stop"}]}"""
        hint = null
        assertEquals("Hallo Welt.", TranscriptionEngine.transcribe(ctx, speech) { hint = it })
        assertNull(hint)
    }

    @Test fun abgeschnitteneOllamaAntwortLiefertDenRohtextMitHinweis() {
        useOllama("ollama")
        ollamaResponse = """{"message":{"content":"Also hallo"},"done":true,"done_reason":"length"}"""
        var hint: String? = null
        assertEquals("Also hallo welt", TranscriptionEngine.transcribe(ctx, speech) { hint = it })
        assertEquals(com.chris.whisperloom.api.TextRefiner.MSG_TRUNCATED, hint)
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

    // --- ElevenLabs (Scribe) --------------------------------------------------------------

    private fun useElevenLabs() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "elevenlabs"
        prefs.apiBaseUrl = "http://127.0.0.1:${server.address.port}/v1"
        prefs.apiKey = "xi-geheim"
        prefs.language = "auto"
    }

    @Test fun diktatMitElevenLabsSchicktRohesPcmUndKeyterms() {
        useElevenLabs()
        prefs.apiPrompt = "Treitges\nWhisperLoom"
        val text = TranscriptionEngine.transcribe(ctx, speech)

        // "deu" -> "de": die deutschen Fuellwoerter greifen (mit "deu" bliebe das "ähm" stehen).
        assertEquals("Also hallo welt", text)
        assertEquals("xi-geheim", elevenKey)
        assertNull(elevenAuth) // kein Bearer
        assertTrue(elevenBody.contains("name=\"model_id\"\r\n\r\nscribe_v2\r\n"))
        assertTrue(elevenBody.contains("name=\"tag_audio_events\"\r\n\r\nfalse\r\n"))
        assertTrue(elevenBody.contains("name=\"timestamps_granularity\"\r\n\r\nnone\r\n"))
        assertTrue(elevenBody.contains("name=\"file_format\"\r\n\r\npcm_s16le_16\r\n"))
        assertTrue(elevenBody.contains("name=\"keyterms\"\r\n\r\nTreitges\r\n"))
        assertTrue(elevenBody.contains("name=\"keyterms\"\r\n\r\nWhisperLoom\r\n"))
        assertFalse(elevenBody.contains("name=\"language_code\"")) // auto
        assertFalse(elevenBody.contains("name=\"model\""))
        assertFalse(elevenBody.contains("name=\"prompt\""))
        assertTrue(elevenBody.contains("filename=\"audio.pcm\""))
        // Die Datei ist rohes PCM ohne WAV-Kopf: genau 2 Byte je Sample.
        val file = elevenBody.substringAfter("Content-Type: application/octet-stream\r\n\r\n").substringBeforeLast("\r\n--")
        assertFalse(file.startsWith("RIFF"))
        assertEquals(AudioUtils.trimSilence(speech).size * 2, file.length)
    }

    @Test fun elevenLabsSchicktDieGewaehlteSprache() {
        useElevenLabs()
        prefs.language = "de"
        TranscriptionEngine.transcribe(ctx, speech)
        assertTrue(elevenBody.contains("name=\"language_code\"\r\n\r\nde\r\n"))
    }

    @Test fun elevenLabsOhneEigenenTextZugangUeberspringtDieVerbesserung() {
        useElevenLabs()
        prefs.refineMode = RefineMode.POLISH
        var hint: String? = null
        assertEquals("Also hallo welt", TranscriptionEngine.transcribe(ctx, speech) { hint = it })
        assertEquals(com.chris.whisperloom.api.TextRefiner.MSG_NO_LLM, hint)
        assertNull(chatBody) // keine Anfrage an einen Chat-Endpunkt, den es bei ElevenLabs nicht gibt
    }

    @Test fun elevenLabsMitAltemTextModellSchicktTrotzdemKeinenChat() {
        // Von frueher gespeichertes llm_model: "wie Erkennung" bleibt bei ElevenLabs ohne Chat.
        useElevenLabs()
        prefs.refineMode = RefineMode.POLISH
        prefs.llmModel = "gpt-4o-mini"
        var hint: String? = null
        assertEquals("Also hallo welt", TranscriptionEngine.transcribe(ctx, speech) { hint = it })
        assertEquals(com.chris.whisperloom.api.TextRefiner.MSG_NO_LLM, hint)
        assertNull(chatBody)
    }

    @Test fun elevenLabsMitEigenemTextZugangVerbessertAufDeutsch() {
        useElevenLabs()
        prefs.refineMode = RefineMode.POLISH
        prefs.llmProviderId = "custom"
        prefs.llmUrl = "http://127.0.0.1:${server.address.port}/v1"
        prefs.llmModel = "qwen3:8b"
        assertEquals("Hallo Welt.", TranscriptionEngine.transcribe(ctx, speech))
        // Erkannte Sprache "deu" kam als "de" an: deutscher Prompt.
        val system = JSONObject(chatBody!!).getJSONArray("messages").getJSONObject(0).getString("content")
        assertTrue(system, system.contains("nur Satzzeichen"))
    }

    @Test fun elevenLabsFehlerSindLesbarMitHinweis() {
        useElevenLabs()
        elevenStatus = 401
        elevenResponse = """{"detail":{"type":"authentication_error","code":"unauthorized","message":"Invalid API key","status":"invalid_api_key"}}"""
        try {
            TranscriptionEngine.transcribe(ctx, speech)
            fail("ApiHttpException erwartet")
        } catch (e: com.chris.whisperloom.api.ApiHttpException) {
            assertEquals(401, e.code)
            assertEquals("Invalid API key", e.detail)
            assertTrue(e.message!!, e.message!!.contains("„Speech to Text“"))
        }

        elevenStatus = 402
        elevenResponse = """{"detail":{"code":"insufficient_credits","message":"You have run out of credits"}}"""
        try {
            TranscriptionEngine.transcribe(ctx, speech)
            fail("ApiHttpException erwartet")
        } catch (e: com.chris.whisperloom.api.ApiHttpException) {
            assertEquals(402, e.code)
            assertTrue(e.message!!, e.message!!.contains("Guthaben"))
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

    // --- "wie Erkennung" bei Together/DeepInfra (Review 3.8.0) -----------------------------

    private fun useTogether() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "together"
        prefs.apiBaseUrl = "http://127.0.0.1:${server.address.port}/v1"
        prefs.apiKey = "tg-geheim"
        prefs.language = "de"
        prefs.refineMode = RefineMode.POLISH
    }

    @Test fun togetherWieErkennungMitEingetipptemModellSchicktDenChatRequest() {
        // Wie auf main: Together kann /chat/completions, der Katalog kennt dort nur kein Modell.
        useTogether()
        prefs.llmModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo"
        assertEquals("Hallo Welt.", TranscriptionEngine.transcribe(ctx, speech))
        assertEquals("meta-llama/Llama-3.3-70B-Instruct-Turbo", JSONObject(chatBody!!).getString("model"))
    }

    @Test fun togetherWieErkennungOhneModellUeberspringtMitHinweis() {
        useTogether()
        var hint: String? = null
        assertEquals("Also hallo welt", TranscriptionEngine.transcribe(ctx, speech) { hint = it })
        assertEquals(com.chris.whisperloom.api.TextRefiner.MSG_NO_MODEL, hint)
        assertNull(chatBody) // kein Request mit "model":""
    }
}
