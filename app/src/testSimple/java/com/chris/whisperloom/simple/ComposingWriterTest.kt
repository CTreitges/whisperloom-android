package com.chris.whisperloom.simple

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Composing-Logik (Konzept §6.1): partial → setComposingText (ersetzt den ganzen offenen Abschnitt), committed →
 * commitText, Cursor-Sprung und Feldende schreiben fest, TYPE_NULL und TalkBack halten das Feld frei von
 * vorlaeufigem Text.
 */
class ComposingWriterTest {

    /** Zeichnet die Aufrufe auf; [before] = Zeichen vor dem Cursor. */
    private class FakeEditor(var before: String = "") : Editor {
        val composing = mutableListOf<String>()
        val commits = mutableListOf<String>()
        var finished = 0
        var deleted = 0
        override fun setComposing(text: String): Boolean { composing += text; return true }
        override fun commit(text: String): Boolean { commits += text; return true }
        override fun finishComposing() { finished++ }
        override fun deleteBefore(chars: Int) { deleted += chars }
        override fun textBeforeCursor(chars: Int): CharSequence = before.takeLast(chars)
    }

    @Test fun partialErsetztDenVorlaeufigenTextUndCommittedSchreibtFest() {
        val e = FakeEditor()
        val w = ComposingWriter(e, inField = true)
        assertEquals("", w.partial("Hallo"))
        assertEquals("", w.partial("Hallo Anna"))
        assertEquals(listOf("Hallo", "Hallo Anna"), e.composing)
        w.committed("Hallo Anna.")
        assertEquals(listOf("Hallo Anna. "), e.commits)
        assertEquals("Hallo Anna. ".length, w.inserted)
        assertEquals("", w.composing)
    }

    @Test fun leerzeichenVorDemAbschnittWennDirektAnEinWortAngefuegtWird() {
        val e = FakeEditor(before = "Moin")
        val w = ComposingWriter(e, inField = true)
        w.partial("Hallo")
        assertEquals(listOf(" Hallo"), e.composing)
        w.committed("Hallo.")
        assertEquals(listOf(" Hallo. "), e.commits)
    }

    @Test fun vorschauModusHaeltDasFeldFreiVonVorlaeufigemText() {
        // TalkBack-Regel (§6.3) bzw. Profil "nur Vorschau": partial nur in die Vorschauzeile, je Abschnitt EIN commit.
        val e = FakeEditor()
        val w = ComposingWriter(e, inField = false)
        assertEquals("Hallo", w.partial("Hallo"))
        assertEquals("Hallo Anna", w.partial("Hallo Anna"))
        assertTrue("kein setComposingText im Vorschau-Modus", e.composing.isEmpty())
        w.committed("Hallo Anna.")
        assertEquals(listOf("Hallo Anna. "), e.commits)
    }

    @Test fun typeNullBekommtNurFertigeSaetze() {
        assertFalse(ComposingWriter.inField(SimpleProfile.Preview.FIELD, composingSupported = false, talkBack = false))
        assertFalse(ComposingWriter.inField(SimpleProfile.Preview.FIELD, composingSupported = true, talkBack = true))
        assertFalse(ComposingWriter.inField(SimpleProfile.Preview.LINE, composingSupported = true, talkBack = false))
        assertTrue(ComposingWriter.inField(SimpleProfile.Preview.FIELD, composingSupported = true, talkBack = false))
    }

    @Test fun cursorSprungSchreibtDenOffenenTextFestUndDerRestKommtNichtDoppelt() {
        val e = FakeEditor()
        val w = ComposingWriter(e, inField = true)
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
        // Der feste Text des Abschnitts bringt nur den Rest.
        w.committed("Hallo Anna, bis morgen.")
        assertEquals(listOf(", bis morgen. "), e.commits)
    }

    @Test fun cursorSprungOhneComposingBereichSchreibtFest() {
        val e = FakeEditor()
        val w = ComposingWriter(e, inField = true)
        w.partial("Hallo")
        w.cursorMoved(3, 3, -1, -1)
        assertEquals(1, e.finished)
        w.committed("Hallo")
        assertTrue("nichts doppelt", e.commits.isEmpty())
    }

    @Test fun feldendeSchreibtFestImFeldUndFuegtImVorschauModusEin() {
        val feld = FakeEditor()
        val wf = ComposingWriter(feld, inField = true)
        wf.partial("Hallo")
        wf.fieldFinished()
        assertEquals(1, feld.finished)
        assertEquals("", wf.composing)

        val vorschau = FakeEditor()
        val wv = ComposingWriter(vorschau, inField = false)
        wv.partial("Hallo")
        wv.fieldFinished()
        assertEquals(listOf("Hallo "), vorschau.commits)
    }

    @Test fun verwerfenEntferntDenComposingText() {
        val e = FakeEditor()
        val w = ComposingWriter(e, inField = true)
        w.partial("Hallo")
        w.discard()
        assertEquals(listOf("Hallo", ""), e.composing)
        assertEquals(1, e.finished)
        assertEquals(0, w.inserted)
    }

    @Test fun leererFesterTextFuegtNichtsEin() {
        val e = FakeEditor()
        val w = ComposingWriter(e, inField = true)
        w.partial("äh")
        w.committed("   ")
        assertTrue(e.commits.isEmpty())
        assertEquals(0, w.inserted)
        assertNull(e.composing.lastOrNull()?.takeIf { it.isNotEmpty() })
    }
}
