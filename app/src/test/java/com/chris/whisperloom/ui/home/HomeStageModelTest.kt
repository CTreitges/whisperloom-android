package com.chris.whisperloom.ui.home

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home-Zeile "Text" mit Modell je Stufe (3.8.6): Sie nennt das Modell, mit dem das naechste Diktat
 * rechnet — das der Diktat-Stufe, mit dem Anzeigenamen aus dem Katalog.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class HomeStageModelTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    /** Alles, was Home braucht: Mikrofon, Overlay, Bedienungshilfe. */
    private val readyStatus = SystemStatus(micGranted = true, canDrawOverlays = true, a11yRunning = true)

    @Before fun setUp() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        prefs.tutorialSeen = true
    }

    private fun home() {
        val env = AppEnv(PrefsState(prefs), readyStatus) { readyStatus }
        val nav = NavState(listOf(Screen.Home))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { HomeScreen(nav) } }
        }
        compose.waitForIdle()
    }

    @Test fun togetherOhneModellSagtKeinModellAuchMitStufenModell() {
        // Review 3.8.6 (UI-1): Die Zeile darf nicht das Stufen-Modell nennen, waehrend Banner und
        // Tastatur "kein Modell" sagen — ohne Modell des Zugangs zaehlt es nicht.
        prefs.sttProviderId = "together"
        prefs.refineMode = RefineMode.BEAUTIFY
        prefs.setLlmModelFor(RefineMode.BEAUTIFY, "Y")
        home()
        compose.onNodeWithText("Verschönern · kein Textmodell eingetragen").assertExists()
    }
}
