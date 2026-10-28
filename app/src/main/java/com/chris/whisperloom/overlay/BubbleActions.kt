package com.chris.whisperloom.overlay

import android.view.View
import androidx.core.view.ViewCompat
import com.chris.whisperloom.R

/**
 * Was am schwebenden Knopf sonst nur per Ziehen geht, als Bedienungshilfe-Aktion (WCAG 2.5.7,
 * UX-Spec §6.20) — nach dem Muster von `ime/SessionKeys`:
 *
 * - **Aufnahme verwerfen** in RECORDING und ERROR (sonst nur per Ziehen aufs ✕),
 * - **Erneut senden** in ERROR (dasselbe wie der Tipp, aber mit Namen im Aktionsmenue),
 * - **15 Minuten ausblenden** in IDLE (sonst nur per Ziehen aufs Ausblenden-Ziel, §6.22).
 *
 * TalkBack, Switch Access und Voice Access bieten sie im Aktionsmenue an.
 */
class BubbleActions(
    private val bubble: View,
    private val onRetry: () -> Unit,
    private val onDiscard: () -> Unit,
    private val onSnooze: () -> Unit,
) {
    private var actionIds = emptyList<Int>()

    fun show(state: BubbleState) {
        for (id in actionIds) ViewCompat.removeAccessibilityAction(bubble, id)
        val ids = mutableListOf<Int>()
        if (state == BubbleState.ERROR) ids += add(R.string.cd_kb_retry, onRetry)
        if (state == BubbleState.RECORDING || state == BubbleState.ERROR) ids += add(R.string.cd_kb_discard, onDiscard)
        if (state == BubbleState.IDLE) ids += add(R.string.cd_bubble_snooze, onSnooze)
        actionIds = ids
    }

    private fun add(label: Int, action: () -> Unit): Int =
        ViewCompat.addAccessibilityAction(bubble, bubble.context.getString(label)) { _, _ ->
            action()
            true
        }
}
