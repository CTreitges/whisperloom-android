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
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.settings.ModelsScreen
import com.chris.whisperloom.ui.settings.RecognitionScreen
import com.chris.whisperloom.ui.settings.SettingsHubScreen
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
 * E4 Abschnitt Textverbesserung, Pflichtkarte "Offline ohne Textmodell", Hub-Zaehler.
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

    // --- Hub und Home zaehlen beide Arten ----------------------------------------------------

    @Test fun hubZaehltWhisperUndTextmodelle() {
        prefs.engine = Engine.OFFLINE
        val status = SystemStatus(installedModels = setOf("small"), installedTextModels = setOf("gemma4_e2b"), modelsUsedBytes = 3_000_000_000)
        screen(env(status)) { SettingsHubScreen(it) }
        compose.onNodeWithText("2 geladen · ${size(3_000_000_000)} belegt").assertExists()
    }
}
