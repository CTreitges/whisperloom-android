package com.chris.whisperloom.api

import com.chris.whisperloom.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer den Anbieter-Katalog. Er ist reine Daten — aber falsche Daten
 * heissen 400/401 beim Anbieter, und die Defaults muessen im Katalog existieren.
 */
class ProviderCatalogTest {

    @Test fun idsSindEindeutig() {
        val ids = ProviderCatalog.providers.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun modellIdsSindJeAnbieterEindeutig() {
        for (p in ProviderCatalog.providers) {
            val stt = p.sttModels.map { it.id }
            val llm = p.llmModels.map { it.id }
            assertEquals("STT ${p.id}", stt.size, stt.toSet().size)
            assertEquals("LLM ${p.id}", llm.size, llm.toSet().size)
        }
    }

    @Test fun cloudAnbieterNutzenHttpsUndVerlangenEinenKey() {
        // needsUrl = eigener Server bzw. Ollama im Heimnetz: Adresse und http:// kommen vom Nutzer.
        for (p in ProviderCatalog.providers.filter { !it.needsUrl }) {
            assertTrue(p.id, p.baseUrl.startsWith("https://"))
            assertTrue(p.id, p.needsKey)
            assertTrue(p.id, p.keyUrl.startsWith("https://"))
            assertFalse(p.id, p.allowsHttp)
            assertEquals(p.id, 90, p.defaultReadTimeoutSec)
            p.sttPathOverride?.let { assertTrue(p.id, it.startsWith("https://")) }
        }
    }

    @Test fun eigenerServerIstOffenKonfigurierbar() {
        val c = ProviderCatalog.custom
        assertEquals("", c.baseUrl)
        assertFalse(c.needsKey)
        assertTrue(c.allowsHttp)
        assertEquals(600, c.defaultReadTimeoutSec)
        assertTrue(c.hasStt)
        assertTrue(c.hasLlm)
        assertEquals("", c.defaultSttModel)
    }

    @Test fun defaultsExistierenImKatalog() {
        val openai = ProviderCatalog.openai
        assertEquals(Prefs.DEFAULT_API_URL, openai.baseUrl)
        assertEquals(Prefs.DEFAULT_API_MODEL, openai.defaultSttModel)
        assertEquals(Prefs.DEFAULT_LLM_MODEL, openai.defaultLlmModel)
        assertNotNull(openai.sttModel(Prefs.DEFAULT_API_MODEL))
        assertNotNull(openai.llmModel(Prefs.DEFAULT_LLM_MODEL))
    }

    @Test fun groqDefaultsSindDieGratisModelle() {
        val groq = ProviderCatalog.byId("groq")
        assertEquals("whisper-large-v3-turbo", groq.defaultSttModel)
        assertEquals("openai/gpt-oss-20b", groq.defaultLlmModel)
    }

    @Test fun groqQwenIstDer38erNachfolger() {
        // qwen/qwen3.6-27b ist seit 2026-09-14 abgeschaltet; der Nachfolger denkt ohne effort=none ebenso laut.
        val groq = ProviderCatalog.byId("groq")
        assertNull(groq.llmModel("qwen/qwen3.6-27b"))
        assertEquals("none", groq.llmModel("qwen/qwen3.8-27b")!!.reasoningEffort)
        assertEquals("Qwen 3.8 27B (Preview)", groq.llmModel("qwen/qwen3.8-27b")!!.label)
    }

    @Test fun gptTranscribeSendetLanguagesArray() {
        assertEquals("languages[]", ProviderCatalog.openai.sttModel("gpt-transcribe")!!.languageField)
        assertEquals("language", ProviderCatalog.openai.sttModel("gpt-4o-transcribe")!!.languageField)
    }

    @Test fun altesDefaultModellIstAlsAuslaufGekennzeichnet() {
        assertTrue(ProviderCatalog.openai.sttModel("gpt-4o-transcribe")!!.label.contains("Auslauf"))
    }

    @Test fun reasoningModelleOhneTemperatureHabenEinenEffort() {
        for (p in ProviderCatalog.providers) {
            // Ausnahme Gemini 3: temperature faellt auf Googles Rat weg, das Denken ist nicht abschaltbar.
            for (m in p.llmModels.filter { !it.temperatureSupported && !it.id.contains("gemini-3") }) {
                assertNotNull("${p.id}/${m.id}", m.reasoningEffort)
            }
        }
    }

    @Test fun anbieterFlagsAusDerRecherche() {
        val mistral = ProviderCatalog.byId("mistral")
        assertFalse(mistral.sttSendsPrompt)
        assertFalse(mistral.sttSendsResponseFormat)
        val openrouter = ProviderCatalog.byId("openrouter")
        assertFalse(openrouter.sttSendsPrompt)
        assertTrue(openrouter.sttSendsResponseFormat)
        assertEquals(
            "https://api.deepinfra.com/v1/audio/transcriptions",
            ProviderCatalog.byId("deepinfra").sttPathOverride,
        )
        assertNull(ProviderCatalog.openai.sttPathOverride)
    }

    @Test fun anzeigeReihenfolgeDerDropdowns() {
        assertEquals(
            listOf("openai", "groq", "mistral", "elevenlabs", "together", "deepinfra", "openrouter", "custom"),
            ProviderCatalog.sttProviders.map { it.id },
        )
        assertEquals(
            listOf("openai", "groq", "mistral", "openrouter", "anthropic", "gemini", "deepseek", "ollama", "ollama-cloud", "custom"),
            ProviderCatalog.llmProviders.map { it.id },
        )
    }

    @Test fun ollamaLokalBrauchtAdresseAberKeinenKey() {
        val o = ProviderCatalog.byId(ProviderCatalog.OLLAMA_ID)
        assertTrue(o.isOllama)
        assertTrue(o.needsUrl)
        assertFalse(o.needsKey)
        assertTrue(o.allowsHttp)
        assertEquals(600, o.defaultReadTimeoutSec)
        assertTrue(o.hasLlm)
        // Ollama kann kein Audio: nie in der Transkriptions-Auswahl.
        assertFalse(o.hasStt)
        assertEquals("", o.defaultLlmModel)
    }

    @Test fun ollamaCloudMitKeyUndEmpfohlenemModell() {
        val c = ProviderCatalog.byId(ProviderCatalog.OLLAMA_CLOUD_ID)
        assertTrue(c.isOllama)
        assertEquals("https://ollama.com", c.baseUrl)
        assertTrue(c.needsKey)
        assertEquals("https://ollama.com/settings/keys", c.keyUrl)
        assertFalse(c.hasStt)
        // Schnellstes Modell ohne Nachdenken (live gemessen) ist der Default.
        assertEquals("gemma4:31b", c.defaultLlmModel)
    }

    @Test fun nurOllamaSprichtDieNativeApi() {
        assertEquals(
            listOf("ollama", "ollama-cloud"),
            ProviderCatalog.providers.filter { it.api == ApiStyle.OLLAMA }.map { it.id },
        )
    }

    @Test fun elevenLabsIstNurErkennungMitScribe() {
        val e = ProviderCatalog.byId(ProviderCatalog.ELEVENLABS_ID)
        assertEquals(ApiStyle.ELEVENLABS, e.api)
        assertEquals("ElevenLabs (Scribe)", e.name)
        assertEquals("https://api.elevenlabs.io/v1", e.baseUrl)
        assertEquals("https://elevenlabs.io/app/settings/api-keys", e.keyUrl)
        assertTrue(e.hasStt)
        assertFalse(e.hasLlm)
        // Vokabular kommt an (als keyterms) — kein "Kontext kommt nicht an"-Hinweis.
        assertTrue(e.sttSendsPrompt)
        // Nur Batch-Modelle: scribe_v2_realtime spricht WebSocket, scribe_v1 ist abgekuendigt.
        assertEquals(listOf("scribe_v2", "scribe_v2_medical"), e.sttModels.map { it.id })
        assertEquals("scribe_v2", e.defaultSttModel)
        assertTrue(e.sttModel("scribe_v2")!!.note.contains("\$0.22/h"))
    }

    /** Katalog-Pflege 2026-09-30: aktuelle Defaults, auslaufende Empfehlungen raus. */
    @Test fun katalogStand20260930() {
        assertEquals("2026-09-30", ProviderCatalog.CATALOG_DATE)
        val deepseek = ProviderCatalog.byId("deepseek")
        assertEquals("deepseek-flash", deepseek.defaultLlmModel)
        // Der alte Name wird noch angenommen und bleibt fuer gespeicherte Auswahlen waehlbar.
        assertTrue(deepseek.llmModel("deepseek-v4-flash")!!.label.contains("alter Name"))
        val gemini = ProviderCatalog.byId("gemini")
        assertEquals(
            listOf("gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-2.5-flash-lite", "gemini-2.5-flash"),
            gemini.llmModels.map { it.id },
        )
        // Reasoning ist ab Gemini 3 nicht abschaltbar -> kein reasoning_effort=none.
        assertNull(gemini.llmModel("gemini-3.5-flash-lite")!!.reasoningEffort)
        // Google raet bei Gemini 3 von temperature < 1 ab -> alle 3.x-Eintraege ohne temperature.
        val gemini3 = (gemini.llmModels + ProviderCatalog.byId("openrouter").llmModels).filter { it.id.contains("gemini-3") }
        assertEquals(3, gemini3.size)
        assertTrue(gemini3.toString(), gemini3.none { it.temperatureSupported })
        assertTrue(gemini.llmModel("gemini-2.5-flash-lite")!!.temperatureSupported)
        // OpenRouter nimmt google/gemini-2.5-* am 2026-10-20 aus dem Angebot.
        val openrouter = ProviderCatalog.byId("openrouter").llmModels.map { it.id }
        assertTrue(openrouter.toString(), openrouter.none { it.startsWith("google/gemini-2.5-") })
        assertTrue(openrouter.contains("google/gemini-3.8-flash"))
    }

    @Test fun gespeicherteModellIdsBleibenUnveraendert() {
        val stt = AccessResolver.resolveStt("groq", "", "gsk", "")
        assertEquals("gemini-2.5-flash-lite", AccessResolver.resolveLlm(stt, "gemini", "", "k", "gemini-2.5-flash-lite").model)
        assertEquals("deepseek-v4-flash", AccessResolver.resolveLlm(stt, "deepseek", "", "k", "deepseek-v4-flash").model)
        assertEquals("google/gemini-2.5-flash-lite", AccessResolver.resolveLlm(stt, "openrouter", "", "k", "google/gemini-2.5-flash-lite").model)
        // Nur wer nie ein Modell gewaehlt hat, bekommt den neuen Default.
        assertEquals("gemini-3.5-flash-lite", AccessResolver.resolveLlm(stt, "gemini", "", "k", "").model)
        assertEquals("deepseek-flash", AccessResolver.resolveLlm(stt, "deepseek", "", "k", "").model)
    }

    @Test fun unbekannteIdFaelltAufOpenAiZurueck() {
        assertEquals("openai", ProviderCatalog.byId("").id)
        assertEquals("openai", ProviderCatalog.byId("gibtsnicht").id)
        assertNull(ProviderCatalog.find("gibtsnicht"))
        assertEquals("groq", ProviderCatalog.byId("groq").id)
    }
}
