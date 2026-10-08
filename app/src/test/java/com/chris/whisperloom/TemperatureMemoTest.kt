package com.chris.whisperloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.api.ChatPayload
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
 * Doppelanfrage (3.8.6): Ein Modell, das `temperature` ablehnt (Claude 5.x, frei eingetragene
 * Reasoning-Modelle), kostet genau EINEN Fehlversuch. Danach merkt sich die App das je Anbieter,
 * Adresse und Modell (ModelCache) — fuer das naechste Diktat wie fuer die weiteren Stuecke einer
 * Sprachnachricht. Der lokale JDK-HttpServer zaehlt jede Anfrage.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TemperatureMemoTest {

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
        // Lehnt temperature ab wie Claude 5.x ueber die OpenAI-Kompatibilitaetsschicht.
        server.createContext("/v1/chat/completions") { ex ->
            val body = JSONObject(ex.requestBody.readBytes().toString(Charsets.UTF_8))
            bodies += body
            val (status, text) = if (body.has("temperature")) 400 to TEMPERATURE_ERROR else 200 to ANSWER
            val out = text.toByteArray()
            ex.sendResponseHeaders(status, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
        RefinePlan.networkCheck = { NetworkCheck { _, _ -> true } }

        prefs = Prefs(ctx)
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "http://127.0.0.1:${server.address.port}/v1"
        prefs.apiModel = "whisper-1"
        prefs.language = "de"
        prefs.llmModel = "qwen3:8b"
        prefs.refineMode = RefineMode.POLISH
    }

    @After fun abbau() {
        server.stop(0)
        RefinePlan.networkCheck = originalNetwork
    }

    @Test fun zweitesDiktatSchicktNurNochEineAnfrage() {
        assertEquals("Hallo Welt.", TranscriptionEngine.transcribe(ctx, speech))
        assertEquals("erstes Diktat: Fehlversuch + zweiter Versuch", 2, bodies.size)

        bodies.clear()
        assertEquals("Hallo Welt.", TranscriptionEngine.transcribe(ctx, speech))
        assertEquals("zweiter Auftrag = 1 Anfrage", 1, bodies.size)
        assertFalse(bodies[0].has("temperature"))
        assertEquals(ChatPayload.MAX_COMPLETION_TOKENS, bodies[0].getInt("max_completion_tokens"))
    }

    @Test fun weitereStueckeEinerSprachnachrichtOhneFehlversuch() {
        prefs.shareRefineMode = RefineMode.POLISH
        val result = SharedRefine.run(ctx, prefs, listOf("Erstes Stück.", "Zweites Stück."), "de")
        assertEquals(listOf("Hallo Welt.", "Hallo Welt."), result.paragraphs)
        // Erstes Stueck: Fehlversuch + zweiter Versuch; das zweite gleich ohne temperature.
        assertEquals(3, bodies.size)
        assertFalse(bodies[2].has("temperature"))
    }

    @Test fun gemerktWirdJeModell() {
        TranscriptionEngine.transcribe(ctx, speech)
        prefs.llmModel = "qwen3:14b"
        bodies.clear()
        TranscriptionEngine.transcribe(ctx, speech)
        assertEquals("anderes Modell: wieder erst mit temperature", 2, bodies.size)
        assertEquals(0, bodies[0].getInt("temperature"))
    }

    private companion object {
        const val ANSWER = """{"choices":[{"message":{"content":"Hallo Welt."},"finish_reason":"stop"}]}"""
        const val TEMPERATURE_ERROR =
            """{"type":"error","error":{"type":"invalid_request_error","message":"temperature: Setting temperature is not supported for this model."}}"""
    }
}
