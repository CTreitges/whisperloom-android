package com.chris.whisperloom.api

import com.chris.whisperloom.RefineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Anweisung an die Textverbesserung. Sie entscheidet ueber die
 * Textqualitaet — vor allem darf sie nie zum Uebersetzen oder Erfinden auffordern.
 */
class RefinePromptTest {

    private val modes = listOf(RefineMode.POLISH, RefineMode.BEAUTIFY, RefineMode.SUMMARIZE, RefineMode.PARAGRAPHS)

    @Test fun deutschUndEnglischUnterscheidenSich() {
        assertTrue(RefinePrompt.build(RefineMode.POLISH, german = true, smartFillers = false).contains("Zeichensetzung"))
        assertTrue(RefinePrompt.build(RefineMode.POLISH, german = false, smartFillers = false).contains("punctuation"))
    }

    @Test fun jederModusHatSeineSchluesselsaetze() {
        assertTrue(RefinePrompt.build(RefineMode.POLISH, true, false).contains("korrigierst"))
        assertTrue(RefinePrompt.build(RefineMode.BEAUTIFY, true, false).contains("verstaendlicher"))
        assertTrue(RefinePrompt.build(RefineMode.BEAUTIFY, true, false).contains("Ich-Perspektive"))
        assertTrue(RefinePrompt.build(RefineMode.SUMMARIZE, true, false).contains("Kernaussagen"))
        assertTrue(RefinePrompt.build(RefineMode.PARAGRAPHS, true, false).contains("Absaetze"))
        assertTrue(RefinePrompt.build(RefineMode.PARAGRAPHS, true, false).contains("Wortlaut sonst unveraendert"))
        assertTrue(RefinePrompt.build(RefineMode.PARAGRAPHS, true, false).contains("fasse nichts zusammen"))

        assertTrue(RefinePrompt.build(RefineMode.POLISH, false, false).contains("clean up"))
        assertTrue(RefinePrompt.build(RefineMode.BEAUTIFY, false, false).contains("first-person"))
        assertTrue(RefinePrompt.build(RefineMode.SUMMARIZE, false, false).contains("key points"))
        assertTrue(RefinePrompt.build(RefineMode.PARAGRAPHS, false, false).contains("do not summarise"))
    }

    @Test fun modiSindNichtVertauscht() {
        assertFalse(RefinePrompt.build(RefineMode.POLISH, true, false).contains("Kernaussagen"))
        assertFalse(RefinePrompt.build(RefineMode.PARAGRAPHS, true, false).contains("verstaendlicher"))
        assertFalse(RefinePrompt.build(RefineMode.BEAUTIFY, true, false).contains("Wortlaut sonst unveraendert"))
    }

    @Test fun standardBleibtBeimBisherigenWortlaut() {
        // Absaetze an = Verhalten bis 3.4 — der Prompt darf sich dadurch nicht veraendern.
        assertEquals(
            "Du korrigierst diktierten Text. Setze Zeichensetzung, Gross- und Kleinschreibung sowie " +
                "Absaetze richtig. Aendere den Inhalt nicht, uebersetze nicht, ergaenze nichts und " +
                "kommentiere nicht. Antworte ausschliesslich mit dem bearbeiteten Text.",
            RefinePrompt.build(RefineMode.POLISH, german = true, smartFillers = false),
        )
        assertEquals(
            "You clean up dictated text. Fix punctuation, capitalisation and paragraphs. Do not change " +
                "the meaning, do not translate, do not add anything and do not comment. Reply only with the edited text.",
            RefinePrompt.build(RefineMode.POLISH, german = false, smartFillers = false),
        )
    }

    @Test fun ohneAutomatischeAbsaetzeEinFliesstext() {
        for (mode in listOf(RefineMode.POLISH, RefineMode.BEAUTIFY, RefineMode.SUMMARIZE)) {
            val de = RefinePrompt.build(mode, german = true, smartFillers = false, paragraphs = false)
            assertTrue(mode.name, de.contains("Setze keine Absaetze"))
            assertFalse(mode.name, de.contains("sowie Absaetze"))
            assertFalse(mode.name, de.contains("Zeichensetzung und Absaetze"))
            assertFalse(mode.name, de.contains("Stichpunkten"))
            val en = RefinePrompt.build(mode, german = false, smartFillers = false, paragraphs = false)
            assertTrue(mode.name, en.contains("Do not add paragraphs"))
            assertFalse(mode.name, en.contains("bullet points"))
        }
    }

    @Test fun absatzModusIgnoriertDenSchalter() {
        val p = RefinePrompt.build(RefineMode.PARAGRAPHS, german = true, smartFillers = false, paragraphs = false)
        assertEquals(RefinePrompt.build(RefineMode.PARAGRAPHS, german = true, smartFillers = false), p)
        assertFalse(p.contains("Setze keine Absaetze"))
    }

    @Test fun ohneSmartFillersKeineFuellwortAnweisung() {
        for (mode in modes) {
            assertFalse(mode.name, RefinePrompt.build(mode, true, false).contains("Fuellwoerter"))
            assertFalse(mode.name, RefinePrompt.build(mode, false, false).contains("filler words"))
        }
    }

    @Test fun mitSmartFillersEntscheidetDieKiSelbst() {
        for (mode in listOf(RefineMode.POLISH, RefineMode.BEAUTIFY, RefineMode.PARAGRAPHS)) {
            val de = RefinePrompt.build(mode, true, true)
            assertTrue(mode.name, de.contains("Fuellwoerter"))
            // Konservativ bleiben ist Teil der Anweisung — sonst verschwinden echte Woerter.
            assertTrue(mode.name, de.contains("Im Zweifel"))
            val en = RefinePrompt.build(mode, false, true)
            assertTrue(mode.name, en.contains("filler words"))
            assertTrue(mode.name, en.contains("When in doubt"))
        }
    }

    @Test fun zusammenfassungIgnoriertSmartFillers() {
        assertFalse(RefinePrompt.build(RefineMode.SUMMARIZE, true, true).contains("Fuellwoerter"))
        assertFalse(RefinePrompt.build(RefineMode.SUMMARIZE, false, true).contains("filler words"))
    }

    @Test fun antwortNurTextRegelInJederKombination() {
        for (mode in modes) {
            for (smart in listOf(true, false)) {
                assertTrue(mode.name, RefinePrompt.build(mode, true, smart).contains("Antworte ausschliesslich mit"))
                assertTrue(mode.name, RefinePrompt.build(mode, false, smart).contains("Reply only with"))
            }
        }
    }

    @Test fun niemalsUebersetzenOderErfinden() {
        for (mode in modes) {
            for (smart in listOf(true, false)) {
                val de = RefinePrompt.build(mode, true, smart)
                assertTrue(mode.name, de.contains("uebersetze nicht"))
                assertTrue(mode.name, de.contains("kommentiere nicht"))
                assertTrue(mode.name, de.contains("ergaenze nichts") || de.contains("keine neuen Fakten"))
                val en = RefinePrompt.build(mode, false, smart).lowercase()
                assertTrue(mode.name, en.contains("do not translate"))
                assertTrue(mode.name, en.contains("do not comment"))
                assertTrue(mode.name, en.contains("do not add") || en.contains("do not invent"))
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun offHatKeineAnweisung() {
        RefinePrompt.build(RefineMode.OFF, true, false)
    }
}
