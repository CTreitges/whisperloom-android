package com.chris.whisperloom.overlay

import android.view.View
import androidx.core.view.ViewCompat
import com.chris.whisperloom.R

/**
 * Was am schwebenden Knopf sonst nur per Ziehen geht, als Bedienungshilfe-Aktion (WCAG 2.5.7,
 * UX-Spec §6.20) — nach dem Muster von `ime/SessionKeys`:
 *
 * - **Aufnahme verwerfen** in RECORDING und ERROR (sonst nur per Ziehen aufs ✕),
 * - **Erneut senden** in ERROR (dasselbe wie der Tipp, aber mit Namen im Aktionsmenue).
 *
 * TalkBack, Switch Access und Voice Access bieten sie im Aktionsmenue an.
 */
class BubbleActions(
    private val bubble: View,
    private val onRetry: () -> Unit,
    private val onDiscard: () -> Unit,
) {
    private var actionIds = emptyList<Int>()

    fun show(state: BubbleState) {
        for (id in actionIds) ViewCompat.removeAccessibilityAction(bubble, id)
        val ids = mutableListOf<Int>()
        if (state == BubbleState.ERROR) ids += add(R.string.cd_kb_retry, onRetry)
        if (state == BubbleState.RECORDING || state == BubbleState.ERROR) ids += add(R.string.cd_kb_discard, onDiscard)
        actionIds = ids
    }

    private fun add(label: Int, action: () -> Unit): Int =
        ViewCompat.addAccessibilityAction(bubble, bubble.context.getString(label)) { _, _ ->
            action()
            true
        }
}
