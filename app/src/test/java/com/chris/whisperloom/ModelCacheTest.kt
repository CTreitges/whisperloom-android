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
        RemoteModel("mistralai/mistral-small-2603", temperatureSupported = true, reasoningEffort = "none"),
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
}
