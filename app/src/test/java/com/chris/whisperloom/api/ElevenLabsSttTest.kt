package com.chris.whisperloom.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun hoechstensHundertKeytermsUndDieLetztenGewinnen() {
        // Ab 101 Begriffen rechnet ElevenLabs mindestens 20 s ab; die eigenen Begriffe stehen am Ende.
        assertEquals(100, ElevenLabsStt.MAX_KEYTERMS)
        val prompt = (1..150).joinToString(", ") { "t$it" }
        val terms = ElevenLabsStt.keyterms(prompt)
        assertEquals(100, terms.size)
        assertEquals("t51", terms.first())
        assertEquals("t150", terms.last())
        assertEquals(100, ElevenLabsStt.keyterms((1..100).joinToString(", ") { "t$it" }).size)
    }

    @Test fun satzstueckeMitSatzzeichenAmEndeFallenWeg() {
        val prompt = "Projekt X., Wirklich?, Achtung!, Teilnehmer:, siehe oben;, und so weiter…, Anna, Node.js, C++, Dr. Hans Peter"
        assertEquals(listOf("Anna", "Node.js", "C++", "Dr. Hans Peter"), ElevenLabsStt.keyterms(prompt))
    }

    @Test fun kappungDesVokabularsVerwirftDasAngeschnitteneStueck() {
        // Alter Freitext in einer Zeile, laenger als 800 Zeichen: Vocabulary schneidet ihn am Wortende.
        val legacy = (1..200).joinToString(", ") { "Begriff Nummer $it" }
        val prompt = com.chris.whisperloom.Vocabulary.prompt(listOf(legacy))
        assertTrue(prompt.cut)
        val cutPiece = prompt.text.substringAfterLast(", ")
        assertTrue(cutPiece, cutPiece.endsWith("…"))
        val terms = ElevenLabsStt.keyterms(prompt.text)
        assertTrue(terms.isNotEmpty())
        // Nur ganze Begriffe — das Stueck am Schnitt kommt weder mit noch ohne "…" an.
        assertTrue(terms.toString(), terms.all { it.matches(Regex("Begriff Nummer \\d+")) })
        assertFalse(terms.contains(cutPiece.removeSuffix("…")))
    }

    @Test fun erkannteSpracheWirdZuIso6391() {
        assertEquals("de", ElevenLabsStt.isoLanguage("deu"))
        assertEquals("en", ElevenLabsStt.isoLanguage("eng"))
        assertEquals("fr", ElevenLabsStt.isoLanguage("FRA"))
        assertEquals("de", ElevenLabsStt.isoLanguage("de"))
        assertEquals("xyz", ElevenLabsStt.isoLanguage("xyz"))
    }
}
