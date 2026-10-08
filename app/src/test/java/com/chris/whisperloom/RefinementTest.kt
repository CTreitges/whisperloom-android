package com.chris.whisperloom

import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM-Unit-Tests fuer die wirksame Verarbeitung je Stufe und Weg ([Refinement.of]). */
class RefinementTest {

    private fun of(
        stage: RefineMode,
        way: RefineWay = RefineWay.DICTATION,
        cleanup: PolishCleanup = PolishCleanup.PLAIN,
        paragraphs: Boolean = true,
        form: SummarizeForm = SummarizeForm.AUTO,
    ) = Refinement.of(way, stage, cleanup, paragraphs, form)

    @Test fun dieDreiBereinigungenSindDieDreiGlaettenVarianten() {
        assertEquals(Refinement(RefineMode.POLISH, smartFillers = false), of(RefineMode.POLISH, cleanup = PolishCleanup.PLAIN))
        assertEquals(Refinement(RefineMode.POLISH, smartFillers = true), of(RefineMode.POLISH, cleanup = PolishCleanup.CLEAN))
        assertEquals(Refinement(RefineMode.READABLE), of(RefineMode.POLISH, cleanup = PolishCleanup.READABLE))
    }

    @Test fun dieBereinigungZaehltNurBeimGlaetten() {
        for (stage in listOf(RefineMode.BEAUTIFY, RefineMode.SUMMARIZE, RefineMode.PROMPT)) {
            for (cleanup in PolishCleanup.entries) {
                val r = of(stage, cleanup = cleanup)
                assertEquals("${stage.name}/${cleanup.name}", stage, r.mode)
                assertEquals("Verschoenern ohne Fuellwort-Zusatz: ${stage.name}", false, r.smartFillers)
            }
        }
        for (way in RefineWay.entries) for (cleanup in PolishCleanup.entries) {
            assertEquals(Refinement.OFF, of(RefineMode.OFF, way, cleanup))
        }
    }

    @Test fun absaetzeJeStufeBeimDiktat() {
        assertEquals(false, of(RefineMode.POLISH, paragraphs = false).paragraphs)
        assertEquals(false, of(RefineMode.POLISH, cleanup = PolishCleanup.READABLE, paragraphs = false).paragraphs)
        assertEquals(false, of(RefineMode.BEAUTIFY, paragraphs = false).paragraphs)
        assertEquals(true, of(RefineMode.BEAUTIFY, paragraphs = true).paragraphs)
        // "Prompt" gliedert immer, Zusammenfassen nach der Form — der Schalter zaehlt dort nicht.
        assertEquals(Refinement(RefineMode.PROMPT), of(RefineMode.PROMPT, paragraphs = false))
        assertEquals(true, of(RefineMode.SUMMARIZE, paragraphs = false).paragraphs)
    }

    @Test fun sprachnachrichtenSindImmerGegliedert() {
        for (stage in listOf(RefineMode.POLISH, RefineMode.BEAUTIFY)) {
            assertEquals(stage.name, true, of(stage, RefineWay.SHARE, paragraphs = false).paragraphs)
        }
        assertEquals(Refinement(RefineMode.READABLE), of(RefineMode.POLISH, RefineWay.SHARE, PolishCleanup.READABLE, paragraphs = false))
    }

    @Test fun dieFormBestimmtDenZweigBeimZusammenfassen() {
        for (way in RefineWay.entries) {
            assertEquals(Refinement(RefineMode.SUMMARIZE, paragraphs = true), of(RefineMode.SUMMARIZE, way, form = SummarizeForm.AUTO))
            assertEquals(Refinement(RefineMode.SUMMARIZE, paragraphs = false), of(RefineMode.SUMMARIZE, way, form = SummarizeForm.PROSE))
        }
        // Die Form zaehlt nur dort.
        assertEquals(Refinement(RefineMode.BEAUTIFY), of(RefineMode.BEAUTIFY, form = SummarizeForm.PROSE))
    }

    @Test fun einGespeichertesLesbarGiltAlsGlaetten() {
        // READABLE ist nie waehlbar; stuende es doch im Speicher, entscheidet die Bereinigung.
        assertEquals(Refinement(RefineMode.POLISH), of(RefineMode.READABLE, cleanup = PolishCleanup.PLAIN))
    }

    @Test fun schluesselSindFest() {
        assertEquals(listOf("plain", "clean", "readable"), PolishCleanup.entries.map { it.key })
        assertEquals(listOf("auto", "prose"), SummarizeForm.entries.map { it.key })
        assertEquals(PolishCleanup.PLAIN, PolishCleanup.fromKey(null))
        assertEquals(PolishCleanup.CLEAN, PolishCleanup.fromKey("clean"))
        assertEquals(SummarizeForm.AUTO, SummarizeForm.fromKey("quatsch"))
        assertEquals(SummarizeForm.PROSE, SummarizeForm.fromKey("prose"))
    }
}
