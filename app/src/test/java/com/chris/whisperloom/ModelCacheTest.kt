package com.chris.whisperloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.api.AccessResolver
import com.chris.whisperloom.api.ApiHttpException
import com.chris.whisperloom.api.ModelKind
import com.chris.whisperloom.api.RemoteModel
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/** Modell-Listen-Cache: Schluessel, Alter, kein Key in der Datei, eigene Datei. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModelCacheTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val sp get() = ctx.getSharedPreferences(ModelCache.FILE, Context.MODE_PRIVATE)
    private val settings get() = ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    private var clock = 1_000_000_000_000L
    private lateinit var cache: ModelCache
    private lateinit var server: HttpServer
    private var status = 200

    @Before fun setUp() {
        sp.edit().clear().commit()
        settings.edit().clear().commit()
        cache = ModelCache(ctx) { clock }
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/models") { ex ->
            val out = """{"data":[{"id":"whisper-large-v3"},{"id":"qwen/qwen3.8-27b"}]}""".toByteArray()
            ex.sendResponseHeaders(status, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
    }

    @After fun tearDown() {
        server.stop(0)
    }

    private fun stt(provider: String, url: String = "", key: String = "sk-streng-geheim") =
        AccessResolver.resolveStt(provider, url, key, "")

    private val models = listOf(
        RemoteModel("openai/gpt-6.1-sol", label = "OpenAI: GPT-6.1 Sol", note = "Auslauf 2027-01-01", temperatureSupported = false),
        RemoteModel("mistralai/mistral-medium-3-5", temperatureSupported = true, reasoningEffort = "none"),
        RemoteModel("x/ohne-flags"),
    )

    @Test fun schluesselAusAnbieterZweckUndAdresse() {
        assertEquals("openrouter|llm|https://openrouter.ai/api/v1", ModelCache.key("openrouter", ModelKind.LLM, " https://openrouter.ai/api/v1/ "))
        assertEquals("custom|stt|http://h:8000/v1", ModelCache.key("custom", ModelKind.STT, "http://h:8000/v1"))
    }

    @Test fun ablegenUndLesenMitAllenFeldern() {
        val access = stt("openrouter")
        val entry = cache.put(access, ModelKind.LLM, models)
        assertEquals(clock, entry.fetchedAt)
        assertEquals(entry, cache.get(access, ModelKind.LLM))
        // Gleiche Adresse mit Schraegstrich am Ende = gleicher Eintrag.
        assertEquals(models, cache.get("openrouter", ModelKind.LLM, "https://openrouter.ai/api/v1/")!!.models)
    }

    @Test fun zweckAnbieterUndAdresseSindGetrennt() {
        cache.put(stt("openrouter"), ModelKind.STT, listOf(RemoteModel("openai/whisper-1")))
        assertNull(cache.get(stt("openrouter"), ModelKind.LLM))
        assertNull(cache.get(stt("groq"), ModelKind.STT))
        cache.put(stt("custom", "http://a:8000/v1"), ModelKind.STT, listOf(RemoteModel("a")))
        cache.put(stt("custom", "http://b:8000/v1"), ModelKind.STT, listOf(RemoteModel("b")))
        assertEquals(listOf("a"), cache.get(stt("custom", "http://a:8000/v1"), ModelKind.STT)!!.models.map { it.id })
        assertEquals(listOf("b"), cache.get(stt("custom", "http://b:8000/v1"), ModelKind.STT)!!.models.map { it.id })
    }

    @Test fun nachEinemTagVeraltet() {
        assertTrue("ohne Liste neu laden", cache.isStale(null))
        val entry = cache.put(stt("groq"), ModelKind.STT, models)
        assertFalse(cache.isStale(entry))
        clock += ModelCache.MAX_AGE_MS
        assertEquals(ModelCache.MAX_AGE_MS, cache.ageMs(entry))
        assertFalse(cache.isStale(entry))
        clock += 1
        assertTrue(cache.isStale(entry))
        // Uhr zurueckgestellt: lieber neu laden als einer Liste "aus der Zukunft" trauen.
        clock = entry.fetchedAt - 1
        assertTrue(cache.isStale(entry))
    }

    @Test fun derKeyLandetNirgendsUndDieEinstellungenBleibenUnberuehrt() {
        cache.put(stt("openrouter", key = "sk-streng-geheim"), ModelKind.LLM, models)
        assertTrue(sp.all.isNotEmpty())
        assertFalse(sp.all.entries.any { (k, v) -> "sk-streng-geheim" in k || "sk-streng-geheim" in v.toString() })
        assertTrue("whisperloom.xml bleibt leer: ${settings.all}", settings.all.isEmpty())
    }

    @Test fun kaputterEintragGiltAlsNichtVorhanden() {
        sp.edit().putString(ModelCache.key("groq", ModelKind.STT, "https://api.groq.com/openai/v1"), "{kaputt").commit()
        assertNull(cache.get(stt("groq"), ModelKind.STT))
        sp.edit().putString(ModelCache.key("groq", ModelKind.STT, "https://api.groq.com/openai/v1"), """{"fetchedAt":1}""").commit()
        assertNull(cache.get(stt("groq"), ModelKind.STT))
    }

    @Test fun neueListeErsetztDieAlteUndFindetModelle() {
        val access = stt("openrouter")
        cache.put(access, ModelKind.LLM, models)
        assertEquals(false, cache.find("openrouter", ModelKind.LLM, "https://openrouter.ai/api/v1", "openai/gpt-6.1-sol")!!.temperatureSupported)
        cache.put(access, ModelKind.LLM, listOf(RemoteModel("neu/modell")))
        assertEquals(listOf("neu/modell"), cache.get(access, ModelKind.LLM)!!.models.map { it.id })
        assertNull(cache.find("openrouter", ModelKind.LLM, "https://openrouter.ai/api/v1", "openai/gpt-6.1-sol"))
        assertNull(cache.find("openrouter", ModelKind.STT, "https://openrouter.ai/api/v1", "neu/modell"))
    }

    /** Ein gespeichertes Server-Modell bekommt beim Senden die Flags aus dem Cache (Prefs -> AccessResolver). */
    @Test fun einstellungenNutzenDieFlagsAusDemCache() {
        val prefs = Prefs(ctx)
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "groq"
        prefs.llmProviderId = "openrouter"
        prefs.llmKey = "or-key"
        prefs.llmModel = "mistralai/mistral-medium-3-5"
        prefs.modelCache.put(prefs.llmAccess(), ModelKind.LLM, models)

        val option = Prefs(ctx).llmAccess().modelOption!!
        assertEquals("none", option.reasoningEffort)
        assertTrue(option.temperatureSupported)

        prefs.llmModel = "openai/gpt-6.1-sol"
        assertFalse(prefs.llmAccess().modelOption!!.temperatureSupported)
        assertEquals("OpenAI: GPT-6.1 Sol", prefs.llmAccess().modelOption!!.label)
    }

    // --- Abgelehnte temperature (3.8.6, Doppelanfrage) -------------------------------------

    @Test fun abgelehnteTemperatureWirdJeAnbieterAdresseUndModellGemerkt() {
        val access = AccessResolver.resolveLlm(stt("groq"), "anthropic", " https://api.anthropic.com/v1/ ", "sk-streng-geheim", "claude-neu")
        assertFalse(cache.rejectsTemperature("anthropic", "https://api.anthropic.com/v1", "claude-neu"))
        cache.rememberNoTemperature(access)
        // Adresse ohne Leerraum und Schraegstrich am Ende — wie der Listen-Schluessel.
        assertTrue(sp.contains("noTemp|anthropic|https://api.anthropic.com/v1|claude-neu"))
        assertTrue(cache.rejectsTemperature("anthropic", "https://api.anthropic.com/v1/", "claude-neu"))
        assertFalse("anderes Modell", cache.rejectsTemperature("anthropic", "https://api.anthropic.com/v1", "claude-alt"))
        assertFalse("anderer Anbieter", cache.rejectsTemperature("openrouter", "https://api.anthropic.com/v1", "claude-neu"))
        assertFalse("andere Adresse", cache.rejectsTemperature("anthropic", "https://proxy/v1", "claude-neu"))
        // Kein Key in der Datei, die Einstellungen bleiben unberuehrt.
        assertFalse(sp.all.entries.any { (k, v) -> "sk-streng-geheim" in k || "sk-streng-geheim" in v.toString() })
        assertTrue(settings.all.isEmpty())
    }

    /** Die Einstellungen fragen ohne temperature, sobald das Modell sie abgelehnt hat — auch ein Katalog-Modell. */
    @Test fun gemerktesModellGehtOhneTemperatureRaus() {
        val prefs = Prefs(ctx)
        prefs.engine = Engine.ONLINE
        prefs.llmProviderId = "openai"
        prefs.llmKey = "sk"
        prefs.llmModel = "gpt-4o-mini"
        assertTrue(prefs.llmAccess().modelOption!!.temperatureSupported)
        prefs.modelCache.rememberNoTemperature(prefs.llmAccess())
        assertFalse(Prefs(ctx).llmAccess().modelOption!!.temperatureSupported)
        assertEquals("GPT-4o mini", Prefs(ctx).llmAccess().modelOption!!.label)
    }

    @Test fun aktualisierenLaedtUndLegtAb() {
        val access = stt("groq", "http://127.0.0.1:${server.address.port}/v1")
        val entry = cache.refresh(access, ModelKind.STT)
        assertEquals(listOf("whisper-large-v3"), entry.models.map { it.id })
        assertEquals(entry, cache.get(access, ModelKind.STT))

        // Fehler: Ausnahme fuer die Snackbar, die alte Liste bleibt stehen.
        status = 500
        clock += 1000
        assertThrows(ApiHttpException::class.java) { cache.refresh(access, ModelKind.STT) }
        assertEquals(entry, cache.get(access, ModelKind.STT))
    }

    @Test fun fehlschlagWirdEinenTagGemerktUndErfolgVergisstIhn() {
        val access = stt("groq", "http://127.0.0.1:${server.address.port}/v1")
        val key = ModelCache.key("groq", ModelKind.STT, access.baseUrl)
        assertFalse(cache.failedRecently(key))
        status = 500
        assertThrows(ApiHttpException::class.java) { cache.refresh(access, ModelKind.STT) }
        assertTrue(cache.failedRecently(key))
        assertFalse("nur dieser Schluessel", cache.failedRecently(ModelCache.key("groq", ModelKind.LLM, access.baseUrl)))
        assertNull("der Merker ist keine Liste", cache.get(access, ModelKind.STT))
        assertFalse(sp.all.entries.any { (k, v) -> "sk-streng-geheim" in k || "sk-streng-geheim" in v.toString() })
        clock += ModelCache.MAX_AGE_MS
        assertTrue(cache.failedRecently(key))
        clock += 1
        assertFalse("nach einem Tag wieder automatisch", cache.failedRecently(key))

        // Erneut gescheitert, dann geklappt: der Merker ist weg.
        assertThrows(ApiHttpException::class.java) { cache.refresh(access, ModelKind.STT) }
        assertTrue(cache.failedRecently(key))
        status = 200
        cache.refresh(access, ModelKind.STT)
        assertFalse(cache.failedRecently(key))
    }
}
