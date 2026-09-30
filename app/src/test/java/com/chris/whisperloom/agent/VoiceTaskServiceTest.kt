package com.chris.whisperloom.agent

import android.Manifest
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
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
import java.util.concurrent.atomic.AtomicInteger

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

    /** Ohne Netz-Bedingung? je Einreihung — der Dienst ist der automatische Weg (mit Netz-Bedingung). */
    private val manuell = mutableListOf<Boolean>()
    private val policies = mutableListOf<ExistingWorkPolicy>()
    private val echterEnqueue = VoiceTaskWork.enqueueImpl
    private val echteFabrik = VoiceTaskService.detectorFactory
    private var controller: ServiceController<VoiceTaskService>? = null
    private lateinit var gelesen: CountDownLatch
    private lateinit var ausgelesen: CountDownLatch
    private val detektoren = mutableListOf<WidgetProfile>()

    @Before fun aufbauen() {
        app.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        store = VoiceTaskStore(app)
        store.clear()
        Prefs(app).apply {
            agentEnabled = true
            agentUrl = "https://bridge.example.de"
            agentToken = "geheim"
        }
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        VoiceTaskWork.enqueueImpl = { _, policy, request ->
            eingereiht++
            manuell += ohneNetzBedingung(request)
            policies += policy
        }
    }

    @After fun abbauen() {
        VoiceTaskWork.enqueueImpl = echterEnqueue
        VoiceTaskService.detectorFactory = echteFabrik
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
    private fun quelle(amplitude: Short) = quelle(MAX_LESEVORGAENGE) { amplitude }

    /**
     * Mikrofon mit Pegel je Lesevorgang. [ausgelesen] faellt beim ersten Lesen nach dem letzten
     * Puffer — der Aufnahme-Thread liest, schreibt und meldet den Pegel der Reihe nach, also hat
     * der Detektor dann jeden Puffer gesehen.
     */
    private fun quelle(lesevorgaenge: Int, pegel: (Int) -> Short) {
        gelesen = CountDownLatch(1)
        ausgelesen = CountDownLatch(1)
        val gezaehlt = AtomicInteger(0)
        ShadowAudioRecord.setSource(object : ShadowAudioRecord.AudioRecordSource {
            override fun readInShortArray(data: ShortArray, offset: Int, size: Int, blocking: Boolean): Int {
                // Das Schatten-Mikrofon liefert so schnell, wie die CPU kann — anders als ein
                // echtes, das 16 000 Werte pro SEKUNDE gibt. Ohne Deckel sammelt der
                // Aufnahme-Thread waehrend einer simulierten Minute hunderte Megabyte und der
                // Test stirbt mit OutOfMemoryError. 0 heisst fuer den Aufnahme-Thread
                // "gerade nichts da" und laesst ihn weiterlaufen.
                val n = gezaehlt.getAndIncrement()
                if (n >= lesevorgaenge) {
                    ausgelesen.countDown()
                    return 0
                }
                val amplitude = pegel(n)
                for (i in 0 until size) data[offset + i] = if (i % 2 == 0) amplitude else (-amplitude).toShort()
                gelesen.countDown()
                return size
            }
        })
    }

    /** Rund 16 s Ton bei 16 kHz — mehr braucht kein Test, und es bleibt unter einem Megabyte. */
    private val MAX_LESEVORGAENGE = 200

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

    private fun senden(service: VoiceTaskService, action: String, widgetId: Int? = null) {
        val intent = Intent(app, VoiceTaskService::class.java).setAction(action)
        widgetId?.let { intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, it) }
        service.onStartCommand(intent, 0, 1)
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
        assertEquals("Automatischer Weg: mit Netz-Bedingung, nicht wie ein Tipp", listOf(false), manuell)
        // Ein neuer Auftrag loest den alten Job ab — mit KEEP ginge er lautlos verloren.
        assertEquals(listOf(ExistingWorkPolicy.REPLACE), policies)
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

    @Test fun eineVergesseneAufnahmeWirdVonSelbstAbgeschickt() {
        // Start und Stopp sind zwei getrennte Tipps: ohne Notbremse liefe der Mikrofon-Dienst
        // unbegrenzt weiter, wenn jemand nach dem Start das Telefon einsteckt.
        tonQuelle()
        aufnehmen(dienst())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(VoiceTaskService.MAX_DURATION_MS))

        assertEquals(VoiceTaskState.WORKING, store.state)
        assertTrue("Das Gesprochene darf nicht verloren gehen", store.hasWork)
        assertEquals(1, eingereiht)
    }

    @Test fun vorDerHoechstdauerLaeuftDieAufnahmeWeiter() {
        tonQuelle()
        aufnehmen(dienst())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(VoiceTaskService.MAX_DURATION_MS - 1_000))

        assertEquals(VoiceTaskState.RECORDING, store.state)
        assertEquals(0, eingereiht)
    }

    // --- Auto-Stopp ----------------------------------------------------------

    /** Widget [WIDGET] an ein eigenes Profil binden. */
    private fun profil(autoStop: Boolean, pause: SpeechPause = SpeechPause.NORMAL): WidgetProfile {
        val profiles = WidgetProfileStore(app)
        val p = profiles.create("Einkauf").copy(autoStop = autoStop, pause = pause)
        profiles.save(p)
        profiles.bind(WIDGET, p.id)
        return p
    }

    /**
     * Detektor ohne Wartezeiten. Die Schattenuhr steht, solange der Test sie nicht schiebt — jeder
     * Puffer kommt also bei "0 ms" an; so entscheidet schon der erste stille Puffer nach dem Ton.
     */
    private fun schnellerDetektor(noSpeechMs: Long = AutoStopDetector.NO_SPEECH_MS) {
        VoiceTaskService.detectorFactory = { p ->
            detektoren += p
            AutoStopDetector(pauseMs = 0, speechConfirmMs = 0, minRecordingMs = 0, noSpeechMs = noSpeechMs)
        }
    }

    private fun tonDannStille() = quelle(40) { n -> (if (n < 20) 8000 else 0).toShort() }

    /**
     * START OHNE den Main-Looper laufen zu lassen: die Entscheidung des Aufnahme-Threads bleibt
     * eingereiht, bis der Test die Uhr geschoben hat — sonst liefe sie bei 0 ms und waere "zu kurz".
     */
    private fun starten(service: VoiceTaskService, widgetId: Int = WIDGET) {
        val intent = Intent(app, VoiceTaskService::class.java)
            .setAction(VoiceTaskService.ACTION_START)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        service.onStartCommand(intent, 0, 1)
        assertTrue("Der Aufnahme-Thread hat nicht alles gelesen", ausgelesen.await(5, TimeUnit.SECONDS))
    }

    @Test fun autoStoppSendetNachDerSprechpauseOhneTipp() {
        val p = profil(autoStop = true, pause = SpeechPause.SHORT)
        schnellerDetektor()
        tonDannStille()
        starten(dienst())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(VoiceTaskState.WORKING, store.state)
        assertTrue("Das Gesprochene muss als Auftrag auf der Platte liegen", store.hasWork)
        assertEquals("Genau einmal einreihen, ohne STOP-Tipp", 1, eingereiht)
        assertEquals("Das Profil des startenden Widgets entscheidet", listOf(p), detektoren)
    }

    @Test fun ohneAutoStoppNimmtDasWidgetWeiterAuf() {
        profil(autoStop = false)
        schnellerDetektor()
        tonDannStille()
        starten(dienst())
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Tippen startet, Tippen stoppt — wie ohne Profil", VoiceTaskState.RECORDING, store.state)
        assertEquals(0, eingereiht)
        assertTrue("Ohne Auto-Stopp gibt es keinen Detektor", detektoren.isEmpty())
    }

    @Test fun nichtsGehoertVerwirftUndSendetNichts() {
        profil(autoStop = true)
        schnellerDetektor(noSpeechMs = 0)
        // Leises Raumrauschen (Effektivwert ~0,005): das Mikrofon liefert, aber niemand spricht.
        quelle(20) { 150.toShort() }
        val s = dienst()
        starten(s)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(VoiceTaskState.ERROR, store.state)
        assertEquals(app.getString(R.string.widget_no_speech), store.message)
        assertFalse("Nichts Gehoertes darf nicht gesendet werden", store.hasWork)
        assertEquals(0, eingereiht)

        // Ohne Auftrag wird der naechste Tipp (RETRY) zum neuen START — und der nimmt wieder auf.
        tonQuelle()
        senden(s, VoiceTaskService.ACTION_START, WIDGET)
        assertTrue(gelesen.await(5, TimeUnit.SECONDS))
        assertEquals(VoiceTaskState.RECORDING, store.state)
    }

    @Test fun einStummesMikrofonIstMitAutoStoppKeinNichtsGehoert() {
        // Mikrofon belegt (Telefonat) oder still entzogen: exakt Nullen. Der Detektor meldet
        // "keine Sprache" — gezeigt wird trotzdem der wahre Grund, nicht "sprich naeher".
        profil(autoStop = true)
        schnellerDetektor(noSpeechMs = 0)
        quelle(20) { 0.toShort() }
        starten(dienst())
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(VoiceTaskState.ERROR, store.state)
        assertEquals(app.getString(R.string.widget_silent), store.message)
        assertFalse("Auch hier wird nichts gesendet", store.hasWork)
        assertEquals(0, eingereiht)
    }

    @Test fun einTippImAutoModusBeendetGenauEinmal() {
        // Die Sprechpause ist schon erkannt und eingereiht, da kommt der STOP-Tipp: es bleibt bei
        // einem Auftrag, die spaete Entscheidung laeuft ins Leere.
        profil(autoStop = true)
        schnellerDetektor()
        tonDannStille()
        val s = dienst()
        starten(s)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4))
        senden(s, VoiceTaskService.ACTION_STOP)

        assertEquals(VoiceTaskState.WORKING, store.state)
        assertEquals(1, eingereiht)
    }

    @Test fun einSpaetesNichtsGehoertUebermaltDenAuftragNicht() {
        // "Keine Sprache" ist schon entschieden und eingereiht, da kommt der STOP-Tipp: der Auftrag
        // ist unterwegs. Liefe die spaete Entscheidung noch, stuende das Widget auf Fehler — und
        // der naechste Tipp schickte denselben Auftrag ein zweites Mal.
        profil(autoStop = true)
        VoiceTaskService.detectorFactory = { AutoStopDetector(speechConfirmMs = Long.MAX_VALUE / 2, noSpeechMs = 0) }
        tonDannStille()
        val s = dienst()
        starten(s)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(4))
        senden(s, VoiceTaskService.ACTION_STOP)

        assertEquals(VoiceTaskState.WORKING, store.state)
        assertEquals("", store.message)
        assertEquals(1, eingereiht)
    }

    @Test fun dieSprechpauseKommtAusDemProfil() {
        SpeechPause.entries.forEach { pause ->
            val d = echteFabrik(WidgetProfile("x", autoStop = true, pause = pause))
            assertEquals(pause.name, pause.ms, d.pauseMs)
        }
    }

    private companion object {
        const val WIDGET = 7
    }
}
