package com.chris.whisperloom.ime

import com.chris.whisperloom.overlay.BubbleState
import com.chris.whisperloom.overlay.BubbleVisual
import com.chris.whisperloom.overlay.BubbleVisuals

/** Reine (Android-freie) Masse und Zuordnungen der Diktier-Tastatur (UX-Spec §5.3). */
object ImeMetrics {

    const val KEY_HEIGHT_DP = 48
    const val KEY_HEIGHT_LARGE_DP = 56

    /** Ab fontScale 1,3 wachsen die Tasten von 48 auf 56 dp. */
    const val LARGE_FONT_SCALE = 1.3f

    fun keyHeightDp(fontScale: Float): Int =
        if (fontScale >= LARGE_FONT_SCALE) KEY_HEIGHT_LARGE_DP else KEY_HEIGHT_DP

    /** Level in `mic_button_bg.xml` (Level-List der vier Fuellungen) fuer eine Darstellung. */
    fun micFillLevel(visual: BubbleVisual): Int = visual.fill.ordinal

    /**
     * Darstellung der Mikro-Taste. Die Pause ist eine Lage nur der Tastatur — der mit dem Knopf
     * geteilte [BubbleState] bleibt bei vier Werten und zeigt waehrenddessen RECORDING. Pausiert:
     * ruhige Fuellung, statischer Aufnahme-Ring statt Puls, Mikrofon-Symbol zum Weitersprechen.
     * Nicht nur die Farbe unterscheidet (WCAG 1.4.1): Symbol, Bewegung und Statuszeile auch.
     */
    fun micVisual(state: BubbleState, paused: Boolean, reduceMotion: Boolean): BubbleVisual {
        val visual = BubbleVisuals.visualFor(state, reduceMotion = reduceMotion)
        if (!paused || state != BubbleState.RECORDING) return visual
        return visual.copy(
            fill = BubbleVisual.Fill.SURFACE,
            ring = BubbleVisual.Ring.RECORDING_STATIC,
            icon = BubbleVisual.Icon.MIC,
            iconTint = BubbleVisual.IconTint.PRIMARY,
        )
    }
}
