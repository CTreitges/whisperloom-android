package com.chris.whisperloom.ui

import android.app.Application
import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.Role
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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.BuildConfig
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.overlay.FloatingMicService
import com.chris.whisperloom.ui.components.HUB_DIVIDER_TAG
import com.chris.whisperloom.ui.components.hasIllustration
import com.chris.whisperloom.ui.home.HomeScreen
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.RouteRequest
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SetupRouter
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.settings.ButtonKeyboardScreen
import com.chris.whisperloom.ui.settings.HelpScreen
import com.chris.whisperloom.ui.settings.ModelsScreen
import com.chris.whisperloom.ui.settings.RecognitionScreen
import com.chris.whisperloom.whisper.DownloadState
import com.chris.whisperloom.whisper.ModelCatalog
import com.chris.whisperloom.whisper.ModelDownloads
import com.chris.whisperloom.whisper.ModelStore
import java.io.RandomAccessFile
import com.chris.whisperloom.ui.settings.SettingsHubScreen
import com.chris.whisperloom.ui.settings.SHARE_REFINE_TAG
import com.chris.whisperloom.ui.settings.TextAccessScreen
import com.chris.whisperloom.ui.settings.TextDictationScreen
import com.chris.whisperloom.ui.settings.TextRulesScreen
import com.chris.whisperloom.ui.settings.TextShareScreen
import com.chris.whisperloom.ui.setup.SetupScreen
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import com.chris.whisperloom.ui.tutorial.TutorialKind
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
 * Assistent-Schritte 1/2a und ihre Illustrationen, E2, E3, E4, B3, E5, Hub. Hohes Fenster, damit scrollende Spalten und
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

    private fun screen(env: AppEnv, content: @Composable (NavState) -> Unit): NavState {
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { content(nav) } }
        }
        compose.waitForIdle()
        return nav
    }

    /** Bildtext (TalkBack) einer Illustration. */
    /** Illustration an Drawable UND Bildtext — nur der Text liesse ein vertauschtes Bild durch. */
    private fun bild(image: Int, text: Int) = compose.onNode(hasIllustration(image, ctx.getString(text)))

    /** Fuer "nicht da" genuegt der Bildtext. */
    private fun bildtext(text: Int) = compose.onNodeWithContentDescription(ctx.getString(text))

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
        bild(R.drawable.ill_setup_engine, R.string.img_setup_engine).assertIsDisplayed()
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

    /** Emulator-Befund 3.8.0: ElevenLabs + "wie Erkennung" zeigte "Glätten · " mit leerem Modell. */
    @Test fun homeTextZeileSagtKeineTextverbesserungStattLeeremModell() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "elevenlabs"
        prefs.apiKey = "xi"
        prefs.refineMode = RefineMode.POLISH
        prefs.tutorialSeen = true
        screen(env(readyStatus)) { HomeScreen(it) }
        compose.onNodeWithText("Glätten · ElevenLabs bietet keine Textverbesserung").assertExists()
        compose.onNodeWithText("Glätten · ").assertDoesNotExist()
    }

    private fun homeMitTogether(model: String) {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "together"
        prefs.apiKey = "tg"
        prefs.llmModel = model
        prefs.refineMode = RefineMode.POLISH
        prefs.tutorialSeen = true
        screen(env(readyStatus)) { HomeScreen(it) }
    }

    @Test fun homeTextZeileBeiTogetherOhneModell() {
        homeMitTogether("")
        compose.onNodeWithText("Glätten · kein Textmodell eingetragen").assertExists()
    }

    @Test fun homeTextZeileBeiTogetherMitEingetipptemModell() {
        homeMitTogether("meta-llama/Llama-3.3-70B-Instruct-Turbo")
        compose.onNodeWithText("Glätten · meta-llama/Llama-3.3-70B-Instruct-Turbo").assertExists()
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

    /** Ein Tab der Widget-Leiste — "Widgets" steht auch als Titel da. */
    private fun widgetTab(name: String) = hasText(name) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    /**
     * Echte App-Wurzel im Pro-Tab (Deep-Link widgets: Home -> Hub -> Widgets(PRO)); liefert den
     * Back-Dispatcher fuer "Zurueck".
     */
    private fun appImProTab(): OnBackPressedDispatcher {
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        prefs.tutorialSeen = true
        prefs.proWidgetsEnabled = true
        lateinit var back: OnBackPressedDispatcher
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            WhisperLoomTheme { WhisperLoomApp(env(readyStatus), route = RouteRequest(AppNav.ROUTE_WIDGETS)) }
        }
        compose.waitForIdle()
        compose.onNode(widgetTab("Pro Widgets")).assertIsSelected()
        return back
    }

    private fun proAnleitungOeffnen() {
        compose.onNodeWithText("Anleitung ansehen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Pro Widgets freischalten").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seite 1 von 6").assertIsDisplayed()
    }

    /** Wieder im Pro-Tab, nicht in Home. */
    private fun wiederImProTab() {
        compose.onNode(widgetTab("Pro Widgets")).assertIsSelected()
        compose.onNodeWithText("Neues Pro Widget").assertExists()
        compose.onNodeWithText("Mikro-Knopf starten").assertDoesNotExist()
    }

    @Test fun proWidgetsTutorialAusDemProTabFuehrtNachUeberspringenUndLosGehtsDorthinZurueck() {
        appImProTab()
        proAnleitungOeffnen()
        compose.onNodeWithText("Überspringen").performClick()
        compose.waitForIdle()
        wiederImProTab()
        assertTrue(Prefs(ctx).agentTutorialSeen)

        proAnleitungOeffnen()
        repeat(5) {
            compose.onNodeWithText("Weiter").performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Los geht's").performClick()
        compose.waitForIdle()
        wiederImProTab()
    }

    @Test fun proWidgetsTutorialZurueckAufSeite1FuehrtZumProTab() {
        val back = appImProTab()
        proAnleitungOeffnen()
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
        wiederImProTab()
        // Zurueck auf Seite 1 = ueberspringen: das Heft gilt als gesehen.
        assertTrue(Prefs(ctx).agentTutorialSeen)
    }

    @Test fun proWidgetsTutorialOhneVorigenScreenFuehrtNachHome() {
        val nav = NavState(listOf(Screen.Tutorial(kind = TutorialKind.PRO_WIDGETS)))
        finishTutorial(nav, TutorialKind.PRO_WIDGETS)
        assertEquals(listOf(Screen.Home), nav.snapshot())
    }

    @Test fun einsteigerTutorialFuehrtWeiterNachHomeAuchAusDerHilfe() {
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub, Screen.Help(), Screen.Tutorial()))
        finishTutorial(nav, TutorialKind.BASICS)
        assertEquals(listOf(Screen.Home), nav.snapshot())
    }

    // --- Assistent ---------------------------------------------------------------

    @Test fun jederEinrichtungsschrittZeigtSeineIllustration() {
        // Spec §8.2: Illustration statt Icon-Kreis; Zugang (online) = Schluessel, Tastatur = Tutorial-Bild.
        // Spec §8.3: im Assistenten hoechstens 140 dp hoch.
        prefs.welcomeSeen = true
        prefs.engine = Engine.ONLINE
        var step by mutableIntStateOf(SetupRouter.STEP_ENGINE)
        val nav = NavState(listOf(Screen.Setup(step)))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env()) { SetupScreen(step, nav) } }
        }
        mapOf(
            SetupRouter.STEP_ENGINE to (R.drawable.ill_setup_engine to R.string.img_setup_engine),
            SetupRouter.STEP_ACCESS to (R.drawable.ill_help_key to R.string.img_help_key),
            SetupRouter.STEP_MIC to (R.drawable.ill_setup_mic to R.string.img_setup_mic),
            SetupRouter.STEP_OVERLAY to (R.drawable.ill_setup_overlay to R.string.img_setup_overlay),
            SetupRouter.STEP_A11Y to (R.drawable.ill_setup_a11y to R.string.img_setup_a11y),
            SetupRouter.STEP_NOTIF to (R.drawable.ill_setup_notif to R.string.img_setup_notif),
            SetupRouter.STEP_KEYBOARD to (R.drawable.ill_tutorial_keyboard to R.string.tutorial_img_keyboard),
        ).forEach { (s, illustration) ->
            step = s
            compose.waitForIdle()
            val hoehe = bild(illustration.first, illustration.second).assertIsDisplayed().getUnclippedBoundsInRoot().height
            // Ohne die 140-dp-Grenze des Assistenten waere das Bild so hoch wie in der Hilfe (168 dp).
            assertTrue("Schritt $s: Bild $hoehe hoch, erlaubt 140 dp", hoehe <= 140.dp)
        }
    }

    @Test fun offlineZeigtBeimModellSchrittDieOfflineIllustration() {
        prefs.welcomeSeen = true
        prefs.engine = Engine.OFFLINE
        screen(env()) { SetupScreen(SetupRouter.STEP_ACCESS, it) }
        compose.onNodeWithText("Offline-Modell laden").assertIsDisplayed()
        bild(R.drawable.ill_help_offline, R.string.img_help_offline).assertIsDisplayed()
        bildtext(R.string.img_help_key).assertDoesNotExist()
    }

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

    @Test fun schritt2aElevenLabsZeigtScribeUndDatenschutz() {
        prefs.welcomeSeen = true
        prefs.engine = Engine.ONLINE
        app(env())
        compose.onNodeWithTag("dropdown:Anbieter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("ElevenLabs (Scribe)").performClick()
        compose.waitForIdle()
        assertEquals("elevenlabs", Prefs(ctx).sttProviderId)
        assertEquals("", Prefs(ctx).apiModel)
        compose.onNodeWithText("https://api.elevenlabs.io/v1").assertExists()
        compose.onNodeWithTag("dropdown:Modell").assertTextContains("Scribe v2")
        compose.onNodeWithText("Audio wird zur Erkennung an ElevenLabs gesendet.").assertExists()
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

    /** Eine eigene ID mit abgeleiteten Flags (Snapshot von gpt-transcribe) bleibt zum Bearbeiten vorbelegt. */
    @Test fun eigenesModellMitAbgeleitetenFlagsBleibtVorbelegt() {
        prefs.welcomeSeen = true
        prefs.engine = Engine.ONLINE
        prefs.apiModel = "gpt-transcribe-2026-08-01"
        app(env())
        compose.onNodeWithTag("dropdown:Modell").assertTextContains("gpt-transcribe-2026-08-01")
        compose.onNodeWithTag("dropdown:Modell").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Eigenes Modell …").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("Modell-ID")).assertTextContains("gpt-transcribe-2026-08-01")
    }

    // --- E2 Text: Unterseiten (3.8.6) ----------------------------------------------

    @Test fun textStufeSchreibtRefineModeUndSchaltetSmartFillersFrei() {
        screen(env()) { TextDictationScreen(it) }
        compose.onNodeWithText("Füllwörter intelligent entfernen").assertIsNotEnabled()
        compose.onNodeWithText("Glätten").performClick()
        compose.waitForIdle()
        assertEquals(RefineMode.POLISH, Prefs(ctx).refineMode)
        compose.onNodeWithText("Füllwörter intelligent entfernen").assertIsEnabled()
    }

    @Test fun shareStufeIstEigeneKarteUndSchreibtNurShareRefineMode() {
        prefs.promptLevelEnabled = true
        screen(env()) { TextShareScreen(it) }
        compose.onNodeWithText("Geteilte Sprachnachrichten").assertExists()
        val inShareCard = hasAnyAncestor(hasTestTag(SHARE_REFINE_TAG))
        compose.onNode(hasText("Aus") and inShareCard).assertIsSelected()
        // "Prompt" gibt es nur fuers Diktat, nie fuer eine fremde Nachricht.
        compose.onNode(hasText("Prompt") and inShareCard).assertDoesNotExist()
        compose.onNode(hasText("Zusammenfassen") and inShareCard).performClick()
        compose.waitForIdle()
        assertEquals(RefineMode.SUMMARIZE, Prefs(ctx).shareRefineMode)
        assertEquals(RefineMode.OFF, Prefs(ctx).refineMode)
        compose.onNode(hasText("Zusammenfassen") and inShareCard).assertIsSelected()
    }

    /** Review: "intelligent entfernen" wirkt auch auf geteilte Audios — also auch dann bedienbar. */
    @Test fun intelligenteFuellwoerterSindMitNurDerShareStufeBedienbar() {
        val e = env()
        screen(e) { TextDictationScreen(it) }
        compose.onNodeWithText("Füllwörter intelligent entfernen").assertIsNotEnabled()
        compose.onNodeWithText("Gilt auch für Sprachnachrichten.", substring = true).assertDoesNotExist()
        compose.runOnIdle { e.prefs.shareRefineMode = RefineMode.POLISH } // Seite Sprachnachrichten
        compose.waitForIdle()
        compose.onNodeWithText("Gilt auch für Sprachnachrichten.", substring = true).assertExists()
        assertEquals(RefineMode.OFF, Prefs(ctx).refineMode)
        compose.onNodeWithText("Füllwörter intelligent entfernen").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(true, Prefs(ctx).smartFillers)
        // Die Absaetze bleiben Sache des Diktats: ohne Diktat-Stufe weiter gesperrt.
        compose.onNodeWithText("Automatische Absätze").assertIsNotEnabled()
    }

    @Test fun fuellwoerterSheetFuegtEigenesWortHinzu() {
        screen(env()) { TextRulesScreen(it) }
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
        screen(env()) { TextDictationScreen(it) }
        compose.onNodeWithText("Automatische Absätze").assertIsNotEnabled()
        compose.onNodeWithText("Glätten").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Automatische Absätze").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(false, Prefs(ctx).refineParagraphs)
    }

    @Test fun lesbarerGlaettenIstAusUndWirktNurMitGlaetten() {
        screen(env()) { TextDictationScreen(it) }
        compose.onNodeWithText("Wirkt mit der Stufe „Glätten“.").assertExists()
        compose.onNodeWithText("Verschönern").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Wirkt mit der Stufe „Glätten“.").assertExists()
        compose.onNodeWithText("Glätten").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Lesbarer glätten").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(true, Prefs(ctx).polishReadable)
        // Gespeichert bleibt "Glaetten" — erst die Anfrage ans Modell wird zu READABLE.
        assertEquals(RefineMode.POLISH, Prefs(ctx).refineMode)
        assertEquals(RefineMode.READABLE, Prefs(ctx).dictationStage)
        // Der Schalter der Diktat-Seite gilt nur fuers Diktat (3.8.6).
        assertEquals(false, Prefs(ctx).sharePolishReadable)
    }

    /** 3.8.6: Sprachnachrichten haben einen eigenen Schalter — die Share-Stufe macht den des Diktats nicht wirksam. */
    @Test fun lesbarerGlaettenDesDiktatsMitNurDerShareStufeOhneWirkung() {
        prefs.shareRefineMode = RefineMode.POLISH
        screen(env()) { TextDictationScreen(it) }
        assertEquals(RefineMode.OFF, Prefs(ctx).refineMode)
        compose.onNodeWithText("Wirkt mit der Stufe „Glätten“.").assertExists()
    }

    /** Review 3.5.0 HOCH: nach aus/an darf weder die Ollama-Adresse noch der Ollama-Key haengen bleiben. */
    @Test fun eigenerZugangAusUndAnVergisstAlteAdresseUndKey() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "groq"
        prefs.apiKey = "gsk-stt"
        prefs.refineMode = RefineMode.POLISH
        prefs.llmProviderId = "ollama"
        prefs.llmUrl = "http://homeserver:11434"
        prefs.llmKey = "ollama-key"
        prefs.llmModel = "gemma3"
        screen(env()) { TextAccessScreen(it) }
        compose.onNodeWithText("Eigenen Zugang verwenden").performClick() // aus
        compose.waitForIdle()
        compose.onNodeWithText("Eigenen Zugang verwenden").performClick() // wieder an
        compose.waitForIdle()
        val p = Prefs(ctx)
        assertEquals("groq", p.llmProviderId)
        assertEquals("", p.llmUrl)
        assertEquals("", p.llmKey)
        val llm = p.llmAccess()
        assertEquals("https://api.groq.com/openai/v1", llm.baseUrl)
        assertEquals("gsk-stt", llm.apiKey) // der eigene Groq-Key, nicht der von Ollama
    }

    @Test fun ollamaImHeimnetzFragtNachDerServerAdresse() {
        prefs.refineMode = RefineMode.POLISH
        prefs.llmProviderId = "ollama"
        screen(env()) { TextAccessScreen(it) }
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
        screen(env()) { TextAccessScreen(it) }
        compose.onNodeWithText("https://ollama.com").assertExists()
        // Leeres Modell = Empfehlung je Stufe (3.8.6), nicht mehr das erste Katalogmodell.
        compose.onNodeWithTag("dropdown:Modell").assertTextContains("Empfehlung je Stufe")
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

    /** Reiner Erkennungs-Anbieter: "wie Erkennung" hiesse keine Textverbesserung — das klar sagen. */
    @Test fun textZugangBeiElevenLabsSagtKeineTextverbesserung() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "elevenlabs"
        prefs.apiKey = "xi-stt"
        prefs.refineMode = RefineMode.POLISH
        screen(env()) { TextAccessScreen(it) }
        compose.onNodeWithText("ElevenLabs erkennt nur Sprache und bietet keine Textverbesserung.").assertExists()
        // Kein Modellfeld fuer einen Anbieter ohne Textmodelle, keine Pruefung ins Leere.
        compose.onNode(hasSetTextAction() and hasText("Modell")).assertDoesNotExist()
        compose.onNodeWithText("Zugang prüfen").assertIsNotEnabled()
        compose.onNodeWithText("Eigenen Zugang eintragen").performClick()
        compose.waitForIdle()
        val p = Prefs(ctx)
        assertEquals("openai", p.llmProviderId)
        assertEquals("", p.llmAccess().apiKey) // der ElevenLabs-Key geht nie an OpenAI
    }

    /** Together/DeepInfra "wie Erkennung": Chat geht mit eingetipptem Modell — freies Feld wie auf main. */
    @Test fun textZugangBeiTogetherWieErkennungZeigtFreiesModellfeld() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "together"
        prefs.apiKey = "tg"
        prefs.refineMode = RefineMode.POLISH
        screen(env()) { TextAccessScreen(it) }
        compose.onNodeWithText("bietet keine Textverbesserung", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Zugang prüfen").assertIsNotEnabled() // ohne Modell ginge die Pruefung ins Leere
        compose.onNode(hasSetTextAction() and hasText("Modell")).performTextInput("meta-llama/Llama-3.3-70B-Instruct-Turbo")
        compose.waitForIdle()
        assertEquals("meta-llama/Llama-3.3-70B-Instruct-Turbo", Prefs(ctx).llmModel)
        assertEquals("same", Prefs(ctx).llmProviderId)
        compose.onNodeWithText("Zugang prüfen").assertIsEnabled()
    }

    @Test fun textZugangBeiTogetherWieErkennungMitProOhnePicker() {
        // Die Together-Liste taugt nur fuer die Erkennung: kein Picker, kein Nachladen, freies Feld.
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "together"
        prefs.serverModelsEnabled = true
        prefs.refineMode = RefineMode.POLISH
        screen(env()) { TextAccessScreen(it) }
        compose.onNodeWithTag("picker:Modell").assertDoesNotExist()
        compose.onNodeWithText("Modelle aktualisieren").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Modell")).assertExists()
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
        // Die Textmodelle darunter haben eigene Empfehlung und RAM-Grenze (TextModelUiTest).
        compose.onNode(hasText("Small") and hasText("Empfohlen")).assertExists()
        compose.onNode(hasText("Large v3 Turbo") and hasText("Für dieses Gerät zu groß")).assertExists()
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

    @Test fun kontextFeldNenntBeiElevenLabsKeytermsUndAufpreis() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "elevenlabs"
        prefs.apiKey = "k"
        screen(env()) { RecognitionScreen(it) }
        compose.onNodeWithText("etwa 20 % Aufpreis", substring = true).assertExists()
        compose.onNodeWithText("Dieser Anbieter nimmt kein Vokabular entgegen", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Kostet nichts extra", substring = true).assertDoesNotExist()
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

    @Test fun hilfeHatAchtAbschnitteInDieserReihenfolge() {
        screen(env()) { HelpScreen(1, it) }
        val reihenfolge = listOf(
            "So funktioniert's", "Einrichtung Schritt für Schritt", "API-Key bekommen", "Eigener Server",
            "Offline-Modus", "Widgets & Pro Widgets", "Datenschutz", "Wenn etwas nicht klappt",
        )
        val oben = reihenfolge.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals("Von oben nach unten: $reihenfolge", oben.sorted(), oben)
    }

    /** Eine Hilfe-Karte: Titel, Illustration mit Bildtext und der Kurztext direkt darunter. */
    private data class HilfeKarte(val titel: String, val bild: Int, val bildtext: Int, val kurztext: Int)

    @Test fun jederHilfeAbschnittBeginntMitSeinerIllustration() {
        screen(env()) { HelpScreen(1, it) }
        val karten = listOf(
            HilfeKarte("So funktioniert's", R.drawable.ill_tutorial_button, R.string.tutorial_img_button, R.string.help_s1_intro),
            HilfeKarte("Einrichtung Schritt für Schritt", R.drawable.ill_help_setup, R.string.img_help_setup, R.string.help_s2_intro),
            HilfeKarte("API-Key bekommen", R.drawable.ill_help_key, R.string.img_help_key, R.string.help_s3_intro),
            // Eigenes Bild fuer den Erkennungs-Server — nicht der Editor eines Pro Widgets (ill_agent_server).
            HilfeKarte("Eigener Server", R.drawable.ill_help_server, R.string.img_help_server, R.string.help_s4_intro),
            HilfeKarte("Offline-Modus", R.drawable.ill_help_offline, R.string.img_help_offline, R.string.help_s5_body),
            HilfeKarte("Widgets & Pro Widgets", R.drawable.ill_pro_widgets, R.string.img_pro_widgets, R.string.help_widgets_intro),
            HilfeKarte("Datenschutz", R.drawable.ill_help_privacy, R.string.img_help_privacy, R.string.help_s6_intro),
            HilfeKarte("Wenn etwas nicht klappt", R.drawable.ill_help_trouble, R.string.img_help_trouble, R.string.help_s7_intro),
        )
        // Der Kurztext traegt Inhalt, der beim Kuerzen aus den Details dorthin gewandert ist
        // (z. B. Datenschutz: "speichert keine Aufnahmen") — er muss unter dem Bild stehen.
        fun beginntMitBildUndKurztext(k: HilfeKarte) {
            val bild = bild(k.bild, k.bildtext).assertExists().fetchSemanticsNode().boundsInRoot
            val kurz = compose.onNodeWithText(ctx.getString(k.kurztext)).assertExists().fetchSemanticsNode().boundsInRoot
            assertTrue("${k.titel}: Kurztext steht unter dem Bild", kurz.top >= bild.bottom)
        }
        // Abschnitt 1 ist offen, alle anderen zu: nur sein Bild und sein Kurztext sind da.
        beginntMitBildUndKurztext(karten.first())
        karten.drop(1).forEach { k ->
            bildtext(k.bildtext).assertDoesNotExist()
            compose.onNodeWithText(ctx.getString(k.kurztext)).assertDoesNotExist()
        }
        karten.drop(1).forEach { k ->
            compose.onNodeWithText(k.titel).performClick()
            compose.waitForIdle()
            beginntMitBildUndKurztext(k)
            // Wieder zu, sonst faellt der Rest aus dem Sichtbereich der LazyColumn.
            compose.onNodeWithText(k.titel).performClick()
            compose.waitForIdle()
        }
    }

    @Test fun abschnitt6SindDieWidgetsMitWegZurAnleitung() {
        val nav = screen(env()) { HelpScreen(6, it) }
        bild(R.drawable.ill_pro_widgets, R.string.img_pro_widgets).assertExists()
        bildtext(R.string.img_help_offline).assertDoesNotExist()
        bildtext(R.string.img_help_privacy).assertDoesNotExist()
        compose.onNodeWithText("Freischalten: Einstellungen → Erweitert → Pro Widgets.").assertExists()
        // Spec §8.3: anlegen (Name), Server je Widget, platzieren (Widget-Profil) — jede Zeile ist Pflicht.
        listOf(R.string.help_widgets_create, R.string.help_widgets_server, R.string.help_widgets_place).forEach {
            compose.onNodeWithText(ctx.getString(it)).assertExists()
        }
        compose.onNodeWithText("Server je Widget", substring = true).assertExists()
        // 3.8.0: die zweite Freischalt-Funktion unter "Erweitert" steht dort, wo "Erweitert" erklaert wird.
        compose.onNodeWithText(ctx.getString(R.string.help_widgets_models)).assertExists()
        assertTrue(ctx.getString(R.string.help_widgets_models).contains("„${ctx.getString(R.string.pro_server_models)}“"))
        compose.onNodeWithText("Anleitung Pro Widgets").performClick()
        compose.waitForIdle()
        assertEquals(Screen.Tutorial(kind = TutorialKind.PRO_WIDGETS), nav.current)
    }

    @Test fun abschnitt3NenntElevenLabsMitBerechtigungSpeechToText() {
        screen(env()) { HelpScreen(3, it) }
        compose.onNodeWithText("ElevenLabs (Scribe)").assertExists()
        compose.onNodeWithText(ctx.getString(R.string.help_key_elevenlabs)).assertExists()
        assertTrue(ctx.getString(R.string.help_key_elevenlabs).contains("„Speech to Text“"))
    }

    @Test fun abschnitt6FuehrtZuDenWidgets() {
        val nav = screen(env()) { HelpScreen(6, it) }
        compose.onNodeWithText("Zu den Widgets").performClick()
        compose.waitForIdle()
        assertEquals(Screen.Widgets(), nav.current)
    }

    @Test fun abschnitt7IstDerDatenschutz() {
        screen(env()) { HelpScreen(7, it) }
        bild(R.drawable.ill_help_privacy, R.string.img_help_privacy).assertExists()
        bildtext(R.string.img_pro_widgets).assertDoesNotExist()
    }

    @Test fun einZuHoherAbschnittOeffnetDenLetzten() {
        // Bis 3.7.0 war 7 der letzte Abschnitt; jetzt ist es 8 "Wenn etwas nicht klappt".
        screen(env()) { HelpScreen(99, it) }
        bild(R.drawable.ill_help_trouble, R.string.img_help_trouble).assertExists()
        compose.onNodeWithText("Knopf erscheint nicht").assertExists()
        bildtext(R.string.img_help_privacy).assertDoesNotExist()
    }

    @Test fun knopfUndTastaturBeginntMitDerKurzanleitung() {
        screen(env()) { ButtonKeyboardScreen(it) }
        bild(R.drawable.ill_tutorial_button, R.string.tutorial_img_button).assertExists()
        val kurzanleitung = compose.onNodeWithText("Kurzanleitung").fetchSemanticsNode().boundsInRoot.top
        val knopf = compose.onNodeWithText("Schwebender Knopf").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Kurzanleitung steht oben", kurzanleitung < knopf)
        compose.onNodeWithText("Knopf antippen = Aufnahme").assertExists()
    }

    @Test fun hubZeigtAchtZeilenInVierGruppen() {
        // User-Entscheidung U3: Grundlagen, Bedienung, Pro, Info — in genau dieser Reihenfolge.
        prefs.engine = Engine.ONLINE
        screen(env()) { SettingsHubScreen(it) }
        val reihenfolge = listOf(
            "GRUNDLAGEN", "Erkennung", "Offline-Modelle", "Text",
            "BEDIENUNG", "Knopf & Tastatur", "Widgets",
            "PRO", "Erweitert",
            "INFO", "Anleitung & Hilfe", "Über WhisperLoom",
        )
        val oben = reihenfolge.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals("Von oben nach unten: $reihenfolge", oben.sorted(), oben)
        assertEquals("Keine zwei auf einer Hoehe", oben.size, oben.toSet().size)
        compose.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertExists()
    }

    @Test fun dieLetzteZeileJederGruppeHatKeinenTrenner() {
        prefs.engine = Engine.ONLINE
        screen(env()) { SettingsHubScreen(it) }
        val zeilen = listOf(
            "Erkennung", "Offline-Modelle", "Text", "Knopf & Tastatur", "Widgets", "Erweitert",
            "Anleitung & Hilfe", "Über WhisperLoom",
        ).map { it to compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        // Jeder Trenner gehoert zur naechsthoeheren Zeile ueber ihm.
        val mitTrenner = compose.onAllNodesWithTag(HUB_DIVIDER_TAG).fetchSemanticsNodes().map { trenner ->
            zeilen.filter { it.second < trenner.boundsInRoot.top }.maxBy { it.second }.first
        }
        assertEquals(listOf("Erkennung", "Offline-Modelle", "Knopf & Tastatur", "Anleitung & Hilfe"), mitTrenner)
    }

    @Test fun dieGruppenSindUeberschriftenInNormalerSchreibung() {
        // Grossbuchstaben nur fuer das Auge; TalkBack liest "Grundlagen" und springt per Ueberschrift.
        prefs.engine = Engine.ONLINE
        screen(env()) { SettingsHubScreen(it) }
        mapOf("GRUNDLAGEN" to "Grundlagen", "BEDIENUNG" to "Bedienung", "PRO" to "Pro", "INFO" to "Info").forEach { (sichtbar, gelesen) ->
            compose.onNodeWithText(sichtbar)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(gelesen)))
        }
    }

    @Test fun ohneProFunktionenStehtErweitertAufAus() {
        prefs.engine = Engine.ONLINE
        screen(env()) { SettingsHubScreen(it) }
        // Kein Schalter auf Hub-Ebene (Spec §2.3) — nur der Wert.
        compose.onNodeWithText("Aus").assertExists()
    }

    @Test fun eingeschalteteProWidgetsStehenUnterErweitert() {
        // Der Server steht seit 3.7.1 je Widget im Profil — im Hub zaehlt nur der Schalter.
        prefs.engine = Engine.ONLINE
        prefs.proWidgetsEnabled = true
        screen(env()) { SettingsHubScreen(it) }
        compose.onNodeWithText("Pro Widgets").assertExists()
        compose.onNodeWithText("Aus").assertDoesNotExist()
    }

    @Test fun beideProFunktionenStehenUnterErweitert() {
        prefs.engine = Engine.ONLINE
        prefs.proWidgetsEnabled = true
        prefs.promptLevelEnabled = true
        screen(env()) { SettingsHubScreen(it) }
        compose.onNodeWithText("Pro Widgets · Stufe „Prompt“").assertExists()
    }

    @Test fun modelleVomServerStehenUnterErweitert() {
        prefs.engine = Engine.ONLINE
        prefs.promptLevelEnabled = true
        prefs.serverModelsEnabled = true
        screen(env()) { SettingsHubScreen(it) }
        compose.onNodeWithText("Stufe „Prompt“ · Modelle vom Server").assertExists()
    }

    // --- Patchnotes (P): "?" neben der Versionsnummer ------------------------------

    /** Echte App-Wurzel, eingerichtet; [route] z. B. Einstellungen. Liefert den Back-Dispatcher. */
    private fun appMitZurueck(route: RouteRequest? = null): OnBackPressedDispatcher {
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        prefs.tutorialSeen = true
        lateinit var back: OnBackPressedDispatcher
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            WhisperLoomTheme { WhisperLoomApp(env(readyStatus), route = route) }
        }
        compose.waitForIdle()
        return back
    }

    /** Kopf der neuesten Version — immer das erste Item der Patchnotes-Liste. */
    private val patchnotesHero = hasContentDescription("Was ist neu in Version", substring = true)

    /** Patchnotes sind geladen (Assets auf Dispatchers.IO). */
    private fun patchnotesGeladen() {
        compose.waitUntil(5_000) { compose.onAllNodes(patchnotesHero).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun homeFragezeichenOeffnetPatchnotes() {
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        val nav = screen(env(readyStatus)) { HomeScreen(it) }
        compose.onNodeWithContentDescription("Patchnotes anzeigen").assertIsDisplayed().performClick()
        compose.waitForIdle()
        assertEquals(Screen.Patchnotes, nav.current)
    }

    @Test fun ausDenPatchnotesFuehrtZurueckNachHome() {
        val back = appMitZurueck()
        compose.onNodeWithContentDescription("Patchnotes anzeigen").performClick()
        compose.waitForIdle()
        patchnotesGeladen()
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Mikro-Knopf starten").assertIsDisplayed()
        compose.onNode(patchnotesHero).assertDoesNotExist()
    }

    @Test fun ueberSheetFragezeichenOeffnetPatchnotesOhneSheet() {
        prefs.engine = Engine.ONLINE
        val nav = screen(env()) { SettingsHubScreen(it) }
        compose.onNodeWithText("Über WhisperLoom").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Quellcode auf GitHub").assertExists()
        compose.onNodeWithContentDescription("Patchnotes anzeigen").performClick()
        compose.waitForIdle()
        assertEquals(Screen.Patchnotes, nav.current)
        compose.onNodeWithText("Quellcode auf GitHub").assertDoesNotExist()
    }

    @Test fun ausDenPatchnotesFuehrtZurueckInDenHubOhneSheet() {
        val back = appMitZurueck(RouteRequest(AppNav.ROUTE_SETTINGS))
        compose.onNodeWithText("Über WhisperLoom").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Patchnotes anzeigen").performClick()
        compose.waitForIdle()
        patchnotesGeladen()
        compose.runOnIdle { back.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Anleitung & Hilfe").assertIsDisplayed()
        compose.onNodeWithText("Quellcode auf GitHub").assertDoesNotExist()
    }

    @Test fun hilfeFusszeileOeffnetPatchnotes() {
        val nav = screen(env()) { HelpScreen(1, it) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Patchnotes anzeigen"))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Patchnotes anzeigen").performClick()
        compose.waitForIdle()
        assertEquals(Screen.Patchnotes, nav.current)
    }
}
