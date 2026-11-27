package com.chris.whisperloom.overlay

import com.chris.whisperloom.overlay.ModeCross.CrossTarget

/**
 * Reine (Android-freie) Auswertung der Touch-Geste am schwebenden Knopf (UX-Spec §6.22,
 * docs/design/bubble-textfeld.md §1/§5) — JVM-unit-testbar, nach dem Muster von
 * [com.chris.whisperloom.ime.DictationGesture].
 *
 * Ein Druck ist erst offen ([Phase.PRESSED]). Daraus wird
 * - ein **Tipp**, wenn der Finger ohne Bewegung losgelassen wird,
 * - **Halten** ([Phase.HELD]), wenn die Halte-Zeit ohne Bewegung ablaeuft (nur in IDLE): das
 *   Modus-Kreuz ist offen, mit Aufnahme ([held], Halten zum Sprechen) oder ohne. Bewegung waehlt
 *   dann nur ein Ziel ([aim]) und verschiebt nichts. Loslassen auf einem Modus = [Release.PICK],
 *   auf dem ✕ = [Release.DISCARD], ohne Ziel = senden bzw. ohne Aufnahme ein Tipp,
 * - **Ziehen** ([Phase.DRAGGING]), sobald der Finger vor der Halte-Zeit den Touch-Slop verlaesst.
 *
 * Der Listener im [FloatingMicService] fuehrt nur aus, was diese Klasse entscheidet.
 */
class BubbleGesture {

    enum class Phase { NONE, PRESSED, HELD, DRAGGING }

    /** Was das Loslassen ausloest; ob das Ziel unten getroffen ist, prueft der Dienst. */
    enum class Release {
        /** Kein offener Druck. */
        NONE,

        /** Kurzer Tipp (oder Halten ohne Aufnahme und ohne Ziel): wie bisher umschalten. */
        TAP,

        /** Gehaltene Aufnahme ohne Ziel: senden. */
        SEND,

        /** Ende eines Zugs vor der Halte-Zeit. */
        DROP,

        /** Im Kreuz auf einem Modus losgelassen ([armed]): Modus setzen, mit Aufnahme damit senden. */
        PICK,

        /** Im Kreuz auf dem ✕ losgelassen: verwerfen. */
        DISCARD,
    }

    companion object {
        /**
         * Endet ein gehaltenes Diktat ohne Ziel frueher (gemessen ab Ablauf der Halte-Zeit), war es ein
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

    /** Ziel im Kreuz unter dem Finger; bleibt nach dem Loslassen bis zum naechsten Druck lesbar. */
    var armed: CrossTarget? = null
        private set

    fun down() {
        phase = Phase.PRESSED
        held = false
        armed = null
    }

    /**
     * Die Halte-Zeit ist abgelaufen. true = jetzt das Kreuz oeffnen. Nur aus einem unbewegten Druck
     * im Ruhezustand ([idle]) — waehrend einer Aufnahme oder Uebertragung bleibt ein langer Druck ein Tipp.
     */
    fun holdElapsed(idle: Boolean): Boolean {
        if (phase != Phase.PRESSED || !idle) return false
        phase = Phase.HELD
        return true
    }

    /** Der Dienst hat zum Halten die Aufnahme gestartet (Halten zum Sprechen). */
    fun recordingStarted() {
        if (phase == Phase.HELD) held = true
    }

    /** Ziel im offenen Kreuz ([ModeCross.pick]); ohne Kreuz wirkungslos. */
    fun aim(target: CrossTarget?) {
        if (phase == Phase.HELD) armed = target
    }

    /** Der Finger hat den Touch-Slop verlassen. true = der Zug beginnt gerade (nur vor der Halte-Zeit). */
    fun beyondSlop(): Boolean {
        if (phase != Phase.PRESSED) return false
        phase = Phase.DRAGGING
        return true
    }

    val dragging: Boolean get() = phase == Phase.DRAGGING

    /** Loslassen. Danach ist die Geste wieder in Ruhe. */
    fun up(): Release {
        val release = when (phase) {
            Phase.NONE -> Release.NONE
            Phase.PRESSED -> Release.TAP
            Phase.HELD -> when (armed) {
                is CrossTarget.Mode -> Release.PICK
                CrossTarget.Discard -> Release.DISCARD
                null -> if (held) Release.SEND else Release.TAP
            }
            Phase.DRAGGING -> Release.DROP
        }
        phase = Phase.NONE
        return release
    }

    /**
     * Abgefangener Touch (ACTION_CANCEL). Eine gehaltene Aufnahme wird gesendet, damit kein
     * Diktat verloren geht; weder Tipp noch Moduswahl werden ausgeloest. true = senden.
     */
    fun cancel(): Boolean {
        val send = held && phase != Phase.NONE
        phase = Phase.NONE
        armed = null
        return send
    }
}
