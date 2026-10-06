package com.chris.whisperloom.llm

import android.Manifest
import android.content.Context
import android.os.Looper
import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.TranscriptResult
import com.chris.whisperloom.TranscriptionBackend
import com.chris.whisperloom.TranscriptionEngine
import com.chris.whisperloom.api.WavUpload
import com.chris.whisperloom.whisper.ModelCatalog
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.TextModelCatalog
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAudioRecord
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Offline-Diktat mit lokaler Textverbesserung fuer Dienst-Tests (Tastatur, schwebender Knopf):
 * whisper-Modell und Gemma als Sparse-Dateien, Erkennung als Fake ("also ähm hallo welt"), das
 * Textmodell als [FakeTextModel], ein Mikrofon mit Deckel. Regel "lokal", Stufe "Glaetten".
 */
class OfflineRefineFixture(private val app: Context) {

    private val originalBackend = TranscriptionEngine.backendFactory
    private val originalModel = LocalTextEngine.factory
    lateinit var made: MutableList<FakeTextModel>
        private set

    /** Faellt, sobald das Mikrofon alle Puffer geliefert hat — genug Ton fuer ein Diktat. */
    lateinit var recorded: CountDownLatch
        private set

    fun setUp() {
        app.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        shadowOf(app as android.app.Application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        Prefs(app).apply {
            engine = Engine.OFFLINE
            offlineRefine = OfflineRefineRule.LOCAL
            refineMode = RefineMode.POLISH
            language = "de"
        }
        installSparse(app, ModelCatalog.SMALL)
        installSparse(app, TextModelCatalog.GEMMA4_E2B)
        deviceRam(app, 8)
        TranscriptionEngine.backendFactory = { _, _ ->
            object : TranscriptionBackend {
                override val label = "Offline · Small"
                override fun transcribe(upload: WavUpload, language: String) = TranscriptResult("also ähm hallo welt")
            }
        }
        LocalTextEngine.init(app)
        made = fakeTextModels { _, _ -> "Lokal verbessert." }
        microphone()
    }

    fun tearDown() {
        made.forEach { it.proceed() }
        TranscriptionEngine.backendFactory = originalBackend
        resetTextEngine(originalModel)
        ShadowAudioRecord.clearSource()
        ModelStore(app).dir.deleteRecursively()
    }

    /**
     * Rund eine Sekunde Ton, danach "gerade nichts da" (0): das Schatten-Mikrofon liefert so schnell
     * wie die CPU — ohne Deckel liefe der Aufnahme-Thread in den OutOfMemoryError (wie VoiceTaskServiceTest).
     */
    private fun microphone() {
        recorded = CountDownLatch(1)
        val reads = AtomicInteger()
        ShadowAudioRecord.setSource(object : ShadowAudioRecord.AudioRecordSource {
            override fun readInShortArray(data: ShortArray, offset: Int, size: Int, blocking: Boolean): Int {
                val n = minOf(size, SAMPLES_PER_READ)
                if (reads.getAndIncrement() >= READS) {
                    recorded.countDown()
                    return 0
                }
                for (i in 0 until n) data[offset + i] = if (i % 2 == 0) 8000 else -8000
                return n
            }
        })
    }

    /**
     * Wartet, bis das Vorwaermen beim Aufnahmestart das Textmodell geladen hat, und haelt dessen
     * naechste Rechnung an — so steht die Uebertragung nachher pruefbar in der Textverbesserung.
     */
    fun holdWarmedModel(): FakeTextModel {
        waitFor("Aufnahmestart hat das Textmodell nicht vorgewaermt") { LocalTextEngine.isLoaded && made.isNotEmpty() }
        return made.single().also { it.holdNext() }
    }

    fun awaitRecorded() = assertTrue("Mikrofon nicht ausgelesen", recorded.await(5, TimeUnit.SECONDS))

    /** Main-Looper abarbeiten, bis [condition] gilt (der io-Thread postet zurueck). */
    fun waitFor(what: String, condition: () -> Boolean) {
        val until = System.currentTimeMillis() + 5_000
        while (true) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            if (System.currentTimeMillis() > until) fail(what)
            Thread.sleep(10)
        }
    }

    private companion object {
        const val READS = 40
        const val SAMPLES_PER_READ = 400
    }
}
