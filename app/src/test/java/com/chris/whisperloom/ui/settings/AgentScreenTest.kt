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
        shadowOf(ApplicationProvider.getApplicationContext() as android.app.Application)
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
    }

    private fun show() {
        nav = NavState(listOf(Screen.SettingsHub, Screen.Agent))
        val env = AppEnv(PrefsState(Prefs(ctx)), SystemStatus()) { SystemStatus() }
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
        shadowOf(ApplicationProvider.getApplicationContext() as android.app.Application)
            .denyPermissions(Manifest.permission.RECORD_AUDIO)
        Prefs(ctx).agentEnabled = true
        show()
        compose.onNodeWithText("Für den Sprachauftrag fehlt die Mikrofon-Berechtigung.").assertIsDisplayed()
    }
}
