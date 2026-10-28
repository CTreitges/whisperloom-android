package com.chris.whisperloom.ime

import android.view.View
import androidx.core.view.ViewCompat
import com.chris.whisperloom.R

/**
 * Was ein offenes Diktat an der Tastatur ausser den Wisch-Zielen veraendert — nach dem Muster von
 * [GestureTargets] eine kleine Klasse um die Views:
 *
 * - **Mikro-Taste:** Ohne liegenden Finger (festgestellt oder pausiert) ist ein Tipp Pause bzw.
 *   Weiter. Fuer TalkBack bekommt sie deshalb die Aktionen "Senden" und "Verwerfen" — sonst muesste
 *   man waehrend der Aufnahme erst den Fokus auf die kleinen Tasten daneben bringen. Mit "Tippen
 *   statt Halten" sendet der Tipp; dann ist die Pause eine Aktion. Im Fehlerzustand kommt
 *   "Erneut senden" dazu (Plan CTR-272 B3).
 * - **Globus:** gesperrt, solange ein Diktat offen ist. Ein Tastaturwechsel beendet den Dienst und
 *   verwuerfe das (pausierte) Audio still. Die Taste bleibt antippbar, damit sie sagen kann, warum.
 */
class SessionKeys(
    private val mic: View,
    private val globe: View,
    private val onSend: () -> Unit,
    private val onDiscard: () -> Unit,
    private val onPause: () -> Unit = {},
    private val onRetry: () -> Unit = {},
    private val onUndo: () -> Unit = {},
) {
    private var actionIds = emptyList<Int>()

    /**
     * [open] = es gibt ein Diktat; [withoutFinger] = festgestellt oder pausiert; [pause] = die
     * Aktion "Pausieren" anbieten (Tippen statt Halten, festgestellt); [retry] = Fehlerzustand mit
     * gepuffertem Audio; [undo] = die Frist nach dem Verwerfen laeuft (die Statuszeile erst anzusteuern,
     * schafft man mit TalkBack in 2 s kaum).
     */
    fun show(open: Boolean, withoutFinger: Boolean, pause: Boolean = false, retry: Boolean = false, undo: Boolean = false) {
        for (id in actionIds) ViewCompat.removeAccessibilityAction(mic, id)
        val next = mutableListOf<Int>()
        if (withoutFinger) {
            if (pause) next += action(R.string.cd_kb_pause, onPause)
            next += action(R.string.cd_kb_send, onSend)
            next += action(R.string.cd_kb_discard, onDiscard)
        }
        if (retry) next += action(R.string.cd_kb_retry, onRetry)
        if (undo) next += action(R.string.history_undo, onUndo)
        actionIds = next
        globe.alpha = if (open) BLOCKED_ALPHA else 1f
        globe.contentDescription = globe.context.getString(
            if (open) R.string.cd_kb_switch_blocked else R.string.cd_kb_switch,
        )
    }

    private fun action(label: Int, run: () -> Unit): Int =
        ViewCompat.addAccessibilityAction(mic, mic.context.getString(label)) { _, _ ->
            run()
            true
        }

    private companion object {
        /** Deckkraft deaktivierter Elemente in Material 3. */
        const val BLOCKED_ALPHA = 0.38f
    }
}
