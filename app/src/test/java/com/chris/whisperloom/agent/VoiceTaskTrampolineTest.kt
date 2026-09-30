package com.chris.whisperloom.agent

import android.Manifest
import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAudioRecord
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

/** Die unsichtbare Zwischenstation: wer den Mikrofon-Dienst startet und wann stattdessen die App aufgeht. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceTaskTrampolineTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var store: VoiceTaskStore

    /** Jede Einreihung: true = ohne Netz-Bedingung (vom Tipp), false = wartet auf Netz. */
    private val aufrufe = mutableListOf<Boolean>()
    private val policies = mutableListOf<ExistingWorkPolicy>()
    private var phase = JobPhase.NONE
    private val echterEnqueue = VoiceTaskWork.enqueueImpl
    private val echtePhase = VoiceTaskWork.phaseImpl

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
        shadowOf(app).clearNextStartedActivities()
        VoiceTaskWork.enqueueImpl = { _, policy, request ->
            policies += policy
            aufrufe += ohneNetzBedingung(request)
        }
        VoiceTaskWork.phaseImpl = { phase }
    }

    @After fun abbauen() {
        VoiceTaskWork.enqueueImpl = echterEnqueue
        VoiceTaskWork.phaseImpl = echtePhase
        ShadowAudioRecord.clearSource()
    }

    private fun auftrag() = store.begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z")

    /** Ein echtes Widget — nur so ist pruefbar, was nach dem Tipp zu sehen ist. */
    private fun widget(): Int =
        shadowOf(AppWidgetManager.getInstance(app)).createWidget(VoiceTaskWidget::class.java, R.layout.widget_task)

    private fun zeile(widget: Int): String =
        shadowOf(AppWidgetManager.getInstance(app)).getViewFor(widget)
            .findViewById<TextView>(R.id.widget_status).text.toString()

    private fun tippen(tap: TapIntent) {
        Robolectric.buildActivity(
            VoiceTaskTrampolineActivity::class.java,
            VoiceTaskTrampolineActivity.intent(app, tap),
        ).create().get()
    }

    private fun gestarteterDienst(): Intent? = shadowOf(app).nextStartedService

    @Test fun tippenAufBereitStartetDenAufnahmeDienst() {
        tippen(TapIntent.START)
        assertEquals(VoiceTaskService.ACTION_START, gestarteterDienst()?.action)
    }

    @Test fun dasTrampolinBleibtNichtStehen() {
        val activity = Robolectric.buildActivity(
            VoiceTaskTrampolineActivity::class.java,
            VoiceTaskTrampolineActivity.intent(app, TapIntent.START),
        ).create().get()
        assertTrue("Die Zwischenstation muss sich sofort beenden", activity.isFinishing)
    }

    @Test fun ohneMikrofonBerechtigungFuehrtDerTippInDieApp() {
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        tippen(TapIntent.START)
        assertNull("Ohne Berechtigung darf kein Dienst starten", gestarteterDienst())
        val ziel = shadowOf(app).nextStartedActivity
        assertNotNull("Der Tipp darf nicht ins Leere laufen", ziel)
        assertEquals(AppNav.ROUTE_AGENT, ziel?.getStringExtra(AppNav.EXTRA_ROUTE))
    }

    @Test fun ohneEingerichtetenServerFuehrtDerTippInDieApp() {
        Prefs(app).agentToken = ""
        tippen(TapIntent.START)
        assertNull(gestarteterDienst())
        assertEquals(AppNav.ROUTE_AGENT, shadowOf(app).nextStartedActivity?.getStringExtra(AppNav.EXTRA_ROUTE))
    }

    @Test fun erneutSendenBrauchtKeinMikrofonUndKeinenDienst() {
        auftrag()
        tippen(TapIntent.RETRY)
        assertEquals(listOf(true), aufrufe)
        assertNull("Erneut senden darf keinen Foreground-Service kosten", gestarteterDienst())
    }

    @Test fun erneutSendenStelltDenStoreAufSenden() {
        // Sonst stuende der Store waehrend des Laufs auf ERROR, und jedes onUpdate zeichnete
        // einen Fehler ohne Grund, obwohl gerade gesendet wird.
        auftrag()
        store.state = VoiceTaskState.ERROR
        store.message = "Server nicht erreichbar"
        phase = JobPhase.NONE

        tippen(TapIntent.RETRY)

        assertEquals(listOf(true), aufrufe)
        assertEquals(VoiceTaskState.WORKING, store.state)
        assertEquals("", store.message)
        assertNull(gestarteterDienst())
    }

    @Test fun einDoppeltippAufErneutSendenBrichtDenLaufNichtAb() {
        // Der erste Tipp hat den Job gestartet; der zweite trifft noch die alte Flaeche.
        auftrag()
        store.state = VoiceTaskState.ERROR
        store.attemptStartedAt = SystemClock.elapsedRealtime()
        phase = JobPhase.RUNNING

        tippen(TapIntent.RETRY)

        assertEquals("Ein laufender Versuch wird nicht ersetzt", emptyList<Boolean>(), aufrufe)
    }

    @Test fun erneutSendenOhneAuftragNimmtNeuAuf() {
        // Nach "Kein Ton aufgenommen" liegt nichts herum — der Tipp ist dann ein neuer Anlauf.
        tippen(TapIntent.RETRY)
        assertEquals(emptyList<Boolean>(), aufrufe)
        assertEquals(VoiceTaskService.ACTION_START, gestarteterDienst()?.action)
    }

    @Test fun derTippInDieEinstellungenOeffnetDieApp() {
        tippen(TapIntent.SETUP)
        assertEquals(AppNav.ROUTE_AGENT, shadowOf(app).nextStartedActivity?.getStringExtra(AppNav.EXTRA_ROUTE))
        assertNull(gestarteterDienst())
    }

    @Test fun jedeAbsichtHatEineEigeneAction() {
        // Sonst haelt das System zwei PendingIntents fuer denselben und "erneut senden"
        // wuerde eine neue Aufnahme starten.
        val actions = TapIntent.entries.map { VoiceTaskTrampolineActivity.intent(app, it).action }
        assertEquals(TapIntent.entries.size, actions.toSet().size)
    }

    // --- Tipp einer bestimmten Widget-Instanz ---------------------------------

    private fun tippen(tap: TapIntent, widget: Int) {
        Robolectric.buildActivity(
            VoiceTaskTrampolineActivity::class.java,
            VoiceTaskTrampolineActivity.intent(app, tap, widget),
        ).create().get()
    }

    private fun widgetIdIm(intent: Intent?): Int? =
        intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

    @Test fun derStartNenntDemDienstDasWidget() {
        // Dessen Profil bestimmt, wie die Aufnahme endet.
        tippen(TapIntent.START, 42)
        val dienst = gestarteterDienst()
        assertEquals(VoiceTaskService.ACTION_START, dienst?.action)
        assertEquals(42, widgetIdIm(dienst))
    }

    @Test fun ohneWidgetStartetDerDienstWieBisher() {
        tippen(TapIntent.START)
        val dienst = gestarteterDienst()
        assertEquals(VoiceTaskService.ACTION_START, dienst?.action)
        assertFalse(dienst!!.hasExtra(AppWidgetManager.EXTRA_APPWIDGET_ID))
    }

    @Test fun einNeuerAnlaufUeberErneutSendenNenntDasWidgetEbenfalls() {
        // Ohne Auftrag ist "erneut senden" eine neue Aufnahme — mit dem Profil des getippten Widgets.
        tippen(TapIntent.RETRY, 42)
        assertEquals(42, widgetIdIm(gestarteterDienst()))
    }

    @Test fun erneutSendenMitWidgetBleibtBeimAuftrag() {
        // #10: die Instanz aendert nichts an der Bedeutung des Tipps.
        auftrag()
        tippen(TapIntent.RETRY, 42)
        assertEquals(listOf(true), aufrufe)
        assertNull(gestarteterDienst())
    }

    @Test fun anstossenMitWidgetBleibtBeimAlten() {
        auftrag()
        store.state = VoiceTaskState.WORKING
        phase = JobPhase.WAITING

        tippen(TapIntent.REFRESH, 42)

        assertEquals(listOf(true), aufrufe)
        assertEquals(listOf(ExistingWorkPolicy.REPLACE), policies)
        assertNull(gestarteterDienst())
    }

    @Test fun zweiWidgetsHabenVerschiedeneTippIntents() {
        // Extras zaehlen beim Vergleich von PendingIntents nicht — die Instanz steht deshalb in data.
        val eins = VoiceTaskTrampolineActivity.intent(app, TapIntent.START, 1)
        val zwei = VoiceTaskTrampolineActivity.intent(app, TapIntent.START, 2)
        assertFalse(eins.filterEquals(zwei))
        assertEquals(TapIntent.START, VoiceTaskTrampolineActivity.intentOf(eins))
        assertEquals(1, widgetIdIm(eins))
        assertNull("Ohne Instanz wie bisher", VoiceTaskTrampolineActivity.intent(app, TapIntent.START).data)
    }

    @Test fun eineIntentOhneAbsichtIstHarmlos() {
        assertEquals(TapIntent.NONE, VoiceTaskTrampolineActivity.intentOf(Intent()))
        assertEquals(TapIntent.NONE, VoiceTaskTrampolineActivity.intentOf(null))
    }

    // --- Tipp auf "Wird gesendet …" (#10) -----------------------------------

    @Test fun einWartenderAuftragWirdSofortGesendet() {
        // Genau #10: der Job wartete im Backoff, galt als "eingeplant", und der Tipp tat nichts.
        val w = widget()
        auftrag()
        store.state = VoiceTaskState.WORKING
        store.message = "Server nicht erreichbar"
        phase = JobPhase.WAITING

        tippen(TapIntent.REFRESH)

        assertEquals("Sofort und ohne Netz-Bedingung einreihen", listOf(true), aufrufe)
        // KEEP liesse den wartenden Job liegen — genau der Fehler aus #10.
        assertEquals("Der Tipp muss den wartenden Job abloesen", listOf(ExistingWorkPolicy.REPLACE), policies)
        assertEquals(VoiceTaskState.WORKING, store.state)
        assertEquals("", store.message)
        assertEquals(app.getString(R.string.widget_working), zeile(w))
        assertNull(gestarteterDienst())
        assertNull("Der Tipp darf die App nicht oeffnen", shadowOf(app).nextStartedActivity)
    }

    @Test fun einAuftragOhneJobWirdNeuEingereiht() {
        // Der Prozess starb zwischen dem Ablegen des Auftrags und dem Einreihen.
        auftrag()
        store.state = VoiceTaskState.WORKING
        phase = JobPhase.NONE

        tippen(TapIntent.REFRESH)

        assertEquals(listOf(true), aufrufe)
    }

    @Test fun einFrischLaufenderWorkerWirdWederErsetztNochUebermalt() {
        val w = widget()
        auftrag()
        store.state = VoiceTaskState.WORKING
        store.attemptStartedAt = SystemClock.elapsedRealtime()
        phase = JobPhase.RUNNING
        // Merkzeichen: jedes Neuzeichnen wuerde diese Zeile ersetzen.
        VoiceTaskWidgetView.push(app, VoiceTaskState.ERROR, message = "Merkzeichen")
        val vorher = zeile(w)
        assertTrue("Vorbedingung: das Merkzeichen ist gezeichnet", vorher.contains("Merkzeichen"))

        tippen(TapIntent.REFRESH)

        assertEquals("Eine zweite, bezahlte Transkription waere falsch", emptyList<Boolean>(), aufrufe)
        assertEquals("Die Flaeche gehoert dem laufenden Worker", vorher, zeile(w))
    }

    @Test fun einHaengenderWorkerWirdNachDerStallZeitErsetzt() {
        auftrag()
        store.state = VoiceTaskState.WORKING
        store.attemptStartedAt = SystemClock.elapsedRealtime()
        phase = JobPhase.RUNNING
        ShadowSystemClock.advanceBy(Duration.ofMillis(VoiceTaskUi.STALL_MS))

        tippen(TapIntent.REFRESH)

        assertEquals(listOf(true), aufrufe)
    }

    @Test fun einDoppeltippErsetztDenGeradeEingereihtenLaufNicht() {
        // Der Stempel stammt von einem alten Versuch (Retry setzt ihn nicht zurueck). Tipp 1 reiht
        // ein; der neue Job ist schon RUNNING, bevor sein doWork stempelt. Ohne frischen Stempel
        // vom Tipp hielte Tipp 2 ihn fuer haengend und braeche ihn per REPLACE wieder ab.
        auftrag()
        store.state = VoiceTaskState.WORKING
        store.attemptStartedAt = SystemClock.elapsedRealtime()
        ShadowSystemClock.advanceBy(Duration.ofMillis(VoiceTaskUi.STALL_MS * 2))
        phase = JobPhase.WAITING

        tippen(TapIntent.REFRESH)
        phase = JobPhase.RUNNING
        tippen(TapIntent.REFRESH)

        assertEquals("Nur der erste Tipp reiht ein", listOf(true), aufrufe)
        assertEquals(SystemClock.elapsedRealtime(), store.attemptStartedAt)
    }

    @Test fun eineLaufendeOfflineErkennungWirdAuchNachDerStallZeitNichtErsetzt() {
        // Ein Ersatz stellte sich hinter die laufende whisper-Erkennung und verdoppelte die Wartezeit.
        auftrag()
        store.state = VoiceTaskState.WORKING
        store.attemptStartedAt = SystemClock.elapsedRealtime()
        store.offlineRecognition = true
        phase = JobPhase.RUNNING
        ShadowSystemClock.advanceBy(Duration.ofMillis(VoiceTaskUi.STALL_MS * 2))

        tippen(TapIntent.REFRESH)

        assertEquals(emptyList<Boolean>(), aufrufe)
    }

    @Test fun ohneOffenenAuftragWirdNurDerEchteZustandGezeichnet() {
        val w = widget()
        store.state = VoiceTaskState.WORKING
        VoiceTaskWidgetView.push(app, VoiceTaskState.WORKING)
        assertEquals(app.getString(R.string.widget_working), zeile(w))
        phase = JobPhase.NONE

        tippen(TapIntent.REFRESH)

        assertEquals(emptyList<Boolean>(), aufrufe)
        assertEquals(app.getString(R.string.widget_ready), zeile(w))
    }

    /** Der Worker wird genau waehrend der Phasen-Abfrage des Tipps fertig — so, wie er es selbst tut. */
    private fun workerEndetImTipp(state: VoiceTaskState, message: String = "") {
        auftrag()
        store.state = VoiceTaskState.WORKING
        VoiceTaskWidgetView.push(app, VoiceTaskState.WORKING)
        VoiceTaskWork.phaseImpl = {
            store.clear()
            store.state = state
            store.message = message
            VoiceTaskWidgetView.push(app, state, message = message)
            JobPhase.NONE
        }
    }

    @Test fun einWorkerDerMittenImTippFertigWirdWirdNichtUebermalt() {
        // Nebenbefund aus #10: frueher las der Tipp den Store und zeichnete danach — endete der
        // Worker genau dazwischen, uebermalte "Wird gesendet …" (oder "bereit") das "Gesendet".
        val w = widget()
        workerEndetImTipp(VoiceTaskState.SENT)

        tippen(TapIntent.REFRESH)

        assertEquals(emptyList<Boolean>(), aufrufe)
        assertEquals(app.getString(R.string.widget_sent), zeile(w))
    }

    @Test fun nichtsVerstandenBleibtNachEinemTippImAbschlussStehen() {
        // Sonst stuende "Tippen und sprechen" da, und der Nutzer hielte den Auftrag fuer angekommen.
        val w = widget()
        workerEndetImTipp(VoiceTaskState.ERROR, VoiceTaskPipeline.MSG_EMPTY)
        val fehler = VoiceTaskWidgetView.status(app, VoiceTaskState.ERROR, 0, VoiceTaskPipeline.MSG_EMPTY)

        tippen(TapIntent.REFRESH)

        assertEquals(emptyList<Boolean>(), aufrufe)
        assertEquals(fehler, zeile(w))
    }
}
