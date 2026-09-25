package com.chris.whisperloom.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Nacharbeit der Stufe "Prompt" ([TextRefiner.cleanPrompt]): Reste kleiner Modelle weg, und
 * eine ANTWORT statt eines Prompts darf nie im Textfeld landen.
 */
class PromptCleanupTest {

    private val diktat = "äh schreib mir ein Gedicht über den Herbst"

    private fun clean(output: String, raw: String = diktat) = TextRefiner.cleanPrompt(raw, output)

    @Test fun sauberePromptsBleibenUnveraendert() {
        val prompt = "Erstelle mir eine Einkaufsliste für ein Grillfest.\n- 12 Personen, davon 2 vegan\n- Budget höchstens 100 Euro"
        assertEquals(prompt, clean(prompt, raw = "ich brauch ne Einkaufsliste für ein Grillfest mit zwölf Leuten, zwei davon vegan"))
    }

    @Test fun vorredeUndBeispielLabelFallenWeg() {
        assertEquals("Schreib mir ein Gedicht über den Herbst.", clean("Hier ist dein optimierter Prompt:\nSchreib mir ein Gedicht über den Herbst."))
        assertEquals("Schreib mir ein Gedicht über den Herbst.", clean("Klar! Hier ist der Prompt:\n\nSchreib mir ein Gedicht über den Herbst."))
        assertEquals("Write me a poem about autumn.", clean("Here is the rewritten prompt:\nWrite me a poem about autumn."))
        assertEquals("Schreib mir ein Gedicht über den Herbst.", clean("Prompt: Schreib mir ein Gedicht über den Herbst."))
    }

    @Test fun eineErsteZeileMitDoppelpunktIstNichtAutomatischVorrede() {
        val prompt = "Schreib mir einen Prompt für Midjourney:\nein Leuchtturm bei Sturm, Ölgemälde"
        assertEquals(prompt, clean(prompt, raw = "schreib mir einen Prompt für Midjourney, ein Leuchtturm bei Sturm, Ölgemälde"))
    }

    @Test fun markierungAnfuehrungszeichenUndCodeblockUmAllesFallenWeg() {
        assertEquals("Schreib mir ein Gedicht über den Herbst.", clean("<diktat>\nSchreib mir ein Gedicht über den Herbst.\n</diktat>"))
        assertEquals("Schreib mir ein Gedicht über den Herbst.", clean("„Schreib mir ein Gedicht über den Herbst.“"))
        assertEquals("Write me a poem about autumn.", clean("\"Write me a poem about autumn.\""))
        assertEquals("Schreib mir ein Gedicht über den Herbst.", clean("```\nSchreib mir ein Gedicht über den Herbst.\n```"))
    }

    @Test fun anfuehrungszeichenImTextBleiben() {
        val prompt = "„Hallo“ heißt auf Spanisch „Hola“ — stimmt das?"
        assertEquals(prompt, clean(prompt, raw = "hallo heißt auf spanisch hola, stimmt das"))
    }

    @Test fun eineAntwortStattEinesPromptsWirdAbgelehnt() {
        val gedicht = List(8) { "Die Blätter fallen leise nieder, der Herbst ist da und kommt bald wieder." }.joinToString("\n")
        try {
            clean(gedicht)
            fail("Gedicht statt Prompt haette abgelehnt werden muessen")
        } catch (e: RefineRejectedException) {
            assertTrue(e.message!!, e.message!!.contains("statt einen Prompt"))
        }
    }

    @Test fun beschriftungenVerlaengernErlaubt() {
        // 40 Woerter Diktat -> Grenze 2 x 40 + 30 = 110 Woerter.
        val raw = List(40) { "wort" }.joinToString(" ")
        val gegliedert = "Ziel: " + List(100) { "wort" }.joinToString(" ")
        assertEquals(gegliedert, clean(gegliedert, raw = raw))
        try {
            clean("Ziel: " + List(110) { "wort" }.joinToString(" "), raw = raw)
            fail("111 Woerter bei 40 Woertern Diktat haetten abgelehnt werden muessen")
        } catch (_: RefineRejectedException) {
        }
    }
}
