package com.chris.whisperloom.overlay

/**
 * Reine (Android-freie) Auswertung der Touch-Geste am schwebenden Knopf (UX-Spec §6.22) —
 * JVM-unit-testbar, nach dem Muster von [com.chris.whisperloom.ime.DictationGesture].
 *
 * Ein Druck ist erst offen ([Phase.PRESSED]). Daraus wird
 * - ein **Tipp**, wenn der Finger ohne Bewegung losgelassen wird,
 * - **Halten zum Sprechen** ([Phase.HELD]), wenn die Halte-Zeit ohne Bewegung ablaeuft
 *   (nur in IDLE und nur mit Schalter) — Loslassen sendet dann,
 * - **Ziehen** ([Phase.DRAGGING]), sobald der Finger den Touch-Slop verlaesst. Nach dem
 *   Halten bleibt die Aufnahme dabei gehalten: Loslassen sendet, ueber dem Ziel verwirft es.
 *
 * Der Listener im [FloatingMicService] fuehrt nur aus, was diese Klasse entscheidet.
 */
class BubbleGesture {

    enum class Phase { NONE, PRESSED, HELD, DRAGGING }

    /** Was das Loslassen ausloest; ob ein Ziel getroffen ist, prueft der Dienst. */
    enum class Release {
        /** Kein offener Druck. */
        NONE,

        /** Kurzer Tipp: wie bisher umschalten. */
        TAP,

        /** Gehaltene Aufnahme ohne Ziehen: senden. */
        SEND,

        /** Ende eines Zugs; [held] sagt, ob dabei eine gehaltene Aufnahme laeuft. */
        DROP,
    }

    companion object {
        /**
         * Endet ein gehaltenes Diktat ohne Zug frueher (gemessen ab Ablauf der Halte-Zeit), war es ein
         * langsamer Tipp: Die Aufnahme laeuft weiter wie nach einem Tipp, statt zu senden.
         */
        const val HOLD_MIN_MS = 1_000L

        /**
         * Langsamer Tipp: Zwischen Ablauf der Halte-Zeit ([downTime] + [holdTimeout]) und dem Loslassen
         * ([upTime]) lag weniger als [HOLD_MIN_MS]. Zeiten aus `MotionEvent.getEventTime()`.
         */
        fun isSlowTap(downTime: Long, upTime: Long, holdTimeout: Long): Boolean =
            upTime - downTime - holdTimeout < HOLD_MIN_MS
    }

    var phase = Phase.NONE
        private set

    /** Die laufende Aufnahme hat dieser Druck per Halten gestartet. */
    var held = false
        private set

    fun down() {
        phase = Phase.PRESSED
        held = false
    }

    /**
     * Die Halte-Zeit ist abgelaufen. true = jetzt die Aufnahme starten. Nur aus einem
     * unbewegten Druck, mit [enabled] Schalter und im Ruhezustand ([idle]) — waehrend einer
     * Aufnahme oder Uebertragung bleibt ein langer Druck ein Tipp.
     */
    fun holdElapsed(enabled: Boolean, idle: Boolean): Boolean {
        if (phase != Phase.PRESSED || !enabled || !idle) return false
        phase = Phase.HELD
        held = true
        return true
    }

    /** Der Finger hat den Touch-Slop verlassen. true = der Zug beginnt gerade. */
    fun beyondSlop(): Boolean {
        if (phase != Phase.PRESSED && phase != Phase.HELD) return false
        phase = Phase.DRAGGING
        return true
    }

    val dragging: Boolean get() = phase == Phase.DRAGGING

    /** Loslassen. Danach ist die Geste wieder in Ruhe. */
    fun up(): Release {
        val release = when (phase) {
            Phase.NONE -> Release.NONE
            Phase.PRESSED -> Release.TAP
            Phase.HELD -> Release.SEND
            Phase.DRAGGING -> Release.DROP
        }
        phase = Phase.NONE
        return release
    }

    /**
     * Abgefangener Touch (ACTION_CANCEL). Eine gehaltene Aufnahme wird gesendet, damit kein
     * Diktat verloren geht; ein Tipp wird nicht ausgeloest. true = senden.
     */
    fun cancel(): Boolean {
        val send = held && phase != Phase.NONE
        phase = Phase.NONE
        return send
    }
}
