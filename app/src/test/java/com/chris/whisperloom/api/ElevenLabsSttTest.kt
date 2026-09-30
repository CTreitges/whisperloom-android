package com.chris.whisperloom.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die ElevenLabs-Felder. ElevenLabs validiert streng (422) — ein falsches
 * Feld oder ein unzulaessiger Begriff faellt sonst erst live auf.
 */
class ElevenLabsSttTest {

    private fun stt(model: String = "", url: String = "") =
        AccessResolver.resolveStt(ProviderCatalog.ELEVENLABS_ID, url, "xi", model, 0)

    private fun names(fields: List<Pair<String, String>>) = fields.map { it.first }

    @Test fun endpunktUndKeyHeader() {
        assertEquals("https://api.elevenlabs.io/v1/speech-to-text", ElevenLabsStt.url(stt()))
        assertEquals("http://127.0.0.1:8/v1/speech-to-text", ElevenLabsStt.url(stt(url = "http://127.0.0.1:8/v1/")))
        assertEquals("xi-api-key", ElevenLabsStt.AUTH_HEADER)
    }

    @Test fun felderFuerScribeInFesterReihenfolge() {
        assertEquals(
            listOf(
                "model_id" to "scribe_v2",
                "language_code" to "de",
                "tag_audio_events" to "false",
                "timestamps_granularity" to "none",
                "file_format" to "pcm_s16le_16",
                "keyterms" to "Anna",
                "keyterms" to "Kubernetes",
            ),
            ElevenLabsStt.fields(stt(), "de", "Anna, Kubernetes"),
        )
    }

    @Test fun gewaehltesModellGehtAlsModelId() {
        val f = ElevenLabsStt.fields(stt("scribe_v2_medical"), "en", "")
        assertEquals("model_id" to "scribe_v2_medical", f.first())
        assertFalse(names(f).contains("model"))
    }

    @Test fun autoLaesstDieSpracheWeg() {
        assertFalse(names(ElevenLabsStt.fields(stt(), "auto", "")).contains("language_code"))
        assertFalse(names(ElevenLabsStt.fields(stt(), " ", "")).contains("language_code"))
    }

    @Test fun ohneVokabularKeinKeytermsUndNieLoggingOderPrompt() {
        val f = names(ElevenLabsStt.fields(stt(), "de", "  "))
        assertFalse(f.contains("keyterms"))
        assertFalse(f.contains("prompt"))
        assertFalse(f.contains("enable_logging"))
        assertFalse(f.contains("response_format"))
    }

    @Test fun keytermsNachDenRegelnVonElevenLabs() {
        val grenze = "a".repeat(49)
        val prompt = listOf(
            "  Anna ",
            "Dr. Hans Peter Müller Jr", // 5 Woerter: erlaubt
            "eins zwei drei vier fünf sechs", // 6 Woerter: weg
            grenze, // 49 Zeichen: erlaubt
            "b".repeat(50), // 50 Zeichen: weg
            "a<b", "x{y}", "[z]", "c\\d", // verbotene Zeichen: weg
            "",
            "Anna", // doppelt
        ).joinToString(", ")
        assertEquals(listOf("Anna", "Dr. Hans Peter Müller Jr", grenze), ElevenLabsStt.keyterms(prompt))
    }

    @Test fun hoechstensTausendKeyterms() {
        val prompt = (1..1200).joinToString(", ") { "t$it" }
        val terms = ElevenLabsStt.keyterms(prompt)
        assertEquals(ElevenLabsStt.MAX_KEYTERMS, terms.size)
        assertEquals("t1", terms.first())
    }

    @Test fun erkannteSpracheWirdZuIso6391() {
        assertEquals("de", ElevenLabsStt.isoLanguage("deu"))
        assertEquals("en", ElevenLabsStt.isoLanguage("eng"))
        assertEquals("fr", ElevenLabsStt.isoLanguage("FRA"))
        assertEquals("de", ElevenLabsStt.isoLanguage("de"))
        assertEquals("xyz", ElevenLabsStt.isoLanguage("xyz"))
    }
}
