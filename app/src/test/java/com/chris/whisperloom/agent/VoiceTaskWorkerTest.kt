package com.chris.whisperloom.agent

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.testing.TestListenableWorkerBuilder
import android.os.SystemClock
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.api.ApiHttpException
import com.chris.whisperloom.api.ApiNetworkException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.net.SocketTimeoutException
import java.time.Duration

/**
 * Die Huelle um [VoiceTaskPipeline]: was der WorkManager als Ergebnis bekommt und was danach
 * gespeichert ist. Statt eines Attrappen-Objekts laeuft eine ECHTE Pipeline mit gesetzten
 * Enden — so wird die Verdrahtung mitgeprueft und nicht nur der Worker fuer sich.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceTaskWorkerTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var store: VoiceTaskStore
    private val echteFactory = VoiceTaskWorker.pipelineFactory
    private var transkribiert = 0
    private val gesendet = mutableListOf<String>()

    @Before fun aufbauen() {
        app.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        store = VoiceTaskStore(app)
        store.clear()
        pipeline()
    }

    @After fun abbauen() {
        VoiceTaskWorker.pipelineFactory = echteFactory
    }

    /**
     * [beimErkennen] laeuft mitten in der Transkription — dort, wo in Wirklichkeit bis zu 600 s
     * vergehen und ein neuer Auftrag, ein Verwerfen oder ein Stopp dazwischenkommen kann.
     */
    private fun pipeline(
        erkannt: String = "Kauf Milch",
        send: (String) -> Unit = { gesendet += it },
        veredelungAusgefallen: String? = null,
        beimErkennen: () -> Unit = {},
    ) {
        VoiceTaskWorker.pipelineFactory = { _, s, aktuell ->
            VoiceTaskPipeline(
                samples = { s.loadSamples() },
                transcribe = {
                    transkribiert++
                    beimErkennen()
                    // So meldet die echte Fabrik einen Ausfall der Textverbesserung.
                    veredelungAusgefallen?.let { grund -> s.refineSkipped = grund }
                    erkannt
                },
                send = send,
                stillCurrent = aktuell,
            )
        }
    }

    private fun auftragAnlegen() = store.begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z")

    private fun worker(attempt: Int = 0): VoiceTaskWorker =
        TestListenableWorkerBuilder<VoiceTaskWorker>(app).setRunAttemptCount(attempt).build()

    private fun lauf(attempt: Int = 0): ListenableWorker.Result = worker(attempt).startWork().get()

    /** Ein echtes Widget auf dem Startbildschirm — nur so ist pruefbar, was der Nutzer SIEHT. */
    private fun widget(): Int {
        Prefs(app).apply {
            agentEnabled = true
            agentUrl = "https://bridge.example.de"
            agentToken = "geheim"
        }
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        return shadowOf(AppWidgetManager.getInstance(app)).createWidget(VoiceTaskWidget::class.java, R.layout.widget_task)
    }

    private fun zeile(widget: Int): String =
        shadowOf(AppWidgetManager.getInstance(app)).getViewFor(widget)
            .findViewById<TextView>(R.id.widget_status).text.toString()

    @Test fun ohneAuftragIstNichtsZuTun() {
        assertTrue(lauf() is ListenableWorker.Result.Success)
        assertEquals(0, transkribiert)
    }

    @Test fun erfolgLoeschtDenAuftragUndRaeumtDasAudioWeg() {
        auftragAnlegen()
        assertTrue(lauf() is ListenableWorker.Result.Success)
        assertEquals(listOf("Kauf Milch"), gesendet)
        assertFalse("Erledigtes darf nicht liegen bleiben", store.hasWork)
        assertFalse(store.audioFile.isFile)
        assertEquals(VoiceTaskState.SENT, store.state)
    }

    @Test fun derWorkerHaeltNachDemErfolgNichtAufUndUebermaltNichts() {
        // Ein frueherer Entwurf schlief zwei Sekunden in doWork und setzte danach READY. In
        // dieser Zeit blieb der Auftrag RUNNING — eine in dem Fenster aufgenommene neue
        // Aufnahme waere von ExistingWorkPolicy.KEEP lautlos verworfen und anschliessend mit
        // "bereit" uebermalt worden.
        auftragAnlegen()
        val vorher = System.nanoTime()
        lauf()
        val gedauert = (System.nanoTime() - vorher) / 1_000_000
        assertTrue("doWork hat $gedauert ms gebraucht — es darf nicht warten", gedauert < 1_000)
        assertEquals("Nach dem Erfolg bleibt gesendet stehen", VoiceTaskState.SENT, store.state)
    }

    @Test fun einVoruebergehenderFehlerFuehrtZumSpaeterenVersuch() {
        auftragAnlegen()
        pipeline(send = { throw ApiHttpException(503, "Hermes nicht erreichbar") })
        assertTrue(lauf(attempt = 0) is ListenableWorker.Result.Retry)
        assertEquals("Der Text muss fuer den naechsten Versuch liegen bleiben", "Kauf Milch", store.text)
        assertTrue(store.hasWork)
        assertTrue("Der gescheiterte Versuch muss sichtbar sein", store.message.contains("503"))
    }

    @Test fun nachDemLetztenVersuchIstSchluss() {
        auftragAnlegen()
        pipeline(send = { throw ApiHttpException(503, "Hermes nicht erreichbar") })
        assertTrue(lauf(attempt = VoiceTaskWork.MAX_ATTEMPTS - 1) is ListenableWorker.Result.Failure)
        assertEquals(VoiceTaskState.ERROR, store.state)
        assertTrue("Der Auftrag bleibt gepuffert — ein Tipp wiederholt ihn", store.hasWork)
        assertTrue(store.message.contains("503"))
    }

    @Test fun einEndgueltigerFehlerWirdNichtWiederholt() {
        auftragAnlegen()
        pipeline(send = { throw ApiHttpException(401, "Token stimmt nicht") })
        assertTrue(lauf() is ListenableWorker.Result.Failure)
        assertEquals(VoiceTaskState.ERROR, store.state)
        assertTrue(store.message.contains("401"))
        assertTrue("Ein Tipp nach dem Korrigieren des Tokens soll reichen", store.hasWork)
    }

    @Test fun nichtsVerstandenLaesstNichtsZumWiederholenUebrig() {
        auftragAnlegen()
        pipeline(erkannt = "   ")
        assertTrue(lauf() is ListenableWorker.Result.Failure)
        assertFalse(store.hasWork)
        assertEquals(VoiceTaskPipeline.MSG_EMPTY, store.message)
        assertTrue(gesendet.isEmpty())
    }

    @Test fun derBereitsErkannteTextWirdNichtNochmalTranskribiert() {
        auftragAnlegen()
        store.text = "Schon erkannt"
        assertTrue(lauf(attempt = 1) is ListenableWorker.Result.Success)
        assertEquals("Der teure Schritt darf kein zweites Mal laufen", 0, transkribiert)
        assertEquals(listOf("Schon erkannt"), gesendet)
    }

    @Test fun ohneVorherigenTextWirdTranskribiert() {
        auftragAnlegen()
        lauf()
        assertEquals(1, transkribiert)
    }

    @Test fun eineAusgefalleneTextverbesserungIstNachHerSichtbar() {
        // Der Rohtext geht trotzdem raus — aber der Nutzer soll erfahren, dass "Glaetten"
        // diesmal nicht gegriffen hat, statt die Erkennung dafuer verantwortlich zu machen.
        auftragAnlegen()
        pipeline(veredelungAusgefallen = "API-Fehler 429")
        assertTrue(lauf() is ListenableWorker.Result.Success)
        assertEquals(listOf("Kauf Milch"), gesendet)
        assertEquals(VoiceTaskState.SENT, store.state)
        assertEquals("API-Fehler 429", store.message)
    }

    @Test fun ohneAusfallBleibtDieMeldungLeer() {
        auftragAnlegen()
        store.message = "alter Fehler"
        lauf()
        assertEquals("", store.message)
    }

    // --- #10: Widget haengt auf "Wird gesendet …" ------------------------------

    @Test fun regression10NachEinerZeitueberschreitungKommtGesendet() {
        // Der Ablauf aus dem Issue: der erste Versuch laeuft in den Read-Timeout (der Container
        // kam gerade erst hoch), der zweite geht durch. Das Widget muss beides zeigen.
        val w = widget()
        auftragAnlegen()
        val timeout = SocketTimeoutException("Read timed out")
        pipeline(send = { throw ApiNetworkException(timeout) })

        assertTrue(lauf(attempt = 0) is ListenableWorker.Result.Retry)
        val grund = ApiNetworkException.describe(timeout)
        assertEquals(VoiceTaskState.WORKING, store.state)
        assertEquals(grund, store.message)
        assertTrue(store.hasWork)
        assertEquals(app.getString(R.string.widget_working_retry, grund), zeile(w))

        pipeline()
        assertTrue(lauf(attempt = 1) is ListenableWorker.Result.Success)
        assertEquals(VoiceTaskState.SENT, store.state)
        assertEquals("", store.message)
        assertFalse(store.hasWork)
        assertEquals("Der zweite Versuch nimmt den gecachten Text", 1, transkribiert)
        assertEquals(app.getString(R.string.widget_sent), zeile(w))
    }

    @Test fun zuLaufbeginnStehtDerStoreAufSenden() {
        // Sonst zeichnete ein onUpdate waehrend des Laufs noch den alten Fehler ohne Grund.
        auftragAnlegen()
        store.state = VoiceTaskState.ERROR
        store.message = "Server nicht erreichbar"
        var imSenden: Triple<VoiceTaskState, String, Long>? = null
        pipeline(send = { imSenden = Triple(store.state, store.message, store.attemptStartedAt) })

        lauf()

        assertEquals(VoiceTaskState.WORKING, imSenden?.first)
        assertEquals("", imSenden?.second)
        assertTrue("Der Stempel fuer die Stall-Erkennung fehlt", (imSenden?.third ?: 0L) > 0L)
    }

    @Test fun einNeuerAuftragWaehrendDerErkennungBleibtUnangetastet() {
        auftragAnlegen()
        var neu = ""
        pipeline(beimErkennen = { neu = auftragAnlegen() })

        assertTrue(lauf() is ListenableWorker.Result.Success)

        assertTrue("Der alte Lauf darf nichts senden", gesendet.isEmpty())
        assertEquals(neu, store.requestId)
        assertTrue("Das Audio des neuen Auftrags muss bleiben", store.audioFile.isFile)
        assertEquals("Der alte Text gehoert nicht zum neuen Auftrag", "", store.text)
        assertNotEquals(VoiceTaskState.SENT, store.state)
        assertNotEquals(VoiceTaskState.ERROR, store.state)
        assertTrue(store.hasWork)
    }

    @SuppressLint("RestrictedApi") // stop() ist die Stelle, an der WorkManager einen Worker abbricht.
    @Test fun einGestoppterLaufSendetNichtsUndBehaeltDenBezahltenText() {
        auftragAnlegen()
        val w = worker()
        pipeline(beimErkennen = { w.stop(WorkInfo.STOP_REASON_CANCELLED_BY_APP) })

        w.startWork().get()

        assertTrue("Nach dem Stopp darf nichts mehr an die Bridge gehen", gesendet.isEmpty())
        assertEquals(VoiceTaskState.WORKING, store.state)
        assertEquals("", store.message)
        assertEquals("Die Transkription ist bezahlt", "Kauf Milch", store.text)
        assertTrue(store.hasWork)
    }

    @Test fun einVerworfenerAuftragBleibtVerworfen() {
        // "Offenen Auftrag verwerfen" waehrend der Erkennung: kein Versand, kein Fehler, nichts da.
        auftragAnlegen()
        pipeline(beimErkennen = { store.clear() })

        lauf()

        assertTrue(gesendet.isEmpty())
        assertNotEquals(VoiceTaskState.ERROR, store.state)
        assertFalse(store.hasWork)
    }

    // --- Eigentuemer-Wache: der Besitz wechselt WAEHREND des Versands ----------------
    // stillCurrent fragt die Pipeline nur einmal, vor dem Senden. Der Versand dauert bis zu 75 s;
    // was in der Zeit passiert, faengt allein die Wache im Worker ab.

    @Test fun einNeuerAuftragWaehrendDesVersandsBleibtUnangetastet() {
        auftragAnlegen()
        var neu = ""
        pipeline(send = { neu = auftragAnlegen(); gesendet += it })

        lauf()

        assertEquals("clear() des alten Laufs loeschte den neuen Auftrag", neu, store.requestId)
        assertTrue("Das Audio des neuen Auftrags muss bleiben", store.audioFile.isFile)
        assertEquals("Der alte Text gehoert nicht zum neuen Auftrag", "", store.text)
        assertNotEquals(VoiceTaskState.SENT, store.state)
    }

    @SuppressLint("RestrictedApi") // stop() ist die Stelle, an der WorkManager einen Worker abbricht.
    @Test fun einWaehrendDesVersandsGestoppterLaufZeichnetKeinenFehlversuch() {
        val w = widget()
        auftragAnlegen()
        val lauf = worker()
        pipeline(send = {
            lauf.stop(WorkInfo.STOP_REASON_CANCELLED_BY_APP)
            throw ApiNetworkException(SocketTimeoutException("Read timed out"))
        })

        lauf.startWork().get()

        assertEquals("", store.message)
        assertEquals("Die Flaeche gehoert dem Nachfolger", app.getString(R.string.widget_working), zeile(w))
        assertEquals("Die Transkription ist bezahlt", "Kauf Milch", store.text)
    }

    @Test fun einWaehrendDesVersandsVerworfenerAuftragZeigtNichtGesendet() {
        val w = widget()
        auftragAnlegen()
        pipeline(send = { store.clear(); gesendet += it })

        lauf()

        assertNotEquals(VoiceTaskState.SENT, store.state)
        assertNotEquals(app.getString(R.string.widget_sent), zeile(w))
        assertFalse(store.hasWork)
    }

    // --- Offline-Erkennung: kein Ersatz per Tipp (sie laesst sich nicht abbrechen) ---

    /** Was ein Tipp in diesem Moment taete, wenn der Lauf schon doppelt so lange wie die Stall-Zeit liefe. */
    private fun tippNachDerStallZeit(): Nudge {
        ShadowSystemClock.advanceBy(Duration.ofMillis(VoiceTaskUi.STALL_MS * 2))
        val laeuftSeit = VoiceTaskUi.runningFor(store.attemptStartedAt, SystemClock.elapsedRealtime())
        return VoiceTaskUi.nudge(store.hasWork, JobPhase.RUNNING, laeuftSeit, store.offlineRecognition)
    }

    @Test fun eineLaufendeOfflineErkennungErsetztKeinTipp() {
        Prefs(app).engine = Engine.OFFLINE
        auftragAnlegen()
        var beimErkennen: Nudge? = null
        var beimSenden: Nudge? = null
        pipeline(
            beimErkennen = { beimErkennen = tippNachDerStallZeit() },
            send = { beimSenden = tippNachDerStallZeit(); gesendet += it },
        )

        assertTrue(lauf() is ListenableWorker.Result.Success)

        assertEquals("Waehrend whisper rechnet, stellte sich ein Ersatz nur dahinter an", Nudge.WAIT, beimErkennen)
        assertEquals("Ein haengender Versand danach bleibt ersetzbar", Nudge.SEND_NOW, beimSenden)
        assertFalse(store.offlineRecognition)
    }

    @Test fun eineOnlineErkennungBleibtNachDerStallZeitErsetzbar() {
        Prefs(app).engine = Engine.ONLINE
        auftragAnlegen()
        var beimErkennen: Nudge? = null
        pipeline(beimErkennen = { beimErkennen = tippNachDerStallZeit() })

        lauf()

        assertEquals(Nudge.SEND_NOW, beimErkennen)
    }

    @Test fun mitGecachtemTextGibtEsKeineOfflineErkennung() {
        // Liegengeblieben nach einem Prozesstod mitten in der Erkennung — der naechste Lauf raeumt ihn weg.
        Prefs(app).engine = Engine.OFFLINE
        auftragAnlegen()
        store.text = "Schon erkannt"
        store.offlineRecognition = true
        var beimSenden: Nudge? = null
        pipeline(send = { beimSenden = tippNachDerStallZeit(); gesendet += it })

        lauf(attempt = 1)

        assertEquals(Nudge.SEND_NOW, beimSenden)
    }

    @SuppressLint("RestrictedApi") // stop() ist die Stelle, an der WorkManager einen Worker abbricht.
    @Test fun einGestoppterOfflineLaufLaesstDenMerkerDesNachfolgersStehen() {
        // WorkManager stoppt den Lauf (Deadline), der Nachfolger mit derselben Kennung steht schon
        // hinter der Erkennung an. Der alte Lauf darf dessen Merker nicht loeschen.
        Prefs(app).engine = Engine.OFFLINE
        auftragAnlegen()
        val w = worker()
        pipeline(beimErkennen = {
            w.stop(WorkInfo.STOP_REASON_TIMEOUT)
            store.offlineRecognition = true // so setzt ihn der Nachfolger zu seinem Laufbeginn
        })

        w.startWork().get()

        assertTrue(store.offlineRecognition)
        assertTrue(gesendet.isEmpty())
    }

    @Test fun einLeererLaufStelltEinStehengebliebenesSendenRichtig() {
        val w = widget()
        store.state = VoiceTaskState.WORKING
        VoiceTaskWidgetView.push(app, VoiceTaskState.WORKING)
        assertEquals(app.getString(R.string.widget_working), zeile(w))

        assertTrue(lauf() is ListenableWorker.Result.Success)

        assertEquals(app.getString(R.string.widget_ready), zeile(w))
    }

    @Test fun einLeererLaufLaesstEineFehlermeldungStehen() {
        val w = widget()
        val keinTon = app.getString(R.string.widget_silent)
        store.state = VoiceTaskState.ERROR
        store.message = keinTon
        VoiceTaskWidgetView.push(app, VoiceTaskState.ERROR, message = keinTon)
        val vorher = zeile(w)
        assertTrue("Vorbedingung: der Fehler ist gezeichnet", vorher.contains(keinTon))

        lauf()

        assertEquals(vorher, zeile(w))
    }

    @Test fun einLeererLaufUebermaltKeineLaufendeAufnahme() {
        val w = widget()
        store.state = VoiceTaskState.RECORDING
        VoiceTaskWidgetView.push(app, VoiceTaskState.RECORDING, 7_000)

        lauf()

        assertEquals("0:07", zeile(w))
    }
}
