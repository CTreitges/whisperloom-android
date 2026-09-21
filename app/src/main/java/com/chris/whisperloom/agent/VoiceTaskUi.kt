package com.chris.whisperloom.agent

import com.chris.whisperloom.Formats
import kotlin.math.sqrt

/**
 * Die Zustaende des Sprachauftrag-Widgets. Vier davon sind die aus der Spezifikation
 * (bereit, nimmt auf, arbeitet, Fehler); [SENT] ist die kurze Erfolgsmeldung davor
 * zurueck auf [READY], [NO_MIC] der Sonderfall "Mikrofon nicht erlaubt".
 *
 * Wahrheit ist immer der Dienst bzw. der gespeicherte Auftrag — das Widget haelt
 * keinen eigenen Zustand, es wird bei jedem Uebergang neu gezeichnet.
 */
enum class VoiceTaskState {
    READY,
    RECORDING,
    WORKING,
    SENT,
    ERROR,
    NO_MIC,

    /** Sprachauftrag ist nicht eingeschaltet oder nicht eingerichtet — Tipp fuehrt in die Einstellungen. */
    OFF,
}

/** Was ein Tipp auf das Widget bedeutet. Absichten, kein Umschalter (ein Doppelklick darf nichts kippen). */
enum class TapIntent {
    /** Aufnahme beginnen (ueber die Trampolin-Activity). */
    START,

    /** Laufende Aufnahme beenden und abschicken. */
    STOP,

    /** Gescheiterten Auftrag erneut senden. */
    RETRY,

    /** In die App fuehren — Mikrofon erlauben bzw. Server eintragen. */
    SETUP,

    /**
     * Nur nachsehen, was wirklich Sache ist. Sichtbar passiert dabei nichts — ausser das
     * Widget zeigt einen Zustand, den es gar nicht mehr gibt (nach einem Neustart bleibt die
     * zuletzt gezeichnete Flaeche stehen, auch wenn nichts mehr laeuft).
     */
    REFRESH,

    /** Keine Absicht — Platzhalter fuer eine Intent ohne Angabe. */
    NONE,
}

/** Wie eine beendete Aufnahme zu bewerten ist. */
enum class Verdict {
    /** Brauchbar — Auftrag anlegen. */
    OK,

    /** Kuerzer als [VoiceTaskUi.MIN_DURATION_MS] — ein Fehlgriff, kein Auftrag. */
    TOO_SHORT,

    /** Kein Ton angekommen. Der einzige Weg, den lautlosen Fehlschlag zu bemerken. */
    SILENT,
}

/** Reine (Android-freie) Anzeige- und Entscheidungs-Helfer des Widgets — JVM-unit-testbar. */
object VoiceTaskUi {

    /**
     * Effektivpegel, unter dem eine Aufnahme als tonlos gilt. Das ist die einzige Absicherung
     * gegen den stillen Fehlschlag: Android 14+ darf einem Hintergrund-Start das Mikrofon
     * entziehen, ohne eine Exception zu werfen — die Datei entsteht dann trotzdem, nur mit
     * lauter Nullen. 0,003 liegt deutlich unter normalem Raumrauschen (~0,01) und deutlich
     * ueber einer stummgeschalteten Quelle (exakt 0).
     */
    const val SILENCE_RMS = 0.003f

    /** So lange bleibt "gesendet" stehen, bevor das Widget auf "bereit" zurueckfaellt. */
    const val SENT_HOLD_MS = 2_000L

    /** Kuerzeste brauchbare Aufnahme; darunter war es ein Fehlgriff, kein Auftrag. */
    const val MIN_DURATION_MS = 700L

    fun rms(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        return sqrt(sum / samples.size).toFloat()
    }

    /** Leer oder durchgehend still — beides heisst: es kam kein Ton an. */
    fun isSilent(samples: FloatArray): Boolean = rms(samples) < SILENCE_RMS

    /** Aufnahmedauer als m:ss (dieselbe Form wie am schwebenden Knopf). */
    fun timerText(millis: Long): String = Formats.duration(millis)

    /** Reihenfolge zaehlt: zu kurz ist die praezisere Meldung, still waere hier auch immer wahr. */
    fun verdict(durationMs: Long, samples: FloatArray): Verdict = when {
        durationMs < MIN_DURATION_MS -> Verdict.TOO_SHORT
        isSilent(samples) -> Verdict.SILENT
        else -> Verdict.OK
    }

    fun tap(state: VoiceTaskState): TapIntent = when (state) {
        VoiceTaskState.READY, VoiceTaskState.SENT -> TapIntent.START
        VoiceTaskState.RECORDING -> TapIntent.STOP
        VoiceTaskState.ERROR -> TapIntent.RETRY
        VoiceTaskState.NO_MIC, VoiceTaskState.OFF -> TapIntent.SETUP
        // Sichtbar tut ein Tipp hier nichts: eine zweite Aufnahme oder ein zweiter Versand
        // waeren falsch. Er holt nur den echten Zustand — sonst bliebe ein nach einem Neustart
        // haengendes "arbeitet" fuer immer stehen, ohne jede Tippflaeche.
        VoiceTaskState.WORKING -> TapIntent.REFRESH
    }

    /**
     * Welcher Zustand nach einem Neustart (Reboot, Prozesstod, Widget neu hinzugefuegt) gilt.
     * Eine Aufnahme ueberlebt so etwas nie; ein bereits abgeschickter Auftrag schon, denn er
     * liegt als Datei im Speicher. Ohne diese Umrechnung zeigte das Widget "bereit" ueber
     * einem haengenden Auftrag — oder ewig "nimmt auf", obwohl nichts mehr laeuft.
     */
    fun afterRestart(stored: VoiceTaskState, hasWork: Boolean): VoiceTaskState = when {
        !hasWork -> VoiceTaskState.READY
        stored == VoiceTaskState.WORKING || stored == VoiceTaskState.ERROR -> stored
        // Liegt Arbeit da, ist jeder andere gespeicherte Zustand eine Luege: aufnehmen kann nach
        // einem Neustart niemand mehr, und "gesendet" waere es dann nicht. Der Auftrag ist
        // mittendrin abgerissen und gehoert in den Fehler-Zustand (Tipp = erneut senden).
        else -> VoiceTaskState.ERROR
    }
}
