package com.chris.whisperloom.agent

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import com.chris.whisperloom.AudioRecorder
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import java.time.Instant

/**
 * Nimmt fuer das Widget auf. Der Dienst lebt nur waehrend der Aufnahme: danach uebernimmt
 * der WorkManager (Transkription + Versand), und der Dienst beendet sich.
 *
 * Der Zustandsautomat liegt ausschliesslich hier, und die Aktionen sind ABSICHTEN
 * ([ACTION_START]/[ACTION_STOP]), kein Umschalter — ein Doppelklick auf dem Startbildschirm
 * darf den Zustand nicht kippen. Ein STOP ohne laufende Aufnahme wird verworfen, nicht als
 * Fehler behandelt.
 *
 * `startForeground` ist die ERSTE Anweisung in [onCreate], vor allem, was mit Audio zu tun
 * hat (5-Sekunden-Regel).
 */
class VoiceTaskService : Service() {

    private val recorder = AudioRecorder()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var store: VoiceTaskStore

    @Volatile private var recording = false
    private var startedAt = 0L
    private var recordedAt = ""

    /**
     * stopSelf() in onCreate haelt ein bereits eingereihtes onStartCommand NICHT auf — ohne
     * dieses Merkmal liefe danach noch start() durch und liesse ein eingefrorenes "nimmt auf"
     * stehen, obwohl der Dienst gleich abgeraeumt wird.
     */
    private var foregroundFehlgeschlagen = false

    /**
     * Notbremse. Start und Stopp sind zwei getrennte Tipps — wer nach dem Start das Telefon
     * einsteckt, liesse den Mikrofon-Dienst sonst unbegrenzt laufen und ~32 kB/s im Speicher
     * sammeln. Anders als beim Overlay und bei der Tastatur gibt es hier kein natuerliches
     * Ende. Die Grenze wirkt wie ein Tipp auf "senden": der Auftrag geht raus.
     */
    private val notbremse = Runnable {
        if (recording) {
            Log.i(TAG, "Hoechstdauer erreicht — Aufnahme wird abgeschickt")
            stop()
            stopSelf()
        }
    }

    /** Sekunden-Takt fuer die laufende Dauer im Widget; ohne ihn stuende die Zeit still. */
    private val tick = object : Runnable {
        override fun run() {
            if (!recording) return
            val elapsed = elapsedMs()
            VoiceTaskWidgetView.push(this@VoiceTaskService, VoiceTaskState.RECORDING, elapsed)
            main.postDelayed(this, TICK_MS - elapsed % TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = VoiceTaskStore(this)
        try {
            VoiceTaskNotification.ensureChannel(this)
            // ServiceCompat, nicht startForeground direkt: die Variante mit Typ gibt es erst ab
            // Android 10, minSdk ist 26.
            ServiceCompat.startForeground(
                this,
                VoiceTaskNotification.ID,
                VoiceTaskNotification.build(this),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Foreground-Start fehlgeschlagen", e)
            foregroundFehlgeschlagen = true
            fail(getString(R.string.widget_fgs_failed))
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (foregroundFehlgeschlagen) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_STOP -> stop()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(tick)
        main.removeCallbacks(notbremse)
        if (recording) recorder.cancel()
        recording = false
        super.onDestroy()
    }

    private fun start() {
        // Absicht, kein Umschalter: ein zweites START waehrend der Aufnahme ist ein Doppelklick.
        if (recording) return
        if (!VoiceTaskWidget.hasMicPermission(this)) {
            fail(getString(R.string.widget_no_mic))
            stopSelf()
            return
        }
        if (!recorder.start()) {
            fail(getString(R.string.widget_silent))
            stopSelf()
            return
        }
        recording = true
        startedAt = SystemClock.elapsedRealtime()
        recordedAt = Instant.now().toString()
        store.state = VoiceTaskState.RECORDING
        VoiceTaskWidgetView.push(this, VoiceTaskState.RECORDING, 0)
        main.postDelayed(tick, TICK_MS)
        main.postDelayed(notbremse, MAX_DURATION_MS)
    }

    private fun stop() {
        // STOP ohne laufende Aufnahme: verwerfen, nicht als Fehler zeigen. Kommt der Tipp von
        // einer nach einem Neustart stehengebliebenen Flaeche, wird sie hier gleich richtiggestellt.
        if (!recording) {
            VoiceTaskWidget.refresh(this)
            stopSelf()
            return
        }
        recording = false
        main.removeCallbacks(tick)
        main.removeCallbacks(notbremse)
        val duration = elapsedMs()
        val samples = recorder.stop()

        // Erfolgskontrolle ueber den PEGEL, nicht ueber "Datei existiert": wenn Android dem
        // Dienst das Mikrofon still entzogen hat, kommen lauter Nullen an — das ist der
        // einzige Weg, den Fehlschlag ueberhaupt zu bemerken.
        when (VoiceTaskUi.verdict(duration, samples)) {
            Verdict.TOO_SHORT -> fail(getString(R.string.widget_too_short))
            Verdict.SILENT -> fail(getString(R.string.widget_silent))
            Verdict.OK -> hand(samples, duration)
        }
        stopSelf()
    }

    /** Auftrag auf die Platte legen und dem WorkManager uebergeben; ab hier lebt der Dienst nicht mehr. */
    private fun hand(samples: FloatArray, duration: Long) {
        store.begin(samples, duration, recordedAt)
        store.message = ""
        store.state = VoiceTaskState.WORKING
        VoiceTaskWidgetView.push(this, VoiceTaskState.WORKING)
        VoiceTaskWork.enqueue(this)
    }

    private fun fail(message: String) {
        store.state = VoiceTaskState.ERROR
        store.message = message
        VoiceTaskWidgetView.push(this, VoiceTaskState.ERROR, message = message)
    }

    private fun elapsedMs(): Long = SystemClock.elapsedRealtime() - startedAt

    companion object {
        const val ACTION_START = "com.chris.whisperloom.agent.START"
        const val ACTION_STOP = "com.chris.whisperloom.agent.STOP"

        /** Sekunden-Takt der Dauer-Anzeige. */
        const val TICK_MS = 1_000L

        /**
         * Hoechstdauer einer Aufnahme. Dieselbe Groessenordnung wie die Stueckelung geteilter
         * Sprachnachrichten; laengere Auftraege spricht niemand am Startbildschirm.
         */
        const val MAX_DURATION_MS = 5 * 60 * 1000L

        private const val TAG = "VoiceTaskService"

        // Bewusst KEIN start(context)-Einstieg: der Dienst wird ausschliesslich ueber das
        // Trampolin gestartet (siehe VoiceTaskTrampolineActivity), ein direkter
        // startForegroundService waere genau der Hintergrund-Start, den es vermeidet.
    }
}
