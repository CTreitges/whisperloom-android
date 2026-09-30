package com.chris.whisperloom

import com.chris.whisperloom.api.AccessResolver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-Unit-Tests fuer "Zugang vollstaendig?" — Baustein von SetupRouter.recognitionReady und TranscriptionEngine.isConfigured. */
class SetupStateTest {

    @Test fun textverbesserungBrauchtAuchEinModell() {
        // Ollama lokal / eigener Server: Adresse da, kein Key noetig — aber ohne Modell nicht bereit.
        assertFalse(SetupState.llmComplete("http://homeserver:11434", "", needsKey = false, model = ""))
        assertTrue(SetupState.llmComplete("http://homeserver:11434", "", needsKey = false, model = "gemma3"))
        assertFalse(SetupState.llmComplete("https://ollama.com", "", needsKey = true, model = "gemma4:31b"))
        assertTrue(SetupState.llmComplete("https://ollama.com", "k", needsKey = true, model = "gemma4:31b"))
    }

    @Test fun zugangVollstaendig() {
        assertTrue(SetupState.sttComplete("https://api.openai.com/v1", "sk", needsKey = true))
        assertFalse(SetupState.sttComplete("https://api.openai.com/v1", "", needsKey = true))
        assertFalse(SetupState.sttComplete("https://api.openai.com/v1", "  ", needsKey = true))
        assertTrue(SetupState.sttComplete("http://192.168.1.50:8000/v1", "", needsKey = false))
        assertFalse(SetupState.sttComplete("", "", needsKey = false))
        assertFalse(SetupState.sttComplete("  ", "sk", needsKey = true))
    }

    @Test fun leisteNurBeiZugangMitChat() {
        // ElevenLabs "wie Erkennung" mit altem llm_model: vollstaendig aussehend, aber ohne Chat.
        val eleven = AccessResolver.resolveStt("elevenlabs", "", "xi", "", 0)
        assertFalse(SetupState.llmReady(AccessResolver.resolveLlm(eleven, "same", "", "", "gpt-4o-mini")))
        // Together "wie Erkennung": mit eingetipptem Modell bereit, ohne nicht.
        val together = AccessResolver.resolveStt("together", "", "tg", "", 0)
        assertTrue(SetupState.llmReady(AccessResolver.resolveLlm(together, "same", "", "", "meta-llama/Llama-3.3-70B-Instruct-Turbo")))
        assertFalse(SetupState.llmReady(AccessResolver.resolveLlm(together, "same", "", "", "")))
        // Katalog-Anbieter mit Key: bereit wie bisher.
        val openai = AccessResolver.resolveStt("openai", "", "sk", "", 0)
        assertTrue(SetupState.llmReady(AccessResolver.resolveLlm(openai, "same", "", "", "")))
    }
}
