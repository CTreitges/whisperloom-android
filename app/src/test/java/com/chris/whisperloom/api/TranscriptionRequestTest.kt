package com.chris.whisperloom.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Multipart-Felder je Anbieter/Modell. Ein falsches Feld heisst
 * 400/422 beim Anbieter — und das faellt sonst erst live auf.
 */
class TranscriptionRequestTest {

    private fun stt(provider: String, model: String = "", url: String = "") =
        AccessResolver.resolveStt(provider, url, "k", model, 0)

    private fun names(fields: List<Pair<String, String>>) = fields.map { it.first }

    @Test fun gptTranscribeSendetLanguagesArray() {
        val f = TranscriptionRequest.fields(stt("openai", "gpt-transcribe"), "de", "Namen: Chris")
        // CTR-273: das Vokabular geht zusaetzlich als keywords[] raus.
        assertEquals(listOf("model", "response_format", "languages[]", "prompt", "keywords[]"), names(f))
        assertTrue(f.contains("languages[]" to "de"))
        assertTrue(f.contains("prompt" to "Namen: Chris"))
        assertTrue(f.contains("keywords[]" to "Namen: Chris"))
        assertFalse(names(f).contains("language"))
    }

    // --- Vokabular als Wortliste (CTR-273, F5/F6) ----------------------------------------

    @Test fun gptTranscribeBekommtPromptUndKeywords() {
        val f = TranscriptionRequest.fields(stt("openai", "gpt-transcribe"), "auto", "Treitges, WhisperLoom, Voice Command")
        assertEquals(
            listOf(
                "model" to "gpt-transcribe",
                "response_format" to "json",
                "prompt" to "Treitges, WhisperLoom, Voice Command",
                "keywords[]" to "Treitges",
                "keywords[]" to "WhisperLoom",
                "keywords[]" to "Voice Command",
            ),
            f,
        )
    }

    /** Ein Begriff mit < oder > liesse OpenAI die ganze Anfrage ablehnen — er faellt weg, prompt bleibt. */
    @Test fun keywordMitSpitzerKlammerFaelltWeg() {
        val f = TranscriptionRequest.fields(stt("openai", "gpt-transcribe"), "de", "<b>Chris</b>, Anna, a>b, Satz am Ende.")
        assertEquals(listOf("Anna"), f.filter { it.first == "keywords[]" }.map { it.second })
        assertTrue(f.contains("prompt" to "<b>Chris</b>, Anna, a>b, Satz am Ende."))
    }

    @Test fun andereOpenAiModelleOhneKeywords() {
        for (model in listOf("gpt-4o-transcribe", "gpt-4o-mini-transcribe", "whisper-1")) {
            assertFalse(model, names(TranscriptionRequest.fields(stt("openai", model), "de", "Chris")).contains("keywords[]"))
        }
        assertFalse(names(TranscriptionRequest.fields(stt("groq"), "de", "Chris")).contains("keywords[]"))
    }

    @Test fun mistralBekommtVokabularAlsContextBiasOhnePrompt() {
        val f = TranscriptionRequest.fields(stt("mistral"), "de", "Treitges, WhisperLoom")
        assertEquals(
            listOf("model" to "voxtral-mini-latest", "language" to "de", "context_bias[]" to "Treitges", "context_bias[]" to "WhisperLoom"),
            f,
        )
    }

    /** Mistral-Pattern `^[^,\s]+$`: mehrteilige Begriffe werden mit _ verbunden (Doku: affordable_health_care). */
    @Test fun contextBiasOhneLeerzeichen() {
        val bias = TranscriptionRequest.contextBias("Christof  Treitges, Claude Code, Pro\tWidget")
        assertEquals(listOf("Christof_Treitges", "Claude_Code", "Pro_Widget"), bias)
        val pattern = Regex("^[^,\\s]+$")
        assertTrue(bias.all { pattern.matches(it) })
    }

    /** Geschuetztes Leerzeichen (U+00A0, U+202F) aus kopiertem Text: auch das wird zu _. */
    @Test fun contextBiasAuchOhneUnicodeLeerraum() {
        val bias = TranscriptionRequest.contextBias("Dr Müller, 10 km")
        assertEquals(listOf("Dr_Müller", "10_km"), bias)
        val unicodePattern = Regex("(?U)^[^,\\s]+$")
        assertTrue(bias.all { unicodePattern.matches(it) })
    }

    /** Freitext ist kein Schlagwort: mehr als 5 Woerter oder 49 Zeichen fallen weg (wie bei ElevenLabs). */
    @Test fun wortlistenOhneFreitext() {
        val prompt = "Bitte WhisperLoom immer zusammen schreiben und so, ${"x".repeat(50)}, Chris"
        assertEquals(listOf("Chris"), TranscriptionRequest.contextBias(prompt))
        assertEquals(listOf("Chris"), TranscriptionRequest.keywords(prompt))
        assertEquals(listOf("a b c d e"), TranscriptionRequest.keywords("a b c d e, a b c d e f"))
    }

    @Test fun contextBiasHoechstensHundertUndDieLetztenGewinnen() {
        val prompt = (1..130).joinToString(", ") { "w$it" }
        val bias = TranscriptionRequest.contextBias(prompt)
        assertEquals(100, bias.size)
        assertEquals("w31", bias.first())
        assertEquals("w130", bias.last())
    }

    @Test fun ohneVokabularKeineWortlisten() {
        assertFalse(names(TranscriptionRequest.fields(stt("mistral"), "de", "  ")).contains("context_bias[]"))
        assertFalse(names(TranscriptionRequest.fields(stt("openai", "gpt-transcribe"), "de", "")).contains("keywords[]"))
        // OpenRouter (auch Voxtral) kennt context_bias nicht.
        assertFalse(names(TranscriptionRequest.fields(stt("openrouter"), "de", "Chris")).contains("context_bias[]"))
    }

    @Test fun altesOpenAiModellSendetLanguage() {
        val f = TranscriptionRequest.fields(stt("openai", "gpt-4o-transcribe"), "de", "")
        assertEquals(listOf("model" to "gpt-4o-transcribe", "response_format" to "json", "language" to "de"), f)
    }

    @Test fun groqBekommtAlleFelder() {
        val f = TranscriptionRequest.fields(stt("groq"), "en", "ctx")
        assertEquals(listOf("model", "response_format", "language", "prompt"), names(f))
        assertEquals("whisper-large-v3-turbo", f[0].second)
    }

    @Test fun mistralBekommtWederPromptNochResponseFormat() {
        val f = TranscriptionRequest.fields(stt("mistral"), "de", "ctx")
        // CTR-273: das Vokabular kommt als context_bias statt als prompt an.
        assertEquals(listOf("model" to "voxtral-mini-latest", "language" to "de", "context_bias[]" to "ctx"), f)
    }

    @Test fun openRouterBekommtKeinenPrompt() {
        val f = TranscriptionRequest.fields(stt("openrouter"), "de", "ctx")
        assertEquals(listOf("model", "response_format", "language"), names(f))
    }

    @Test fun autoLaesstDieSpracheWeg() {
        assertFalse(names(TranscriptionRequest.fields(stt("openai", "gpt-transcribe"), "auto", "")).any { it.startsWith("language") })
        assertFalse(names(TranscriptionRequest.fields(stt("groq"), "", "")).contains("language"))
    }

    // --- "Deutsch + Englisch" (CTR-273, F3) -----------------------------------------------

    @Test fun deutschUndEnglischGehtAnGptTranscribeAlsZweiSprachen() {
        val f = TranscriptionRequest.fields(stt("openai", "gpt-transcribe"), "de+en", "")
        assertEquals(
            listOf("model" to "gpt-transcribe", "response_format" to "json", "languages[]" to "de", "languages[]" to "en"),
            f,
        )
    }

    @Test fun deutschUndEnglischOhneSprachfeldBeiEinemCode() {
        // whisper-1, Groq, Mistral, eigener Server kennen nur einen Code -> der Anbieter erkennt selbst.
        for (access in listOf(stt("openai", "whisper-1"), stt("groq"), stt("mistral"), stt("custom", "m", "http://s:8000/v1"))) {
            val f = names(TranscriptionRequest.fields(access, "de+en", ""))
            assertFalse("${access.provider.id}: $f", f.any { it.startsWith("language") })
        }
    }

    @Test fun leererPromptWirdNichtGesendet() {
        assertFalse(names(TranscriptionRequest.fields(stt("groq"), "de", "   ")).contains("prompt"))
    }

    @Test fun eigenerServerMitFreiemModell() {
        val f = TranscriptionRequest.fields(stt("custom", "Systran/faster-whisper-medium", "http://s:8000/v1"), "de", "ctx")
        assertEquals(
            listOf(
                "model" to "Systran/faster-whisper-medium", "response_format" to "json",
                "language" to "de", "prompt" to "ctx",
            ),
            f,
        )
    }

    @Test fun eigenerServerOhneModellSendetKeinLeeresModelFeld() {
        // Review API-7: custom hat keinen Modell-Default; model="" quittieren speaches/LocalAI mit 422.
        val f = TranscriptionRequest.fields(stt("custom", "", "http://s:8000/v1"), "de", "")
        assertEquals(listOf("response_format" to "json", "language" to "de"), f)
        assertFalse(names(TranscriptionRequest.fields(stt("custom", "   ", "http://s:8000/v1"), "auto", "")).contains("model"))
    }

    @Test fun endpunktAusBaseUrlOderOverride() {
        assertEquals(
            "https://api.openai.com/v1/audio/transcriptions",
            TranscriptionRequest.url(stt("openai")),
        )
        assertEquals(
            "http://s:8000/v1/audio/transcriptions",
            TranscriptionRequest.url(stt("custom", url = "http://s:8000/v1/")),
        )
        assertEquals(
            "https://api.deepinfra.com/v1/audio/transcriptions",
            TranscriptionRequest.url(stt("deepinfra")),
        )
    }
}
