package com.chris.whisperloom.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Zugangs-Aufloesung. Hier entscheidet sich, an welchen Server
 * welcher Key geht — ein Fehler hier schickt den OpenAI-Key an Groq.
 */
class AccessResolverTest {

    @Test fun neuerNutzerBekommtOpenAiDefaults() {
        val a = AccessResolver.resolveStt("openai", "", "sk-x", "", 0)
        assertEquals("https://api.openai.com/v1", a.baseUrl)
        assertEquals("gpt-transcribe", a.model)
        assertEquals(90_000, a.readTimeoutMs)
        assertEquals("openai", a.provider.id)
        assertEquals("languages[]", a.modelOption!!.languageField)
    }

    @Test fun bestandsnutzerBehaeltAltesModellUndUrl() {
        // Vor v3: kein stt_provider, aber Key + gpt-4o-transcribe gespeichert.
        val a = AccessResolver.resolveStt("", "https://api.openai.com/v1", "sk-x", "gpt-4o-transcribe", 0)
        assertEquals("openai", a.provider.id)
        assertEquals("gpt-4o-transcribe", a.model)
        assertTrue(a.modelOption!!.label.contains("Auslauf"))
        assertEquals("language", a.modelOption!!.languageField)
    }

    @Test fun werteWerdenGetrimmt() {
        val a = AccessResolver.resolveStt("groq", "  https://api.groq.com/openai/v1/ ", " gsk ", " whisper-large-v3 ", 0)
        assertEquals("https://api.groq.com/openai/v1/", a.baseUrl)
        assertEquals("gsk", a.apiKey)
        assertEquals("whisper-large-v3", a.model)
        assertNotNull(a.modelOption)
    }

    @Test fun eigenerServerOhneUrlBleibtLeer() {
        val a = AccessResolver.resolveStt("custom", "", "", "", 0)
        assertEquals("", a.baseUrl)
        assertEquals("", a.model)
        assertEquals(600_000, a.readTimeoutMs)
        assertNull(a.modelOption)
        assertFalse(a.provider.needsKey)
    }

    @Test fun timeoutAusDenEinstellungenGewinnt() {
        assertEquals(120_000, AccessResolver.resolveStt("custom", "http://x/v1", "", "m", 120).readTimeoutMs)
        assertEquals(45_000, AccessResolver.resolveStt("openai", "", "k", "", 45).readTimeoutMs)
    }

    @Test fun unbekanntesModellHatKeineOption() {
        val a = AccessResolver.resolveStt("openai", "", "k", "mein-eigenes", 0)
        assertEquals("mein-eigenes", a.model)
        assertNull(a.modelOption)
    }

    // --- LLM ------------------------------------------------------------------

    private val stt = AccessResolver.resolveStt("openai", "", "sk-x", "", 45)

    @Test fun sameUebernimmtDenTranskriptionsZugang() {
        val l = AccessResolver.resolveLlm(stt, "same", "", "", "")
        assertEquals(stt.baseUrl, l.baseUrl)
        assertEquals("sk-x", l.apiKey)
        assertEquals(45_000, l.readTimeoutMs)
        assertEquals("openai", l.provider.id)
        assertEquals("gpt-4o-mini", l.model)
        assertTrue(l.modelOption!!.temperatureSupported)
    }

    @Test fun leererProviderZaehltAlsSame() {
        val l = AccessResolver.resolveLlm(stt, "", "", "", "gpt-5.6-luna")
        assertEquals("sk-x", l.apiKey)
        assertEquals("gpt-5.6-luna", l.model)
        assertFalse(l.modelOption!!.temperatureSupported)
        assertEquals("none", l.modelOption!!.reasoningEffort)
    }

    @Test fun sameIgnoriertStaleLlmFelder() {
        // Bei "same" sind URL/Key im UI versteckt — alte Werte duerfen nicht durchsickern.
        val l = AccessResolver.resolveLlm(stt, "same", "http://alt/v1", "alter-key", "")
        assertEquals(stt.baseUrl, l.baseUrl)
        assertEquals("sk-x", l.apiKey)
    }

    @Test fun andererAnbieterBekommtNieDenSttKey() {
        val l = AccessResolver.resolveLlm(stt, "groq", "", "", "")
        assertEquals("groq", l.provider.id)
        assertEquals("https://api.groq.com/openai/v1", l.baseUrl)
        assertEquals("", l.apiKey)
        assertEquals("openai/gpt-oss-20b", l.model)
        assertEquals(90_000, l.readTimeoutMs)
    }

    @Test fun andererAnbieterMitEigenenFeldern() {
        val l = AccessResolver.resolveLlm(stt, "groq", " https://proxy/v1 ", " gsk ", " qwen/qwen3.6-27b ")
        assertEquals("https://proxy/v1", l.baseUrl)
        assertEquals("gsk", l.apiKey)
        assertEquals("qwen/qwen3.6-27b", l.model)
        assertEquals("none", l.modelOption!!.reasoningEffort)
    }

    @Test fun gleicherAnbieterExplizitErbtLeereFelder() {
        val l = AccessResolver.resolveLlm(stt, "openai", "", "", "gpt-4.1-mini")
        assertEquals(stt.baseUrl, l.baseUrl)
        assertEquals("sk-x", l.apiKey)
        assertEquals(45_000, l.readTimeoutMs)
        assertEquals("gpt-4.1-mini", l.model)
    }

    @Test fun eigenerServerErbtUrlVomEigenenSttServer() {
        val sttCustom = AccessResolver.resolveStt("custom", "http://192.168.1.50:8000/v1", "", "whisper-1", 0)
        val l = AccessResolver.resolveLlm(sttCustom, "custom", "", "", "qwen3:8b")
        assertEquals("http://192.168.1.50:8000/v1", l.baseUrl)
        assertEquals("", l.apiKey)
        assertEquals(600_000, l.readTimeoutMs)
        assertEquals("qwen3:8b", l.model)
        assertNull(l.modelOption)
    }

    @Test fun ollamaCloudNutztDenKatalogUndDenEigenenKey() {
        val l = AccessResolver.resolveLlm(stt, "ollama-cloud", "", "ok-key", "")
        assertEquals("https://ollama.com", l.baseUrl)
        assertEquals("ok-key", l.apiKey)
        assertEquals("gemma4:31b", l.model)
        assertEquals(90_000, l.readTimeoutMs)
        // Der Key des Transkriptions-Anbieters darf nie an ollama.com gehen.
        assertEquals("", AccessResolver.resolveLlm(stt, "ollama-cloud", "", "", "").apiKey)
    }

    @Test fun ollamaLokalOhneAdresseBleibtLeer() {
        val l = AccessResolver.resolveLlm(stt, "ollama", "", "", "")
        assertEquals("", l.baseUrl)
        assertEquals("", l.model)
        val mitAdresse = AccessResolver.resolveLlm(stt, "ollama", "http://192.168.1.10:11434", "", "gemma3")
        assertEquals("http://192.168.1.10:11434", mitAdresse.baseUrl)
        assertEquals(600_000, mitAdresse.readTimeoutMs)
    }

    @Test fun ollamaNebenCloudStt() {
        val l = AccessResolver.resolveLlm(stt, "custom", "http://192.168.1.50:11434/v1", "", "qwen3:8b")
        assertEquals("http://192.168.1.50:11434/v1", l.baseUrl)
        assertEquals("", l.apiKey)
        assertEquals(600_000, l.readTimeoutMs)
        assertTrue(l.provider.isCustom)
    }
}
