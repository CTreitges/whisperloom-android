package com.chris.whisperloom.ime

import android.content.Context
import android.util.Log
import com.chris.whisperloom.AudioRecorder
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.TranscriptionEngine
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.HistorySource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ein offenes Tastatur-Diktat, wenn der Dienst endet. Der Globus ist gesperrt, aber ein Wechsel ueber
 * das Tastatur-Symbol der Navigationsleiste oder die Systemeinstellungen beendet den Dienst trotzdem —
 * und mit ihm das Audio im Speicher. Statt es still zu verwerfen, wird es wie ein normales Diktat
 * erkannt und verarbeitet und landet im Verlauf; ein Feld gibt es nicht mehr. Prozessweit wie
 * HistoryJobs: der Prozess lebt nach dem Dienst weiter.
 *
 * Verlauf aus oder ein Passwort-/Inkognito-Feld: verworfen wie bisher. Keine Benachrichtigung, nie
 * Text loggen.
 */
internal object DictationRescue {

    private const val TAG = "DictationRescue"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * [recorder] haelt das Diktat angehalten (der Aufrufer pausiert eine laufende Aufnahme, damit das
     * Mikrofon sofort frei ist). [privateField] = es gehoerte in ein Passwort- oder Inkognito-Feld.
     */
    fun rescue(context: Context, recorder: AudioRecorder, privateField: Boolean) {
        val app = context.applicationContext
        if (privateField || !Prefs(app).historyEnabled) {
            recorder.cancel()
            return
        }
        scope.launch {
            try {
                val samples = recorder.stop()
                // Wie beim Senden: unter 0,3 s ist es ein versehentlicher Tipp.
                if (samples.size < AudioRecorder.SAMPLE_RATE * 3 / 10) return@launch
                History.record(app, HistorySource.KEYBOARD, TranscriptionEngine.transcribe(app, samples))
            } catch (e: Throwable) {
                // Auch ein OutOfMemoryError bei sehr langen Diktaten; nur die Art des Fehlers.
                Log.w(TAG, "Offenes Diktat nicht gerettet (${e.javaClass.simpleName})")
            }
        }
    }
}
