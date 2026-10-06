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
 * eine laufende Rechnung pruefen ([started] meldet ihren Beginn). [cancel] beendet sie wie cancelProcess.
 */
class FakeTextModel(
    val file: File,
    val cacheDir: File,
    private val answer: (system: String, user: String) -> String = { _, user -> user },
) : LocalTextModel {

    val calls = mutableListOf<Pair<String, String>>()
    var cancels = 0
    var closes = 0
    @Volatile private var hold = false
    @Volatile var started = CountDownLatch(1)
        private set
    private val gate = CountDownLatch(1)

    fun holdNext() {
        started = CountDownLatch(1)
        hold = true
    }

    override fun generate(system: String, user: String): String {
        check(closes == 0) { "generate nach close" }
        synchronized(calls) { calls += system to user }
        started.countDown()
        if (hold) check(gate.await(5, TimeUnit.SECONDS)) { "Test hat die Rechnung nicht freigegeben" }
        return answer(system, user)
    }

    override fun cancel() {
        cancels++
        gate.countDown()
    }

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

/** Setzt den Halter nach einem Test zurueck: Modell frei, echte Fabrik, normale Leerlauf-Zeit. */
fun resetTextEngine(original: (File, File) -> LocalTextModel) {
    LocalTextEngine.release()
    LocalTextEngine.factory = original
    LocalTextEngine.idleReleaseMs = 2 * 60_000L
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
