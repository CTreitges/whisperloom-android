package com.chris.whisperloom.simple

import com.chris.whisperloom.simple.SimpleProfile.Preview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Composing-Logik (Konzept §6.1): partial → setComposingText (ersetzt den ganzen offenen Abschnitt), committed →
 * commitText, Cursor-Sprung und Feldende schreiben fest, TYPE_NULL und TalkBack halten das Feld frei von
 * vorlaeufigem Text, "Loeschen" entfernt nur den eigenen Text.
 */
class ComposingWriterTest {

    /**
     * Fuehrt den Feldtext wirklich: fester Text + Composing-Bereich am Cursor (der Cursor steht immer am Ende).
     * [composingOk] = false stellt ein Feld dar, das kein Composing kann (setComposingText liefert false).
     */
    private class FakeEditor(start: String = "", private val composingOk: Boolean = true) : Editor {
        val fixedText = StringBuilder(start)
        var composingText = ""
        val commits = mutableListOf<String>()
        var finished = 0
        var deleted = 0
        val text: String get() = fixedText.toString() + composingText
        override fun setComposing(text: String): Boolean {
            if (!composingOk) return false
            composingText = text
            return true
        }
        override fun commit(text: String): Boolean {
            composingText = ""
            fixedText.append(text)
            commits += text
            return true
        }
        override fun finishComposing() {
            finished++
            fixedText.append(composingText)
            composingText = ""
        }
        override fun deleteBefore(chars: Int) {
            deleted += chars
            fixedText.setLength((fixedText.length - chars).coerceAtLeast(0))
        }
        override fun textBeforeCursor(chars: Int): CharSequence = fixedText.takeLast(chars)
    }

    @Test fun partialErsetztDenVorlaeufigenTextUndCommittedSchreibtFest() {
        val e = FakeEditor()
        val w = ComposingWriter(e, Preview.FIELD)
        assertEquals("", w.partial("Hallo"))
        assertEquals("Hallo", e.text)
        assertEquals("", w.partial("Hallo Anna"))
        assertEquals("Hallo Anna", e.text)
        w.committed("Hallo Anna.")
        assertEquals("Hallo Anna. ", e.text)
        assertEquals(listOf("Hallo Anna. "), e.commits)
        assertEquals("Hallo Anna. ".length, w.inserted)
        assertEquals("", w.composing)
    }

    @Test fun leerzeichenVorDemAbschnittWennDirektAnEinWortAngefuegtWird() {
        val e = FakeEditor(start = "Moin")
        val w = ComposingWriter(e, Preview.FIELD)
        w.partial("Hallo")
        assertEquals("Moin Hallo", e.text)
        w.committed("Hallo.")
        assertEquals("Moin Hallo. ", e.text)
    }

    @Test fun vorschauModusHaeltDasFeldFreiVonVorlaeufigemText() {
        // TalkBack-Regel (§6.3) bzw. Profil "nur Vorschau": partial nur in die Vorschauzeile, je Abschnitt EIN commit.
        val e = FakeEditor()
        val w = ComposingWriter(e, Preview.LINE)
        assertEquals("Hallo", w.partial("Hallo"))
        assertEquals("Hallo Anna", w.partial("Hallo Anna"))
        assertEquals("kein setComposingText im Vorschau-Modus", "", e.text)
        w.committed("Hallo Anna.")
        assertEquals(listOf("Hallo Anna. "), e.commits)
    }

    @Test fun vorschauAusZeigtNichtsBisZumFestenText() {
        val e = FakeEditor()
        val w = ComposingWriter(e, Preview.OFF)
        assertEquals("", w.partial("Hallo"))
        assertEquals("", e.text)
        w.committed("Hallo.")
        assertEquals("Hallo. ", e.text)
    }

    @Test fun modusJeFeld() {
        assertEquals(Preview.LINE, ComposingWriter.modeFor(Preview.FIELD, composingSupported = false, talkBack = false))
        assertEquals(Preview.LINE, ComposingWriter.modeFor(Preview.FIELD, composingSupported = true, talkBack = true))
        assertEquals(Preview.LINE, ComposingWriter.modeFor(Preview.LINE, composingSupported = true, talkBack = false))
        assertEquals(Preview.FIELD, ComposingWriter.modeFor(Preview.FIELD, composingSupported = true, talkBack = false))
        assertEquals("OFF bleibt OFF, auch mit TalkBack", Preview.OFF, ComposingWriter.modeFor(Preview.OFF, composingSupported = true, talkBack = true))
    }

    @Test fun feldOhneComposingFaelltAufDieVorschauZurueck() {
        // §6.5: liefert setComposingText false, kommen nur noch fertige Saetze; der Rest geht in die Vorschau.
        val e = FakeEditor(composingOk = false)
        val w = ComposingWriter(e, Preview.FIELD)
        assertEquals("Hallo", w.partial("Hallo"))
        assertFalse(w.inField)
        assertEquals("Hallo Anna", w.partial("Hallo Anna"))
        w.committed("Hallo Anna.")
        assertEquals("Hallo Anna. ", e.text)
    }

    @Test fun cursorSprungSchreibtDenOffenenTextFestUndDerRestKommtNichtDoppelt() {
        val e = FakeEditor()
        val w = ComposingWriter(e, Preview.FIELD)
        w.partial("Hallo Anna")
        // Cursor im Composing-Bereich (5..15, davor steht anderer Text): nichts passiert.
        w.cursorMoved(10, 10, 5, 15)
        w.cursorMoved(15, 15, 5, 15)
        assertEquals(0, e.finished)
        // Cursor davor: festschreiben.
        w.cursorMoved(2, 2, 5, 15)
        assertEquals(1, e.finished)
        assertEquals("Hallo Anna".length, w.inserted)
        assertEquals("", w.composing)
        // Weitere Partials desselben Abschnitts bringen nur den Rest — nichts doppelt im Feld (Review W2).
        w.partial("Hallo Anna, bis")
        assertEquals("Hallo Anna, bis", e.text)
        w.cursorMoved(0, 0, -1, -1)
        assertEquals(2, e.finished)
        w.partial("Hallo Anna, bis morgen")
        assertEquals("Hallo Anna, bis morgen", e.text)
        // Der feste Text des Abschnitts bringt nur den Rest.
        w.committed("Hallo Anna, bis morgen.")
        assertEquals("Hallo Anna, bis morgen. ", e.text)
        // commitText ersetzt das offene "morgen" durch den Rest nach dem festen Teil — nur der kommt ins Feld.
        assertEquals(listOf(" morgen. "), e.commits)
    }

    @Test fun cursorSprungOhneComposingBereichSchreibtFest() {
        val e = FakeEditor()
        val w = ComposingWriter(e, Preview.FIELD)
        w.partial("Hallo")
        w.cursorMoved(3, 3, -1, -1)
        assertEquals(1, e.finished)
        w.committed("Hallo")
        assertTrue("nichts doppelt", e.commits.isEmpty())
        assertEquals("Hallo", e.text)
    }

    @Test fun feldendeSchreibtFestImFeldUndFuegtImVorschauModusEin() {
        val feld = FakeEditor()
        val wf = ComposingWriter(feld, Preview.FIELD)
        wf.partial("Hallo")
        wf.fieldFinished()
        assertEquals(1, feld.finished)
        assertEquals("Hallo", feld.text)
        assertEquals("", wf.composing)

        val vorschau = FakeEditor()
        val wv = ComposingWriter(vorschau, Preview.LINE)
        wv.partial("Hallo")
        wv.fieldFinished()
        assertEquals(listOf("Hallo "), vorschau.commits)
    }

    @Test fun flushNachAbbruchHaeltDenOffenenTextFest() {
        // Review K2: endet die Sitzung ohne festen Text, bleibt der vorlaeufige Text als fester Text — die naechste
        // Aufnahme ueberschreibt ihn nicht mehr.
        val e = FakeEditor()
        val w = ComposingWriter(e, Preview.FIELD)
        w.committed("Erster Satz.")
        w.partial("zweiter Satz")
        w.flush()
        assertEquals("Erster Satz. zweiter Satz", e.text)
        assertEquals("Erster Satz. zweiter Satz".length, w.inserted)
        val w2 = ComposingWriter(e, Preview.FIELD)
        w2.partial("dritter")
        assertEquals("Erster Satz. zweiter Satz dritter", e.text)
    }

    @Test fun verwerfenEntferntDenComposingText() {
        val e = FakeEditor()
        val w = ComposingWriter(e, Preview.FIELD)
        w.partial("Hallo")
        w.discard()
        assertEquals("", e.text)
        assertEquals(1, e.finished)
        assertEquals(0, w.inserted)
    }

    @Test fun leererFesterTextFuegtNichtsEin() {
        val e = FakeEditor()
        val w = ComposingWriter(e, Preview.FIELD)
        w.partial("äh")
        w.committed("   ")
        assertTrue(e.commits.isEmpty())
        assertEquals(0, w.inserted)
        assertEquals("", e.text)
    }

    @Test fun loeschenEntferntNurDenEigenenTextVorDemCursor() {
        // Review K1: nach Feldwechsel oder Cursor-Sprung steht fremder Text vor dem Cursor — der bleibt.
        val e = FakeEditor(start = "Alt. ")
        val w = ComposingWriter(e, Preview.FIELD)
        w.partial("Hallo")
        w.committed("Hallo Anna.")
        assertEquals("Alt. Hallo Anna. ", e.text)
        assertTrue(w.removeInserted())
        assertEquals("Alt. ", e.text)
        assertEquals(0, w.inserted)
        assertFalse("zweites Loeschen entfernt nichts mehr", w.removeInserted())

        val fremd = FakeEditor()
        val w2 = ComposingWriter(fremd, Preview.FIELD)
        w2.committed("Meins.")
        fremd.fixedText.append("Fremd")
        assertFalse(w2.removeInserted())
        assertEquals("Meins. Fremd", fremd.text)
        assertEquals(0, fremd.deleted)
    }
}
