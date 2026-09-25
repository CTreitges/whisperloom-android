package com.chris.whisperloom.ime

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.overlay.BubbleAnimators

/**
 * Schnellzugriff auf die Textverbesserung in der Diktier-Tastatur (UX-Spec §8.1): die vier
 * Stufen — plus "Prompt", wenn in den erweiterten Optionen eingeschaltet — als ausklappbare
 * Zeile ueber der Tastenreihe. Nach dem Muster von [GestureTargets] eine Klasse um die Views,
 * damit der Dienst nur verdrahtet.
 *
 * Eingeklappt ist die Zeile `GONE` — die Tastatur darf im Ruhezustand nicht hoeher werden.
 *
 * Ohne eingerichteten KI-Zugang sind die KI-Stufen abgeblendet statt ins Leere zu
 * fuehren: sie kosten je eine Anfrage an ein Sprachmodell, und ohne Zugang kaeme nur der
 * Rohtext zurueck. "Aus" bleibt immer waehlbar.
 */
class RefineBar(
    private val row: ViewGroup,
    private val reduceMotion: () -> Boolean,
) {

    /** Reihenfolge wie im Einstellungs-Screen ([RefineMode.settings]). */
    private val keys: List<Pair<RefineMode, Button>> = listOf(
        RefineMode.OFF to row.findViewById(R.id.refine_off),
        RefineMode.POLISH to row.findViewById(R.id.refine_polish),
        RefineMode.BEAUTIFY to row.findViewById(R.id.refine_beautify),
        RefineMode.SUMMARIZE to row.findViewById(R.id.refine_summarize),
        RefineMode.PROMPT to row.findViewById(R.id.refine_prompt),
    )

    val isShown: Boolean get() = row.visibility == View.VISIBLE

    fun bind(onPick: (RefineMode) -> Unit) {
        for ((mode, key) in keys) {
            key.setOnClickListener { onPick(mode) }
            // Der Tastentext allein sagt nicht, wozu er gehoert.
            key.contentDescription = key.context.getString(R.string.cd_kb_refine_level, key.text)
        }
    }

    /**
     * Zeigt die Zeile mit [selected] als gewaehlter Stufe. [llmReady] `false` blendet die
     * KI-Stufen ab. [promptEnabled] entscheidet bei jedem Aufklappen neu, ob "Prompt" dabei
     * ist — der Schalter kann sich in der App geaendert haben, waehrend die Tastatur lebte.
     */
    fun show(selected: RefineMode, llmReady: Boolean, promptEnabled: Boolean = false) {
        keys.first { it.first == RefineMode.PROMPT }.second.visibility =
            if (promptEnabled) View.VISIBLE else View.GONE
        select(selected)
        setLlmReady(llmReady)
        if (row.visibility == View.VISIBLE) return
        row.visibility = View.VISIBLE
        if (reduceMotion()) {
            row.alpha = 1f
            row.scaleX = 1f
            row.scaleY = 1f
        } else {
            BubbleAnimators.appear(row)
        }
    }

    fun hide() {
        row.visibility = View.GONE
    }

    /** `isSelected` statt `isActivated`: TalkBack sagt dazu von sich aus "ausgewaehlt". */
    fun select(mode: RefineMode) {
        for ((m, key) in keys) key.isSelected = m == mode
    }

    private fun setLlmReady(ready: Boolean) {
        for ((mode, key) in keys) key.isEnabled = ready || mode == RefineMode.OFF
    }
}
