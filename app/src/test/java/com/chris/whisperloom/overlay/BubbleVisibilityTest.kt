package com.chris.whisperloom.overlay

import com.chris.whisperloom.a11y.FieldFocus
import com.chris.whisperloom.a11y.FieldKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sichtbarkeit des schwebenden Knopfs (UX-Spec §6.22): nur bei fokussiertem, editierbarem Feld und
 * offener Bildschirmtastatur, nie im Passwortfeld, ohne Bedienungshilfe nie — ausser waehrend
 * Aufnahme oder Senden. Ausgeblendet bleibt er bis zum naechsten anderen Feld.
 */
class BubbleVisibilityTest {

    private val feldA = FieldKey(windowId = 7, nodeId = 101)
    private val feldB = FieldKey(windowId = 7, nodeId = 202)
    private val feldAndererFenster = FieldKey(windowId = 9, nodeId = 101)

    private fun tippen(feld: FieldKey?, tastatur: Boolean = true, passwort: Boolean = false) =
        FieldFocus(feld, passwort, tastatur)

    private fun mit(vararg fokus: FieldFocus?) = BubbleVisibility().apply { fokus.forEach(::onFocus) }

    @Test fun n1_ohneMeldungKeinKnopf() {
        assertFalse("frisch gestartet, noch kein Feld (Home-Screen)", BubbleVisibility().visible(busy = false))
    }

    @Test fun editierbarUndTastaturOffen_zeigen() {
        assertTrue(mit(tippen(feldA)).visible(busy = false))
    }

    @Test fun n2_bedienungshilfeAus_nie() {
        assertFalse(mit(tippen(feldA), null).visible(busy = false))
    }

    @Test fun n3_tastaturZu_nicht() {
        assertFalse(mit(tippen(feldA, tastatur = false)).visible(busy = false))
        assertFalse("erst offen, dann geschlossen", mit(tippen(feldA), tippen(feldA, tastatur = false)).visible(busy = false))
    }

    @Test fun keinEditierbaresFeld_nicht() {
        assertFalse("Tastatur offen, aber Fokus ohne Feld", mit(tippen(null)).visible(busy = false))
    }

    @Test fun n4_aufnahmeOderSendenBleibtSichtbar() {
        assertTrue("Tastatur zu", mit(tippen(feldA, tastatur = false)).visible(busy = true))
        assertTrue("App gewechselt, kein Feld", mit(tippen(null, tastatur = false)).visible(busy = true))
        assertTrue("Bedienungshilfe weg: laufendes Mikrofon bleibt sichtbar", mit(null).visible(busy = true))
    }

    @Test fun n5_passwortfeld_nicht() {
        assertFalse(mit(tippen(feldA, passwort = true)).visible(busy = false))
    }

    @Test fun n6_zahlenUndTelefonfelderSindNormaleFelder() {
        // Der Eingabetyp zaehlt nur fuer "Passwort"; Zahl/Telefon melden password = false.
        assertTrue(mit(tippen(feldB, passwort = false)).visible(busy = false))
    }

    @Test fun n12_ausgeblendetBisZumAnderenFeld() {
        val v = mit(tippen(feldA))
        v.hide()
        assertFalse(v.visible(busy = false))
        v.onFocus(tippen(feldA, tastatur = false))
        v.onFocus(tippen(feldA))
        assertFalse("gleiches Feld, Tastatur erneut offen: weiter weg", v.visible(busy = false))
        v.onFocus(tippen(null))
        v.onFocus(tippen(feldA))
        assertFalse("Fokus kurz ohne Feld, dann wieder dasselbe: weiter weg", v.visible(busy = false))
        v.onFocus(tippen(feldB))
        assertTrue("anderes Feld: wieder da", v.visible(busy = false))
        v.onFocus(tippen(feldA))
        assertTrue("danach gilt das erste Feld neu", v.visible(busy = false))
    }

    @Test fun n12_anderesFensterGiltAlsAnderesFeld() {
        val v = mit(tippen(feldA))
        v.hide()
        v.onFocus(tippen(feldAndererFenster))
        assertTrue(v.visible(busy = false))
    }

    @Test fun ausgeblendetOhneBekanntesFeld_naechstesFeldZeigt() {
        val v = BubbleVisibility()
        v.hide()
        v.onFocus(tippen(feldA))
        assertTrue(v.visible(busy = false))
    }

    @Test fun ausgeblendetUeberstehtBedienungshilfeNeustart() {
        val v = mit(tippen(feldA))
        v.hide()
        v.onFocus(null)
        v.onFocus(tippen(feldA))
        assertFalse(v.visible(busy = false))
    }

    @Test fun n14_meldungTraegtKeinenInhalt() {
        // Die Meldung hat genau diese Felder: Kennung (Fenster, Knoten), Passwort, Tastatur — kein Text.
        assertEquals(setOf("field", "password", "keyboardOpen"), instanzFelder(FieldFocus::class.java))
        assertEquals(setOf("windowId", "nodeId"), instanzFelder(FieldKey::class.java))
    }

    /** Instanzfelder ohne statische ($stable vom Compose-Compiler). */
    private fun instanzFelder(c: Class<*>) =
        c.declaredFields.filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }.map { it.name }.toSet()

    // --- Deckkraft ------------------------------------------------------------

    @Test fun ruheHalbDeckend() {
        assertEquals(0.5f, BubbleVisibility.alpha(BubbleState.IDLE, touching = false, copiedHint = false), 0f)
    }

    @Test fun beruehrungAufnahmeSendenVoll() {
        assertEquals(1f, BubbleVisibility.alpha(BubbleState.IDLE, touching = true, copiedHint = false), 0f)
        assertEquals(1f, BubbleVisibility.alpha(BubbleState.RECORDING, touching = false, copiedHint = false), 0f)
        assertEquals(1f, BubbleVisibility.alpha(BubbleState.SENDING, touching = false, copiedHint = false), 0f)
    }

    @Test fun fehlerUndKopiertHinweisVoll() {
        assertEquals("Fehler will Aufmerksamkeit", 1f, BubbleVisibility.alpha(BubbleState.ERROR, false, false), 0f)
        assertEquals("Hinweis lesbar", 1f, BubbleVisibility.alpha(BubbleState.IDLE, false, copiedHint = true), 0f)
    }
}
