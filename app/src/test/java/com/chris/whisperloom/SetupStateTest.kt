package com.chris.whisperloom

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
}
