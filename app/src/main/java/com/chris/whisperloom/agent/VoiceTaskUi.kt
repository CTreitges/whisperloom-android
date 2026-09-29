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
     * Jetzt senden, ausser es wird gerade wirklich gearbeitet. Wartet der Auftrag (Backoff,
     * fehlendes Netz, Standby) oder gibt es gar keinen Job mehr, reiht der Tipp ihn sofort neu
     * ein; haengt ein laufender Versuch laenger als [VoiceTaskUi.STALL_MS], ersetzt er ihn.
     * Ohne Auftrag wird nur der echte Zustand gezeichnet. Die Entscheidung faellt in
     * [VoiceTaskUi.nudge].
     */
    REFRESH,

    /** Keine Absicht — Platzhalter fuer eine Intent ohne Angabe. */
    NONE,
}

/** Was der WorkManager zum Auftrag gerade tut — verdichtet auf das, was ein Tipp wissen muss. */
enum class JobPhase {
    /** Kein offener Job (nie eingereiht, erledigt, abgebrochen oder endgueltig gescheitert). */
    NONE,

    /** Eingereiht, laeuft aber nicht: Backoff, Netz-Bedingung, Standby oder Doze. */
    WAITING,

    /** Ein Worker arbeitet gerade (transkribiert oder sendet). */
    RUNNING,
}

/** Was ein Tipp auf "Wird gesendet …" bzw. "erneut senden" ausloest. */
enum class Nudge {
    /** Nichts zu senden — nur den echten Zustand zeichnen. */
    REDRAW,

    /** Auftrag sofort (neu) einreihen, ohne Netz-Bedingung. */
    SEND_NOW,

    /** Ein Worker arbeitet wirklich: nichts tun, auch nicht neu zeichnen — die Flaeche gehoert ihm. */
    WAIT,
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

    /**
     * Ab dieser Laufzeit gilt ein laufender Versuch als haengend, und ein Tipp ersetzt ihn.
     *
     * Untergrenze: ueber 2 x `Http.CONNECT_TIMEOUT_MS` + [AgentBridge.READ_TIMEOUT_MS]
     * (= 105 s). So ueberholt ein Tipp nie einen laufenden Versand mit gecachtem Text — die
     * Bridge claimt die request_id vor ihrer synchronen Arbeit, ein Duplikat bekaeme dann
     * "202 duplicate" und zeigte "Gesendet", obwohl der erste Versand noch scheitern kann.
     * Obergrenze: unter der 10-min-Deadline des WorkManagers, sonst kaeme der Ausweg nie.
     * Eine legitim lange Erkennung beim Anbieter kann laenger dauern; wer dann tippt, zahlt sie
     * doppelt — das ist der bewusste Preis gegen die Sackgasse.
     *
     * Offline gilt die Frist waehrend der Erkennung NICHT ([VoiceTaskStore.offlineRecognition]):
     * whisper rechnet auf einem einzigen Thread und laesst sich von WorkManager nicht abbrechen.
     * Ein Ersatz stellte sich hinter die laufende Erkennung, und jeder Tipp verlaengerte die
     * Wartezeit um eine volle Erkennung, statt sie abzukuerzen.
     */
    const val STALL_MS = 180_000L

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
        // Keine neue Aufnahme: der Tipp stoesst den wartenden Auftrag an (siehe [nudge]). Ohne
        // diesen Weg bliebe ein haengendes "arbeitet" fuer immer stehen, ohne jede Tippflaeche.
        VoiceTaskState.WORKING -> TapIntent.REFRESH
    }

    /**
     * Wie lange der aktuelle Versuch schon laeuft. Beide Werte aus `SystemClock.elapsedRealtime`.
     * 0, wenn der Beginn unbekannt ist (0) oder nach einem Neustart in der "Zukunft" liegt —
     * dann gilt der Versuch als frisch, ein Tipp wartet also lieber einmal zu oft.
     */
    fun runningFor(startedAt: Long, now: Long): Long =
        if (startedAt <= 0 || now < startedAt) 0 else now - startedAt

    /**
     * Was ein Tipp auf "Wird gesendet …" bzw. "erneut senden" tut. Wartet der Job nur (oder gibt
     * es keinen), wird sofort gesendet. Arbeitet ein Worker, wird er erst nach [STALL_MS]
     * ersetzt — sonst kostete jeder ungeduldige Tipp eine zweite, bezahlte Transkription.
     * Eine laufende Offline-Erkennung ([offlineRecognition]) wird nie ersetzt, siehe [STALL_MS].
     */
    fun nudge(hasWork: Boolean, phase: JobPhase, runningForMs: Long, offlineRecognition: Boolean = false): Nudge = when {
        !hasWork -> Nudge.REDRAW
        phase == JobPhase.RUNNING && (offlineRecognition || runningForMs < STALL_MS) -> Nudge.WAIT
        else -> Nudge.SEND_NOW
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
