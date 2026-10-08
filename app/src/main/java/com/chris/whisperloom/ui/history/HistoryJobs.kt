package com.chris.whisperloom.ui.history

import android.content.Context
import android.util.Log
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.Processing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * "Andere Stufe …" im Hintergrund, prozessweit wie ModelDownloads: die Rechnung uebersteht Rotation
 * und das Verlassen des Eintrags. Der Eintrag zeigt [running] als Chip mit Ladeanzeige und holt
 * sich das Ergebnis aus [finished] ([consume]). Die eingestellte Stufe aendert sich dabei nie.
 */
object HistoryJobs {

    private const val TAG = "HistoryJobs"

    /** Eine Rechnung: Eintrag + Verarbeitung. */
    data class Job(val id: String, val processing: Processing)

    /**
     * Ergebnis einer Rechnung. [saved] = die Fassung liegt im Eintrag. Sonst blieb der Eintrag
     * unveraendert: [reason] = warum ohne KI (null = unbekannt), [gone] = der Eintrag ist weg.
     * [note] = Hinweis ohne Folgen (online gescheitert, lokal verbessert).
     */
    data class Finished(
        val job: Job,
        val saved: Boolean,
        val reason: String? = null,
        val note: String? = null,
        val gone: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _running = MutableStateFlow<Set<Job>>(emptySet())
    val running: StateFlow<Set<Job>> = _running.asStateFlow()

    private val _finished = MutableStateFlow<List<Finished>>(emptyList())
    val finished: StateFlow<List<Finished>> = _finished.asStateFlow()

    /** Startet die Rechnung; laeuft dieselbe schon, nichts. @return true = gestartet */
    fun start(context: Context, id: String, processing: Processing): Boolean {
        val job = Job(id, processing)
        if (job in _running.getAndUpdate { it + job }) return false
        val app = context.applicationContext
        scope.launch {
            val done = try {
                val result = History.reprocess(app, id, processing)
                when {
                    result == null -> Finished(job, saved = false, gone = true)
                    processing == Processing.OFF || result.refined -> Finished(job, saved = true, note = result.note)
                    else -> Finished(job, saved = false, reason = result.skipped)
                }
            } catch (e: Exception) {
                // Nie Text loggen — nur die Art des Fehlers.
                Log.w(TAG, "Neu verarbeiten fehlgeschlagen (${e.javaClass.simpleName})")
                Finished(job, saved = false)
            }
            // Erst das Ergebnis, dann der Lauf weg: sonst zeigte der Eintrag kurz die alte Auswahl.
            _finished.update { it + done }
            _running.update { it - job }
        }
        return true
    }

    /** Ergebnis gesehen (Hinweis gezeigt, Chip gewaehlt). */
    fun consume(finished: Finished) {
        _finished.update { it - finished }
    }
}
