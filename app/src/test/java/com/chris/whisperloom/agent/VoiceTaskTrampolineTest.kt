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
    private val echtesCancel = VoiceTaskWork.cancelImpl
    private var abgebrochen = 0

    @Before fun aufbauen() {
        app.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        store = VoiceTaskStore(app)
        store.clear()
        serverEinrichten(app)
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        shadowOf(app).clearNextStartedActivities()
        VoiceTaskWork.enqueueImpl = { _, policy, request ->
            policies += policy
            aufrufe += ohneNetzBedingung(request)
        }
        VoiceTaskWork.phaseImpl = { phase }
        VoiceTaskWork.cancelImpl = { abgebrochen++ }
    }

    @After fun abbauen() {
        VoiceTaskWork.enqueueImpl = echterEnqueue
        VoiceTaskWork.phaseImpl = echtePhase
        VoiceTaskWork.cancelImpl = echtesCancel
        ShadowAudioRecord.clearSource()
    }

    private fun auftrag() = store.begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z", WidgetProfile.DEFAULT_ID)

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

    /** Wohin die App aufging: Route und (bei "widgets") das Profil, dessen Editor sich oeffnet. */
    private fun appZiel(): Pair<String?, String?> =
        shadowOf(app).nextStartedActivity.let { it?.getStringExtra(AppNav.EXTRA_ROUTE) to it?.getStringExtra(AppNav.EXTRA_PROFILE) }

    private val proTab = AppNav.ROUTE_WIDGETS to null

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
        // Pro-Tab: dort steht die Mikrofon-Karte.
        assertEquals("Der Tipp darf nicht ins Leere laufen", proTab, appZiel())
    }

    @Test fun ohneEingerichtetenServerOeffnetDerTippDenEditorDesWidgets() {
        serverEinrichten(app, token = "")
        tippen(TapIntent.START)
        assertNull(gestarteterDienst())
        assertEquals(AppNav.ROUTE_WIDGETS to WidgetProfile.DEFAULT_ID, appZiel())
    }

    @Test fun ohneProWidgetsFuehrtDerTippNachErweitert() {
        Prefs(app).proWidgetsEnabled = false
        tippen(TapIntent.START)
        assertNull("Aus heisst aus — auch mit eingerichtetem Server", gestarteterDienst())
        assertEquals(AppNav.ROUTE_ADVANCED to null, appZiel())
    }

    @Test fun proAusGehtVorMikrofonUndServer() {
        serverEinrichten(app, token = "")
        Prefs(app).proWidgetsEnabled = false
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        tippen(TapIntent.SETUP)
        assertEquals(AppNav.ROUTE_ADVANCED to null, appZiel())
    }

    @Test fun dasMikrofonGehtVorDemServer() {
        // Ohne Mikrofon nuetzt auch ein Server nichts — die Karte dafuer steht im Pro-Tab.
        serverEinrichten(app, token = "")
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        tippen(TapIntent.SETUP)
        assertEquals(proTab, appZiel())
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
        // Nichts fehlt (z. B. ein alter Tipp von "aus"): dann eben der Pro-Tab.
        tippen(TapIntent.SETUP)
        assertEquals(proTab, appZiel())
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

    @Test fun esZaehltDerServerDesGetipptenWidgets() {
        // Seit 3.7.1 hat jedes Widget seinen eigenen Server: ein anderes eingerichtetes Widget hilft nicht.
        val profiles = WidgetProfileStore(app)
        val ohne = profiles.create("Ohne Server")
        profiles.bind(42, ohne.id)
        tippen(TapIntent.START, 42)
        assertNull(gestarteterDienst())
        assertEquals("Der Editor genau dieses Widgets", AppNav.ROUTE_WIDGETS to ohne.id, appZiel())

        serverEinrichten(app, token = "")
        val mit = serverEinrichten(app, profiles.create("Mit Server").id)
        profiles.bind(43, mit.id)
        tippen(TapIntent.START, 43)
        assertEquals("Auch wenn das Standardprofil keinen Server hat", 43, widgetIdIm(gestarteterDienst()))
    }

    @Test fun derSetupTippEinesWidgetsOhneServerOeffnetDessenEditor() {
        // So tippt das Widget im Zustand "Server fehlt": SETUP mit seiner Instanz.
        val profiles = WidgetProfileStore(app)
        val ohne = profiles.create("Ohne Server")
        profiles.bind(42, ohne.id)
        tippen(TapIntent.SETUP, 42)
        assertEquals(AppNav.ROUTE_WIDGETS to ohne.id, appZiel())
        assertNull(gestarteterDienst())
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

    // --- Erneut senden nur, was das Widget des Auftrags senden kann ------------

    /** Ein roter Auftrag des Widgets [name]; [server] = false: dessen Server ist ungueltig. */
    private fun auftragVon(name: String, server: Boolean = true): WidgetProfile {
        val created = WidgetProfileStore(app).create(name)
        val p = if (server) serverEinrichten(app, created.id) else created
        store.begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z", p.id)
        store.state = VoiceTaskState.ERROR
        return p
    }

    /** Widget [widgetId] zeigt ein Profil mit eigenem, gueltigem Server. */
    private fun widgetMitServer(widgetId: Int, name: String = "Mit Server"): WidgetProfile {
        val p = serverEinrichten(app, WidgetProfileStore(app).create(name).id)
        WidgetProfileStore(app).bind(widgetId, p.id)
        return p
    }

    @Test fun mitProWidgetsAusFuehrtErneutSendenNachErweitert() {
        // Frueher lief jeder Tipp erneut in "Pro Widgets sind aus" — und nie dorthin, wo man es aendert.
        auftrag()
        store.state = VoiceTaskState.ERROR
        store.message = app.getString(R.string.widget_task_pro_off)
        Prefs(app).proWidgetsEnabled = false

        tippen(TapIntent.RETRY, 42)

        assertEquals("Nichts senden", emptyList<Boolean>(), aufrufe)
        assertEquals(AppNav.ROUTE_ADVANCED to null, appZiel())
        assertTrue("Der Auftrag bleibt fuer nach dem Einschalten", store.hasWork)
        assertNull(gestarteterDienst())
    }

    @Test fun ohneServerImWidgetDesAuftragsOeffnetErneutSendenDessenEditor() {
        // Getippt wird Widget B mit gueltigem Server, der Auftrag gehoert aber A ohne Server. Frueher
        // schickte jeder Tipp auf B den Auftrag von A erneut ins Leere, und B konnte nie aufnehmen.
        val a = auftragVon("A", server = false)
        widgetMitServer(43, "B")

        tippen(TapIntent.RETRY, 43)

        assertEquals("Nichts senden", emptyList<Boolean>(), aufrufe)
        assertEquals("Der Editor von A, nicht von B", AppNav.ROUTE_WIDGETS to a.id, appZiel())
        assertTrue("Nach dem Eintragen reicht ein Tipp", store.hasWork)
        assertNull(gestarteterDienst())
    }

    @Test fun istDasWidgetDesAuftragsGeloeschtWirdVerworfenUndNeuAufgenommen() {
        // Den Auftrag kann niemand mehr senden (nie an einen fremden Server) — wie im Worker verwerfen.
        val w = widget()
        val a = auftragVon("A")
        WidgetProfileStore(app).delete(a.id)

        tippen(TapIntent.RETRY, w)

        assertEquals(1, abgebrochen)
        assertFalse(store.hasWork)
        assertEquals("Nichts senden", emptyList<Boolean>(), aufrufe)
        assertEquals("Das Widget zeigt wieder seinen Ruhezustand", app.getString(R.string.widget_ready), zeile(w))
        assertEquals("Und der Tipp nimmt neu auf", w, widgetIdIm(gestarteterDienst()))
    }

    @Test fun einSendbarerAuftragGehtAuchVomAnderenWidgetRaus() {
        // Unveraendert: hat das Widget des Auftrags einen Server, sendet jeder Tipp ihn erneut.
        auftragVon("A")
        widgetMitServer(43, "B")

        tippen(TapIntent.RETRY, 43)

        assertEquals(listOf(true), aufrufe)
        assertEquals(0, abgebrochen)
        assertTrue(store.hasWork)
        assertNull(gestarteterDienst())
        assertNull("Die App bleibt zu", shadowOf(app).nextStartedActivity)
    }

    @Test fun einAuftragVonVor371OhneProfilGehtUeberDasStandardprofilRaus() {
        // Leere profile_id = Auftrag von vor 3.7.1 — wie im Worker das Standardprofil, nicht verwerfen.
        store.begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z", "")
        store.state = VoiceTaskState.ERROR

        tippen(TapIntent.RETRY, 42)

        assertEquals("Wird angestossen", listOf(true), aufrufe)
        assertEquals("Nichts verworfen", 0, abgebrochen)
        assertTrue(store.hasWork)
        assertNull("Kein neuer Anlauf", gestarteterDienst())
        assertNull("Die App bleibt zu", shadowOf(app).nextStartedActivity)
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
