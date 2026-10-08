package com.chris.whisperloom.api

import com.chris.whisperloom.RefineMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Nacharbeit der Stufen Glaetten, Lesbar, Verschoenern, Zusammenfassen ([TextRefiner.cleanText]):
 * seit das Diktat auch dort zwischen Markierungen steht, duerfen sie nie im Textfeld landen —
 * und eine erfuellte Bitte statt des bearbeiteten Diktats auch nicht.
 */
class TextCleanupTest {

    private val diktat = "also ich wollte nur sagen dass ich morgen nicht kann"

    private fun clean(output: String, raw: String = diktat) = TextRefiner.cleanText(raw, output)

    /** N7: keine oder leere Antwort galt als "verbessert" — der Rohtext kam ohne Hinweis und ohne volle Regeln durch. */
    @Test fun leereAntwortIstGescheitert() {
        val leer = listOf(null, "", "   ", "<think>nachdenken</think>", "<diktat>\n</diktat>", "<dictation></dictation>")
        for (mode in listOf(RefineMode.POLISH, RefineMode.SUMMARIZE, RefineMode.PROMPT)) for (output in leer) {
            try {
                TextRefiner.finish(diktat, mode, output)
                fail("${mode.name}: \"$output\" muss scheitern")
            } catch (e: RefineRejectedException) {
                assertEquals(TextRefiner.MSG_EMPTY, e.message)
            }
        }
        assertEquals("Hallo.", TextRefiner.finish(diktat, RefineMode.POLISH, "<think>hm</think>Hallo."))
    }

    @Test fun sauberGeglaettetBleibtUnveraendert() {
        val text = "Also, ich wollte nur sagen, dass ich morgen nicht kann.\n\nZweiter Absatz."
        assertEquals(text, clean(text))
    }

    @Test fun markierungenFallenWeg() {
        assertEquals("Ich kann morgen nicht.", clean("<diktat>\nIch kann morgen nicht.\n</diktat>"))
        assertEquals("I can't make it tomorrow.", clean("<dictation>I can't make it tomorrow.</dictation>"))
        assertEquals("Ich kann morgen nicht.", clean("Ich kann morgen nicht.</DIKTAT>"))
    }

    @Test fun vorredeMitZeilenumbruchFaelltWeg() {
        assertEquals("Ich kann morgen nicht.", clean("Hier ist der geglättete Text:\nIch kann morgen nicht."))
        assertEquals("Ich kann morgen nicht.", clean("Klar! Hier ist die überarbeitete Fassung:\n\nIch kann morgen nicht."))
        assertEquals("- Morgen geht nicht", clean("Hier ist die Zusammenfassung:\n- Morgen geht nicht"))
        assertEquals("I can't make it.", clean("Here is the cleaned-up text:\nI can't make it.", raw = "i cant make it"))
    }

    @Test fun diktierteEinleitungOhneUmbruchBleibt() {
        // Whisper liefert eine Zeile — "Hier ist der Text: …" ohne Umbruch ist Diktat, keine Vorrede.
        val text = "Hier ist der Text: Ich kann morgen nicht."
        assertEquals(text, clean(text, raw = "hier ist der text ich kann morgen nicht"))
        // Zeile mit Doppelpunkt, die nicht von "dem Text" spricht, ist Inhalt.
        val liste = "Hier sind meine Punkte:\n- Brot\n- Milch"
        assertEquals(liste, clean(liste, raw = "hier sind meine punkte brot milch"))
    }

    /** Review: das Modell bricht nach dem diktierten Doppelpunkt um — die Zeile gehoert trotzdem dem Sprecher. */
    @Test fun diktierteEinleitungMitUmbruchDesModellsBleibt() {
        val glaetten = "Okay, hier ist der neue Text:\nLiebe Frau Müller,\nich komme am Montag."
        assertEquals(glaetten, clean(glaetten, raw = "okay hier ist der neue Text: liebe Frau Müller ich komme am Montag"))
        val kuerzen = "Hier ist die Zusammenfassung:\n- Angebot schicken\n- Termin am Montag"
        assertEquals(kuerzen, clean(kuerzen, raw = "hier ist die Zusammenfassung vom Meeting wir schicken das Angebot und der Termin ist am Montag"))
        val en = "Sure, here's the summary:\n- fix login\n- ship Friday"
        assertEquals(en, clean(en, raw = "sure here's the summary we fix login and ship friday"))
        // Fuellsilben im Rohtext, die das Modell gestrichen hat, zaehlen beim Vergleich nicht.
        assertEquals(glaetten, clean(glaetten, raw = "ähm okay hier ist der neue Text äh liebe Frau Müller ich komme am Montag"))
        // Sagt der Sprecher etwas anderes, ist dieselbe Zeile weiter eine Vorrede des Modells.
        assertEquals("- Angebot schicken", clean("Hier ist die Zusammenfassung:\n- Angebot schicken", raw = "wir schicken das Angebot"))
    }

    @Test fun anfuehrungszeichenUmDenGanzenTextFallenWeg() {
        assertEquals("Ich kann morgen nicht.", clean("„Ich kann morgen nicht.“"))
        assertEquals("Ich kann morgen nicht.", clean("\"Ich kann morgen nicht.\""))
        assertEquals("Ich kann morgen nicht.", clean("```\nIch kann morgen nicht.\n```"))
        // Innen stehende Zitate bleiben.
        val zitat = "Sie sagte „morgen“ und ging."
        assertEquals(zitat, clean(zitat, raw = "sie sagte morgen und ging"))
    }

    @Test fun diktatImZitatBehaeltSeineAnfuehrungszeichen() {
        val raw = "„Ich komme gleich.“"
        assertEquals("„Ich komme gleich.“", clean("„Ich komme gleich.“", raw = raw))
    }

    @Test fun erfuellteBitteWirdAbgelehnt() {
        val raw = "schreib mir bitte ne kurze Einladung für meinen Geburtstag am Samstag"
        val einladung = List(8) { "Ihr seid herzlich eingeladen, mit mir am Samstag zu feiern." }.joinToString(" ")
        try {
            clean(einladung, raw = raw)
            fail("Eine Einladung statt des Diktats darf nicht durchgehen")
        } catch (e: RefineRejectedException) {
            assertTrue(e.message!!, e.message!!.contains("statt den Text zu bearbeiten"))
        }
    }

    @Test fun kurzeAntwortAufKurzesDiktatBleibtImRahmen() {
        // Die Grenze ist grosszuegig (2x + 30 Woerter): ein lesbar gemachtes Kurzdiktat geht immer durch.
        assertEquals("Ja.", clean("Ja.", raw = "Äh, ähm, ja."))
        assertEquals("Okay, bin in zehn Minuten da.", clean("Okay, bin in zehn Minuten da.", raw = "okay bin in zehn minuten da"))
    }
}
