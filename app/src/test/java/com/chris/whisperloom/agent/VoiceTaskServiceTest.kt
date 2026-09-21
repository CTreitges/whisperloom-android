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

    /** Mikrofon, das durchgehend einen Ton liefert. */
    private fun tonQuelle(amplitude: Short = 8000) {
        ShadowAudioRecord.setSource(object : ShadowAudioRecord.AudioRecordSource {
            override fun readInShortArray(data: ShortArray, offset: Int, size: Int, blocking: Boolean): Int {
                for (i in 0 until size) data[offset + i] = if (i % 2 == 0) amplitude else (-amplitude).toShort()
                return size
            }
        })
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
        senden(dienst(), VoiceTaskService.ACTION_START)
        assertEquals(VoiceTaskState.RECORDING, store.state)
    }

    @Test fun einZweitesStartIstEinDoppelklickUndAendertNichts() {
        tonQuelle()
        val s = dienst()
        senden(s, VoiceTaskService.ACTION_START)
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
        senden(s, VoiceTaskService.ACTION_START)
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
        ShadowAudioRecord.setSource(object : ShadowAudioRecord.AudioRecordSource {
            override fun readInShortArray(data: ShortArray, offset: Int, size: Int, blocking: Boolean): Int {
                java.util.Arrays.fill(data, offset, offset + size, 0)
                return size
            }
        })
        val s = dienst()
        senden(s, VoiceTaskService.ACTION_START)
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
        senden(s, VoiceTaskService.ACTION_START)
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
        senden(s, VoiceTaskService.ACTION_START)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4))
        senden(s, VoiceTaskService.ACTION_STOP)
        assertEquals("", store.message)
    }
}
