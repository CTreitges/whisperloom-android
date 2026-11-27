package com.chris.whisperloom.overlay

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants

/**
 * Reine (Android-freie) Motion-Werte und -Kurven fuer Knopf und IME-Taste (UX-Spec §5.4).
 * Die eigentlichen Animatoren baut [BubbleAnimators]; hier steht nur die nachvollziehbare
 * Rechnung, damit sie ohne Geraet testbar ist. (HapticFeedbackConstants sind Compile-Zeit-
 * Konstanten und werden inline uebernommen — kein Android-Laufzeitcode.)
 */
object BubbleMotion {

    const val PULSE_DURATION_MS = 1200L
    const val PULSE_ALPHA_START = 0.45f
    const val PULSE_SCALE_END = 1.35f

    /** Der Pegel hebt den Startradius des Pulses um bis zu 15 %. */
    const val PULSE_LEVEL_BOOST = 0.15f

    /** Rotierender Sende-Bogen: 1 Umdrehung pro Sekunde. */
    const val ARC_ROTATION_MS = 1000L

    const val SHAKE_DURATION_MS = 300L
    const val SHAKE_AMPLITUDE_DP = 6f

    const val SUCCESS_RING_MS = 300L
    const val COPIED_HINT_MS = 2000L
    const val ICON_FADE_MS = 150L
    const val FILL_CROSSFADE_MS = 200L

    /** Deckkraft-Wechsel Ruhe (halb) <-> Beruehrung/Aufnahme (voll), UX-Spec §6.22. */
    const val ALPHA_FADE_MS = 150L

    const val CANCEL_APPEAR_MS = 200L
    const val CANCEL_SCALE_START = 0.8f
    const val CANCEL_HIT_SCALE = 1.12f

    /** Puls-Alpha bei Fortschritt 0..1: 0,45 -> 0. */
    fun pulseAlpha(fraction: Float): Float = PULSE_ALPHA_START * (1f - fraction.coerceIn(0f, 1f))

    /** Puls-Skalierung: Start 1,0 (+ bis 15 % je nach Pegel 0..1), Ende 1,35. */
    fun pulseScale(fraction: Float, level: Float): Float {
        val start = 1f + PULSE_LEVEL_BOOST * level.coerceIn(0f, 1f)
        return start + (PULSE_SCALE_END - start) * fraction.coerceIn(0f, 1f)
    }

    /** Shake-Keyframes (translationX in dp): drei Halbschwingungen ±6 dp, zurueck auf 0. */
    fun shakeOffsetsDp(): FloatArray =
        floatArrayOf(0f, SHAKE_AMPLITUDE_DP, -SHAKE_AMPLITUDE_DP, SHAKE_AMPLITUDE_DP, 0f)

    /** Reduce-Motion = Settings.Global.ANIMATOR_DURATION_SCALE ist 0 (Bedienungshilfe/Entwickleroptionen). */
    fun isReduceMotion(animatorDurationScale: Float): Boolean = animatorDurationScale <= 0f

    enum class Haptic { CONFIRM, CONTEXT_CLICK, REJECT }

    /** CONFIRM/REJECT gibt es erst ab API 30 — davor KEYBOARD_TAP bzw. LONG_PRESS. */
    @SuppressLint("InlinedApi") // Konstanten werden inline uebernommen und per [sdk] geschuetzt.
    fun hapticConstant(kind: Haptic, sdk: Int): Int = when (kind) {
        Haptic.CONFIRM ->
            if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.KEYBOARD_TAP
        Haptic.CONTEXT_CLICK -> HapticFeedbackConstants.CONTEXT_CLICK
        Haptic.REJECT ->
            if (sdk >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }
}
