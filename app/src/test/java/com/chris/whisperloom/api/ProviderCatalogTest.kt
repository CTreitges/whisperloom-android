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
            // Ausnahme Claude (direkt wie ueber OpenRouter): lehnt temperature ab, die Kompatibilitaets-
            // schicht ignoriert reasoning_effort — das Denken steuert thinkingType.
            for (m in p.llmModels.filter { !it.temperatureSupported && !it.id.contains("gemini-3") && !it.id.contains("claude-") }) {
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

    // --- Katalog-Pflege 2026-10-08 (3.8.6): Empfehlung je Stufe --------------------------------

    @Test fun katalogStand20261008() {
        assertEquals("2026-10-08", ProviderCatalog.CATALOG_DATE)
    }

    @Test fun empfehlungenZumGlaettenUndUmformulieren() {
        val expected = mapOf(
            "openai" to ("gpt-6-luna" to "gpt-6-sol"),
            "anthropic" to ("claude-haiku-5-5" to "claude-sonnet-5-5"),
            "groq" to ("openai/gpt-oss-20b" to "openai/gpt-oss-120b"),
            "mistral" to ("mistral-small-latest" to "mistral-large-2512"),
            "gemini" to ("gemini-3.5-flash-lite" to "gemini-3.8-flash"),
            "deepseek" to ("deepseek-flash" to "deepseek-v4-pro"),
            "openrouter" to ("anthropic/claude-haiku-5.5" to "anthropic/claude-sonnet-5.5"),
            "ollama-cloud" to ("gemma4:31b" to "mistral-large-3:675b"),
        )
        for ((id, models) in expected) {
            val p = ProviderCatalog.byId(id)
            assertEquals(id, models.first, p.defaultLlmModel)
            assertEquals(id, models.second, p.rewriteLlmModel)
        }
        // Ohne Katalog (Ollama lokal, eigener Server) gibt es keine Empfehlung.
        assertEquals("", ProviderCatalog.byId("ollama").rewriteLlmModel)
        assertEquals("", ProviderCatalog.custom.rewriteLlmModel)
    }

    @Test fun umformulierenEmpfehlungStehtImKatalog() {
        for (p in ProviderCatalog.providers.filter { it.rewriteLlmModel.isNotBlank() }) {
            assertNotNull(p.id, p.llmModel(p.rewriteLlmModel))
            assertTrue("${p.id}: sonst reicht der Standard", p.rewriteLlmModel != p.defaultLlmModel)
        }
    }

    /** Claude ab 4.7 lehnt jedes gesetzte temperature ab (HTTP 400) — sonst geht jedes Diktat zweimal raus. */
    @Test fun claude5OhneTemperature() {
        val claude = (ProviderCatalog.byId("anthropic").llmModels + ProviderCatalog.byId("openrouter").llmModels)
            .filter { it.id.contains("claude-") && !it.id.contains("-4") }
        assertEquals(claude.toString(), 6, claude.size)
        assertTrue(claude.toString(), claude.none { it.temperatureSupported })
        assertTrue(claude.toString(), claude.none { it.reasoningEffort != null })
        // Haiku 4.5 laeuft noch (Legacy) und kann temperature.
        assertTrue(ProviderCatalog.byId("anthropic").llmModel("claude-haiku-4-5")!!.temperatureSupported)
    }

    /** Gemessen 2026-10-08: welches Claude-Modell welchen thinking-Wert annimmt. */
    @Test fun claudeDenkenNurWoDasModellEsZulaesst() {
        val anthropic = ProviderCatalog.byId("anthropic")
        assertEquals("disabled", anthropic.llmModel("claude-haiku-5-5")!!.thinkingType)
        assertEquals("disabled", anthropic.llmModel("claude-sonnet-5")!!.thinkingType)
        // Bei "disabled" kommt HTTP 400.
        assertEquals("between_tools", anthropic.llmModel("claude-sonnet-5-5")!!.thinkingType)
        // Opus lehnt beide Werte ab und denkt immer adaptiv.
        assertNull(anthropic.llmModel("claude-opus-5-5")!!.thinkingType)
        // OpenRouter hat eine andere Parameter-Syntax: dort hilft nur das hoehere Limit.
        assertTrue(ProviderCatalog.byId("openrouter").llmModels.none { it.thinkingType != null })
    }

    @Test fun deepSeekDenktNichtUndGeminiFlashNurKurz() {
        assertTrue(ProviderCatalog.byId("deepseek").llmModels.all { it.thinkingType == "disabled" })
        assertEquals("low", ProviderCatalog.byId("gemini").llmModel("gemini-3.8-flash")!!.reasoningEffort)
        assertEquals("low", ProviderCatalog.byId("openrouter").llmModel("google/gemini-3.8-flash")!!.reasoningEffort)
        // Nur dort, wo es gemessen bzw. dokumentiert ist.
        val others = ProviderCatalog.providers.filter { it.id != "anthropic" && it.id != "deepseek" }
        assertTrue(others.flatMap { it.llmModels }.none { it.thinkingType != null })
    }

    @Test fun alteStandardmodelleBleibenWaehlbar() {
        val openai = ProviderCatalog.openai
        assertNotNull(openai.llmModel("gpt-4o-mini"))
        assertTrue(openai.llmModel("gpt-5.4-nano")!!.note.contains("2027-04-01"))
        assertTrue(ProviderCatalog.byId("anthropic").llmModel("claude-haiku-4-5")!!.label.contains("Legacy"))
        assertTrue(ProviderCatalog.byId("openrouter").llmModel("anthropic/claude-haiku-4.5")!!.label.contains("Legacy"))
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
