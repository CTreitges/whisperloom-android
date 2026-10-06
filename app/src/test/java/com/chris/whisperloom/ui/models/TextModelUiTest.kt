package com.chris.whisperloom.ui.models

import android.app.Application
import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.llm.installSparse
import com.chris.whisperloom.ui.home.HomeScreen
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.SetupRouter
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.settings.ModelsScreen
import com.chris.whisperloom.ui.settings.RecognitionScreen
import com.chris.whisperloom.ui.settings.SettingsHubScreen
import com.chris.whisperloom.ui.settings.TextSettingsScreen
import com.chris.whisperloom.ui.setup.SetupScreen
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import com.chris.whisperloom.whisper.DownloadState
import com.chris.whisperloom.whisper.ModelCatalog
import com.chris.whisperloom.whisper.ModelDownloadService
import com.chris.whisperloom.whisper.ModelDownloads
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.TextModelCatalog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Das lokale Textmodell in der Oberflaeche (Spec §4), Compose-Semantik unter Robolectric:
 * E4 Abschnitt Textverbesserung, Pflichtkarte "Offline ohne Textmodell", E2 Karte "Offline-Erkennung"
 * und Online-Zugang je Regel, Assistent 2b, Home-Zeile und -Banner, Hub-Unterzeilen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class TextModelUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    @Before fun setUp() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
    }

    @After fun tearDown() {
        ModelStore(ctx).dir.deleteRecursively()
    }

    private fun env(status: SystemStatus = SystemStatus()) = AppEnv(PrefsState(prefs), status) { status }

    private fun screen(env: AppEnv, content: @Composable (NavState) -> Unit): NavState {
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { content(nav) } }
        }
        compose.waitForIdle()
        return nav
    }

    private fun size(bytes: Long) = Formatter.formatShortFileSize(ctx, bytes)

    /** Der zuletzt gestartete Dienst (ShadowApplication), null = keiner. */
    private fun startedService(): Intent? = shadowOf(ctx as Application).nextStartedService

    // --- E4 Offline-Modelle: Abschnitt Textverbesserung --------------------------------------

    @Test fun offlineModelleHabenZweiAbschnitteInDieserReihenfolge() {
        screen(env()) { ModelsScreen(it) }
        val reihenfolge = listOf(
            "SPRACHERKENNUNG", "Small", "Quelle: huggingface.co/ggerganov/whisper.cpp",
            "TEXTVERBESSERUNG", "Gemma 4 E2B", "Gemma 4 E4B", "Quelle: huggingface.co/litert-community (Gemma 4, Apache 2.0)",
        )
        val oben = reihenfolge.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals("Von oben nach unten: $reihenfolge", oben.sorted(), oben)
        // Ueberschriften: TalkBack liest normale Schreibung und springt per Ueberschrift.
        compose.onNodeWithText("TEXTVERBESSERUNG")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Textverbesserung")))
    }

    @Test fun textmodelleNennenGroesseInklusiveCacheUndE4bIstUnter8GbGedimmt() {
        screen(env(SystemStatus(totalRamBytes = 6L shl 30))) { ModelsScreen(it) }
        val e2b = TextModelCatalog.GEMMA4_E2B
        compose.onNodeWithText(
            "${size(e2b.bytes)} · +${size(e2b.extraDiskBytes)} beim ersten Start · ~1,8 GB RAM · ab 6 GB Gerätespeicher",
        ).assertExists()
        compose.onNodeWithContentDescription("Gemma 4 E2B herunterladen").assertIsEnabled()
        compose.onNodeWithContentDescription("Gemma 4 E4B herunterladen").assertIsNotEnabled()
        compose.onNodeWithText("Für dieses Gerät zu groß").assertExists()
    }

    @Test fun ladenStartetDenDownloadDesTextmodellsNachDemDialogFuerMobileDaten() {
        screen(env()) { ModelsScreen(it) }
        compose.onNodeWithContentDescription("Gemma 4 E2B herunterladen").performClick()
        compose.waitForIdle()
        // Robolectric meldet ein Mobilfunknetz: erst D2, ohne Bestaetigung kein Download.
        compose.onNodeWithText("${size(TextModelCatalog.GEMMA4_E2B.bytes)} werden heruntergeladen. Im WLAN ist das kostenlos.").assertExists()
        assertEquals(null, startedService())
        compose.onNodeWithText("Laden").performClick()
        compose.waitForIdle()
        val started = startedService()
        assertEquals(ModelDownloadService::class.java.name, started?.component?.className)
        assertEquals("gemma4_e2b", started?.getStringExtra(ModelDownloadService.EXTRA_MODEL_ID))
    }

    @Test fun installiertesTextmodellIstWaehlbarUndSetztDasLokaleModell() {
        installSparse(ctx, TextModelCatalog.GEMMA4_E4B)
        screen(env()) { ModelsScreen(it) }
        compose.onNode(isSelectable() and hasText("Gemma 4 E2B")).assertIsNotEnabled().assertIsSelected() // Standard, nicht geladen
        compose.onNode(isSelectable() and hasText("Gemma 4 E4B")).assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals("gemma4_e4b", Prefs(ctx).localLlmModel)
        assertEquals("whisper-Auswahl unberuehrt", "small", Prefs(ctx).offlineModel)
    }

    @Test fun textmodellLoeschenSagtWasOfflineDannPassiert() {
        installSparse(ctx, TextModelCatalog.GEMMA4_E2B)
        screen(env()) { ModelsScreen(it) }
        compose.onNodeWithContentDescription("Gemma 4 E2B löschen").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(
            "${size(TextModelCatalog.GEMMA4_E2B.bytes)} werden frei. Für die lokale Textverbesserung muss dann wieder ein Textmodell geladen werden." +
                "\n\nDies ist das aktive Textmodell — offline kommt der Text danach ohne KI.",
        ).assertExists()
        compose.onNodeWithText("Löschen").performClick()
        compose.waitForIdle()
        assertEquals(false, ModelStore(ctx).isInstalled(TextModelCatalog.GEMMA4_E2B))
    }

    // --- Pflichtkarte "Offline ohne Textmodell" ----------------------------------------------

    /** Offline mit Stufe "Glaetten", Regel [rule], kein Textmodell. */
    private fun offlineOhneTextmodell(rule: OfflineRefineRule = OfflineRefineRule.LOCAL) {
        prefs.engine = Engine.OFFLINE
        prefs.refineMode = RefineMode.POLISH
        prefs.offlineRefine = rule
    }

    private val pflichtkarte get() = compose.onAllNodesWithTag(LOCAL_MODEL_REQUIRED_TAG)

    @Test fun ladeModellIstDasGewaehlteOderDasEmpfohleneWennEsNichtPasst() {
        assertEquals(TextModelCatalog.GEMMA4_E4B, textModelToLoad("gemma4_e4b", 8L shl 30))
        assertEquals(TextModelCatalog.GEMMA4_E2B, textModelToLoad("gemma4_e4b", 6L shl 30))
        assertEquals(TextModelCatalog.GEMMA4_E2B, textModelToLoad("unbekannt", 8L shl 30))
    }

    @Test fun pflichtkarteInDerErkennungBeiOfflineOhneTextmodell() {
        offlineOhneTextmodell()
        screen(env(SystemStatus(installedModels = setOf("small")))) { RecognitionScreen(it) }
        pflichtkarte.assertCountEquals(1)
        compose.onNodeWithText("Offline ohne Textmodell").assertExists()
        compose.onNodeWithText("Textmodell laden (${size(TextModelCatalog.GEMMA4_E2B.bytes)})").assertIsEnabled()
    }

    @Test fun ueberspringenSetztDieRegelUndDieKarteVerschwindet() {
        offlineOhneTextmodell(OfflineRefineRule.ONLINE_LOCAL)
        screen(env(SystemStatus(installedModels = setOf("small")))) { RecognitionScreen(it) }
        compose.onNodeWithText("Überspringen").performClick()
        compose.waitForIdle()
        assertEquals(OfflineRefineRule.SKIP, Prefs(ctx).offlineRefine)
        pflichtkarte.assertCountEquals(0)
    }

    @Test fun keinePflichtkarteWennNichtsFehlt() {
        // Online erkannt: die Regel gilt nicht.
        offlineOhneTextmodell()
        prefs.engine = Engine.ONLINE
        screen(env()) { RecognitionScreen(it) }
        pflichtkarte.assertCountEquals(0)
    }

    @Test fun keinePflichtkarteMitGeladenemTextmodellOderOhneStufe() {
        offlineOhneTextmodell()
        val ready = SystemStatus(installedModels = setOf("small"), installedTextModels = setOf("gemma4_e2b"))
        screen(env(ready)) { RecognitionScreen(it) }
        pflichtkarte.assertCountEquals(0)
    }

    @Test fun keinePflichtkarteWennBeideStufenAusSind() {
        offlineOhneTextmodell()
        prefs.refineMode = RefineMode.OFF
        screen(env(SystemStatus(installedModels = setOf("small")))) { RecognitionScreen(it) }
        pflichtkarte.assertCountEquals(0)
    }

    @Test fun pflichtkarteAuchBeiNurDerShareStufe() {
        offlineOhneTextmodell()
        prefs.refineMode = RefineMode.OFF
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        screen(env(SystemStatus(installedModels = setOf("small")))) { ModelsScreen(it) }
        pflichtkarte.assertCountEquals(1)
    }

    @Test fun pflichtkarteLaedtDasEmpfohleneWennDasGewaehlteZuGrossIst() {
        offlineOhneTextmodell()
        prefs.localLlmModel = "gemma4_e4b"
        screen(env(SystemStatus(installedModels = setOf("small"), totalRamBytes = 6L shl 30))) { RecognitionScreen(it) }
        compose.onNodeWithText("Textmodell laden (${size(TextModelCatalog.GEMMA4_E2B.bytes)})").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Laden").performClick() // D2, Robolectric meldet Mobilfunk
        compose.waitForIdle()
        assertEquals("gemma4_e2b", startedService()?.getStringExtra(ModelDownloadService.EXTRA_MODEL_ID))
        assertEquals("gemma4_e2b", Prefs(ctx).localLlmModel)
    }

    @Test fun waehrendDesDownloadsFortschrittStattKnoepfe() {
        offlineOhneTextmodell()
        ModelDownloads.update("gemma4_e2b", DownloadState.Running(1_294_073_856, TextModelCatalog.GEMMA4_E2B.bytes, 5_000_000))
        try {
            screen(env(SystemStatus(installedModels = setOf("small")))) { RecognitionScreen(it) }
            pflichtkarte.assertCountEquals(1)
            compose.onNodeWithText("50 %", substring = true).assertExists()
            compose.onNodeWithText("Überspringen").assertDoesNotExist()
        } finally {
            ModelDownloads.clear("gemma4_e2b")
        }
    }

    @Test fun ladenIstGesperrtSolangeEinAndererDownloadLaeuft() {
        offlineOhneTextmodell()
        ModelDownloads.update("small", DownloadState.Running(1_000, ModelCatalog.SMALL.bytes, 500))
        try {
            screen(env(SystemStatus(installedModels = setOf("base")))) { RecognitionScreen(it) }
            compose.onNodeWithText("Textmodell laden (${size(TextModelCatalog.GEMMA4_E2B.bytes)})").assertIsNotEnabled()
            compose.onNodeWithText("Überspringen").assertIsEnabled()
        } finally {
            ModelDownloads.clear("small")
        }
    }

    // --- Geraet, auf das kein Textmodell passt (4 GB: offline ja, Gemma nein) -----------------

    private val vierGb = 4L shl 30

    @Test fun passtKeinTextmodellGibtEsKeinePflichtkarte() {
        offlineOhneTextmodell()
        screen(env(SystemStatus(installedModels = setOf("small"), totalRamBytes = vierGb))) { RecognitionScreen(it) }
        pflichtkarte.assertCountEquals(0)
    }

    @Test fun passtKeinTextmodellWarntHomeNicht() {
        offlineOhneTextmodell()
        screen(env(homeStatus().copy(totalRamBytes = vierGb))) { HomeScreen(it) }
        compose.onNodeWithText("Glätten · offline übersprungen").assertExists()
        compose.onNodeWithText("Beheben").assertDoesNotExist()
    }

    @Test fun passtKeinTextmodellSindDieLokalenRegelnMitGrundGesperrt() {
        offlineOhneTextmodell()
        screen(env(SystemStatus(installedModels = setOf("small"), totalRamBytes = vierGb))) { TextSettingsScreen(it) }
        pflichtkarte.assertCountEquals(0)
        compose.onNodeWithText("Für ein lokales Textmodell braucht das Gerät mindestens 6 GB RAM.").assertExists()
        compose.onNodeWithText("Laden (${size(TextModelCatalog.GEMMA4_E2B.bytes)})").assertDoesNotExist()
        compose.onNode(isSelectable() and hasText("Lokales Textmodell")).assertIsNotEnabled()
        compose.onNode(isSelectable() and hasText("Online, ohne Netz lokal")).assertIsNotEnabled()
        compose.onNode(isSelectable() and hasText("Überspringen")).assertIsEnabled().assertIsSelected()
        // Der Online-Zugang erklaert "Ueberspringen", nicht das lokale Textmodell.
        compose.onNodeWithText("Ohne eigenen Zugang kommt der Text bei Offline-Erkennung ohne KI.", substring = true).assertExists()
    }

    // --- E2 Text: Karte "Offline-Erkennung" und Online-Zugang ---------------------------------

    @Test fun karteOfflineErkennungStehtZwischenShareUndOnlineZugang() {
        screen(env()) { TextSettingsScreen(it) }
        val reihenfolge = listOf("Geteilte Sprachnachrichten", "Offline-Erkennung", "Online-Zugang für die Textverbesserung", "Regeln ohne KI")
        val oben = reihenfolge.map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertEquals("Von oben nach unten: $reihenfolge", oben.sorted(), oben)
        compose.onNodeWithText("Textverbesserung bei Offline-Erkennung").assertExists()
    }

    @Test fun regelStandardIstLokalUndDasRadioSchreibtDiePref() {
        screen(env()) { TextSettingsScreen(it) }
        compose.onNode(isSelectable() and hasText("Lokales Textmodell")).assertIsSelected()
        compose.onNode(isSelectable() and hasText("Online, ohne Netz lokal")).performClick()
        compose.waitForIdle()
        assertEquals(OfflineRefineRule.ONLINE_LOCAL, Prefs(ctx).offlineRefine)
        compose.onNode(isSelectable() and hasText("Überspringen")).performClick()
        compose.waitForIdle()
        assertEquals(OfflineRefineRule.SKIP, Prefs(ctx).offlineRefine)
        compose.onNode(isSelectable() and hasText("Überspringen")).assertIsSelected()
    }

    @Test fun textKarteZeigtDiePflichtkarteStattDerModellzeile() {
        offlineOhneTextmodell()
        screen(env(SystemStatus(installedModels = setOf("small")))) { TextSettingsScreen(it) }
        pflichtkarte.assertCountEquals(1)
        compose.onNodeWithText("Kein Textmodell geladen").assertDoesNotExist()
    }

    @Test fun ohnePflichtLaedtDieModellzeileOhneDieRegelZuAendern() {
        offlineOhneTextmodell(OfflineRefineRule.SKIP)
        screen(env(SystemStatus(installedModels = setOf("small")))) { TextSettingsScreen(it) }
        pflichtkarte.assertCountEquals(0)
        compose.onNodeWithText("Kein Textmodell geladen").assertExists()
        compose.onNodeWithText("Laden (${size(TextModelCatalog.GEMMA4_E2B.bytes)})").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Laden").performClick() // D2
        compose.waitForIdle()
        assertEquals("gemma4_e2b", startedService()?.getStringExtra(ModelDownloadService.EXTRA_MODEL_ID))
        assertEquals("Regel bleibt, wie gewaehlt", OfflineRefineRule.SKIP, Prefs(ctx).offlineRefine)
    }

    @Test fun geladenesTextmodellMitAendernZuDenOfflineModellen() {
        prefs.engine = Engine.OFFLINE
        val nav = screen(env(SystemStatus(installedModels = setOf("small"), installedTextModels = setOf("gemma4_e2b")))) {
            TextSettingsScreen(it)
        }
        compose.onNodeWithText("Gemma 4 E2B").assertExists()
        compose.onNodeWithText("Geladen · ${size(TextModelCatalog.GEMMA4_E2B.bytes)}").assertExists()
        compose.onNodeWithText("Ändern").performClick()
        compose.waitForIdle()
        assertEquals(Screen.Models, nav.current)
    }

    @Test fun onlineZugangOfflineMitLokalBrauchtKeinenZugang() {
        prefs.engine = Engine.OFFLINE
        screen(env()) { TextSettingsScreen(it) }
        compose.onNodeWithText("Bei Offline-Erkennung verbessert das lokale Textmodell — dafür brauchst du keinen Online-Zugang.")
            .assertExists()
        compose.onNodeWithText("Eigenen Zugang eintragen").assertDoesNotExist()
    }

    @Test fun onlineZugangOfflineMitOnlineLokalOderUeberspringenBietetEigenenZugangAn() {
        prefs.engine = Engine.OFFLINE
        prefs.offlineRefine = OfflineRefineRule.ONLINE_LOCAL
        screen(env()) { TextSettingsScreen(it) }
        compose.onNodeWithText("Ohne eigenen Zugang verbessert bei Offline-Erkennung das lokale Textmodell.", substring = true)
            .assertExists()
        compose.onNode(isSelectable() and hasText("Überspringen")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ohne eigenen Zugang kommt der Text bei Offline-Erkennung ohne KI.", substring = true).assertExists()
        compose.onNodeWithText("Eigenen Zugang eintragen").performClick()
        compose.waitForIdle()
        assertEquals("eigener Zugang an", true, PrefsState(Prefs(ctx)).llmUseOwn)
    }

    @Test fun karteFehltWennOfflineAufDemGeraetNichtGeht() {
        screen(env(SystemStatus(offlineSupported = false))) { TextSettingsScreen(it) }
        compose.onNodeWithText("Offline-Erkennung").assertDoesNotExist()
    }

    // --- Assistent 2b ------------------------------------------------------------------------

    private fun schritt2b(status: SystemStatus = SystemStatus(installedModels = setOf("small"))) {
        prefs.welcomeSeen = true
        prefs.engine = Engine.OFFLINE
        screen(env(status)) { SetupScreen(SetupRouter.STEP_ACCESS, it) }
    }

    @Test fun schritt2bWeiterErstNachDerWahl() {
        schritt2b()
        compose.onNodeWithText("Textverbesserung ohne Netz").assertExists()
        compose.onNodeWithText("Textmodell laden oder „Überspringen“ wählen.").assertExists()
        compose.onNodeWithText("Weiter").assertIsNotEnabled()
        compose.onNode(isSelectable() and hasText("Überspringen")).performClick()
        compose.waitForIdle()
        assertEquals(OfflineRefineRule.SKIP, Prefs(ctx).offlineRefine)
        compose.onNode(isSelectable() and hasText("Überspringen")).assertIsSelected()
        compose.onNodeWithText("Weiter").assertIsEnabled()
    }

    @Test fun schritt2bLokalStartetDenDownloadUndAendertDieStufeNicht() {
        prefs.offlineRefine = OfflineRefineRule.SKIP
        schritt2b()
        compose.onNode(isSelectable() and hasText("Lokales Textmodell")).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Laden").performClick() // D2
        compose.waitForIdle()
        assertEquals("gemma4_e2b", startedService()?.getStringExtra(ModelDownloadService.EXTRA_MODEL_ID))
        assertEquals("Klick ist die Wahl", OfflineRefineRule.LOCAL, Prefs(ctx).offlineRefine)
        assertEquals("Stufe bleibt", RefineMode.OFF, Prefs(ctx).refineMode)
    }

    @Test fun schritt2bWeiterWaehrendDasTextmodellImHintergrundLaedt() {
        ModelDownloads.update("gemma4_e2b", DownloadState.Running(1_000, TextModelCatalog.GEMMA4_E2B.bytes, 500))
        try {
            schritt2b()
            compose.onNode(isSelectable() and hasText("Lokales Textmodell")).assertIsSelected()
            compose.onNodeWithText("Lädt im Hintergrund weiter — du kannst schon weitermachen.").assertExists()
            compose.onNodeWithText("Weiter").assertIsEnabled()
        } finally {
            ModelDownloads.clear("gemma4_e2b")
        }
    }

    @Test fun schritt2bZurueckZuLokalWaehrendDasTextmodellLaedt() {
        prefs.offlineRefine = OfflineRefineRule.SKIP
        ModelDownloads.update("gemma4_e2b", DownloadState.Running(1_000, TextModelCatalog.GEMMA4_E2B.bytes, 500))
        try {
            schritt2b()
            compose.onNode(isSelectable() and hasText("Überspringen")).assertIsSelected()
            compose.onNode(isSelectable() and hasText("Lokales Textmodell")).assertIsEnabled().performClick()
            compose.waitForIdle()
            assertEquals(OfflineRefineRule.LOCAL, Prefs(ctx).offlineRefine)
            assertEquals("kein zweiter Start, der Download laeuft schon", null, startedService())
        } finally {
            ModelDownloads.clear("gemma4_e2b")
        }
    }

    @Test fun schritt2bLokalErstNachDemErkennungsmodell() {
        ModelDownloads.update("small", DownloadState.Running(1_000, ModelCatalog.SMALL.bytes, 500))
        try {
            schritt2b(SystemStatus())
            compose.onNode(isSelectable() and hasText("Lokales Textmodell")).assertIsNotEnabled()
            compose.onNode(isSelectable() and hasText("Überspringen")).assertIsEnabled()
        } finally {
            ModelDownloads.clear("small")
        }
    }

    @Test fun schritt2bOhneErkennungsmodellBleibtWeiterGesperrt() {
        prefs.offlineRefine = OfflineRefineRule.SKIP
        schritt2b(SystemStatus())
        compose.onNodeWithText("Weiter").assertIsNotEnabled()
    }

    @Test fun schritt2bMitStufeZeigtDiePflichtkarte() {
        prefs.refineMode = RefineMode.POLISH
        schritt2b()
        pflichtkarte.assertCountEquals(1)
        compose.onNode(isSelectable() and hasText("Lokales Textmodell")).assertDoesNotExist()
        compose.onNodeWithText("Weiter").assertIsNotEnabled()
    }

    @Test fun schritt2bZuWenigRamNurUeberspringenUndDasIstSchonGewaehlt() {
        schritt2b(SystemStatus(installedModels = setOf("small"), totalRamBytes = 4L shl 30))
        compose.onNode(isSelectable() and hasText("Lokales Textmodell")).assertIsNotEnabled()
        compose.onNode(isSelectable() and hasText("Überspringen")).assertIsEnabled().assertIsSelected()
        compose.onNodeWithText("Weiter").assertIsEnabled()
    }

    // --- Home und Hub ------------------------------------------------------------------------

    /** Alles, was Home braucht, offline mit whisper-Modell; [textModels] = geladene Textmodelle. */
    private fun homeStatus(textModels: Set<String> = emptySet()) = SystemStatus(
        micGranted = true, canDrawOverlays = true, a11yRunning = true,
        installedModels = setOf("small"), installedTextModels = textModels,
    )

    @Test fun homeWarntOhneTextmodellUndDasBannerFuehrtZuText() {
        offlineOhneTextmodell()
        val nav = screen(env(homeStatus())) { HomeScreen(it) }
        compose.onNodeWithText("Glätten · Textmodell fehlt — Text ohne KI").assertExists()
        compose.onNodeWithText("Offline ohne Textmodell — der Text kommt ohne KI.").assertExists()
        compose.onNodeWithText("Beheben").performClick()
        compose.waitForIdle()
        assertEquals(Screen.TextSettings, nav.current)
    }

    @Test fun homeNurMitShareStufeWarnenZeileUndBannerGemeinsam() {
        offlineOhneTextmodell()
        prefs.refineMode = RefineMode.OFF
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        screen(env(homeStatus())) { HomeScreen(it) }
        compose.onNodeWithText("Aus · geteilte Sprachnachrichten: Textmodell fehlt").assertExists()
        compose.onNodeWithText("Offline ohne Textmodell — der Text kommt ohne KI.").assertExists()
    }

    @Test fun homeNurMitShareStufeUndTextmodellIstNeutralAus() {
        offlineOhneTextmodell()
        prefs.refineMode = RefineMode.OFF
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        screen(env(homeStatus(setOf("gemma4_e2b")))) { HomeScreen(it) }
        compose.onNodeWithText("Aus").assertExists()
        compose.onNodeWithText("Beheben").assertDoesNotExist()
    }

    @Test fun homeMitGeladenemTextmodellRechnetLokalOhneBanner() {
        offlineOhneTextmodell()
        screen(env(homeStatus(setOf("gemma4_e2b")))) { HomeScreen(it) }
        compose.onNodeWithText("Glätten · lokal · Gemma 4 E2B").assertExists()
        compose.onNodeWithText("Beheben").assertDoesNotExist()
    }

    @Test fun homeUeberspringenOhneEigenenZugangIstNeutral() {
        offlineOhneTextmodell(OfflineRefineRule.SKIP)
        screen(env(homeStatus())) { HomeScreen(it) }
        compose.onNodeWithText("Glätten · offline übersprungen").assertExists()
        compose.onNodeWithText("Beheben").assertDoesNotExist()
    }

    /** Eigener, vollstaendiger Online-Zugang fuer die Textverbesserung. */
    private fun eigenerZugang() {
        prefs.llmProviderId = "groq"
        prefs.llmKey = "gsk"
    }

    @Test fun homeOnlineOhneNetzLokalMitZugangOhneTextmodellSagtNichtTextOhneKi() {
        offlineOhneTextmodell(OfflineRefineRule.ONLINE_LOCAL)
        eigenerZugang()
        screen(env(homeStatus())) { HomeScreen(it) }
        // Mit Netz geht der Text online — nur ohne Netz kommt er ohne KI.
        compose.onNodeWithText(" · ohne Netz ohne KI", substring = true).assertExists()
        compose.onNodeWithText("Offline ohne Textmodell — ohne Netz kommt der Text ohne KI.").assertExists()
        compose.onNodeWithText("Glätten · Textmodell fehlt — Text ohne KI").assertDoesNotExist()
        compose.onNodeWithText("Offline ohne Textmodell — der Text kommt ohne KI.").assertDoesNotExist()
    }

    @Test fun pflichtkarteMitEigenemZugangSagtWasUeberspringenBewirkt() {
        offlineOhneTextmodell(OfflineRefineRule.ONLINE_LOCAL)
        eigenerZugang()
        screen(env(SystemStatus(installedModels = setOf("small")))) { RecognitionScreen(it) }
        compose.onNodeWithText("Mit Netz verbessert dein Online-Zugang, ohne Netz ein lokales Modell:", substring = true).assertExists()
        compose.onNodeWithText("dann kommt der Text ohne Netz ohne KI.", substring = true).assertExists()
    }

    @Test fun pflichtkarteLokalMitEigenemZugangUeberspringenGehtOnline() {
        offlineOhneTextmodell(OfflineRefineRule.LOCAL)
        eigenerZugang()
        screen(env(SystemStatus(installedModels = setOf("small")))) { RecognitionScreen(it) }
        compose.onNodeWithText("dann verbessert mit Netz dein Online-Zugang, sonst kommt der Text ohne KI.", substring = true).assertExists()
    }

    @Test fun homeOnlineOhneNetzLokalNenntDasOnlineModell() {
        offlineOhneTextmodell(OfflineRefineRule.ONLINE_LOCAL)
        prefs.llmProviderId = "groq"
        prefs.llmKey = "gsk"
        screen(env(homeStatus(setOf("gemma4_e2b")))) { HomeScreen(it) }
        compose.onNodeWithText(" · ohne Netz lokal", substring = true).assertExists()
    }

    @Test fun hubNenntDieRegelNurOfflineMitStufe() {
        offlineOhneTextmodell(OfflineRefineRule.ONLINE_LOCAL)
        prefs.removeFillers = false
        prefs.autoCapitalize = false
        prefs.trailingSpace = false
        screen(env(homeStatus())) { SettingsHubScreen(it) }
        compose.onNodeWithText("Glätten · online, ohne Netz lokal").assertExists()
    }

    @Test fun hubOhneRegelBeiStufeAus() {
        offlineOhneTextmodell()
        prefs.refineMode = RefineMode.OFF
        prefs.removeFillers = true
        screen(env(homeStatus())) { SettingsHubScreen(it) }
        compose.onNodeWithText("lokal bei Offline", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Aus · Füllwörter", substring = true).assertExists()
    }

    @Test fun hubZaehltWhisperUndTextmodelle() {
        prefs.engine = Engine.OFFLINE
        val status = SystemStatus(installedModels = setOf("small"), installedTextModels = setOf("gemma4_e2b"), modelsUsedBytes = 3_000_000_000)
        screen(env(status)) { SettingsHubScreen(it) }
        compose.onNodeWithText("2 geladen · ${size(3_000_000_000)} belegt").assertExists()
    }
}
