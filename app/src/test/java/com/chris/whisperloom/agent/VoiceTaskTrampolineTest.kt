package com.chris.whisperloom.agent

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.Prefs
import org.junit.After
import org.junit.Assert.assertEquals
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

/** Die unsichtbare Zwischenstation: wer den Mikrofon-Dienst startet und wann stattdessen die App aufgeht. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceTaskTrampolineTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private lateinit var store: VoiceTaskStore
    private var eingereiht = 0
    private val echterEnqueue = VoiceTaskWork.enqueueImpl
    private val echtesIsScheduled = VoiceTaskWork.isScheduledImpl

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
        VoiceTaskWork.enqueueImpl = { eingereiht++ }
    }

    @After fun abbauen() {
        VoiceTaskWork.enqueueImpl = echterEnqueue
        VoiceTaskWork.isScheduledImpl = echtesIsScheduled
        ShadowAudioRecord.clearSource()
    }

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
        store.begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z")
        tippen(TapIntent.RETRY)
        assertEquals(1, eingereiht)
        assertNull("Erneut senden darf keinen Foreground-Service kosten", gestarteterDienst())
    }

    @Test fun erneutSendenOhneAuftragNimmtNeuAuf() {
        // Nach "Kein Ton aufgenommen" liegt nichts herum — der Tipp ist dann ein neuer Anlauf.
        tippen(TapIntent.RETRY)
        assertEquals(0, eingereiht)
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

    @Test fun eineIntentOhneAbsichtIstHarmlos() {
        assertEquals(TapIntent.NONE, VoiceTaskTrampolineActivity.intentOf(Intent()))
        assertEquals(TapIntent.NONE, VoiceTaskTrampolineActivity.intentOf(null))
    }

    @Test fun einAuftragOhneJobWirdBeimNachsehenNeuEingereiht() {
        // Der Fall, den es in Produktion wirklich gibt: der Prozess stirbt zwischen dem Ablegen
        // des Auftrags und dem Einreihen. Ohne diesen Weg stuende das Widget fuer immer auf
        // "Wird gesendet …", und eine neue Aufnahme waere auch nicht moeglich.
        store.begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z")
        store.state = VoiceTaskState.WORKING
        VoiceTaskWork.isScheduledImpl = { false }

        tippen(TapIntent.REFRESH)

        assertEquals("Der liegengebliebene Auftrag muss wieder eingereiht werden", 1, eingereiht)
        assertNull(gestarteterDienst())
        assertNull("Nachsehen darf die App nicht oeffnen", shadowOf(app).nextStartedActivity)
    }

    @Test fun einLaufenderAuftragWirdNichtDoppeltEingereiht() {
        store.begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z")
        store.state = VoiceTaskState.WORKING
        VoiceTaskWork.isScheduledImpl = { true }

        tippen(TapIntent.REFRESH)

        assertEquals("Waehrend wirklich gearbeitet wird, tut der Tipp nichts", 0, eingereiht)
    }

    @Test fun ohneOffenenAuftragWirdNichtsEingereiht() {
        VoiceTaskWork.isScheduledImpl = { false }
        tippen(TapIntent.REFRESH)
        assertEquals(0, eingereiht)
    }
}
