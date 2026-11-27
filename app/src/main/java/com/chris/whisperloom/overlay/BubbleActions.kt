package com.chris.whisperloom.overlay

import com.chris.whisperloom.RefineMode

/** Eine Bedienungshilfe-Aktion am schwebenden Knopf (TalkBack, Schalterzugriff; N11). */
sealed interface BubbleAction {
    /** Modus setzen und ansagen, ohne etwas zu starten; [active] = das ist der aktuelle Modus. */
    data class SetMode(val mode: RefineMode, val active: Boolean) : BubbleAction

    /** Wie Ablegen aufs Ziel unten: weg bis zum naechsten Feld. */
    data object Hide : BubbleAction

    /** Laufende Aufnahme oder gepuffertes Audio verwerfen. */
    data object Discard : BubbleAction
}

/**
 * Welche Aktionen der Knopf anbietet (docs/design/bubble-textfeld.md §4) — Android-frei.
 * Wischen ist mit Screenreader oder Schalter nicht bedienbar; der Doppeltipp bleibt der Tipp.
 * Modi gibt es nicht waehrend der Uebertragung (die Stufe laeuft dann schon); waehrend der Aufnahme
 * gilt ein neuer Modus fuer genau dieses Diktat und alle folgenden.
 */
object BubbleActions {
    fun of(state: BubbleState, modes: List<RefineMode>, current: RefineMode): List<BubbleAction> {
        val modeActions = if (state == BubbleState.SENDING) emptyList() else modes.map { BubbleAction.SetMode(it, it == current) }
        return modeActions + when (state) {
            BubbleState.IDLE -> listOf(BubbleAction.Hide)
            BubbleState.RECORDING, BubbleState.ERROR -> listOf(BubbleAction.Discard)
            BubbleState.SENDING -> emptyList()
        }
    }
}
