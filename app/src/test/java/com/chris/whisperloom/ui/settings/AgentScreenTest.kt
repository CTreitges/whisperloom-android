package com.chris.whisperloom.ui.settings

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.agent.VoiceTaskState
import com.chris.whisperloom.agent.VoiceTaskStore
import com.chris.whisperloom.agent.VoiceTaskWork
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
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

/** E7 — Erweiterte Optionen: Schalter, Adresse, Token, Pruefung, Anleitung. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class AgentScreenTest {

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
        nav = NavState(listOf(Screen.SettingsHub, Screen.Agent))
        env = AppEnv(PrefsState(Prefs(ctx)), gelesenerStatus) { gelesenerStatus }
        compose.setContent {
            WhisperLoomTheme {
                CompositionLocalProvider(LocalAppEnv provides env) { AgentScreen(nav) }
            }
        }
        compose.waitForIdle()
    }

    @Test fun derSchalterIstZuerstAus() {
        show()
        compose.onNodeWithText("Sprachauftrag aktivieren").assertIsDisplayed()
        assertFalse(Prefs(ctx).agentEnabled)
    }

    @Test fun derSchalterSchreibtSofortDurch() {
        show()
        compose.onNodeWithText("Sprachauftrag aktivieren").performClick()
        compose.waitForIdle()
        assertTrue("Kein Speichern-Knopf — die Zuweisung schreibt direkt", Prefs(ctx).agentEnabled)
    }

    @Test fun dieAdresseWirdSofortGespeichert() {
        show()
        compose.onNodeWithText("Server-Adresse").performTextInput("https://bridge.example.de")
        compose.waitForIdle()
        assertEquals("https://bridge.example.de", Prefs(ctx).agentUrl)
    }

    @Test fun einTippfehlerInDerAdresseWirdBenannt() {
        show()
        compose.onNodeWithText("Server-Adresse").performTextInput("bridge.example.de")
        compose.waitForIdle()
        compose.onNodeWithText("URL muss mit http:// oder https:// beginnen").assertIsDisplayed()
    }

    @Test fun pruefenBleibtGesperrtSolangeEtwasFehlt() {
        show()
        compose.onNodeWithText("Verbindung prüfen").assertIsNotEnabled()
    }

    @Test fun pruefenWirdMitAdresseUndTokenMoeglich() {
        Prefs(ctx).apply {
            agentUrl = "https://bridge.example.de"
            agentToken = "geheim"
        }
        show()
        compose.onNodeWithText("Verbindung prüfen").assertIsEnabled()
    }

    @Test fun derDatenschutzHinweisStehtDa() {
        show()
        compose.onNodeWithText("Transkript und Auftrag gehen an genau den Server, den du hier einträgst — sonst nirgendwohin. Das Aufnehmen selbst läuft wie beim Diktat über die eingestellte Erkennung.")
            .assertIsDisplayed()
    }

    @Test fun dieAnleitungFuehrtInsZweiteTutorial() {
        show()
        compose.onNodeWithText("Anleitung ansehen").performClick()
        compose.waitForIdle()
        assertEquals(Screen.Tutorial(kind = TutorialKind.AGENT), nav.current)
    }

    @Test fun ohneMikrofonErscheintDerHinweisNurWennEingeschaltet() {
        Prefs(ctx).agentEnabled = true
        show(micGranted = false)
        compose.onNodeWithText("Für den Sprachauftrag fehlt die Mikrofon-Berechtigung.").assertIsDisplayed()
    }

    @Test fun derHinweisVerschwindetSobaldDasMikrofonErlaubtIst() {
        // Der Screen muss den Status lesen, nicht selbst checkSelfPermission rufen: sonst
        // bliebe die Warnung stehen, nachdem der Nutzer die Berechtigung gerade erteilt hat.
        Prefs(ctx).agentEnabled = true
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
        store.begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z")
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
