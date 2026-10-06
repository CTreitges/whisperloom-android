package com.chris.whisperloom.llm

import android.app.ActivityManager
import android.content.Context
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.OfflineModel
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Lokales Textmodell fuer Tests (in der JVM gibt es kein liblitertlm_jni.so). Zeichnet Aufrufe auf;
 * nach [holdNext] blockiert die naechste [generate], bis der Test [proceed] ruft — so laesst sich
 * eine laufende Rechnung pruefen ([started] meldet ihren Beginn). [cancel] beendet sie wie
 * cancelProcess (LiteRT-LM wirft dann "CANCELLED"), und zwar ueber denselben [CancelSlot] wie das
 * echte Modell: nur, wenn der Auftrag der laufenden Rechnung abgebrochen ist.
 */
class FakeTextModel(
    val file: File,
    val cacheDir: File,
    private val answer: (system: String, user: String) -> String = { _, user -> user },
) : LocalTextModel {

    val calls = mutableListOf<Pair<String, String>>()
    @Volatile var cancels = 0
    var closes = 0
    @Volatile private var hold = false
    @Volatile var started = CountDownLatch(1)
        private set
    private val gate = CountDownLatch(1)
    @Volatile private var aborted = false
    private val slot = CancelSlot<Unit> {
        cancels++
        aborted = true
        if (!ignoresCancel) gate.countDown()
    }

    /** Haengt wie LiteRT-LM #2202: cancelProcess beendet die angehaltene Rechnung nicht, erst [proceed]. */
    @Volatile var ignoresCancel = false

    /** Laeuft in [generate] vor dem Eintragen der Rechnung — fuer einen Abbruch genau in dieser Luecke. */
    @Volatile var beforeEnter: () -> Unit = {}

    fun holdNext() {
        started = CountDownLatch(1)
        hold = true
    }

    override fun generate(system: String, user: String, cancelled: () -> Boolean): String {
        check(closes == 0) { "generate nach close" }
        aborted = false
        beforeEnter()
        slot.enter(Unit, cancelled)
        try {
            synchronized(calls) { calls += system to user }
            started.countDown()
            if (hold) check(gate.await(5, TimeUnit.SECONDS)) { "Test hat die Rechnung nicht freigegeben" }
            if (aborted) throw IllegalStateException("CANCELLED: Task cancelled")
            return answer(system, user)
        } finally {
            slot.leave()
        }
    }

    override fun cancel() = slot.cancel()

    override fun close() {
        closes++
    }

    fun proceed() = gate.countDown()
}

/** Haengt eine Fabrik mit [answer] ein und liefert die Liste der erzeugten Fakes (je Laden einer). */
fun fakeTextModels(answer: (String, String) -> String = { _, user -> user }): MutableList<FakeTextModel> {
    val made = mutableListOf<FakeTextModel>()
    LocalTextEngine.factory = { file, cache -> FakeTextModel(file, cache, answer).also { synchronized(made) { made += it } } }
    return made
}

/** Die Zeiten des Halters ab Werk — beim ersten Zugriff auf diese Datei festgehalten, vor jedem Test. */
private val defaultBudget = LocalTextEngine.budget
private val defaultHungGraceMs = LocalTextEngine.hungGraceMs
private val defaultLockWaitMs = LocalTextEngine.lockWaitMs
private val defaultWatchTickMs = LocalTextEngine.watchTickMs

/** Setzt den Halter nach einem Test zurueck: Modell frei, echte Fabrik, normale Zeiten. */
fun resetTextEngine(original: (File, File) -> LocalTextModel) {
    LocalTextEngine.release()
    LocalTextEngine.factory = original
    LocalTextEngine.idleReleaseMs = 2 * 60_000L
    LocalTextEngine.warmHoldMs = 10 * 60_000L
    LocalTextEngine.budget = defaultBudget
    LocalTextEngine.hungGraceMs = defaultHungGraceMs
    LocalTextEngine.lockWaitMs = defaultLockWaitMs
    LocalTextEngine.watchTickMs = defaultWatchTickMs
}

/** Sparse-Datei in Katalog-Groesse: fuer isInstalled zaehlen nur Laenge und fehlende .part. */
fun installSparse(ctx: Context, model: OfflineModel) {
    val store = ModelStore(ctx)
    store.ensureDir()
    RandomAccessFile(store.file(model), "rw").use { it.setLength(model.bytes) }
}

/** Geraete-RAM fuer [com.chris.whisperloom.whisper.OfflineSupport.fitsDevice]. */
fun deviceRam(ctx: Context, gib: Long) {
    val am = ctx.getSystemService(ActivityManager::class.java)
    shadowOf(am).setMemoryInfo(ActivityManager.MemoryInfo().apply { totalMem = gib shl 30 })
}
