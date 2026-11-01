package com.chris.whisperloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.api.NetworkCheck
import com.chris.whisperloom.llm.LocalTextEngine
import com.chris.whisperloom.llm.deviceRam
import com.chris.whisperloom.llm.fakeTextModels
import com.chris.whisperloom.llm.resetTextEngine
import com.sun.net.httpserver.HttpExchange
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
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Netzpruefung in [RefinePlan.of] mit Fallback (CTR-248): ohne Internet, aber mit erreichbarem
 * Fallback im eigenen Netz (Ollama im LAN) wird der Fallback versucht, statt die Verbesserung
 * mit "kein Netz" zu ueberspringen. Beide Zugaenge sind "eigene Server" auf einem lokalen
 * HttpServer (/erst/v1 = erster Zugang ueber das Internet, /fallback/v1 = LAN-Fallback); das Netz
 * ist ein Fake, der je Adresse antwortet. Was "0 Anfragen" heisst, ist nicht rausgegangen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RefinePlanNetworkTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs
    private lateinit var server: HttpServer

    private val originalNetwork = RefinePlan.networkCheck
    private val originalModel = LocalTextEngine.factory

    private val firstHits = AtomicInteger()
    private val fallbackHits = AtomicInteger()

    /** Fake-Netz: Internet an/aus; das LAN (Adressen unter /fallback/) ist erreichbar, solange [lan]. */
    @Volatile private var internet = true
    @Volatile private var lan = true
    private val asked: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val base get() = "http://127.0.0.1:${server.address.port}"
    private val customServer = "Eigener Server (OpenAI-kompatibel)"

    @Before fun aufbau() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/erst/v1/chat/completions") { answer(it, firstHits, "Vom ersten verbessert.") }
        server.createContext("/fallback/v1/chat/completions") { answer(it, fallbackHits, "Vom Fallback verbessert.") }
        server.start()
        RefinePlan.networkCheck = {
            NetworkCheck { url, _ ->
                asked += url
                if (url.contains("/fallback/")) lan else internet
            }
        }
        LocalTextEngine.init(ctx)
        fakeTextModels { _, _ -> "Lokal verbessert." }
        deviceRam(ctx, 8)

        prefs.language = "de"
        prefs.refineMode = RefineMode.POLISH
        // Offline-Erkennung: kein Online-Nachweis des Netzes, der eigene Zugang muss es selbst finden.
        prefs.engine = Engine.OFFLINE
        prefs.offlineRefine = OfflineRefineRule.SKIP
        prefs.llmProviderId = "custom"
        prefs.llmUrl = "$base/erst/v1"
        prefs.llmModel = "qwen3:8b"
        prefs.llmFallbackEnabled = true
        prefs.llmFallbackProviderId = "custom"
        prefs.llmFallbackUrl = "$base/fallback/v1"
        prefs.llmFallbackModel = "gemma4:12b"
    }

    @After fun abbau() {
        server.stop(0)
        RefinePlan.networkCheck = originalNetwork
        resetTextEngine(originalModel)
    }

    private fun answer(ex: HttpExchange, hits: AtomicInteger, text: String) {
        hits.incrementAndGet()
        ex.requestBody.readBytes()
        val out = """{"choices":[{"message":{"content":"$text"}}]}""".toByteArray()
        ex.sendResponseHeaders(200, out.size.toLong())
        ex.responseBody.use { it.write(out) }
    }

    private fun plan() = RefinePlan.of(ctx, prefs, RefineMode.POLISH)

    private fun refine(plan: RefinePlan, notes: MutableList<String>) =
        plan.refine("also ähm hallo welt", "de", Refinement(RefineMode.POLISH), onNote = { notes += it })

    @Test fun keinInternetLanFallbackErreichbarFallbackRechnet() {
        internet = false

        val plan = plan()
        assertEquals(RefineRoute.Online(fallbackLocal = false), plan.route)

        val notes = mutableListOf<String>()
        assertEquals("Vom Fallback verbessert.", refine(plan, notes))
        assertEquals("erster Zugang ohne Netz: keine Anfrage", 0, firstHits.get())
        assertEquals(1, fallbackHits.get())
        assertEquals(listOf(RefinePlan.fallbackMessage(customServer)), notes)
        assertEquals("gemma4:12b", plan.modelLabel())
    }

    @Test fun internetDaErsterZugangWieBisher() {
        val plan = plan()
        assertEquals(RefineRoute.Online(fallbackLocal = false), plan.route)

        val notes = mutableListOf<String>()
        assertEquals("Vom ersten verbessert.", refine(plan, notes))
        assertEquals(1, firstHits.get())
        assertEquals(0, fallbackHits.get())
        assertTrue(notes.isEmpty())
        assertEquals("qwen3:8b", plan.modelLabel())
        // Mit Internet wird nur der erste Zugang geprueft.
        assertEquals(listOf("$base/erst/v1"), asked.toList())
    }

    @Test fun wederInternetNochLanKeinNetzWieBisher() {
        internet = false
        lan = false

        assertEquals(RefineRoute.Raw(RefineHint.NO_NET), plan().route)
        assertEquals(0, firstHits.get() + fallbackHits.get())
    }

    @Test fun keinInternetOhneFallbackKeinNetzWieBisher() {
        internet = false
        prefs.llmFallbackEnabled = false

        assertEquals(RefineRoute.Raw(RefineHint.NO_NET), plan().route)
        assertEquals(listOf("$base/erst/v1"), asked.toList())
    }
}
