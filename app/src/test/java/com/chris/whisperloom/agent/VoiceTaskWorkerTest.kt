package com.chris.whisperloom.agent

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.chris.whisperloom.api.ApiHttpException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
        app.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        store = VoiceTaskStore(app)
        store.clear()
        pipeline()
    }

    @After fun abbauen() {
        VoiceTaskWorker.pipelineFactory = echteFactory
    }

    private fun pipeline(
        erkannt: String = "Kauf Milch",
        send: (String) -> Unit = { gesendet += it },
        veredelungAusgefallen: String? = null,
    ) {
        VoiceTaskWorker.pipelineFactory = { _, s ->
            VoiceTaskPipeline(
                samples = { s.loadSamples() },
                transcribe = {
                    transkribiert++
                    // So meldet die echte Fabrik einen Ausfall der Textverbesserung.
                    veredelungAusgefallen?.let { grund -> s.refineSkipped = grund }
                    erkannt
                },
                send = send,
            )
        }
    }

    private fun auftragAnlegen() = store.begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z")

    private fun lauf(attempt: Int = 0): ListenableWorker.Result =
        TestListenableWorkerBuilder<VoiceTaskWorker>(app).setRunAttemptCount(attempt).build().startWork().get()

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
}
