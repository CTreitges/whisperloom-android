package com.chris.whisperloom.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Flags fuer Modell-IDs ohne exakten Katalog-Treffer ([ModelLists.optionFor]): Server-Metadaten,
 * Katalog-Snapshot, Heuristik je Familie. Rein (JVM).
 */
class ModelFlagsTest {

    private fun p(id: String) = ProviderCatalog.byId(id)

    private fun llm(provider: String, id: String, server: RemoteModel? = null) =
        ModelLists.optionFor(p(provider), ModelKind.LLM, id, server)

    private fun stt(provider: String, id: String, server: RemoteModel? = null) =
        ModelLists.optionFor(p(provider), ModelKind.STT, id, server)

    @Test fun katalogTrefferBleibtDerKatalogEintrag() {
        assertSame(p("openai").llmModel("gpt-5.6-luna"), llm("openai", "gpt-5.6-luna"))
        assertSame(p("openai").sttModel("gpt-transcribe"), stt("openai", "gpt-transcribe"))
    }

    @Test fun ohneHinweisBleibtEsBeiDenDefaults() {
        assertNull(llm("openai", ""))
        assertNull(llm("openai", "mein-eigenes"))
        assertNull(llm("openai", "gpt-4.1"))
        assertNull(llm("groq", "moonshotai/kimi-k3"))
        assertNull(stt("groq", "whisper-large-v4"))
        assertNull(llm("custom", "llama3.2"))
    }

    @Test fun openAiReasoningNamenOhneTemperature() {
        for (id in listOf("gpt-6-astra", "gpt-6.1-sol", "gpt-5.5", "o4-mini", "o3", "ft:gpt-5-mini:firma::abc")) {
            val o = llm("openai", id)!!
            assertFalse(id, o.temperatureSupported)
            // Ohne Angabe denken sie auf "medium" — "low" nimmt jede dieser Familien an.
            assertEquals(id, "low", o.reasoningEffort)
            assertEquals(id, o.label)
        }
        // Gleiche Familie ueber OpenRouter ohne Metadaten (frei getippt): Name ohne Anbieter-Praefix.
        val viaRouter = llm("openrouter", "openai/gpt-6-astra")!!
        assertFalse(viaRouter.temperatureSupported)
        assertNull("effort-Heuristik nur bei OpenAI selbst", viaRouter.reasoningEffort)
        // Snapshot eines Katalog-Modells behaelt dessen Stufe (gpt-5-mini: minimal) statt "low".
        assertEquals("minimal", llm("openai", "gpt-5-mini-2025-08-07")!!.reasoningEffort)
        // Klassische Modelle bekommen keine Stufe.
        assertNull(llm("openai", "gpt-4.1"))
    }

    @Test fun snapshotErbtVomKatalogModell() {
        val mini = llm("openai", "gpt-5-mini-2025-08-07")!!
        assertFalse(mini.temperatureSupported)
        assertEquals("minimal", mini.reasoningEffort)
        assertTrue(llm("openai", "gpt-4o-mini-2024-07-18")!!.temperatureSupported)
        // Snapshot von gpt-4o-mini-transcribe: language, obwohl der Name nach gpt-*transcribe* aussieht.
        assertEquals("language", stt("openai", "gpt-4o-mini-transcribe-2025-12-15")!!.languageField)
        assertEquals("languages[]", stt("openai", "gpt-transcribe-2026-08-01")!!.languageField)
    }

    @Test fun neueGptTranscribeVarianteBekommtLanguagesArray() {
        val neu = stt("openai", "gpt-5-transcribe")!!
        assertEquals("languages[]", neu.languageField)
        assertTrue("temperature ist kein Erkennungs-Flag", neu.temperatureSupported)
        // OpenRouter nimmt language (ISO-639-1), auch fuer OpenAI-Modelle.
        assertNull(stt("openrouter", "openai/gpt-5-transcribe"))
    }

    @Test fun groqQwen3OhneNachdenkenUndGptOssLow() {
        // qwen3.8 steht im Katalog — die Heuristik greift fuer die naechste Qwen3-Variante.
        assertEquals("none", llm("groq", "qwen/qwen3.9-32b")!!.reasoningEffort)
        assertEquals("low", llm("groq", "openai/gpt-oss-safeguard-20b")!!.reasoningEffort)
        assertTrue(llm("groq", "qwen/qwen3.9-32b")!!.temperatureSupported)
        // Nur bei Groq; OpenRouter hat eigene Metadaten.
        assertNull(llm("openrouter", "qwen/qwen3.9-32b"))
    }

    @Test fun serverMetadatenGewinnen() {
        val sol = llm(
            "openrouter", "openai/gpt-6.1-sol",
            RemoteModel("openai/gpt-6.1-sol", label = "OpenAI: GPT-6.1 Sol", note = "Auslauf 2027-01-01", temperatureSupported = false),
        )!!
        assertFalse(sol.temperatureSupported)
        assertNull(sol.reasoningEffort)
        assertEquals("OpenAI: GPT-6.1 Sol", sol.label)
        assertEquals("Auslauf 2027-01-01", sol.note)
        // Metadaten schlagen die Namens-Heuristik.
        assertTrue(llm("openrouter", "openai/gpt-5.4-mini", RemoteModel("openai/gpt-5.4-mini", temperatureSupported = true))!!.temperatureSupported)
        assertEquals("none", llm("openrouter", "mistralai/x", RemoteModel("mistralai/x", reasoningEffort = "none"))!!.reasoningEffort)
        // Ohne Flags: Anzeigename vom Server, sonst Defaults.
        val plain = llm("anthropic", "claude-opus-6", RemoteModel("claude-opus-6", label = "Claude Opus 6"))!!
        assertEquals("Claude Opus 6", plain.label)
        assertTrue(plain.temperatureSupported)
        assertNull(plain.reasoningEffort)
        assertEquals("language", plain.languageField)
    }

    @Test fun reasoningOhneTemperatureSendetMaxCompletionTokens() {
        val stt = AccessResolver.resolveStt("openai", "", "sk", "")
        val s = ChatPayload.sampling(AccessResolver.resolveLlm(stt, "same", "", "", "gpt-6-astra"))
        assertNull(s.temperature)
        assertEquals("low", s.reasoningEffort)
        assertEquals(ChatPayload.MAX_COMPLETION_TOKENS, s.maxCompletionTokens)
    }

    @Test fun gemini3OhneTemperatureNurBeimAnbieterGemini() {
        // Google raet bei Gemini 3 von temperature < 1 ab; Reasoning ist nicht abschaltbar -> kein effort.
        for (id in listOf("gemini-3.6-flash", "gemini-3.1-pro-preview", "gemini-3-flash-preview")) {
            val o = llm("gemini", id)!!
            assertFalse(id, o.temperatureSupported)
            assertNull(id, o.reasoningEffort)
        }
        // Snapshot eines Katalog-Modells erbt dessen Flag.
        assertFalse(llm("gemini", "gemini-3.5-flash-lite-preview-09-2026")!!.temperatureSupported)
        // Gemini 2.5 und andere Anbieter bleiben bei den Defaults.
        assertNull(llm("gemini", "gemini-2.5-pro"))
        assertNull(llm("custom", "gemini-3.6-flash"))
    }

    @Test fun claudeSnapshotErbtTemperatureUndThinking() {
        // Ein datierter Snapshot vom Server bekommt die Flags seines Katalog-Modells — ohne Namens-Heuristik.
        val haiku = llm("anthropic", "claude-haiku-5-5-20261007")!!
        assertFalse(haiku.temperatureSupported)
        assertEquals("disabled", haiku.thinkingType)
        assertEquals("between_tools", llm("anthropic", "claude-sonnet-5-5-20260601")!!.thinkingType)
        // Unbekanntes Claude-Modell: keine Heuristik, der zweite Versuch und das Merken fangen es.
        assertNull(llm("anthropic", "claude-opus-6"))
    }

    @Test fun gelistetGiltAuchFuerDatierteSnapshots() {
        // Anthropic listet datierte IDs: die Empfehlung "claude-haiku-4-5" ist damit gelistet.
        val anthropic = p("anthropic").llmModels
        assertEquals(
            setOf("claude-haiku-4-5", "claude-opus-5-5"),
            ModelLists.listedIds(anthropic, listOf("claude-haiku-4-5-20251001", "claude-opus-5-5")),
        )
        assertEquals(setOf("claude-sonnet-5"), ModelLists.listedIds(anthropic, listOf("claude-sonnet-5")))
        // Gleiche Regel wie beim Erben der Flags: das laengste Katalog-Modell gewinnt. Turbo gehoert
        // zu Turbo, nicht auch zu "whisper-large-v3".
        val groq = p("groq").sttModels
        assertEquals(setOf("whisper-large-v3-turbo"), ModelLists.listedIds(groq, listOf("whisper-large-v3-turbo")))
        assertEquals(setOf("whisper-large-v3-turbo"), ModelLists.listedIds(groq, listOf("whisper-large-v3-turbo-2026-01-01")))
        assertEquals(emptySet<String>(), ModelLists.listedIds(groq, listOf("whisper-large-v2", "distil-whisper")))
        // Kein Snapshot ohne Bindestrich-Grenze.
        assertEquals(emptySet<String>(), ModelLists.listedIds(anthropic, listOf("claude-haiku-4-50")))
    }
}
