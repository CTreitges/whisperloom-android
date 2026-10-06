package com.chris.whisperloom.agent

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import java.io.File
import java.util.UUID

/**
 * Der eine offene Sprachauftrag, ueber Prozessgrenzen hinweg. Noetig, weil nach dem
 * Abschicken niemand mehr lebt, der etwas im Speicher halten koennte: der Dienst beendet
 * sich, die Arbeit laeuft im WorkManager, und das Widget darf auch nach einem Neustart
 * nicht "bereit" ueber einem haengenden Auftrag zeigen.
 *
 * Eigene Datei (`whisperloom_agent.xml`) statt der Einstellungen — das hier sind
 * Betriebsdaten, keine Praeferenzen, und der Einstellungs-Horcher in
 * [com.chris.whisperloom.ui.state.PrefsState] soll davon nichts mitbekommen.
 *
 * Das Audio liegt in `filesDir`, NICHT in `cacheDir`: das System raeumt den Cache bei
 * Platzmangel weg — mitten im Wiederholungsversuch waere der Auftrag dann verloren.
 */
class VoiceTaskStore(context: Context) {

    private val app = context.applicationContext
    private val sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    val audioFile: File get() = File(app.filesDir, AUDIO_NAME)

    var state: VoiceTaskState
        get() = runCatching { VoiceTaskState.valueOf(sp.getString(KEY_STATE, null) ?: "") }
            .getOrDefault(VoiceTaskState.READY)
        set(v) = sp.edit { putString(KEY_STATE, v.name) }

    /** Bleibt ueber alle Wiederholungen gleich — darauf baut die Idempotenz der Bridge. */
    var requestId: String
        get() = sp.getString(KEY_REQUEST_ID, "") ?: ""
        private set(v) = sp.edit { putString(KEY_REQUEST_ID, v) }

    /** Erkannter Text, sobald er da ist: ein Wiederholungsversuch soll nicht erneut transkribieren. */
    var text: String
        get() = sp.getString(KEY_TEXT, "") ?: ""
        set(v) = sp.edit { putString(KEY_TEXT, v) }

    /** Anzeigetext des Widgets (Fehlergrund bzw. Erfolgsmeldung). */
    var message: String
        get() = sp.getString(KEY_MESSAGE, "") ?: ""
        set(v) = sp.edit { putString(KEY_MESSAGE, v) }

    /**
     * Grund, warum die Textverbesserung ausgefallen ist ("" = alles normal gelaufen).
     *
     * Der Rohtext kommt dann trotzdem durch — so macht es auch die Tastatur. Nur sieht man
     * es dort: ohne diesen Merker haette der Nutzer geglaubt, "Glaetten" sei angewandt
     * worden, und haette den schlechteren Text der Erkennung angelastet.
     */
    var refineSkipped: String
        get() = sp.getString(KEY_REFINE_SKIPPED, "") ?: ""
        set(v) = sp.edit { putString(KEY_REFINE_SKIPPED, v) }

    var durationMs: Long
        get() = sp.getLong(KEY_DURATION, 0)
        private set(v) = sp.edit { putLong(KEY_DURATION, v) }

    /**
     * Widget-Profil, das den Auftrag aufgenommen hat — sein Server bekommt ihn ([VoiceTaskWorker]).
     * Die Profil-Id, nicht die Widget-Id: die aendert sich bei einer Wiederherstellung, und die
     * Bindung kann ein "Neu konfigurieren" umhaengen. Leer = Auftrag von vor 3.7.1.
     */
    var profileId: String
        get() = sp.getString(KEY_PROFILE_ID, "") ?: ""
        private set(v) = sp.edit { putString(KEY_PROFILE_ID, v) }

    /** ISO-8601, geht als `recorded_at` an die Bridge. */
    var recordedAt: String
        get() = sp.getString(KEY_RECORDED_AT, "") ?: ""
        private set(v) = sp.edit { putString(KEY_RECORDED_AT, v) }

    /**
     * Beginn des aktuellen Versuchs (`SystemClock.elapsedRealtime`, 0 = unbekannt). Grundlage
     * fuer [VoiceTaskUi.STALL_MS]: ab wann ein Tipp einen laufenden Worker als haengend ersetzt.
     * Monoton — Uhrverstellung und Zeitzonenwechsel spielen keine Rolle. Worker und Trampolin
     * setzen es.
     */
    var attemptStartedAt: Long
        get() = sp.getLong(KEY_ATTEMPT_STARTED, 0)
        set(v) = sp.edit { putLong(KEY_ATTEMPT_STARTED, v) }

    /**
     * Rechnet gerade die Offline-Erkennung fuer diesen Auftrag? Dann ersetzt ein Tipp den Lauf
     * auch nach [VoiceTaskUi.STALL_MS] nicht: whisper rechnet auf EINEM Thread und laesst sich
     * von WorkManager nicht unterbrechen — ein Ersatz stellte sich nur hinter die laufende
     * Erkennung und verdoppelte die Wartezeit. Der Worker setzt es zu Laufbeginn und loescht es,
     * sobald die Erkennung vorbei ist.
     */
    var offlineRecognition: Boolean
        get() = sp.getBoolean(KEY_OFFLINE_RECOGNITION, false)
        set(v) = sp.edit { putBoolean(KEY_OFFLINE_RECOGNITION, v) }

    /** Ob ein Auftrag auf Erledigung wartet (Audio oder bereits erkannter Text). */
    val hasWork: Boolean get() = requestId.isNotEmpty() && (text.isNotEmpty() || audioFile.isFile)

    /** Legt einen neuen Auftrag an und verwirft alles Vorherige. */
    fun begin(samples: FloatArray, durationMs: Long, recordedAt: String, profileId: String): String {
        clear()
        val id = UUID.randomUUID().toString()
        runCatching { audioFile.writeBytes(VoiceTaskAudio.toBytes(samples)) }
            .onFailure { Log.e(TAG, "Aufnahme nicht gespeichert", it) }
        requestId = id
        this.durationMs = durationMs
        this.recordedAt = recordedAt
        this.profileId = profileId
        return id
    }

    fun loadSamples(): FloatArray {
        val f = audioFile
        if (!f.isFile) return FloatArray(0)
        return VoiceTaskAudio.fromBytes(f.readBytes())
    }

    /** Auftrag erledigt (oder endgueltig aufgegeben): Audio und Merker weg. */
    fun clear() {
        runCatching { audioFile.delete() }
        sp.edit {
            remove(KEY_REQUEST_ID)
            remove(KEY_TEXT)
            remove(KEY_DURATION)
            remove(KEY_RECORDED_AT)
            remove(KEY_PROFILE_ID)
            remove(KEY_REFINE_SKIPPED)
            remove(KEY_ATTEMPT_STARTED)
            remove(KEY_OFFLINE_RECOGNITION)
        }
    }

    companion object {
        const val FILE = "whisperloom_agent"
        const val AUDIO_NAME = "voice_task.pcm"

        private const val KEY_STATE = "state"
        private const val KEY_REQUEST_ID = "request_id"
        private const val KEY_TEXT = "text"
        private const val KEY_MESSAGE = "message"
        private const val KEY_DURATION = "duration_ms"
        private const val KEY_RECORDED_AT = "recorded_at"
        private const val KEY_PROFILE_ID = "profile_id"
        private const val KEY_REFINE_SKIPPED = "refine_skipped"
        private const val KEY_ATTEMPT_STARTED = "attempt_started_at"
        private const val KEY_OFFLINE_RECOGNITION = "offline_recognition"
        private const val TAG = "VoiceTaskStore"
    }
}
