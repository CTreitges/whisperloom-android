package com.chris.whisperloom.ui.settings

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ui.WhisperLoomApp
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.RouteRequest
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Einstellungen-Hub nach Gegenstaenden (3.9.0): Unterzeilen mit dem aktuellen Wert, jede Zeile
 * oeffnet ihre Seite, und ueber das echte Routing hin und zurueck (Zeilenname = Seitentitel).
 * Reihenfolge, Gruppen und Trenner: MainFlowTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class SettingsHubTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    /** Offline geht, ein Textmodell passt (16 GB), eingerichtet fuer Home. */
    private val status = SystemStatus(
        micGranted = true, canDrawOverlays = true, a11yRunning = true,
        installedModels = setOf("small"), totalRamBytes = 16L shl 30,
    )

    @Before fun setUp() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
    }

    private fun env(s: SystemStatus = status) = AppEnv(PrefsState(prefs), s) { s }

    private fun screen(env: AppEnv, content: @Composable (NavState) -> Unit): NavState {
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { content(nav) } }
        }
        compose.waitForIdle()
        return nav
    }

    private fun hub(env: AppEnv = env()) = screen(env) { SettingsHubScreen(it) }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    // --- Unterzeilen ----------------------------------------------------------------------

    @Test fun textverbesserungNenntDieStufenBeiderWege() {
        prefs.refineMode = RefineMode.POLISH
        prefs.polishReadable = true // die Stufe bleibt "Glaetten"
        hub()
        compose.onNodeWithText("Diktat: Glätten · Sprachnachrichten: Aus").assertExists()
    }

    @Test fun textverbesserungNenntDieStufeDerSprachnachrichten() {
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        hub()
        compose.onNodeWithText("Diktat: Aus · Sprachnachrichten: Zusammenfassen").assertExists()
    }

    @Test fun woerterbuchNenntBegriffeDateiUndRegeln() {
        prefs.apiPrompt = "Anna\nBernd"
        prefs.vocabFileUri = "content://x/namen.md"
        prefs.vocabFileName = "namen.md"
        prefs.removeFillers = true
        prefs.autoCapitalize = true
        prefs.trailingSpace = true // Einfuegen gehoert jetzt zu Knopf & Tastatur
        hub()
        compose.onNodeWithText("2 Begriffe · Datei: namen.md · Füllwörter · Groß-Schreibung").assertExists()
    }

    @Test fun woerterbuchOhneBegriffeUndOhneRegeln() {
        prefs.removeFillers = false
        prefs.autoCapitalize = false
        hub()
        compose.onNodeWithText("Noch keine Begriffe").assertExists()
    }

    @Test fun kiZugangAbWerkOnline() {
        hub()
        // "Wie Erkennung" mit OpenAI, kein Modell gewaehlt: die Empfehlung je Stufe.
        compose.onNodeWithText("Wie Erkennung · OpenAI · Empfehlung je Stufe").assertExists()
    }

    @Test fun kiZugangMitEigenemZugangUndStufenModell() {
        prefs.llmProviderId = "anthropic"
        prefs.llmKey = "sk-ant"
        prefs.setLlmModelFor(RefineMode.SUMMARIZE, "claude-opus-5-5")
        prefs.setLlmModelFor(RefineMode.PROMPT, "claude-opus-5-5") // ohne Pro unsichtbar, zaehlt nicht
        hub()
        compose.onNodeWithText("Anthropic · 1 Stufe mit eigenem Modell").assertExists()
    }

    @Test fun kiZugangZaehltStufenModelleOhneModellImZugangNicht() {
        prefs.llmProviderId = "ollama"
        prefs.llmModel = "" // Pflicht bei Ollama: ohne Modell wirken die Stufen-Modelle nicht
        prefs.setLlmModelFor(RefineMode.POLISH, "qwen3:8b")
        hub()
        compose.onNodeWithText("Stufe mit eigenem Modell", substring = true).assertDoesNotExist()
    }

    @Test fun kiZugangNenntDasModellDesZugangs() {
        prefs.llmProviderId = "anthropic"
        prefs.llmModel = "claude-sonnet-5"
        hub()
        compose.onNodeWithText("Anthropic · Claude Sonnet 5").assertExists()
    }

    @Test fun kiZugangOfflineOhneEigenenZugang() {
        prefs.engine = Engine.OFFLINE
        hub()
        compose.onNodeWithText("Kein Online-Zugang").assertExists()
    }

    @Test fun kiZugangBeiElevenLabsOhneModellTeil() {
        prefs.sttProviderId = "elevenlabs"
        hub()
        compose.onNodeWithText("Wie Erkennung · ElevenLabs").assertExists()
    }

    // --- Navigation ------------------------------------------------------------------------

    @Test fun jedeZeileOeffnetIhreSeite() {
        val nav = hub()
        mapOf(
            "Textverbesserung" to Screen.Refine,
            "Wörterbuch & Regeln" to Screen.Dictionary,
            "Spracherkennung" to Screen.Recognition,
            "KI-Zugang" to Screen.LlmAccess,
            "Offline-Modelle" to Screen.Models,
            "Knopf & Tastatur" to Screen.ButtonKeyboard,
            "Widgets" to Screen.Widgets(),
            "Erweitert" to Screen.Advanced,
            "Anleitung & Hilfe" to Screen.Help(1),
        ).forEach { (zeile, seite) ->
            click(zeile)
            assertEquals(zeile, seite, nav.current)
            nav.pop()
            compose.waitForIdle()
        }
    }

    /** Echtes Routing: Einstellungen › Seite und zurueck in den Hub. Der Titel der Seite ist der Zeilenname. */
    @Test fun seitenUeberDieAppUndZurueck() {
        lateinit var back: OnBackPressedDispatcher
        prefs.tutorialSeen = true
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            WhisperLoomTheme { WhisperLoomApp(env(), route = RouteRequest(AppNav.ROUTE_SETTINGS)) }
        }
        compose.waitForIdle()
        // Je Zeile ein Text, den nur ihre Seite zeigt.
        mapOf(
            "Textverbesserung" to "Bei geteilten Sprachnachrichten",
            "Wörterbuch & Regeln" to "Feste Regeln",
            "Spracherkennung" to "Transkription",
            "KI-Zugang" to "Modell je Stufe",
            "Offline-Modelle" to "Textverbesserung bei Offline-Erkennung",
        ).forEach { (zeile, inhalt) ->
            click(zeile)
            compose.onNodeWithText(inhalt).assertExists()
            compose.onNodeWithText(zeile).assertExists() // Titel; die Hub-Zeile ist weg
            compose.runOnIdle { back.onBackPressed() }
            compose.waitForIdle()
            compose.onNodeWithText(inhalt).assertDoesNotExist()
            compose.onNodeWithText("MODELLE & ZUGÄNGE").assertExists()
        }
    }
}
