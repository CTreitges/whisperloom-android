package com.chris.whisperloom

import android.Manifest
import android.app.Application
import android.media.AudioRecord
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioRecord
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/**
 * Pause und Weiter im [AudioRecorder] (Plan §1.3, Ansatz C): Die Pause gibt das Mikrofon frei,
 * das Aufgenommene bleibt, Weiter haengt mit einer NEUEN AudioRecord-Instanz an.
 *
 * Jede Instanz bekommt ihre eigene [Quelle] — so laesst sich unterscheiden, ob nach der Pause
 * noch die alte Instanz gelesen wurde. Die Quellen liefern nur eine feste Menge und danach
 * "gerade nichts da" (0): das Schatten-Mikrofon liefert sonst so schnell, wie die CPU kann.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioRecorderTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val recorder = AudioRecorder()

    /** Quellen in der Reihenfolge, in der die Aufnahme neue Instanzen anlegt. */
    private val quellen = ArrayDeque<Quelle>()
    private val zugeordnet = IdentityHashMap<AudioRecord, Quelle>()

    @Before fun aufbau() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        // Der Anbieter wird bei JEDEM Lesen gefragt — also je Instanz merken.
        ShadowAudioRecord.setSourceProvider { record ->
            synchronized(zugeordnet) { zugeordnet.getOrPut(record) { quellen.removeFirst() } }
        }
    }

    @After fun abbau() {
        recorder.cancel()
        ShadowAudioRecord.clearSource()
    }

    /**
     * Liefert [samples] Werte mit [pegel], danach 0. Wird [danach] gesetzt, liefert sie ab dann
     * Ton mit diesem Pegel — der darf nicht ankommen, wenn die Instanz nicht mehr gelesen wird.
     */
    private class Quelle(samples: Int, private val pegel: Short) : ShadowAudioRecord.AudioRecordSource {
        private var rest = samples
        val ausgelesen = CountDownLatch(1)
        @Volatile var danach: Short? = null
        val spaeterGelesen = AtomicInteger()

        @Synchronized
        override fun readInShortArray(data: ShortArray, offset: Int, size: Int, blocking: Boolean): Int {
            if (rest > 0) {
                val n = minOf(size, rest)
                fill(data, offset, n, pegel)
                rest -= n
                return n
            }
            // Erst beim Lesen NACH dem letzten Stueck: dann hat der Aufnahme-Thread alles geschrieben.
            ausgelesen.countDown()
            val spaeter = danach ?: return 0
            spaeterGelesen.incrementAndGet()
            fill(data, offset, size, spaeter)
            return size
        }

        private fun fill(data: ShortArray, offset: Int, n: Int, p: Short) {
            for (i in 0 until n) data[offset + i] = if (i % 2 == 0) p else (-p).toShort()
        }

        fun abwarten() = assertTrue("Quelle nicht ausgelesen", ausgelesen.await(5, TimeUnit.SECONDS))
    }

    private fun quelle(samples: Int, pegel: Short) = Quelle(samples, pegel).also { quellen.addLast(it) }

    private fun pegel(sample: Float) = Math.round(abs(sample) * 32768f)

    @Test fun pauseNimmtNichtsAufUndWeiterHaengtLueckenlosAn() {
        val teilA = quelle(16_000, 1000)
        val teilB = quelle(8_000, 3000)

        assertTrue(recorder.start())
        teilA.abwarten()
        recorder.pause()
        assertTrue(recorder.isPaused)
        assertFalse("In der Pause nimmt nichts auf", recorder.isRecording)
        assertTrue("Die Pause ist noch eine offene Aufnahme", recorder.hasSession)
        // Ab jetzt haette die alte Instanz Ton — wer in der Pause weiterliest, saehe ihn.
        teilA.danach = 2000

        assertTrue(recorder.resume())
        assertTrue(recorder.isRecording)
        assertFalse(recorder.isPaused)
        teilB.abwarten()
        val samples = recorder.stop()

        assertEquals("Die alte Instanz wurde nach der Pause noch gelesen", 0, teilA.spaeterGelesen.get())
        assertEquals("A und B lueckenlos, sonst nichts", 24_000, samples.size)
        assertTrue((0 until 16_000).all { pegel(samples[it]) == 1000 })
        assertTrue((16_000 until 24_000).all { pegel(samples[it]) == 3000 })
    }

    @Test fun dieAufnahmezeitZaehltNurAufgenommenesUeberAllePausen() {
        val teilA = quelle(16_000, 1000)
        val teilB = quelle(8_000, 1000)
        recorder.start()
        teilA.abwarten()
        recorder.pause()
        assertEquals(1000L, recorder.recordedMs)
        recorder.resume()
        teilB.abwarten()
        assertEquals(1500L, recorder.recordedMs)
    }

    @Test fun stopAusDerPauseLiefertAllesAufgenommene() {
        // Regression: stop() lieferte ohne laufende Aufnahme ein leeres Array — aus der Pause
        // gesendet kaeme sonst nichts an.
        val teil = quelle(16_000, 1000)
        recorder.start()
        teil.abwarten()
        recorder.pause()
        val samples = recorder.stop()
        assertEquals(16_000, samples.size)
        assertFalse(recorder.hasSession)
        assertFalse(recorder.isPaused)
    }

    @Test fun cancelAusDerPauseVerwirftAlles() {
        val teil = quelle(16_000, 1000)
        recorder.start()
        teil.abwarten()
        recorder.pause()
        recorder.cancel()
        assertFalse(recorder.hasSession)
        assertFalse(recorder.isPaused)
        assertEquals(0L, recorder.recordedMs)
        assertEquals("Nach dem Verwerfen gibt es nichts zu senden", 0, recorder.stop().size)
    }

    @Test fun einNeuerStartNachDerPauseBeginntLeer() {
        val alt = quelle(16_000, 1000)
        val neu = quelle(8_000, 3000)
        recorder.start()
        alt.abwarten()
        recorder.pause()
        recorder.cancel()
        recorder.start()
        neu.abwarten()
        val samples = recorder.stop()
        assertEquals(8_000, samples.size)
        assertTrue(samples.all { pegel(it) == 3000 })
    }

    @Test fun pauseUndWeiterOhneAufnahmeTunNichts() {
        recorder.pause()
        assertFalse(recorder.isPaused)
        assertFalse(recorder.hasSession)
        assertFalse("Weiter ohne Pause startet keine Aufnahme", recorder.resume())
        assertFalse(recorder.isRecording)
    }

    /** Nach dem Senden haelt der Recorder das Audio nicht weiter fest (bei 20 min offline sind das 38 MB). */
    @Test fun stopGibtDenPufferFrei() {
        val teil = quelle(16_000, 1000)
        recorder.start()
        teil.abwarten()
        assertEquals(16_000, recorder.stop().size)
        assertEquals(0L, recorder.recordedMs)
        assertEquals("ein zweites stop() liefert nichts mehr", 0, recorder.stop().size)
    }

    /**
     * Ein Error im Lese-Thread (OutOfMemoryError beim Wachsen des Puffers) beendet die Aufnahme
     * sauber, statt den Tastatur-Prozess zu beenden: das Mikrofon ist frei, das bis dahin
     * Aufgenommene laesst sich noch senden. Hier wirft der Pegel-Rueckruf aus demselben Lesevorgang.
     */
    @Test fun einErrorImLeseThreadPausiertStattZuAbstuerzen() {
        quelle(16_000, 1000)
        val uncaught = AtomicInteger()
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, _ -> uncaught.incrementAndGet() }
        try {
            recorder.onAmplitude = { throw OutOfMemoryError("Test") }
            recorder.start()
            val bis = System.currentTimeMillis() + 5_000
            while (recorder.isRecording && System.currentTimeMillis() < bis) Thread.sleep(10)

            assertFalse("Aufnahme endet", recorder.isRecording)
            assertTrue("das Aufgenommene bleibt", recorder.hasSession)
            assertTrue(recorder.stop().isNotEmpty())
            assertEquals("nicht bis zum Prozess durchgereicht", 0, uncaught.get())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }
}
