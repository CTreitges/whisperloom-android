package com.chris.whisperloom.agent

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.TranscriptionEngine
import java.util.concurrent.TimeUnit

/** Einreihen des Auftrags. Getrennt vom Worker, damit Dienst und Trampolin dieselbe Stelle benutzen. */
object VoiceTaskWork {

    /**
     * EIN fester Name mit KEEP. Das passt, weil das Widget ohnehin nur einen Auftrag zur Zeit
     * kennt: waehrend "arbeitet" nimmt der Dienst kein START an, und ein gescheiterter Auftrag
     * endet als FAILED — also nicht mehr "pending", sodass der Erneut-Senden-Tipp durchkommt.
     * Mit der request_id als Namen waere KEEP nur noch Idempotenz und zwei Auftraege koennten
     * sich ueberholen; das will hier niemand.
     */
    const val UNIQUE_NAME = "whisperloom-voice-task"

    /** Danach ist Schluss mit Wiederholen: rund 10 s, 20 s, 40 s, 80 s, 160 s. */
    const val MAX_ATTEMPTS = 5

    const val BACKOFF_SECONDS = 10L

    fun enqueue(ctx: Context) = enqueueImpl(ctx)

    /**
     * Ob zu diesem Namen wirklich noch etwas aussteht. Ohne diese Frage waere "arbeitet" eine
     * Sackgasse: stirbt der Prozess zwischen [VoiceTaskStore.begin] und [enqueue], gibt es nie
     * einen Auftrag, und das Widget stuende bis zur Neuinstallation auf "Wird gesendet …".
     */
    fun isScheduled(ctx: Context): Boolean = isScheduledImpl(ctx)

    /** Auftrag aufgeben (Einstellungen: "Offenen Auftrag verwerfen"). */
    fun cancel(ctx: Context) = cancelImpl(ctx)

    /**
     * Naht fuer Dienst- und Trampolin-Tests: WorkManager laesst sich auf dem
     * Entwicklungsrechner (linux-aarch64) nicht starten, weil Robolectric dort kein SQLite hat.
     */
    @VisibleForTesting
    var isScheduledImpl: (Context) -> Boolean = { ctx ->
        WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(UNIQUE_NAME).get()
            .any { !it.state.isFinished }
    }

    @VisibleForTesting
    var cancelImpl: (Context) -> Unit = { ctx ->
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_NAME)
    }

    @VisibleForTesting
    var enqueueImpl: (Context) -> Unit = { ctx ->
        val request = OneTimeWorkRequestBuilder<VoiceTaskWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx)
            .beginUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
            .enqueue()
    }
}

/**
 * Transkribiert den aufgenommenen Ton und schickt ihn an die Bridge. Im WorkManager, damit
 * der Auftrag ein ausgeschaltetes Display und ein kurzes Funkloch uebersteht.
 *
 * Duenne Huelle: die Entscheidungen trifft [VoiceTaskPipeline], die ohne Android auskommt.
 * Das ist hier kein Stilmittel, sondern Notwendigkeit — auf dem Entwicklungsrechner
 * (linux-aarch64) laeuft WorkManager unter Robolectric gar nicht, weil dessen SQLite fehlt.
 *
 * [Worker] statt CoroutineWorker: `TranscriptionEngine.transcribe` blockiert, und `doWork`
 * laeuft ohnehin schon auf einem Hintergrund-Thread.
 */
class VoiceTaskWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val store = VoiceTaskStore(ctx)
        if (!store.hasWork) return Result.success()

        VoiceTaskWidgetView.push(ctx, VoiceTaskState.WORKING)
        // Bereits erkannter Text wird NICHT neu transkribiert — das kostet beim Anbieter Geld.
        val cached = store.text.ifBlank { null }

        return when (val outcome = pipeline(ctx, store).run(cached)) {
            is TaskOutcome.Sent -> {
                // VOR dem Aufraeumen lesen — clear() loescht den Merker mit.
                val hinweis = store.refineSkipped
                store.clear()
                finish(ctx, store, VoiceTaskState.SENT, hinweis)
                Result.success()
            }

            is TaskOutcome.Retry -> {
                outcome.text?.let { store.text = it }
                if (runAttemptCount + 1 < VoiceTaskWork.MAX_ATTEMPTS) {
                    Log.i(TAG, "Versuch ${runAttemptCount + 1} gescheitert, spaeter erneut: ${outcome.reason}")
                    store.state = VoiceTaskState.WORKING
                    Result.retry()
                } else {
                    finish(ctx, store, VoiceTaskState.ERROR, outcome.reason)
                    Result.failure()
                }
            }

            is TaskOutcome.Failed -> {
                outcome.text?.let { store.text = it }
                // Nichts verstanden = nichts zu wiederholen; alles andere bleibt gepuffert.
                if (outcome.reason == VoiceTaskPipeline.MSG_EMPTY) store.clear()
                finish(ctx, store, VoiceTaskState.ERROR, outcome.reason)
                Result.failure()
            }
        }
    }

    /**
     * Zustand festhalten und zeichnen.
     *
     * "Gesendet" bleibt danach stehen, bis etwas anderes passiert — es wird NICHT nach zwei
     * Sekunden auf "bereit" zurueckgesetzt. Das war der erste Entwurf und ein Fehler: das
     * Warten haette in doWork stattfinden muessen, der Auftrag waere zwei Sekunden laenger
     * RUNNING geblieben, und ein in dieser Zeit aufgenommener neuer Auftrag waere von
     * ExistingWorkPolicy.KEEP lautlos verworfen und anschliessend auch noch mit "bereit"
     * uebermalt worden. Stehenbleiben ist ehrlicher und kostet nichts: ein Tipp auf
     * "gesendet" startet wie auf "bereit" eine neue Aufnahme.
     */
    private fun finish(ctx: Context, store: VoiceTaskStore, state: VoiceTaskState, message: String) {
        store.state = state
        store.message = message
        VoiceTaskWidgetView.push(ctx, state, message = message)
    }

    companion object {
        private const val TAG = "VoiceTaskWorker"

        /**
         * Naht fuer den Test: sonst laeuft jeder Lauf in die echte Transkription und der Test
         * prueft nur noch, dass kein Zugang eingerichtet ist.
         */
        @VisibleForTesting
        var pipelineFactory: (Context, VoiceTaskStore) -> VoiceTaskPipeline = { ctx, store ->
            val prefs = Prefs(ctx)
            val bridge = AgentBridge(prefs.agentUrl, prefs.agentToken)
            VoiceTaskPipeline(
                samples = { store.loadSamples() },
                transcribe = { samples ->
                    store.refineSkipped = ""
                    TranscriptionEngine.transcribe(ctx, samples) { hinweis ->
                        // Nicht nur ins Log: sonst bekaeme der Nutzer stillschweigend Rohtext,
                        // obwohl "Glaetten" eingeschaltet ist, und hielte die Erkennung fuer schlecht.
                        Log.w(TAG, ctx.getString(R.string.refine_skipped, hinweis))
                        store.refineSkipped = hinweis
                    }
                },
                send = { text -> bridge.send(store.requestId, text, store.recordedAt, store.durationMs) },
            )
        }

        private fun pipeline(ctx: Context, store: VoiceTaskStore) = pipelineFactory(ctx, store)
    }
}
