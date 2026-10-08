package com.chris.whisperloom.ui.settings

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.ui.WhisperLoomApp
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.RouteRequest
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.nav.TextSection
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
 * Text-Hub und Unterseiten (3.8.6): Reihenfolge, Unterzeilen mit dem aktuellen Wert, Navigation je
 * Zeile ueber das echte Routing, und "Lesbarer glaetten" getrennt fuer Diktat und Sprachnachrichten.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class TextHubTest {

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
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub, Screen.TextSettings))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { content(nav) } }
        }
        compose.waitForIdle()
        return nav
    }

    private fun hub(env: AppEnv = env()) = screen(env) { TextSettingsScreen(it) }

    private fun click(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    // --- Hub ------------------------------------------------------------------------------

    @Test fun hubZeigtFuenfZeilenInDreiGruppen() {
        hub()
        val reihenfolge = listOf(
            "KI-STUFEN", "Diktat", "Sprachnachrichten",
            "MODELLE & ZUGANG", "Online-Zugang & Modelle", "Offline-Erkennung",
            "OHNE KI", "Regeln ohne KI",
        )
        val oben = reihenfolge.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals("Von oben nach unten: $reihenfolge", oben.sorted(), oben)
        assertEquals("Keine zwei auf einer Hoehe", oben.size, oben.toSet().size)
    }

    @Test fun hubUnterzeilenAbWerkOnline() {
        prefs.refineMode = RefineMode.POLISH
        prefs.polishReadable = true
        prefs.removeFillers = true
        prefs.autoCapitalize = true
        prefs.trailingSpace = false
        hub()
        compose.onNodeWithText("Glätten · lesbarer").assertExists()
        compose.onNodeWithText("Aus · wortgetreu").assertExists()
        // "Wie Erkennung" mit OpenAI, kein Modell gewaehlt: die Empfehlung je Stufe.
        compose.onNodeWithText("Wie Erkennung · OpenAI · Empfehlung je Stufe").assertExists()
        compose.onNodeWithText("Lokales Textmodell · Textmodell fehlt").assertExists()
        compose.onNodeWithText("Füllwörter · Groß-Schreibung").assertExists()
    }

    @Test fun hubUnterzeilenMitEigenemZugangStufenModellUndTextmodell() {
        prefs.refineMode = RefineMode.BEAUTIFY
        prefs.polishReadable = true // wirkt nur mit Glaetten
        prefs.shareRefineMode = RefineMode.POLISH
        prefs.sharePolishReadable = true
        prefs.llmProviderId = "anthropic"
        prefs.llmKey = "sk-ant"
        prefs.setLlmModelFor(RefineMode.SUMMARIZE, "claude-opus-5-5")
        prefs.setLlmModelFor(RefineMode.PROMPT, "claude-opus-5-5") // ohne Pro unsichtbar, zaehlt nicht
        prefs.localLlmModel = "gemma4_e2b"
        prefs.removeFillers = false
        prefs.autoCapitalize = false
        prefs.trailingSpace = false
        hub(env(status.copy(installedTextModels = setOf("gemma4_e2b"))))
        compose.onNodeWithText("Verschönern").assertExists()
        compose.onNodeWithText("Glätten · lesbarer").assertExists() // die Sprachnachrichten
        compose.onNodeWithText("Anthropic · 1 Stufe mit eigenem Modell").assertExists()
        compose.onNodeWithText("Lokales Textmodell · Gemma 4 E2B").assertExists()
        compose.onNodeWithText("Keine").assertExists()
    }

    @Test fun hubNenntDasModellDesZugangs() {
        prefs.llmProviderId = "anthropic"
        prefs.llmModel = "claude-sonnet-5"
        prefs.offlineRefine = OfflineRefineRule.SKIP
        hub()
        compose.onNodeWithText("Anthropic · Claude Sonnet 5").assertExists()
        compose.onNodeWithText("Überspringen").assertExists()
    }

    @Test fun hubOfflineOhneEigenenZugangHatKeinenOnlineZugang() {
        prefs.engine = Engine.OFFLINE
        hub()
        compose.onNodeWithText("Kein Online-Zugang").assertExists()
    }

    @Test fun hubBeiElevenLabsOhneModellTeil() {
        prefs.sttProviderId = "elevenlabs"
        hub()
        compose.onNodeWithText("Wie Erkennung · ElevenLabs").assertExists()
    }

    @Test fun jedeZeileOeffnetIhreSeite() {
        val nav = hub()
        mapOf(
            "Diktat" to TextSection.DICTATION,
            "Sprachnachrichten" to TextSection.SHARE,
            "Online-Zugang & Modelle" to TextSection.ACCESS,
            "Offline-Erkennung" to TextSection.OFFLINE,
            "Regeln ohne KI" to TextSection.RULES,
        ).forEach { (zeile, seite) ->
            click(zeile)
            assertEquals(zeile, Screen.TextPage(seite), nav.current)
            nav.pop()
        }
    }

    /** Echtes Routing: Einstellungen › Text › Unterseite und zurueck in den Hub. */
    @Test fun unterseitenUeberDieAppUndZurueck() {
        lateinit var back: OnBackPressedDispatcher
        prefs.tutorialSeen = true
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            WhisperLoomTheme { WhisperLoomApp(env(), route = RouteRequest(AppNav.ROUTE_SETTINGS)) }
        }
        compose.waitForIdle()
        click("Text")
        // Je Zeile ein Text, den nur ihre Seite zeigt.
        mapOf(
            "Diktat" to "Textverbesserung (KI)",
            "Sprachnachrichten" to "Geteilte Sprachnachrichten",
            "Online-Zugang & Modelle" to "Modell je Stufe",
            "Offline-Erkennung" to "Textverbesserung bei Offline-Erkennung",
            "Regeln ohne KI" to "Liste bearbeiten",
        ).forEach { (zeile, inhalt) ->
            click(zeile)
            compose.onNodeWithText(inhalt).assertExists()
            compose.runOnIdle { back.onBackPressed() }
            compose.waitForIdle()
            compose.onNodeWithText(inhalt).assertDoesNotExist()
            compose.onNodeWithText("KI-STUFEN").assertExists()
        }
    }

    // --- "Lesbarer glaetten": Diktat und Sprachnachrichten getrennt -------------------------------

    @Test fun lesbarerGlaettenDerSprachnachrichtenWirktNurDort() {
        prefs.refineMode = RefineMode.POLISH
        prefs.shareRefineMode = RefineMode.POLISH
        screen(env()) { TextShareScreen(it) }
        click("Lesbarer glätten")
        val p = Prefs(ctx)
        assertEquals(true, p.sharePolishReadable)
        assertEquals(false, p.polishReadable)
        assertEquals(RefineMode.READABLE, p.shareStage)
        assertEquals(RefineMode.POLISH, p.dictationStage)
    }

    @Test fun lesbarerGlaettenDerSprachnachrichtenNurMitIhrerStufeGlaetten() {
        prefs.refineMode = RefineMode.POLISH // die Diktat-Stufe schaltet ihn nicht frei
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        screen(env()) { TextShareScreen(it) }
        compose.onNodeWithText("Lesbarer glätten").assertIsNotEnabled()
        compose.onNodeWithText("Wirkt mit der Stufe „Glätten“.").assertExists()
        compose.onNode(hasText("Glätten") and hasAnyAncestor(hasTestTag(SHARE_REFINE_TAG))).performClick()
        compose.waitForIdle()
        assertEquals(RefineMode.POLISH, Prefs(ctx).shareRefineMode)
        compose.onNodeWithText("Lesbarer glätten").assertIsEnabled()
    }

    @Test fun lesbarerGlaettenDesDiktatsLaesstDieSprachnachrichtenInRuhe() {
        prefs.refineMode = RefineMode.POLISH
        prefs.shareRefineMode = RefineMode.POLISH
        prefs.sharePolishReadable = true
        screen(env()) { TextDictationScreen(it) }
        click("Lesbarer glätten")
        val p = Prefs(ctx)
        assertEquals(true, p.polishReadable)
        assertEquals(true, p.sharePolishReadable)
        click("Lesbarer glätten")
        assertEquals(false, Prefs(ctx).polishReadable)
        assertEquals(true, Prefs(ctx).sharePolishReadable)
    }
}
