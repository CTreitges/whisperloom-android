package com.chris.whisperloom.ui

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.BuildConfig
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.overlay.FloatingMicService
import com.chris.whisperloom.ui.home.HomeScreen
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.RouteRequest
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.settings.HelpScreen
import com.chris.whisperloom.ui.settings.ModelsScreen
import com.chris.whisperloom.ui.settings.RecognitionScreen
import com.chris.whisperloom.whisper.DownloadState
import com.chris.whisperloom.whisper.ModelCatalog
import com.chris.whisperloom.whisper.ModelDownloads
import com.chris.whisperloom.whisper.ModelStore
import java.io.RandomAccessFile
import com.chris.whisperloom.ui.settings.SettingsHubScreen
import com.chris.whisperloom.ui.settings.TextSettingsScreen
import com.chris.whisperloom.ui.setup.SetupScreen
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Compose-Semantik-Tests der Hauptscreens (Robolectric, kein Bitmap-Rendering): Router,
 * Assistent-Schritte 1/2a, E2, E4, B3, E5, Hub. Hohes Fenster, damit scrollende Spalten und
 * LazyColumns alles komponieren.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class MainFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    /** Alles, was Home braucht: Mikrofon, Overlay, Bedienungshilfe. */
    private val readyStatus = SystemStatus(micGranted = true, canDrawOverlays = true, a11yRunning = true)

    @Before fun setUp() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
    }

    private fun env(status: SystemStatus = SystemStatus()) = AppEnv(PrefsState(prefs), status) { status }

    /** Klassenname des naechsten per startService/startForegroundService gestarteten Dienstes (ShadowApplication). */
    private fun startedService(): String? = shadowOf(ctx as Application).nextStartedService?.component?.className

    private fun app(env: AppEnv) {
        compose.setContent { WhisperLoomTheme { WhisperLoomApp(env) } }
        compose.waitForIdle()
    }

    private fun screen(env: AppEnv, content: @Composable (NavState) -> Unit) {
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { content(nav) } }
        }
        compose.waitForIdle()
    }

    // --- Router ----------------------------------------------------------------

    @Test fun routerZeigtWillkommenBeimAllererstenStart() {
        app(env())
        compose.onNodeWithText("Diktiere in jede App.").assertIsDisplayed()
        compose.onNodeWithText("Los geht's").assertIsDisplayed()
    }

    @Test fun routerZeigtSchritt1WennEngineFehlt() {
        prefs.welcomeSeen = true
        app(env())
        compose.onNodeWithText("Wie soll WhisperLoom Sprache erkennen?").assertIsDisplayed()
        compose.onNodeWithText("Weiter").assertIsNotEnabled()
    }

    @Test fun routerZeigtHomeMitHeroWennEingerichtet() {
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        prefs.tutorialSeen = true // eingerichtet + Tutorial gesehen -> direkt Home
        app(env(readyStatus))
        compose.onNodeWithText("Mikro-Knopf starten").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Online · OpenAI · GPT Transcribe (empfohlen)").assertIsDisplayed()
        compose.onNodeWithText("Mikrofon ✓ · Über Apps ✓ · Bedienungshilfe ✓").assertIsDisplayed()
    }

    @Test fun homeSperrtHeroUndZeigtChipBeiSpaeteremMikrofonEntzug() {
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        // Home bleibt bei spaeterem Entzug offen (kein Rauswurf): Hero gesperrt + klickbarer Warn-Chip.
        screen(env(readyStatus.copy(micGranted = false))) { HomeScreen(it) }
        compose.onNodeWithText("Mikro-Knopf starten").assertIsNotEnabled()
        compose.onNodeWithText("Mikrofon fehlt — beheben").assertIsDisplayed()
        compose.onNodeWithText("Pflicht-Berechtigung fehlt").assertExists()
    }

    // --- Tutorial (T) ------------------------------------------------------------

    @Test fun routerZeigtTutorialEinmalWennEingerichtet() {
        // Bestandsnutzer nach dem Update: Home liegt darunter, Ueberspringen fuehrt dorthin.
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        app(env(readyStatus))
        compose.onNodeWithText("Diktieren mit dem Knopf").assertIsDisplayed()
        compose.onNodeWithText("Mikro-Knopf starten").assertDoesNotExist()
        compose.onNodeWithText("Überspringen").performClick()
        compose.waitForIdle()
        assertTrue(Prefs(ctx).tutorialSeen)
        compose.onNodeWithText("Mikro-Knopf starten").assertIsDisplayed()
    }

    @Test fun routerZeigtKeinTutorialSolangeEinrichtungOffen() {
        prefs.welcomeSeen = true
        prefs.engine = Engine.ONLINE // Key fehlt -> Schritt 2a
        app(env(readyStatus))
        compose.onNodeWithText("Zugang zum Dienst").assertIsDisplayed()
        compose.onNodeWithText("Diktieren mit dem Knopf").assertDoesNotExist()
    }

    @Test fun fertigSeiteFuehrtErstInsTutorialOhneDenKnopfZuStarten() {
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        val nav = NavState(listOf(Screen.Setup(Screen.Setup.DONE)))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env(readyStatus)) { SetupScreen(Screen.Setup.DONE, nav) } }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Knopf starten & los").performClick()
        compose.waitForIdle()
        // Der Knopf wuerde sonst ueber dem Tutorial schweben: Start erst beim Beenden des Tutorials.
        assertEquals(listOf(Screen.Home, Screen.Tutorial(startBubbleAfter = true)), nav.snapshot())
        assertNull(startedService())
    }

    @Test fun fertigSeiteStartetKnopfDirektWennTutorialSchonGesehen() {
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        prefs.tutorialSeen = true
        val nav = NavState(listOf(Screen.Setup(Screen.Setup.DONE)))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env(readyStatus)) { SetupScreen(Screen.Setup.DONE, nav) } }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Knopf starten & los").performClick()
        compose.waitForIdle()
        assertEquals(FloatingMicService::class.java.name, startedService())
        assertEquals(listOf(Screen.Home), nav.snapshot())
    }

    @Test fun knopfStartetErstNachDemTutorial() {
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        // Echte App-Wurzel, per Deep-Link auf der Fertig-Seite (W9): W9 -> Tutorial -> Ueberspringen -> Knopf + Home.
        val route = RouteRequest(AppNav.ROUTE_SETUP, Screen.Setup.DONE)
        compose.setContent { WhisperLoomTheme { WhisperLoomApp(env(readyStatus), route = route) } }
        compose.waitForIdle()
        compose.onNodeWithText("Knopf starten & los").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Diktieren mit dem Knopf").assertIsDisplayed()
        assertNull(startedService())
        compose.onNodeWithText("Überspringen").performClick()
        compose.waitForIdle()
        assertEquals(FloatingMicService::class.java.name, startedService())
        assertTrue(Prefs(ctx).tutorialSeen)
        compose.onNodeWithText("Mikro-Knopf starten").assertIsDisplayed()
    }

    @Test fun hilfeZeileOeffnetTutorialOhneFlagZuruecksetzen() {
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        prefs.tutorialSeen = true
        app(env(readyStatus))
        compose.onNodeWithContentDescription("Anleitung und Hilfe").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Tutorial erneut ansehen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Diktieren mit dem Knopf").assertIsDisplayed()
        assertTrue(Prefs(ctx).tutorialSeen)
    }

    @Test fun homeMehrOeffnetTutorialSeiteSprachnachrichten() {
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        prefs.tutorialSeen = true
        app(env(readyStatus))
        compose.onNodeWithText("Mehr").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sprachnachrichten abtippen").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seite 3 von 4").assertIsDisplayed()
    }

    // --- Assistent ---------------------------------------------------------------

    @Test fun schritt1AuswahlSchreibtEngine() {
        prefs.welcomeSeen = true
        app(env())
        compose.onNodeWithText("Online-Dienst").performClick()
        compose.waitForIdle()
        assertEquals(Engine.ONLINE, Prefs(ctx).engine)
        compose.onNodeWithText("Weiter").assertIsEnabled()
    }

    @Test fun schritt2aAnbieterWechselSetztPresetUndErstesModell() {
        prefs.welcomeSeen = true
        prefs.engine = Engine.ONLINE
        app(env())
        compose.onNodeWithText("Zugang zum Dienst").assertIsDisplayed()
        compose.onNodeWithText("https://api.openai.com/v1").assertExists()
        compose.onNodeWithTag("dropdown:Anbieter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Groq (kostenlos)").performClick()
        compose.waitForIdle()
        assertEquals("groq", Prefs(ctx).sttProviderId)
        assertEquals("", Prefs(ctx).apiBaseUrl)
        assertEquals("", Prefs(ctx).apiModel)
        compose.onNodeWithText("https://api.groq.com/openai/v1").assertExists()
        compose.onNodeWithTag("dropdown:Modell").assertTextContains("Whisper Large v3 Turbo")
    }

    @Test fun eigenesModellUebernimmtFreieId() {
        prefs.welcomeSeen = true
        prefs.engine = Engine.ONLINE
        app(env())
        compose.onNodeWithTag("dropdown:Modell").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Eigenes Modell …").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Modell-ID").performTextInput("mein-modell")
        compose.onNodeWithText("Übernehmen").performClick()
        compose.waitForIdle()
        assertEquals("mein-modell", Prefs(ctx).apiModel)
        compose.onNodeWithTag("dropdown:Modell").assertTextContains("mein-modell")
    }

    // --- E2 Text -----------------------------------------------------------------

    @Test fun textStufeSchreibtRefineModeUndSchaltetSmartFillersFrei() {
        screen(env()) { TextSettingsScreen(it) }
        compose.onNodeWithText("Füllwörter intelligent entfernen").assertIsNotEnabled()
        compose.onNodeWithText("Glätten").performClick()
        compose.waitForIdle()
        assertEquals(RefineMode.POLISH, Prefs(ctx).refineMode)
        compose.onNodeWithText("Füllwörter intelligent entfernen").assertIsEnabled()
    }

    @Test fun fuellwoerterSheetFuegtEigenesWortHinzu() {
        screen(env()) { TextSettingsScreen(it) }
        compose.onNodeWithText("Liste bearbeiten").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Noch keine eigenen Wörter").assertExists()
        compose.onNodeWithText("Wort hinzufügen").performTextInput("sozusagen")
        compose.onNodeWithContentDescription("Wort hinzufügen").performClick()
        compose.waitForIdle()
        assertTrue("sozusagen" in Prefs(ctx).customFillers)
        compose.onNodeWithText("sozusagen").assertExists()
    }

    @Test fun absatzSchalterIstAnUndWirktNurMitStufe() {
        screen(env()) { TextSettingsScreen(it) }
        compose.onNodeWithText("Automatische Absätze").assertIsNotEnabled()
        compose.onNodeWithText("Glätten").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Automatische Absätze").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(false, Prefs(ctx).refineParagraphs)
    }

    @Test fun ollamaImHeimnetzFragtNachDerServerAdresse() {
        prefs.refineMode = RefineMode.POLISH
        prefs.llmProviderId = "ollama"
        screen(env()) { TextSettingsScreen(it) }
        compose.onNodeWithText("Dein eigenes Ollama", substring = true).assertExists()
        compose.onNodeWithText("Modelle vom Server laden").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("Server-Adresse")).performTextInput("http://127.0.0.1:1")
        compose.waitForIdle()
        assertEquals("http://127.0.0.1:1", Prefs(ctx).llmUrl)
        compose.onNodeWithText("Modelle vom Server laden").assertIsEnabled()
        // Ohne Modell-Liste bleibt die freie Eingabe des Modellnamens.
        compose.onNode(hasSetTextAction() and hasText("Modell")).performTextInput("gemma3:4b")
        compose.waitForIdle()
        assertEquals("gemma3:4b", Prefs(ctx).llmModel)
    }

    @Test fun ollamaCloudZeigtModellAuswahlUndFreieEingabe() {
        prefs.refineMode = RefineMode.POLISH
        prefs.llmProviderId = "ollama-cloud"
        screen(env()) { TextSettingsScreen(it) }
        compose.onNodeWithText("https://ollama.com").assertExists()
        compose.onNodeWithTag("dropdown:Modell").assertTextContains("Gemma 4 31B (empfohlen)")
        compose.onNodeWithTag("dropdown:Modell").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("GLM 5.3 Flash").assertExists()
        compose.onNodeWithText("Eigenes Modell …").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("Modell-ID")).performTextInput("kimi-k2.6")
        compose.onNodeWithText("Übernehmen").performClick()
        compose.waitForIdle()
        assertEquals("kimi-k2.6", Prefs(ctx).llmModel)
    }

    // --- Vokabular -------------------------------------------------------------------

    @Test fun vokabularAlsListeImSheet() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "groq"
        prefs.apiKey = "k"
        screen(env()) { RecognitionScreen(it) }
        compose.onNodeWithText("Noch keine Begriffe").assertExists()
        compose.onNodeWithText("Bearbeiten").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("Begriff hinzufügen")).performTextInput("Anna, Kubernetes")
        compose.onNodeWithContentDescription("Begriff hinzufügen").performClick()
        compose.waitForIdle()
        assertEquals("Anna\nKubernetes", Prefs(ctx).apiPrompt)
        compose.onNodeWithText("Eigene Begriffe (2)").assertExists()
        compose.onNodeWithContentDescription("Anna entfernen").performClick()
        compose.waitForIdle()
        assertEquals("Kubernetes", Prefs(ctx).apiPrompt)
        compose.onNodeWithText("Datei verknüpfen").assertExists()
    }

    @Test fun vokabularZeileNenntAnzahlUndDatei() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "groq"
        prefs.apiKey = "k"
        prefs.apiPrompt = "Anna\nBernd"
        prefs.vocabFileUri = "content://x/namen.md"
        prefs.vocabFileName = "namen.md"
        screen(env()) { RecognitionScreen(it) }
        compose.onNodeWithText("2 Begriffe · Datei: namen.md").assertExists()
    }

    // --- E4 Offline-Modelle -------------------------------------------------------

    @Test fun modelleZeigenVierEintraegeEmpfehlungUndGrossGedimmt() {
        screen(env(SystemStatus(totalRamBytes = 4L shl 30))) { ModelsScreen(it) }
        listOf("Tiny", "Base", "Small", "Large v3 Turbo").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Empfohlen").assertExists()
        compose.onNodeWithText("Für dieses Gerät zu groß").assertExists()
        compose.onNodeWithContentDescription("Large v3 Turbo herunterladen").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Small herunterladen").assertIsEnabled()
        compose.onNodeWithText("Noch kein Modell geladen").assertExists()
    }

    @Test fun modellZeileIstEinAuswaehlbaresElementMitNamen() {
        // Review SPEC-3: TalkBack liest "Base, Optionsfeld, …" statt viermal nur "Optionsfeld".
        val store = ModelStore(ctx)
        store.ensureDir()
        RandomAccessFile(store.file(ModelCatalog.BASE), "rw").use { it.setLength(ModelCatalog.BASE.bytes) }
        try {
            screen(env()) { ModelsScreen(it) }
            compose.onNode(isSelectable() and hasText("Small")).assertIsNotEnabled().assertIsSelected() // Default, nicht installiert
            compose.onNode(isSelectable() and hasText("Base")).assertIsEnabled().assertIsNotSelected().performClick()
            compose.waitForIdle()
            assertEquals("base", Prefs(ctx).offlineModel)
            compose.onNode(isSelectable() and hasText("Base")).assertIsSelected()
            // Loeschen bleibt ein eigener, beschrifteter Knoten ausserhalb der Radio-Zeile.
            compose.onNodeWithContentDescription("Base löschen").assertIsEnabled()
        } finally {
            store.delete(ModelCatalog.BASE)
        }
    }

    @Test fun erneutIstWaehrendAnderemDownloadGesperrt() {
        // Review KOR-6: der Dienst ignoriert einen zweiten Start still — der Knopf darf ihn gar nicht anbieten.
        ModelDownloads.update("tiny", DownloadState.Failed("Netzwerkfehler beim Laden", retryable = true))
        try {
            screen(env()) { ModelsScreen(it) }
            compose.onNodeWithText("Erneut").assertIsEnabled()
            ModelDownloads.update("base", DownloadState.Running(1_000, 60_000_000, 500))
            compose.waitForIdle()
            compose.onNodeWithText("Erneut").assertIsNotEnabled()
        } finally {
            ModelDownloads.clear("tiny")
            ModelDownloads.clear("base")
        }
    }

    @Test fun kontextFeldSagtBeiMistralDassNichtsMitgeschicktWird() {
        // Review API-2: kein context_bias umgesetzt — kein Wortlisten-Versprechen in der Oberflaeche.
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "mistral"
        prefs.apiKey = "k"
        screen(env()) { RecognitionScreen(it) }
        compose.onNodeWithText("Dieser Anbieter nimmt kein Vokabular entgegen — es wirkt nur bei anderen Anbietern und offline.")
            .assertExists()
        compose.onNodeWithText("Kontext-Wörter (kommagetrennt)").assertDoesNotExist()
    }

    @Test fun eigenerServerMarkiertLeeresModellAlsFehler() {
        // Review API-7: kein Modell-Default beim eigenen Server -> Pflichtfeld.
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "http://192.168.1.5:8000/v1"
        screen(env()) { RecognitionScreen(it) }
        val modelField = compose.onNode(hasSetTextAction() and hasText("Modell"))
        modelField.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        modelField.performTextInput("whisper-1")
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("Modell")).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    // --- E5 Hilfe, Hub -------------------------------------------------------------

    @Test fun hilfeHatSiebenAbschnitte() {
        screen(env()) { HelpScreen(1, it) }
        listOf(
            "So funktioniert's", "Einrichtung Schritt für Schritt", "API-Key bekommen", "Eigener Server",
            "Offline-Modus", "Datenschutz", "Wenn etwas nicht klappt",
        ).forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test fun hubZeigtSiebenZeilen() {
        prefs.engine = Engine.ONLINE
        screen(env()) { SettingsHubScreen(it) }
        listOf(
            "Erkennung", "Text", "Knopf & Tastatur", "Offline-Modelle", "Anleitung & Hilfe",
            "Erweiterte Optionen", "Über WhisperLoom",
        ).forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertExists()
    }

    @Test fun derSprachauftragStehtImHubAufAusSolangeErNichtEingerichtetIst() {
        prefs.engine = Engine.ONLINE
        screen(env()) { SettingsHubScreen(it) }
        // Kein Schalter auf Hub-Ebene (Spec §2.3) — nur der Wert.
        compose.onNodeWithText("Aus").assertExists()
    }

    @Test fun derEingerichteteSprachauftragZeigtSeinenZweck() {
        prefs.engine = Engine.ONLINE
        prefs.agentEnabled = true
        prefs.agentUrl = "https://bridge.example.de"
        prefs.agentToken = "geheim"
        screen(env()) { SettingsHubScreen(it) }
        compose.onNodeWithText("Sprachauftrag an einen eigenen Agenten").assertExists()
    }
}
