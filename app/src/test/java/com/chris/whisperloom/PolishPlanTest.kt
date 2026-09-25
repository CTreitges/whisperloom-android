package com.chris.whisperloom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Entscheidung, welche Nachbearbeitungs-Stufe greift.
 */
class PolishPlanTest {

    private fun plan(
        removeFillers: Boolean = true,
        refineMode: RefineMode = RefineMode.OFF,
        smartFillers: Boolean = false,
        customFillers: List<String> = emptyList(),
        disabledFillers: Set<String> = emptySet(),
        paragraphs: Boolean = true,
    ) = PolishPlan.options(
        removeFillers = removeFillers,
        autoCapitalize = true,
        language = "de",
        refineMode = refineMode,
        smartFillers = smartFillers,
        customFillers = customFillers,
        disabledFillers = disabledFillers,
        paragraphs = paragraphs,
    )

    @Test fun ohneAutomatischeAbsaetzeWirdKiTextZuEinemAbsatz() {
        assertTrue(plan(refineMode = RefineMode.POLISH, paragraphs = true).keepLineBreaks)
        assertFalse(plan(refineMode = RefineMode.POLISH, paragraphs = false).keepLineBreaks)
        assertFalse(plan(refineMode = RefineMode.OFF, paragraphs = true).keepLineBreaks)
        val flat = TextPolisher.polish("Erster Absatz.\n\nZweiter Absatz.", plan(refineMode = RefineMode.BEAUTIFY, paragraphs = false))
        assertEquals("Erster Absatz. Zweiter Absatz.", flat)
    }

    @Test fun promptBehaeltSeineGliederungAuchOhneAutomatischeAbsaetze() {
        val options = plan(refineMode = RefineMode.PROMPT, paragraphs = false)
        assertTrue(options.keepLineBreaks)
        val prompt = "Erstelle mir eine Einkaufsliste.\n- 12 Personen, davon 2 vegan\n- Budget höchstens 100 Euro"
        assertEquals(prompt, TextPolisher.polish(prompt, options))
    }

    /** Review-Befund: aus `</text>` nach einem Punkt wurde `</Text>`, Fuellwoerter im Material verschwanden. */
    @Test fun promptMaterialBleibtUnangetastet() {
        val options = plan(refineMode = RefineMode.PROMPT, customFillers = listOf("halt"))
        assertFalse("Gross-Schreibung erledigt das Modell", options.autoCapitalize)
        assertFalse("Fuellwoerter erledigt das Modell", options.removeFillers)
        val prompt = "Fasse den Text zusammen.\n\n<text>\nDas Meeting war halt gut. wir machen weiter.\n</text>"
        assertEquals(prompt, TextPolisher.polish(prompt, options))
    }

    @Test fun ohneKiGreiftDieWortliste() {
        assertTrue(plan(removeFillers = true).removeFillers)
    }

    @Test fun ausGeschaltetBleibtAus() {
        assertFalse(plan(removeFillers = false).removeFillers)
    }

    @Test fun kiEntscheidungSchaltetDieWortlisteAb() {
        // Sonst wuerde zweimal gefiltert und das "im Zweifel behalten" der KI
        // waere wieder ausgehebelt — in jedem Modus, nicht nur beim Glaetten.
        for (mode in listOf(RefineMode.POLISH, RefineMode.BEAUTIFY, RefineMode.SUMMARIZE)) {
            assertFalse(mode.name, plan(removeFillers = true, refineMode = mode, smartFillers = true).removeFillers)
        }
    }

    @Test fun glaettenAlleinLaesstDieWortlisteAktiv() {
        assertTrue(plan(removeFillers = true, refineMode = RefineMode.POLISH, smartFillers = false).removeFillers)
    }

    @Test fun intelligenteFilterOhneKiBleibtWirkungslos() {
        // smartFillers braucht den zweiten Aufruf — ohne ihn muss die Wortliste ran.
        assertTrue(plan(removeFillers = true, refineMode = RefineMode.OFF, smartFillers = true).removeFillers)
    }

    @Test fun kiTextBehaeltSeineAbsaetze() {
        assertFalse(plan().keepLineBreaks)
        assertTrue(plan(refineMode = RefineMode.POLISH).keepLineBreaks)
        assertTrue(plan(refineMode = RefineMode.SUMMARIZE).keepLineBreaks)
    }

    @Test fun uebrigeOptionenWerdenDurchgereicht() {
        val o = plan(customFillers = listOf("halt"), disabledFillers = setOf("hmm"))
        assertTrue(o.autoCapitalize)
        assertEquals("de", o.language)
        assertEquals(listOf("halt"), o.customFillers.toList())
        assertEquals(setOf("hmm"), o.disabledFillers)
    }

    @Test fun verbatimUndCleaned() {
        val v = PolishPlan.verbatim("de")
        assertFalse(v.removeFillers)
        assertFalse(v.autoCapitalize)
        val c = PolishPlan.cleaned("de", listOf("halt"), setOf("hmm"))
        assertTrue(c.removeFillers)
        assertFalse(c.autoCapitalize)
        assertEquals(listOf("halt"), c.customFillers.toList())
        assertEquals(setOf("hmm"), c.disabledFillers)
    }

    @Test fun fuellwoerterWerdenNormalisiert() {
        assertEquals(setOf("ähm", "halt", "sozusagen"), PolishPlan.parseFillers("Ähm, halt,sozusagen ; HALT\n"))
        assertEquals(emptySet<String>(), PolishPlan.parseFillers("  , ,\n"))
        assertEquals(setOf("halt"), PolishPlan.normalizeFillers(listOf(" Halt ", "halt", "")))
    }
}
