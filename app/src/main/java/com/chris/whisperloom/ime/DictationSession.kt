package com.chris.whisperloom.ime

/**
 * Reiner (Android-freier) Zustand eines Tastatur-Diktats — JVM-unit-testbar wie [DictationGesture].
 *
 * Der mit dem schwebenden Knopf geteilte [com.chris.whisperloom.overlay.BubbleState] bleibt bei
 * vier Werten; ob eine Aufnahme gehalten, festgestellt oder pausiert ist, weiss nur die Tastatur.
 * Die Pause gibt es nur im festgestellten Zustand: beim Halten liegt der Finger auf der Taste.
 *
 * Die Aufnahmezeit kommt aus den Samples des Recorders, nicht von der Uhr — sie ist ueber alle
 * Teile summiert und steht in der Pause still. An ihr haengt auch der Laengen-Schutz.
 */
class DictationSession {

    enum class Phase {
        /** Kein Diktat offen. */
        NONE,

        /** Finger liegt auf der Mikro-Taste. */
        HOLDING,

        /** Laeuft ohne Finger weiter (nach rechts gewischt oder per Klick gestartet). */
        LOCKED,

        /** Festgestellt und angehalten: Mikrofon frei, das Aufgenommene bleibt. */
        PAUSED,
    }

    /** Laengen-Stufe des Aufgenommenen; begrenzt wird nur die Online-Erkennung. */
    enum class Length { OK, LONG, MAX }

    var phase = Phase.NONE
        private set

    /** Hoechstlaenge erreicht: automatisch pausiert, Weiter gesperrt — nur noch senden oder verwerfen. */
    var capped = false
        private set

    /** Zuletzt gemeldete Aufnahmezeit (siehe [update]). */
    var recordedMs = 0L
        private set

    /** Laengen-Stufe zur letzten Meldung. */
    var length = Length.OK
        private set

    /** Es gibt ein Diktat, das noch gesendet oder verworfen werden muss. */
    val isOpen: Boolean get() = phase != Phase.NONE

    /** Festgestellt und laeuft. */
    val isLocked: Boolean get() = phase == Phase.LOCKED

    val isPaused: Boolean get() = phase == Phase.PAUSED

    /** Ohne liegenden Finger: Verwerfen und Senden sind dann eigene Tasten. */
    val withoutFinger: Boolean get() = phase == Phase.LOCKED || phase == Phase.PAUSED

    fun hold(): Boolean = move(Phase.NONE, Phase.HOLDING)

    fun lock(): Boolean = move(Phase.HOLDING, Phase.LOCKED)

    fun pause(): Boolean = move(Phase.LOCKED, Phase.PAUSED)

    /** Weiter — nicht nach erreichter Hoechstlaenge. */
    fun resume(): Boolean = !capped && move(Phase.PAUSED, Phase.LOCKED)

    /** Hoechstlaenge erreicht: wie [pause], aber Weiter bleibt gesperrt. */
    fun cap(): Boolean {
        if (!pause()) return false
        capped = true
        return true
    }

    /** Gesendet oder verworfen — aus jedem Zustand. */
    fun end() {
        phase = Phase.NONE
        capped = false
        recordedMs = 0L
        length = Length.OK
    }

    /**
     * Vor dem Fortsetzen aus der Pause: die Erkennung kann gewechselt haben (Zahnrad). Offline faellt
     * der Deckel weg; online haelt er eine Pause, die schon ueber der Hoechstlaenge liegt (offline
     * aufgenommen, oder kurz vor dem naechsten Takt von Hand pausiert).
     */
    fun recheck(limited: Boolean) {
        if (isPaused) capped = update(recordedMs, limited) == Length.MAX
    }

    /** Zu lang zum Senden an die Online-Erkennung ([limited]); siehe [SEND_MAX_MS]. */
    fun tooLong(limited: Boolean): Boolean = limited && recordedMs > SEND_MAX_MS

    /**
     * Neuer Stand des Recorders. [limited] = Online-Erkennung (nur die hat eine Obergrenze).
     * Liefert die Laengen-Stufe; bei [Length.MAX] muss der Aufrufer [cap] ausloesen.
     */
    fun update(recordedMs: Long, limited: Boolean): Length {
        this.recordedMs = recordedMs
        length = length(recordedMs, limited)
        return length
    }

    private fun move(from: Phase, to: Phase): Boolean {
        if (phase != from) return false
        phase = to
        return true
    }

    companion object {
        /**
         * Ab hier ein Hinweis in der Statuszeile. OpenAI nimmt hoechstens 25 MB; bei 16 kHz Mono
         * PCM16 sind das gut 13 min, und ein 413 gilt als nicht wiederholbar — das Audio waere weg.
         */
        const val LONG_MS = 10 * 60_000L

        /** Hier pausiert die Aufnahme von selbst, mit Abstand zur Grenze der Anbieter. */
        const val MAX_MS = 12 * 60_000L

        /**
         * Bis hier sendet die Tastatur an die Online-Erkennung. Was der Deckel angehalten hat, liegt
         * hoechstens einen Takt ueber [MAX_MS]; laenger wird ein Diktat nur offline — in der Pause
         * umgestellt, lehnte der Anbieter es ab.
         */
        const val SEND_MAX_MS = MAX_MS + 30_000L

        fun length(recordedMs: Long, limited: Boolean): Length = when {
            !limited -> Length.OK
            recordedMs >= MAX_MS -> Length.MAX
            recordedMs >= LONG_MS -> Length.LONG
            else -> Length.OK
        }
    }
}
