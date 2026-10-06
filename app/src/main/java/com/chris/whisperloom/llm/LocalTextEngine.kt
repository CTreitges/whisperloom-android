package com.chris.whisperloom.llm

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.chris.whisperloom.api.RefinePrompt
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.OfflineSupport
import com.chris.whisperloom.whisper.TextModelCatalog
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Prozessweiter Zugriff auf das lokale Textmodell — Muster wie [com.chris.whisperloom.whisper.WhisperEngine]:
 * IME, schwebender Knopf, Widget-Auftrag und Share-Ansicht teilen sich DIESE Instanz, sonst laege
 * Gemma (E2B ~1,7 GB, E4B ~3,3 GB) doppelt im RAM.
 *
 * Ein Lock fuer alles, was das Modell anfasst: Laden, Generieren, Freigeben. So laeuft immer nur
 * eine Generierung, und [LocalTextModel.close] faellt nie in eine laufende (SIGSEGV, LiteRT-LM #3771).
 * Freigabe nach [IDLE_RELEASE_MS] Leerlauf, bei Speicherdruck ([release] aus onTrimMemory), beim
 * Loeschen des Modells und beim Wechsel auf ein anderes.
 *
 * Nichts wartet ewig (Review c1, Spec §0.4): auf den Lock nur [LOCK_WAIT_MS], eine Rechnung nur
 * ihr Zeitbudget ([budgetMs]) — danach bricht ein Waechter sie ab. Kehrt sie auch dann nicht
 * zurueck (LiteRT-LM #2202, #2799), gilt das Modell als haengend: Folgeauftraege scheitern sofort
 * (Text ohne KI), bis die Rechnung doch zurueckkehrt und das Modell neu geladen wird.
 */
object LocalTextEngine {

    private const val TAG = "LocalTextEngine"

    /** Leerlauf, nach dem das Modell den RAM wieder freigibt. */
    private const val IDLE_RELEASE_MS = 2 * 60_000L

    /**
     * So lange haelt das Vorwaermen das Modell fuer die anstehende Verbesserung: Aufnahme und
     * Erkennung koennen laenger dauern als [IDLE_RELEASE_MS] (Review c5). Erst die Rechnung stellt
     * die Uhr wieder auf den normalen Leerlauf.
     */
    private const val WARM_HOLD_MS = 10 * 60_000L

    /**
     * Zeitbudget einer Rechnung: Grundzeit plus je Wort, gedeckelt. Grosszuegig — der Smoketest
     * brauchte 4–8,5 s fuer kurze Diktate auf 4 Server-Kernen, Mittelklasse-Handys und E4B sind
     * deutlich langsamer.
     */
    private const val BUDGET_BASE_MS = 45_000L
    private const val BUDGET_PER_WORD_MS = 600L
    private const val BUDGET_MAX_MS = 10 * 60_000L

    /**
     * So lange darf eine Rechnung nach cancelProcess noch brauchen, bevor das Modell als haengend
     * gilt. Das Prefill laesst sich nicht abbrechen (Smoketest: der Abbruch wirkt erst danach).
     */
    private const val HUNG_GRACE_MS = 60_000L

    /** Laenger braucht ein gesunder Auftrag vor einem nie: Budget plus Gnadenfrist. */
    private const val LOCK_WAIT_MS = BUDGET_MAX_MS + HUNG_GRACE_MS

    /** Takt des Waechters und der Lock-Wartezeit. */
    private const val WATCH_TICK_MS = 1_000L

    /** Folgeauftraege bei haengendem Modell — beim Nutzer als "Lokales Textmodell fehlgeschlagen". */
    private const val MSG_HUNG = "Lokales Textmodell haengt"

    private val lock = ReentrantLock()

    /** Eine Rechnung kehrt nach dem Abbruch nicht zurueck: Folgeauftraege scheitern sofort. Nur der Waechter setzt es. */
    @Volatile private var hung = false

    @Volatile private var store: ModelStore? = null
    @Volatile private var model: LocalTextModel? = null
    private var loadedModelId: String? = null

    /** Vorwaermen und Leerlauf-Freigabe — ein Daemon-Thread, damit er den Prozess nie aufhaelt. */
    private val worker = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "loom-llm").apply { isDaemon = true }
    }
    private var idleRelease: ScheduledFuture<*>? = null

    /** Waechter der laufenden Rechnung — eigener Thread: [worker] kann im Vorwaermen am Lock warten. */
    private val watchdog = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "loom-llm-watch").apply { isDaemon = true }
    }

    /** Naht fuer Tests: in der JVM gibt es kein liblitertlm_jni.so. (Modelldatei, Cache-Ordner) -> geladenes Modell. */
    @VisibleForTesting
    internal var factory: (File, File) -> LocalTextModel = { file, cache -> LiteRtTextModel(file.path, cache.path) }

    @VisibleForTesting
    internal var idleReleaseMs = IDLE_RELEASE_MS

    @VisibleForTesting
    internal var warmHoldMs = WARM_HOLD_MS

    /** Naht fuer Tests: Zeitbudget je Auftrag (Eingabe -> ms). */
    @VisibleForTesting
    internal var budget: (user: String) -> Long = { budgetMs(RefinePrompt.wordCount(it)) }

    @VisibleForTesting
    internal var hungGraceMs = HUNG_GRACE_MS

    @VisibleForTesting
    internal var lockWaitMs = LOCK_WAIT_MS

    @VisibleForTesting
    internal var watchTickMs = WATCH_TICK_MS

    /**
     * Beim App-Start und als Sicherheitsnetz aus [isReady]. Setzt immer neu (nur ein File-Wrapper):
     * unter Robolectric hat jeder Test ein eigenes filesDir.
     */
    fun init(context: Context) {
        store = ModelStore(context.applicationContext)
    }

    /** Liegt gerade ein Textmodell im Speicher? */
    val isLoaded: Boolean get() = model != null

    /**
     * "Lokales Modell bereit" der Entscheidungstabelle: [modelId] ist ein Textmodell, liegt
     * vollstaendig im Modellordner (exakte Groesse, keine Teildatei) und passt in den RAM.
     */
    fun isReady(context: Context, modelId: String): Boolean {
        init(context)
        val entry = TextModelCatalog.find(modelId) ?: return false
        return store?.isInstalled(entry) == true && OfflineSupport.fitsDevice(context, entry)
    }

    /**
     * Laden anstossen, ohne zu warten (Aufnahmestart): Init dauert Sekunden bis gut 30 s und soll
     * nicht erst hinter der Erkennung beginnen. Das Modell bleibt dann bis zur Verbesserung geladen
     * (hoechstens [WARM_HOLD_MS]). Fehler landen nur im Log — die echte Generierung versucht es
     * erneut und meldet sie dann.
     */
    fun warmUp(modelId: String) {
        worker.execute {
            try {
                locked(warmHoldMs) { ensureLoaded(modelId) }
            } catch (e: Throwable) {
                Log.w(TAG, "Vorwaermen fehlgeschlagen", e)
            }
        }
    }

    /**
     * Blockierend — aus einem Hintergrund-Thread. Laedt [modelId] bei Bedarf und rechnet.
     *
     * @param cancelled Abbruch des Auftrags (Tipp "ohne KI", Widget-Auftrag abgeloest): beim Warten
     *   auf den Lock und nach dem Laden wird dann gar nicht erst gerechnet; waehrend der Rechnung
     *   bricht [cancel] bzw. spaetestens der Waechter ab.
     * @throws IllegalStateException wenn das Modell fehlt, haengt oder der Lock nicht frei wird.
     * @throws CancellationException wenn der Auftrag vor der Rechnung abgebrochen wurde.
     */
    fun generate(modelId: String, system: String, user: String, cancelled: () -> Boolean = { false }): String {
        if (hung) throw IllegalStateException(MSG_HUNG)
        return locked(idleReleaseMs, cancelled) {
            val loaded = ensureLoaded(modelId)
            if (cancelled()) throw CancellationException("Textverbesserung abgebrochen")
            val watch = Watch(loaded, budget(user), cancelled)
            try {
                loaded.generate(system, user, watch.stop)
            } finally {
                watch.close()
            }
        }
    }

    /** Zeitbudget einer Rechnung fuer ein Diktat mit [words] Woertern. */
    internal fun budgetMs(words: Int): Long = minOf(BUDGET_BASE_MS + BUDGET_PER_WORD_MS * words, BUDGET_MAX_MS)

    /**
     * Bricht die laufende Generierung ab, wenn ihr Auftrag abgebrochen ist (seine `cancelled`-Frage
     * sagt true). Das Modell prueft das im selben Schritt wie den Abbruch ([CancelSlot]): so trifft
     * der Tipp "ohne KI" in der Tastatur nie die Rechnung eines anderen Auftrags (Widget, Share)
     * und geht auch kurz vor dem Rechenstart nicht verloren. Nur cancelProcess — nie close mitten
     * in der Rechnung.
     */
    fun cancel() {
        model?.cancel()
    }

    /**
     * Modell aus dem Speicher werfen (Leerlauf, onTrimMemory, Modell geloescht). Kehrt sofort
     * zurueck: wird gerade geladen oder gerechnet, bleibt das Modell — der naechste Anlass versucht
     * es erneut (nach jeder Generierung laeuft die Leerlauf-Uhr neu an).
     */
    fun release() {
        if (!lock.tryLock()) return
        try {
            // Frei ist frei: eine noch ausstehende Leerlauf-Freigabe (Vorwaermen: bis 10 min) entfaellt.
            synchronized(worker) { idleRelease?.cancel(false) }
            val loaded = model ?: return
            Log.i(TAG, "Gebe Textmodell $loadedModelId frei")
            model = null
            loadedModelId = null
            loaded.close()
        } finally {
            lock.unlock()
        }
    }

    /**
     * [block] unter dem Lock, aber ohne ewig darauf zu warten: aufgeben, sobald das Modell haengt,
     * der Auftrag abgebrochen ist oder [lockWaitMs] um sind. Danach laeuft die Leerlauf-Uhr mit
     * [idleMs] neu an — noch unter dem Lock, damit ein spaeteres Vorwaermen sie ueberschreibt, nie umgekehrt.
     */
    private fun <T> locked(idleMs: Long, cancelled: () -> Boolean = { false }, block: () -> T): T {
        val until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(lockWaitMs)
        while (!lock.tryLock(watchTickMs, TimeUnit.MILLISECONDS)) {
            if (hung) throw IllegalStateException(MSG_HUNG)
            if (cancelled()) throw CancellationException("Textverbesserung abgebrochen")
            if (System.nanoTime() > until) throw IllegalStateException("Lokales Textmodell belegt")
        }
        try {
            return block()
        } finally {
            if (hung) unloadHung()
            scheduleIdleRelease(idleMs)
            lock.unlock()
        }
    }

    /**
     * Waechter einer Rechnung: fragt im Takt [watchTickMs] den Abbruch des Auftrags und das
     * Zeitbudget ab. Ist eins erreicht, bricht er per cancelProcess ab — in jedem Takt erneut, denn
     * ein Abbruch kurz vor dem Start der nativen Rechnung verpufft (Smoketest). Kehrt sie danach
     * [hungGraceMs] nicht zurueck, setzt er [hung].
     */
    private class Watch(private val model: LocalTextModel, budgetMs: Long, private val cancelled: () -> Boolean) {
        private val start = System.nanoTime()
        private val budgetNs = TimeUnit.MILLISECONDS.toNanos(budgetMs)
        @Volatile private var overdue = false
        private var stoppedAt = 0L
        private var done = false

        /** Abbruch-Frage dieser Rechnung: Auftrag abgebrochen oder Zeitbudget aufgebraucht. */
        val stop: () -> Boolean = { overdue || cancelled() }

        private val tick = watchdog.scheduleWithFixedDelay(::check, watchTickMs, watchTickMs, TimeUnit.MILLISECONDS)

        @Synchronized
        private fun check() {
            if (done) return
            val now = System.nanoTime()
            if (now - start > budgetNs && !overdue) {
                Log.w(TAG, "Zeitbudget der lokalen Rechnung aufgebraucht — breche ab")
                overdue = true
            }
            if (!stop()) return
            if (stoppedAt == 0L) {
                stoppedAt = now
            } else if (!hung && now - stoppedAt > TimeUnit.MILLISECONDS.toNanos(hungGraceMs)) {
                Log.e(TAG, "Lokale Rechnung kehrt nach dem Abbruch nicht zurueck — Textmodell gilt als haengend")
                hung = true
            }
            model.cancel()
        }

        /** Nach der Rechnung: ab jetzt setzt der Waechter nichts mehr (auch kein [hung]). */
        @Synchronized
        fun close() {
            done = true
            tick.cancel(false)
        }
    }

    /** Nur unter [lock]: die haengende Rechnung ist doch zurueckgekehrt — das Modell neu aufbauen (litertlm.md §6.7). */
    private fun unloadHung() {
        Log.w(TAG, "Baue haengendes Textmodell $loadedModelId neu auf")
        model?.close()
        model = null
        loadedModelId = null
        hung = false
    }

    /** Nur unter [lock]. */
    private fun ensureLoaded(modelId: String): LocalTextModel {
        val store = store ?: throw IllegalStateException("Textmodell fehlt")
        val entry = TextModelCatalog.find(modelId) ?: throw IllegalStateException("Textmodell fehlt")
        if (!store.isInstalled(entry)) throw IllegalStateException("Textmodell fehlt")
        model?.let { if (loadedModelId == modelId) return it }
        // Erst freigeben, dann laden: sonst laegen kurz beide Modelle im RAM.
        model?.close()
        model = null
        loadedModelId = null
        Log.i(TAG, "Lade Textmodell ${entry.id} (${entry.fileName})")
        val cache = store.cacheDir(entry).apply { mkdirs() }
        val loaded = factory(store.file(entry), cache)
        model = loaded
        loadedModelId = modelId
        return loaded
    }

    private fun scheduleIdleRelease(delayMs: Long) = synchronized(worker) {
        idleRelease?.cancel(false)
        idleRelease = worker.schedule({ release() }, delayMs, TimeUnit.MILLISECONDS)
    }
}
