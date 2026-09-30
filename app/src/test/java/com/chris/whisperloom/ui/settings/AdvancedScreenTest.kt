package com.chris.whisperloom.ui.settings

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.agent.VoiceTaskState
import com.chris.whisperloom.agent.VoiceTaskStore
import com.chris.whisperloom.agent.VoiceTaskWork
import com.chris.whisperloom.agent.WidgetProfile
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.nav.WidgetTab
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import com.chris.whisperloom.ui.tutorial.TutorialKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * E7 — "Erweitert": Schalter, Mikrofon, offener Auftrag, Anleitung. Adresse, Token und
 * "Verbindung pruefen" stehen seit 3.7.1 je Widget im Profil-Editor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class AdvancedScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var nav: NavState

    @Before fun setUp() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        ctx.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        VoiceTaskStore(ctx).clear()
    }

    private lateinit var env: AppEnv
    private var gelesenerStatus = SystemStatus(micGranted = true)

    private fun show(micGranted: Boolean = true) {
        gelesenerStatus = SystemStatus(micGranted = micGranted)
        nav = NavState(listOf(Screen.SettingsHub, Screen.Advanced))
        env = AppEnv(PrefsState(Prefs(ctx)), gelesenerStatus) { gelesenerStatus }
        compose.setContent {
            WhisperLoomTheme {
                CompositionLocalProvider(LocalAppEnv provides env) { AdvancedScreen(nav) }
            }
        }
        compose.waitForIdle()
    }

    @Test fun derSchalterIstZuerstAus() {
        show()
        compose.onNodeWithText("Sprachauftrag aktivieren").assertIsDisplayed()
        assertFalse(Prefs(ctx).proWidgetsEnabled)
    }

    @Test fun derSchalterSchreibtSofortDurch() {
        show()
        compose.onNodeWithText("Sprachauftrag aktivieren").performClick()
        compose.waitForIdle()
        assertTrue("Kein Speichern-Knopf — die Zuweisung schreibt direkt", Prefs(ctx).proWidgetsEnabled)
    }

    @Test fun serverUndTokenStehenNichtMehrHier() {
        // Jedes Widget hat seinen eigenen Server — ein globales Feld waere eine zweite, falsche Wahrheit.
        Prefs(ctx).proWidgetsEnabled = true
        show()
        listOf("Server-Adresse", "Token", "Verbindung prüfen").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    @Test fun dieAnleitungFuehrtInsZweiteTutorial() {
        show()
        compose.onNodeWithText("Anleitung ansehen").performClick()
        compose.waitForIdle()
        assertEquals(Screen.Tutorial(kind = TutorialKind.AGENT), nav.current)
    }

    @Test fun derWidgetHinweisFuehrtZuDenProWidgets() {
        show()
        compose.onNodeWithText("Profile, Symbole und Auto-Stopp einstellen").performClick()
        compose.waitForIdle()
        assertEquals(Screen.Widgets(WidgetTab.PRO), nav.current)
    }

    @Test fun ohneMikrofonErscheintDerHinweisNurWennEingeschaltet() {
        Prefs(ctx).proWidgetsEnabled = true
        show(micGranted = false)
        compose.onNodeWithText("Für den Sprachauftrag fehlt die Mikrofon-Berechtigung.").assertIsDisplayed()
    }

    @Test fun derHinweisVerschwindetSobaldDasMikrofonErlaubtIst() {
        // Der Screen muss den Status lesen, nicht selbst checkSelfPermission rufen: sonst
        // bliebe die Warnung stehen, nachdem der Nutzer die Berechtigung gerade erteilt hat.
        Prefs(ctx).proWidgetsEnabled = true
        show(micGranted = false)
        compose.onNodeWithText("Für den Sprachauftrag fehlt die Mikrofon-Berechtigung.").assertIsDisplayed()

        gelesenerStatus = SystemStatus(micGranted = true)
        env.refreshStatus()
        compose.waitForIdle()

        compose.onNodeWithText("Für den Sprachauftrag fehlt die Mikrofon-Berechtigung.").assertDoesNotExist()
    }

    @Test fun ohneOffenenAuftragGibtEsNichtsZuVerwerfen() {
        show()
        compose.onNodeWithText("Offenen Auftrag verwerfen").assertDoesNotExist()
    }

    @Test fun einHaengenderAuftragLaesstSichVerwerfen() {
        val store = VoiceTaskStore(ctx)
        store.begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z", WidgetProfile.DEFAULT_ID)
        store.state = VoiceTaskState.WORKING
        var abgebrochen = 0
        val echtesCancel = VoiceTaskWork.cancelImpl
        VoiceTaskWork.cancelImpl = { abgebrochen++ }
        try {
            show()
            compose.onNodeWithText("Offenen Auftrag verwerfen").performClick()
            compose.waitForIdle()
        } finally {
            VoiceTaskWork.cancelImpl = echtesCancel
        }
        assertEquals(1, abgebrochen)
        assertFalse("Der Auftrag muss wirklich weg sein", VoiceTaskStore(ctx).hasWork)
    }
}
