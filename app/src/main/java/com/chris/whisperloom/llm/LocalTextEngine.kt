package com.chris.whisperloom.llm

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.OfflineSupport
import com.chris.whisperloom.whisper.TextModelCatalog
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Prozessweiter Zugriff auf das lokale Textmodell — Muster wie [com.chris.whisperloom.whisper.WhisperEngine]:
 * IME, schwebender Knopf, Widget-Auftrag und Share-Ansicht teilen sich DIESE Instanz, sonst laege
 * Gemma (E2B ~1,7 GB, E4B ~3,3 GB) doppelt im RAM.
 *
 * Ein Lock fuer alles, was das Modell anfasst: Laden, Generieren, Freigeben. So laeuft immer nur
 * eine Generierung, und [LocalTextModel.close] faellt nie in eine laufende (SIGSEGV, LiteRT-LM #3771).
 * Freigabe nach [IDLE_RELEASE_MS] Leerlauf, bei Speicherdruck ([release] aus onTrimMemory), beim
 * Loeschen des Modells und beim Wechsel auf ein anderes.
 */
object LocalTextEngine {

    private const val TAG = "LocalTextEngine"

    /** Leerlauf, nach dem das Modell den RAM wieder freigibt. */
    private const val IDLE_RELEASE_MS = 2 * 60_000L

    private val lock = ReentrantLock()

    @Volatile private var store: ModelStore? = null
    @Volatile private var model: LocalTextModel? = null
    private var loadedModelId: String? = null

    /** Vorwaermen und Leerlauf-Freigabe — ein Daemon-Thread, damit er den Prozess nie aufhaelt. */
    private val worker = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "loom-llm").apply { isDaemon = true }
    }
    private var idleRelease: ScheduledFuture<*>? = null

    /** Naht fuer Tests: in der JVM gibt es kein liblitertlm_jni.so. (Modelldatei, Cache-Ordner) -> geladenes Modell. */
    @VisibleForTesting
    internal var factory: (File, File) -> LocalTextModel = { file, cache -> LiteRtTextModel(file.path, cache.path) }

    @VisibleForTesting
    internal var idleReleaseMs = IDLE_RELEASE_MS

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
     * nicht erst hinter der Erkennung beginnen. Fehler landen nur im Log — die echte Generierung
     * versucht es erneut und meldet sie dann.
     */
    fun warmUp(modelId: String) {
        worker.execute {
            try {
                lock.withLock { ensureLoaded(modelId) }
            } catch (e: Throwable) {
                Log.w(TAG, "Vorwaermen fehlgeschlagen", e)
            }
            scheduleIdleRelease()
        }
    }

    /**
     * Blockierend — aus einem Hintergrund-Thread. Laedt [modelId] bei Bedarf und rechnet.
     *
     * @param cancelled Abbruch des Auftrags (Tipp "ohne KI"): nach dem Warten auf Lock und Laden
     *   wird dann gar nicht erst gerechnet; waehrend der Rechnung bricht [cancel] ab.
     * @throws IllegalStateException wenn das Modell fehlt.
     * @throws CancellationException wenn der Auftrag vor der Rechnung abgebrochen wurde.
     */
    fun generate(modelId: String, system: String, user: String, cancelled: () -> Boolean = { false }): String {
        try {
            return lock.withLock {
                val loaded = ensureLoaded(modelId)
                if (cancelled()) throw CancellationException("Textverbesserung abgebrochen")
                loaded.generate(system, user, cancelled)
            }
        } finally {
            scheduleIdleRelease()
        }
    }

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
            val loaded = model ?: return
            Log.i(TAG, "Gebe Textmodell $loadedModelId frei")
            model = null
            loadedModelId = null
            loaded.close()
        } finally {
            lock.unlock()
        }
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

    private fun scheduleIdleRelease() = synchronized(worker) {
        idleRelease?.cancel(false)
        idleRelease = worker.schedule({ release() }, idleReleaseMs, TimeUnit.MILLISECONDS)
    }
}
