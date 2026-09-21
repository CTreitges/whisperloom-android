package com.chris.whisperloom.agent

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioRecord
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Der Zustandsautomat des Aufnahme-Dienstes. Das Mikrofon liefert Robolectrics
 * [ShadowAudioRecord] — nur so laesst sich der Unterschied zwischen "Ton da" und
 * "lautloser Fehlschlag" ueberhaupt pruefen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceTaskServiceTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var store: VoiceTaskStore
    private var eingereiht = 0
    private val echterEnqueue = VoiceTaskWork.enqueueImpl
    private var controller: ServiceController<VoiceTaskService>? = null
    private lateinit var gelesen: CountDownLatch

    @Before fun aufbauen() {
        app.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        store = VoiceTaskStore(app)
        store.clear()
        Prefs(app).apply {
            agentEnabled = true
            agentUrl = "https://bridge.example.de"
            agentToken = "geheim"
        }
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        VoiceTaskWork.enqueueImpl = { eingereiht++ }
    }

    @After fun abbauen() {
        VoiceTaskWork.enqueueImpl = echterEnqueue
        ShadowAudioRecord.clearSource()
        controller?.destroy()
    }

    /**
     * Mikrofon, das durchgehend liefert — [amplitude] 0 ist das stummgeschaltete.
     *
     * Der Riegel ist noetig, weil [com.chris.whisperloom.AudioRecorder] in einem ECHTEN Thread
     * liest, der Test aber nur die Schattenuhr vorschiebt: ohne Warten kann STOP kommen, bevor
     * der Thread ein einziges Mal gelesen hat. Dann waere die Aufnahme leer — und der Test
     * pruefte nicht mehr, was er soll. (Genau so ist er einmal auf der CI umgefallen.)
     */
    private fun quelle(amplitude: Short) {
        gelesen = CountDownLatch(1)
        ShadowAudioRecord.setSource(object : ShadowAudioRecord.AudioRecordSource {
            override fun readInShortArray(data: ShortArray, offset: Int, size: Int, blocking: Boolean): Int {
                for (i in 0 until size) data[offset + i] = if (i % 2 == 0) amplitude else (-amplitude).toShort()
                gelesen.countDown()
                return size
            }
        })
    }

    private fun tonQuelle() = quelle(8000)

    private fun stilleQuelle() = quelle(0)

    /** START und warten, bis das Mikrofon wirklich gelesen wurde. */
    private fun aufnehmen(service: VoiceTaskService) {
        senden(service, VoiceTaskService.ACTION_START)
        assertTrue("Der Aufnahme-Thread hat nichts gelesen", gelesen.await(5, TimeUnit.SECONDS))
    }

    private fun dienst(): VoiceTaskService {
        val c = Robolectric.buildService(VoiceTaskService::class.java).create()
        controller = c
        return c.get()
    }

    private fun senden(service: VoiceTaskService, action: String) {
        service.onStartCommand(Intent(app, VoiceTaskService::class.java).setAction(action), 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
    }

    // --- Berechtigung --------------------------------------------------------

    @Test fun ohneMikrofonBerechtigungWirdNichtAufgenommen() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        senden(dienst(), VoiceTaskService.ACTION_START)
        assertEquals(VoiceTaskState.ERROR, store.state)
        assertEquals(app.getString(R.string.widget_no_mic), store.message)
        assertEquals(0, eingereiht)
    }

    // --- Aufnehmen -----------------------------------------------------------

    @Test fun startBeginntDieAufnahme() {
        tonQuelle()
        aufnehmen(dienst())
        assertEquals(VoiceTaskState.RECORDING, store.state)
    }

    @Test fun einZweitesStartIstEinDoppelklickUndAendertNichts() {
        tonQuelle()
        val s = dienst()
        aufnehmen(s)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(3))
        senden(s, VoiceTaskService.ACTION_START)
        assertEquals("START ist eine Absicht, kein Umschalter", VoiceTaskState.RECORDING, store.state)

        // Und die erste Aufnahme laeuft weiter: STOP liefert die vollen drei Sekunden.
        senden(s, VoiceTaskService.ACTION_STOP)
        assertEquals(VoiceTaskState.WORKING, store.state)
        assertTrue("Dauer ${store.durationMs}", store.durationMs >= 3_000)
    }

    @Test fun stopOhneAufnahmeWirdVerworfenNichtAlsFehlerGezeigt() {
        senden(dienst(), VoiceTaskService.ACTION_STOP)
        assertEquals(VoiceTaskState.READY, store.state)
        assertEquals("", store.message)
        assertFalse(store.hasWork)
        assertEquals(0, eingereiht)
    }

    @Test fun unbekannteAktionTutNichts() {
        senden(dienst(), "com.chris.whisperloom.agent.QUATSCH")
        assertEquals(VoiceTaskState.READY, store.state)
        assertEquals(0, eingereiht)
    }

    // --- Beenden -------------------------------------------------------------

    @Test fun eineAufnahmeMitTonWirdZumAuftrag() {
        tonQuelle()
        val s = dienst()
        aufnehmen(s)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4))
        senden(s, VoiceTaskService.ACTION_STOP)

        assertEquals(VoiceTaskState.WORKING, store.state)
        assertTrue("Der Auftrag muss auf der Platte liegen", store.hasWork)
        assertTrue(store.requestId.isNotEmpty())
        assertTrue("Aufnahmezeitpunkt fehlt", store.recordedAt.isNotEmpty())
        assertEquals("Genau einmal einreihen", 1, eingereiht)
    }

    @Test fun eineStilleAufnahmeGiltAlsFehlschlagNichtAlsAuftrag() {
        // Kein Ton, aber lange genug: genau der lautlose Entzug des Mikrofons ab Android 14.
        // Mit dem Riegel heisst "still" wirklich "Nullen gelesen" und nicht "nichts gelesen".
        stilleQuelle()
        val s = dienst()
        aufnehmen(s)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4))
        senden(s, VoiceTaskService.ACTION_STOP)

        assertEquals(VoiceTaskState.ERROR, store.state)
        assertEquals(app.getString(R.string.widget_silent), store.message)
        assertFalse("Ein tonloser Auftrag darf nicht abgeschickt werden", store.hasWork)
        assertEquals(0, eingereiht)
    }

    @Test fun einFehlgriffIstZuKurzUndKeinAuftrag() {
        tonQuelle()
        val s = dienst()
        aufnehmen(s)
        // Uhr NICHT vorschieben: die Aufnahme hat Ton, ist aber zu kurz.
        senden(s, VoiceTaskService.ACTION_STOP)

        assertEquals(VoiceTaskState.ERROR, store.state)
        assertEquals(app.getString(R.string.widget_too_short), store.message)
        assertEquals(0, eingereiht)
    }

    @Test fun einNeuerAuftragLoeschtDieAlteFehlermeldung() {
        tonQuelle()
        store.state = VoiceTaskState.ERROR
        store.message = "alter Fehler"
        val s = dienst()
        aufnehmen(s)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4))
        senden(s, VoiceTaskService.ACTION_STOP)
        assertEquals("", store.message)
    }
}
