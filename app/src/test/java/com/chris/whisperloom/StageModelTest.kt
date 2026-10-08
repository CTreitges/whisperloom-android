package com.chris.whisperloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.api.AccessResolver
import com.chris.whisperloom.api.NetworkCheck
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.util.Collections

/**
 * Modell je Stufe (3.8.6), end-to-end: welches `"model"` beim Diktat und bei geteilten
 * Sprachnachrichten an den Server geht. Eigener Zugang "Anthropic" mit der Adresse eines lokalen
 * JDK-HttpServers — so gelten die Katalog-Empfehlungen, ohne dass etwas rausgeht.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StageModelTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs
    private lateinit var server: HttpServer
    private val bodies: MutableList<JSONObject> = Collections.synchronizedList(mutableListOf())
    private val originalNetwork = RefinePlan.networkCheck
    private val speech = FloatArray(AudioUtils.SAMPLE_RATE) { 0.3f }

    @Before fun aufbau() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(ModelCache.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/audio/transcriptions") { ex ->
            ex.requestBody.readBytes()
            val out = """{"text":"also ähm hallo welt"}""".toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.createContext("/v1/chat/completions") { ex ->
            bodies += JSONObject(ex.requestBody.readBytes().toString(Charsets.UTF_8))
            val out = """{"choices":[{"message":{"content":"Hallo Welt."},"finish_reason":"stop"}]}""".toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
        RefinePlan.networkCheck = { NetworkCheck { _, _ -> true } }

        val url = "http://127.0.0.1:${server.address.port}/v1"
        prefs = Prefs(ctx)
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = url
        prefs.apiModel = "whisper-1"
        prefs.language = "de"
        prefs.llmProviderId = "anthropic"
        prefs.llmUrl = url
        prefs.llmKey = "sk-ant"
    }

    @After fun abbau() {
        server.stop(0)
        RefinePlan.networkCheck = originalNetwork
    }

    /** Das Modell der letzten Anfrage nach einem Diktat mit Stufe [mode]. */
    private fun diktat(mode: RefineMode, readable: Boolean = false): String {
        prefs.refineMode = mode
        prefs.setPolishCleanupFor(RefineWay.DICTATION, if (readable) PolishCleanup.READABLE else PolishCleanup.PLAIN)
        TranscriptionEngine.transcribe(ctx, speech)
        return bodies.last().getString("model")
    }

    @Test fun ohneEigeneModelleDieEmpfehlungJeStufe() {
        assertEquals("claude-haiku-5-5", diktat(RefineMode.POLISH))
        assertEquals("claude-haiku-5-5", diktat(RefineMode.POLISH, readable = true))
        assertEquals("claude-sonnet-5-5", diktat(RefineMode.BEAUTIFY))
        assertEquals("claude-sonnet-5-5", diktat(RefineMode.SUMMARIZE))
        assertEquals(4, bodies.size)
    }

    @Test fun diktatSchicktDasModellSeinerStufe() {
        prefs.setLlmModelFor(RefineMode.POLISH, "claude-sonnet-5")
        prefs.setLlmModelFor(RefineMode.BEAUTIFY, "claude-opus-5-5")
        assertEquals("claude-opus-5-5", diktat(RefineMode.BEAUTIFY))
        // "Lesbar" rechnet mit dem Glaetten-Modell.
        assertEquals("claude-sonnet-5", diktat(RefineMode.POLISH, readable = true))
        assertEquals("claude-sonnet-5-5", diktat(RefineMode.SUMMARIZE))
    }

    @Test fun dasModellDesZugangsGiltFuerStufenOhneEigenes() {
        prefs.llmModel = "claude-sonnet-5"
        prefs.setLlmModelFor(RefineMode.SUMMARIZE, "claude-opus-5-5")
        assertEquals("claude-sonnet-5", diktat(RefineMode.BEAUTIFY))
        assertEquals("claude-opus-5-5", diktat(RefineMode.SUMMARIZE))
    }

    // --- Review 3.8.6 (L1): ohne Modell des Zugangs wirkt kein Stufen-Modell -------------------

    @Test fun togetherWieErkennungOhneModellSchicktAuchMitStufenModellNichts() {
        // Oberflaeche, Tastatur und Banner sagen "kein Modell" — das Diktat darf nicht doch rausgehen.
        prefs.sttProviderId = "together"
        prefs.apiKey = "k"
        prefs.llmProviderId = AccessResolver.LLM_SAME
        prefs.setLlmModelFor(RefineMode.BEAUTIFY, "Y")
        prefs.refineMode = RefineMode.BEAUTIFY
        TranscriptionEngine.transcribe(ctx, speech)
        assertEquals(0, bodies.size)
        assertFalse(SetupState.llmReady(prefs.llmAccess(RefineMode.BEAUTIFY)))
    }

    @Test fun ollamaOhneModellDesZugangsIstAuchMitStufenModellNichtBereit() {
        prefs.llmProviderId = "ollama"
        prefs.llmModel = ""
        prefs.setLlmModelFor(RefineMode.BEAUTIFY, "gemma4:26b")
        assertFalse(SetupState.llmReady(prefs.llmAccess(RefineMode.BEAUTIFY)))
        assertEquals("", prefs.llmAccess(RefineMode.BEAUTIFY).model)
    }

    @Test fun sprachnachrichtSchicktDasModellIhrerStufe() {
        prefs.refineMode = RefineMode.POLISH
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        prefs.setLlmModelFor(RefineMode.SUMMARIZE, "claude-opus-5-5")
        val result = SharedRefine.run(ctx, prefs, listOf("Erstes Stück.", "Zweites Stück."), "de")
        assertEquals(RefineMode.SUMMARIZE, result.mode)
        assertEquals(listOf("claude-opus-5-5", "claude-opus-5-5"), bodies.map { it.getString("model") })
        // Ohne eigenes Modell: die Empfehlung zum Umformulieren, nicht das Glaetten-Modell des Diktats.
        bodies.clear()
        prefs.setLlmModelFor(RefineMode.SUMMARIZE, "")
        SharedRefine.run(ctx, prefs, listOf("Hallo."), "de")
        assertEquals("claude-sonnet-5-5", bodies.single().getString("model"))
    }

    /** 3.9.0: eigene Bereinigung je Weg, aber ein Modell je Stufe fuer beide Wege. */
    @Test fun sprachnachrichtUndDiktatTeilenDasModellDerStufeGleichWelcheBereinigung() {
        prefs.setLlmModelFor(RefineMode.POLISH, "claude-sonnet-5")
        prefs.shareRefineMode = RefineMode.POLISH
        for (cleanup in PolishCleanup.entries) {
            prefs.setPolishCleanupFor(RefineWay.SHARE, cleanup)
            SharedRefine.run(ctx, prefs, listOf("Hallo."), "de")
            assertEquals(cleanup.name, "claude-sonnet-5", bodies.last().getString("model"))
        }
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.CLEAN)
        assertEquals("claude-sonnet-5", diktatMitBereinigung())
    }

    /** Das Modell der letzten Anfrage nach einem Diktat mit "Glaetten" und der eingestellten Bereinigung. */
    private fun diktatMitBereinigung(): String {
        prefs.refineMode = RefineMode.POLISH
        TranscriptionEngine.transcribe(ctx, speech)
        return bodies.last().getString("model")
    }
}
