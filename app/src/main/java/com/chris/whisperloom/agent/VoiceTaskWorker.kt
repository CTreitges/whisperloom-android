package com.chris.whisperloom.agent

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.TranscriptionEngine
import java.util.concurrent.TimeUnit

/** Einreihen des Auftrags. Getrennt vom Worker, damit Dienst und Trampolin dieselbe Stelle benutzen. */
object VoiceTaskWork {

    /**
     * EIN fester Name, und jede Einreihung ersetzt (REPLACE) den bisherigen Job. Das passt, weil
     * das Widget ohnehin nur einen Auftrag zur Zeit kennt: [VoiceTaskStore.begin] hat den alten
     * schon verworfen, und ein Tipp auf "Wird gesendet …" soll einen wartenden oder haengenden
     * Job wirklich abloesen. Frueher stand hier KEEP — damit blieb ein Job im Backoff einfach
     * liegen, und der Tipp tat nichts (#10). Ein abgeloester Worker sendet und schreibt nichts
     * mehr, dafuer sorgen [VoiceTaskPipeline] (stillCurrent) und die Eigentuemer-Wache im Worker.
     */
    const val UNIQUE_NAME = "whisperloom-voice-task"

    /** Danach ist Schluss mit Wiederholen: rund 10 s, 20 s, 40 s, 80 s, 160 s. */
    const val MAX_ATTEMPTS = 5

    const val BACKOFF_SECONDS = 10L

    /** Automatischer Weg nach der Aufnahme ([VoiceTaskService]): wartet auf Netz. */
    fun enqueue(ctx: Context) = enqueueImpl(ctx, ExistingWorkPolicy.REPLACE, request(manual = false))

    /**
     * Tipp auf dem Widget: sofort, OHNE Netz-Bedingung. Ein frischer Request ohne Bedingung und
     * ohne Verzoegerung startet der GreedyScheduler direkt im App-Prozess — Standby-Bucket,
     * Doze-Fenster und eine haengende Netz-Bedingung (VPN, unvalidiertes WLAN) spielen dann
     * keine Rolle. Ohne Netz scheitert er ehrlich und landet nach den Wiederholungen im Fehler.
     */
    fun sendNow(ctx: Context) = enqueueImpl(ctx, ExistingWorkPolicy.REPLACE, request(manual = true))

    /**
     * Was zu diesem Namen gerade passiert. Ohne diese Frage waere "arbeitet" eine Sackgasse:
     * stirbt der Prozess zwischen [VoiceTaskStore.begin] und [enqueue], gibt es nie einen Job;
     * wartet er im Backoff, laeuft er erst Minuten spaeter.
     */
    fun phase(ctx: Context): JobPhase = phaseImpl(ctx)

    /** Auftrag aufgeben (Einstellungen: "Offenen Auftrag verwerfen"). */
    fun cancel(ctx: Context) = cancelImpl(ctx)

    /**
     * Irgendein RUNNING gewinnt, ENQUEUED/BLOCKED heisst "wartet", alles Beendete zaehlt nicht.
     *
     * REPLACE loescht den alten Job samt WorkSpec (WorkManager 2.11: CancelWorkRunnable, danach
     * WorkSpecDao.delete) — er steht NICHT als CANCELLED daneben. Ein abgeloester Worker, der
     * noch rechnet, ist in getWorkInfos also unsichtbar; vor ihm schuetzen stillCurrent und die
     * Eigentuemer-Wache im Worker, nicht diese Phase.
     */
    internal fun phaseOf(states: Collection<WorkInfo.State>): JobPhase = when {
        states.any { it == WorkInfo.State.RUNNING } -> JobPhase.RUNNING
        states.any { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED } -> JobPhase.WAITING
        else -> JobPhase.NONE
    }

    /**
     * Bewusst NICHT expedited: der GreedyScheduler startet einen Request ohne Bedingung sofort
     * im Prozess, und Wiederholungen nach Backoff laufen ohnehin nie expedited. Expedited braeuchte
     * auf API < 31 ausserdem eine Foreground-Notification (getForegroundInfo).
     */
    @VisibleForTesting
    internal fun request(manual: Boolean): OneTimeWorkRequest {
        val builder = OneTimeWorkRequestBuilder<VoiceTaskWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
        if (!manual) {
            builder.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        }
        return builder.build()
    }

    /**
     * Naht fuer Dienst- und Trampolin-Tests: WorkManager laesst sich auf dem
     * Entwicklungsrechner (linux-aarch64) nicht starten, weil Robolectric dort kein SQLite hat.
     * Den echten Weg prueft VoiceTaskWorkManagerTest ueberall sonst (CI, Windows).
     * Das Log ist die Geraete-Diagnose zu #10: Versuch, naechster Termin, Stopp-Grund — ein per
     * REPLACE abgeloester Job taucht darin nicht mehr auf, er ist geloescht.
     */
    @VisibleForTesting
    var phaseImpl: (Context) -> JobPhase = { ctx ->
        val infos = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(UNIQUE_NAME).get()
        infos.forEach {
            Log.i(
                TAG,
                "Job ${it.state}: Versuch ${it.runAttemptCount}, naechster Lauf ${it.nextScheduleTimeMillis}, " +
                    "Stopp-Grund ${it.stopReason}",
            )
        }
        phaseOf(infos.map { it.state })
    }

    @VisibleForTesting
    var cancelImpl: (Context) -> Unit = { ctx ->
        WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_NAME)
    }

    /**
     * Nur das Einreichen. Policy und Request bauen [enqueue] und [sendNow] — so sehen Dienst-
     * und Trampolin-Test beide, und ein Rueckfall auf KEEP (#10) faellt dort auf.
     */
    @VisibleForTesting
    var enqueueImpl: (Context, ExistingWorkPolicy, OneTimeWorkRequest) -> Unit = { ctx, policy, request ->
        WorkManager.getInstance(ctx).beginUniqueWork(UNIQUE_NAME, policy, request).enqueue()
    }

    private const val TAG = "VoiceTaskWork"
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
        if (!store.hasWork) {
            // Nur ein stehengebliebenes "Wird gesendet …" richtigstellen (z. B. nach "Offenen
            // Auftrag verwerfen"). Eine laufende Aufnahme oder eine Fehlermeldung wie "Kein Ton"
            // bleibt stehen — resolve() wuerde sie ohne Auftrag zu "bereit" machen.
            if (store.state == VoiceTaskState.WORKING) VoiceTaskWidget.refresh(ctx)
            return Result.success()
        }

        // Dieser Lauf gehoert zu genau diesem Auftrag. Alles danach prueft, ob das noch stimmt.
        val id = store.requestId
        store.state = VoiceTaskState.WORKING
        store.message = ""
        store.attemptStartedAt = SystemClock.elapsedRealtime()
        VoiceTaskWidgetView.push(ctx, VoiceTaskState.WORKING)
        // Bereits erkannter Text wird NICHT neu transkribiert — das kostet beim Anbieter Geld.
        val cached = store.text.ifBlank { null }
        // Offline laesst sich die Erkennung nicht abbrechen: bis sie vorbei ist, ersetzt kein Tipp
        // diesen Lauf (siehe [VoiceTaskStore.offlineRecognition]).
        store.offlineRecognition = cached == null && Prefs(ctx).engine == Engine.OFFLINE
        val current = { !isStopped && store.requestId == id }

        val outcome = pipeline(ctx, store, current).run(cached) {
            // Nur solange der Lauf zustaendig ist, gehoert ihm der Merker — ein abgeloester Lauf
            // loeschte sonst den seines Nachfolgers.
            if (current()) store.offlineRecognition = false
        }

        // Eigentuemer-Wache: abgeloest (REPLACE), verworfen oder vom System gestoppt. Dann gehoeren
        // Store und Widget einem anderen — nichts ueberschreiben, vor allem kein clear(), das
        // einen neuen Auftrag loeschen wuerde. Das Result ignoriert WorkManager nach onStop ohnehin.
        if (isStopped || store.requestId != id) return abgeloest(store, id, outcome)

        return when (outcome) {
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
                    // Sichtbar machen, dass der Versuch scheiterte und ein Tipp sofort hilft —
                    // frueher stand hier stumm "Wird gesendet …" (#10).
                    store.state = VoiceTaskState.WORKING
                    store.message = outcome.reason
                    VoiceTaskWidgetView.push(ctx, VoiceTaskState.WORKING, message = outcome.reason)
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

            // Nach der Wache oben nicht mehr erreichbar, der Vollstaendigkeit halber gleich behandelt.
            is TaskOutcome.Superseded -> abgeloest(store, id, outcome)
        }
    }

    /**
     * Ein Lauf, der nicht mehr zustaendig ist, laesst Store und Widget in Ruhe. Einzige Ausnahme:
     * gehoert der Auftrag noch ihm (nur gestoppt, nicht ersetzt), wird der erkannte Text
     * gecacht — die Transkription ist bezahlt, der naechste Lauf soll sie nicht wiederholen.
     */
    private fun abgeloest(store: VoiceTaskStore, id: String, outcome: TaskOutcome): Result {
        if (store.requestId == id) recognized(outcome)?.let { store.text = it }
        Log.i(TAG, "Lauf nicht mehr zustaendig (gestoppt: $isStopped) — Store und Widget bleiben unberuehrt")
        return Result.success()
    }

    private fun recognized(outcome: TaskOutcome): String? = when (outcome) {
        is TaskOutcome.Sent -> outcome.text
        is TaskOutcome.Retry -> outcome.text
        is TaskOutcome.Failed -> outcome.text
        is TaskOutcome.Superseded -> outcome.text
    }?.ifBlank { null }

    /**
     * Zustand festhalten und zeichnen.
     *
     * "Gesendet" bleibt danach stehen, bis etwas anderes passiert — es wird NICHT nach zwei
     * Sekunden auf "bereit" zurueckgesetzt. Das war der erste Entwurf und ein Fehler: das
     * Warten haette in doWork stattfinden muessen, der Auftrag waere zwei Sekunden laenger
     * RUNNING geblieben, und ein in dieser Zeit aufgenommener neuer Auftrag waere (damals noch
     * mit ExistingWorkPolicy.KEEP) lautlos verworfen und anschliessend auch noch mit "bereit"
     * uebermalt worden. Stehenbleiben ist ehrlicher und kostet nichts:
     * ein Tipp auf "gesendet" startet wie auf "bereit" eine neue Aufnahme.
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
         *
         * Kennung, Zeitpunkt und Dauer werden beim Bau EINGEFROREN, nicht beim Senden gelesen:
         * ein abgeloester Lauf, der bis zu 600 s in der Erkennung haengt, saehe sonst schon die
         * Kennung eines NEUEN Auftrags und schickte alten Text darunter — die Bridge claimte die
         * Kennung, und der echte Auftrag ginge als Duplikat verloren. Der dritte Parameter ist
         * `stillCurrent` fuer [VoiceTaskPipeline].
         */
        @VisibleForTesting
        var pipelineFactory: (Context, VoiceTaskStore, () -> Boolean) -> VoiceTaskPipeline = { ctx, store, stillCurrent ->
            val prefs = Prefs(ctx)
            val bridge = AgentBridge(prefs.agentUrl, prefs.agentToken)
            val id = store.requestId
            val at = store.recordedAt
            val dur = store.durationMs
            VoiceTaskPipeline(
                samples = { store.loadSamples() },
                transcribe = { samples ->
                    if (store.requestId == id) store.refineSkipped = ""
                    TranscriptionEngine.transcribe(ctx, samples) { hinweis ->
                        // Nicht nur ins Log: sonst bekaeme der Nutzer stillschweigend Rohtext,
                        // obwohl "Glaetten" eingeschaltet ist, und hielte die Erkennung fuer schlecht.
                        Log.w(TAG, ctx.getString(R.string.refine_skipped, hinweis))
                        if (store.requestId == id) store.refineSkipped = hinweis
                    }
                },
                send = { text -> bridge.send(id, text, at, dur) },
                stillCurrent = stillCurrent,
            )
        }

        private fun pipeline(ctx: Context, store: VoiceTaskStore, stillCurrent: () -> Boolean) =
            pipelineFactory(ctx, store, stillCurrent)
    }
}
