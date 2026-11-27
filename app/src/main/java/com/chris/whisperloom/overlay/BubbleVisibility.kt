package com.chris.whisperloom.overlay

import com.chris.whisperloom.a11y.FieldFocus
import com.chris.whisperloom.a11y.FieldKey

/**
 * Wann der schwebende Knopf zu sehen ist (UX-Spec §6.22) — Android-frei, JVM-testbar.
 *
 * Sichtbar nur bei fokussiertem, editierbarem Feld **und** offener Bildschirmtastatur, nie im
 * Passwortfeld, ohne Bedienungshilfe nie. Ausnahme: waehrend Aufnahme oder Senden bleibt er stehen
 * (ein laufendes Mikrofon ist nie unsichtbar, das Ergebnis geht wie gewohnt ins Feld oder in die
 * Zwischenablage). Abgelegt aufs Ziel unten bleibt er weg, bis ein anderes Feld den Fokus bekommt.
 */
class BubbleVisibility {

    /** Letzte Meldung der Bedienungshilfe; null = Bedienungshilfe aus (oder noch nichts gemeldet). */
    private var focus: FieldFocus? = null

    /** Ausgeblendet im Feld [hiddenIn] (null = ohne bekanntes Feld ausgeblendet). */
    private var hidden = false
    private var hiddenIn: FieldKey? = null

    fun onFocus(next: FieldFocus?) {
        focus = next
        val field = next?.field ?: return
        if (hidden && field != hiddenIn) {
            hidden = false
            hiddenIn = null
        }
    }

    /** Ablegen aufs Ziel unten: weg bis zum naechsten anderen Feld, ohne Frist. */
    fun hide() {
        hidden = true
        hiddenIn = focus?.field
    }

    /** [busy] = Aufnahme oder Senden laeuft. */
    fun visible(busy: Boolean): Boolean {
        if (busy) return true
        val f = focus ?: return false
        return f.field != null && !f.password && f.keyboardOpen && !hidden
    }

    companion object {
        /** Ruhe-Deckkraft: der Knopf schwebt durchsichtig ueber der App (kein Regler). */
        const val IDLE_ALPHA = 0.5f

        /**
         * Voll deckend bei Beruehrung, Aufnahme, Senden und Fehler; in Ruhe halb — ausser der Hinweis
         * "Kopiert — einfuegen" steht, der muss lesbar sein.
         */
        fun alpha(state: BubbleState, touching: Boolean, copiedHint: Boolean): Float =
            if (state == BubbleState.IDLE && !touching && !copiedHint) IDLE_ALPHA else 1f
    }
}
