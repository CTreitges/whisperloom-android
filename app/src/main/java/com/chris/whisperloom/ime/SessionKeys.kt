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
 *   man waehrend der Aufnahme erst den Fokus auf die kleinen Tasten daneben bringen.
 * - **Globus:** gesperrt, solange ein Diktat offen ist. Ein Tastaturwechsel beendet den Dienst und
 *   verwuerfe das (pausierte) Audio still. Die Taste bleibt antippbar, damit sie sagen kann, warum.
 */
class SessionKeys(
    private val mic: View,
    private val globe: View,
    private val onSend: () -> Unit,
    private val onDiscard: () -> Unit,
) {
    private var actionIds = emptyList<Int>()

    /** [open] = es gibt ein Diktat; [withoutFinger] = festgestellt oder pausiert. */
    fun show(open: Boolean, withoutFinger: Boolean) {
        for (id in actionIds) ViewCompat.removeAccessibilityAction(mic, id)
        actionIds = if (withoutFinger) {
            listOf(
                ViewCompat.addAccessibilityAction(mic, mic.context.getString(R.string.cd_kb_send)) { _, _ ->
                    onSend()
                    true
                },
                ViewCompat.addAccessibilityAction(mic, mic.context.getString(R.string.cd_kb_discard)) { _, _ ->
                    onDiscard()
                    true
                },
            )
        } else {
            emptyList()
        }
        globe.alpha = if (open) BLOCKED_ALPHA else 1f
        globe.contentDescription = globe.context.getString(
            if (open) R.string.cd_kb_switch_blocked else R.string.cd_kb_switch,
        )
    }

    private companion object {
        /** Deckkraft deaktivierter Elemente in Material 3. */
        const val BLOCKED_ALPHA = 0.38f
    }
}
