package com.chris.whisperloom.ime

import android.view.View
import android.widget.ImageButton
import com.chris.whisperloom.R
import com.chris.whisperloom.overlay.BubbleAnimators

/**
 * Die beiden Wisch-Ziele der Diktier-Tastatur (UX-Spec §5.3) — links verwerfen, rechts
 * feststellen. Nach dem Muster von [com.chris.whisperloom.overlay.MicRings]: eine kleine
 * Klasse um die Views, damit der Service nur noch verdrahtet und das Verhalten ohne
 * laufenden Dienst pruefbar bleibt.
 *
 * Zwei Betriebsarten fuer dieselben zwei Views:
 * - **Ziehen** ([showDragging]): reine Anzeige. Das getroffene Ziel wird hervorgehoben
 *   (`android:state_activated`), bedienbar ist nichts — der Finger liegt ja auf dem Mikrofon.
 * - **Festgestellt** ([showLocked]): echte Tasten. Rechts wird aus dem Schloss das
 *   Senden-Symbol, denn festgestellt ist die Aufnahme dann bereits.
 */
class GestureTargets(
    private val discard: ImageButton,
    private val lock: ImageButton,
    private val reduceMotion: () -> Boolean,
) {

    /** Ziele als Anzeige waehrend des Ziehens; [phase] bestimmt die Hervorhebung. */
    fun showDragging(phase: DictationGesture.Phase) {
        show(clickable = false)
        discard.isActivated = phase == DictationGesture.Phase.CANCEL_ARMED
        lock.isActivated = phase == DictationGesture.Phase.LOCK_ARMED
    }

    /** Ziele als bedienbare Tasten, nachdem die Aufnahme festgestellt wurde. */
    fun showLocked() {
        discard.isActivated = false
        lock.isActivated = false
        lock.setImageResource(R.drawable.ic_send)
        lock.contentDescription = lock.context.getString(R.string.cd_kb_send)
        show(clickable = true)
    }

    fun hide() {
        for (target in targets) {
            target.visibility = View.GONE
            target.isActivated = false
            target.isClickable = false
        }
        // Zurueck vom Senden- auf das Feststellen-Symbol.
        lock.setImageResource(R.drawable.ic_lock)
        lock.contentDescription = lock.context.getString(R.string.cd_kb_lock)
    }

    val isShown: Boolean get() = discard.visibility == View.VISIBLE

    private val targets get() = listOf(discard, lock)

    private fun show(clickable: Boolean) {
        for (target in targets) {
            if (target.visibility != View.VISIBLE) {
                target.visibility = View.VISIBLE
                if (reduceMotion()) {
                    target.alpha = 1f
                    target.scaleX = 1f
                    target.scaleY = 1f
                } else {
                    BubbleAnimators.appear(target)
                }
            }
            target.isClickable = clickable
            target.isFocusable = clickable
            // Als blosse Anzeige aus dem Bedienungshilfen-Baum heraushalten: TalkBack boete
            // sonst Tasten an, die ohne liegenden Finger nichts tun.
            target.importantForAccessibility =
                if (clickable) View.IMPORTANT_FOR_ACCESSIBILITY_YES else View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }
}
