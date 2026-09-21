package com.chris.whisperloom.ime

import kotlin.math.abs

/**
 * Reine (Android-freie) Auswertung der Wisch-Geste am Mikro-Knopf der Diktier-Tastatur —
 * JVM-unit-testbar, nach dem Muster von [com.chris.whisperloom.overlay.BubblePosition].
 *
 * Gemessen wird immer **relativ zum Druckpunkt** (nicht in Bildschirm-Koordinaten): So
 * funktioniert die Geste auch dann, wenn der Finger am Rand der 88-dp-Taste aufsetzt, und
 * sie geraet nicht mit der System-Randgeste in Streit.
 *
 * Nach rechts ziehen stellt die Aufnahme fest, nach links verwirft sie; Loslassen ohne
 * Wischen sendet wie bisher sofort.
 */
object DictationGesture {

    /** Weg ab dem Druckpunkt, ab dem ein Ziel einrastet — wie der Magnet-Radius des Knopfs. */
    const val ARM_DISTANCE_DP = 56

    /**
     * Hysterese: Einmal eingerastet, loest das Ziel erst wieder unter (ARM - HYSTERESE).
     * Ohne das flackert die Anzeige, wenn der Finger genau auf der Schwelle zittert.
     */
    const val RELEASE_HYSTERESIS_DP = 12

    /**
     * Ab dieser senkrechten Abweichung gilt der Zug nicht mehr als waagerechter Wisch —
     * sonst rastet jedes Wegziehen nach oben/unten nebenbei ein Ziel ein.
     */
    const val VERTICAL_TOLERANCE_DP = 64

    /** Was die Geste gerade anzeigt, waehrend der Finger liegt. */
    enum class Phase {
        /** Aufnahme laeuft, kein Ziel eingerastet. */
        RECORDING,

        /** Weit genug nach links: Loslassen verwirft. */
        CANCEL_ARMED,

        /** Weit genug nach rechts: Loslassen stellt fest. */
        LOCK_ARMED,
    }

    /** Was das Loslassen aus der jeweiligen Phase ausloest. */
    enum class Release { SEND, LOCK, DISCARD }

    /**
     * Naechste Phase fuer eine Fingerposition [dx]/[dy] relativ zum Druckpunkt (in Pixeln).
     *
     * [armPx] ist die Einrast-Schwelle, [hysteresisPx] haelt ein eingerastetes Ziel etwas
     * laenger, [verticalPx] begrenzt die erlaubte senkrechte Abweichung. [current] ist die
     * zuletzt gemeldete Phase — nur so ist die Hysterese moeglich.
     *
     * Ein Richtungswechsel verlangt wieder den vollen Weg: Wer von rechts nach links zieht,
     * soll nicht auf halber Strecke schon im Verwerfen landen.
     */
    fun phase(
        dx: Float,
        dy: Float,
        armPx: Float,
        hysteresisPx: Float,
        verticalPx: Float,
        current: Phase,
    ): Phase {
        if (abs(dy) > verticalPx) return Phase.RECORDING
        val holdPx = (armPx - hysteresisPx).coerceAtLeast(0f)
        val lockPx = if (current == Phase.LOCK_ARMED) holdPx else armPx
        val cancelPx = if (current == Phase.CANCEL_ARMED) holdPx else armPx
        return when {
            dx >= lockPx -> Phase.LOCK_ARMED
            dx <= -cancelPx -> Phase.CANCEL_ARMED
            else -> Phase.RECORDING
        }
    }

    /**
     * Loslassen aus [phase]. Ohne eingerastetes Ziel bleibt es beim bisherigen Verhalten
     * (senden) — auch bei ACTION_CANCEL, damit ein abgefangener Touch kein Diktat frisst.
     */
    fun release(phase: Phase): Release = when (phase) {
        Phase.RECORDING -> Release.SEND
        Phase.LOCK_ARMED -> Release.LOCK
        Phase.CANCEL_ARMED -> Release.DISCARD
    }
}
