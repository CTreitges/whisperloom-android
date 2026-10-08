package com.chris.whisperloom.ui.models

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.llm.installSparse
import com.chris.whisperloom.ui.home.HomeScreen
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SetupRouter
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.settings.LlmAccessScreen
import com.chris.whisperloom.ui.settings.ModelsScreen
import com.chris.whisperloom.ui.settings.RecognitionScreen
import com.chris.whisperloom.ui.setup.SetupScreen
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import com.chris.whisperloom.whisper.DownloadState
import com.chris.whisperloom.whisper.ModelCatalog
import com.chris.whisperloom.whisper.ModelDownloads
import com.chris.whisperloom.whisper.ModelStore
import com.chris.whisperloom.whisper.TextModelCatalog
import java.io.File
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshots des lokalen Textmodells zum Ansehen (keine Pixel-Vergleiche): PNGs nach
 * app/build/reports/screenshots/. Wie PatchnotesScreenshotTest: braucht Robolectrics Native-Graphics,
 * auf linux-aarch64 uebersprungen, lokal per x86_64-JVM unter qemu ausfuehrbar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TextModelScreenshotTest {

    companion object {
        /** Der Fehler kommt schon beim Sandbox-Setup, also vor @Before: nur @BeforeClass greift rechtzeitig. */
        @BeforeClass @JvmStatic fun nurMitNativeRuntime() {
            val linuxArm = System.getProperty("os.name").orEmpty().startsWith("Linux") && System.getProperty("os.arch") == "aarch64"
            Assume.assumeFalse("Robolectric-Native-Graphics fehlt auf Linux aarch64", linuxArm)
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    @Before fun setUp() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx).apply {
            welcomeSeen = true
            tutorialSeen = true
            engine = Engine.OFFLINE
            offlineRefine = OfflineRefineRule.LOCAL
            refineMode = RefineMode.POLISH
        }
    }

    @After fun tearDown() {
        ModelStore(ctx).dir.deleteRecursively()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val file = File("build/reports/screenshots/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun screen(status: SystemStatus, content: @Composable (NavState) -> Unit) {
        val env = AppEnv(PrefsState(prefs), status) { status }
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        compose.setContent {
            WhisperLoomTheme { CompositionLocalProvider(LocalAppEnv provides env) { content(nav) } }
        }
        compose.waitForIdle()
    }

    /** Scrollt die Spalte so, dass [matcher] knapp unter ihrem oberen Rand steht. */
    private fun nachOben(matcher: SemanticsMatcher, abstandPx: Float = 60f) {
        val column = compose.onNode(hasScrollAction())
        column.performScrollToNode(matcher)
        compose.waitForIdle()
        val top = compose.onNode(matcher).fetchSemanticsNode().boundsInRoot.top
        val columnTop = column.fetchSemanticsNode().boundsInRoot.top
        column.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, top - columnTop - abstandPx) }
        compose.waitForIdle()
    }

    private val ready = SystemStatus(
        micGranted = true, canDrawOverlays = true, a11yRunning = true,
        installedModels = setOf("small"), modelsUsedBytes = ModelCatalog.SMALL.bytes,
    )

    @Test fun offlineModelleBeideAbschnitte() {
        // Small und Gemma 4 E2B geladen, E4B nicht — und mit 6 GB fuer E4B zu gross (gedimmt).
        installSparse(ctx, ModelCatalog.SMALL)
        installSparse(ctx, TextModelCatalog.GEMMA4_E2B)
        val status = ready.copy(installedTextModels = setOf("gemma4_e2b"), totalRamBytes = 6L shl 30)
        screen(status) { ModelsScreen(it) }
        shot("textmodell-offline-modelle-oben")
        nachOben(hasText("TEXTVERBESSERUNG"))
        shot("textmodell-offline-modelle-textverbesserung")
    }

    @Test fun offlineModelleMitPflichtkarte() {
        installSparse(ctx, ModelCatalog.SMALL)
        screen(ready) { ModelsScreen(it) }
        nachOben(hasText("TEXTVERBESSERUNG"))
        shot("textmodell-offline-modelle-pflichtkarte")
    }

    @Test fun offlineModelleRegelBeiOfflineErkennung() {
        // 3.9.0: die Regel steht unter den Textmodellen (vorher Text › Offline-Erkennung).
        installSparse(ctx, ModelCatalog.SMALL)
        screen(ready) { ModelsScreen(it) }
        nachOben(hasText("Textverbesserung bei Offline-Erkennung"), abstandPx = 400f)
        shot("textmodell-offline-modelle-regel")
    }

    @Test fun kiZugangOffline() {
        // Offline mit Regel "Lokales Textmodell": der KI-Zugang erklaert, dass er nicht noetig ist.
        screen(ready) { LlmAccessScreen(it) }
        shot("textmodell-ki-zugang-offline")
    }

    @Test fun offlineModelleBeideTextmodelleAuf16Gb() {
        // E2B geladen und gewaehlt, E4B passt (16 GB): ein Tipp laedt es.
        installSparse(ctx, ModelCatalog.SMALL)
        installSparse(ctx, TextModelCatalog.GEMMA4_E2B)
        screen(ready.copy(installedTextModels = setOf("gemma4_e2b"), totalRamBytes = 16L shl 30)) { ModelsScreen(it) }
        nachOben(hasText("TEXTVERBESSERUNG"))
        shot("textmodell-offline-modelle-auswahl-16gb")
    }

    @Test fun assistent2bNachLokalAuf16Gb() {
        // Nach "Lokales Textmodell" auf 16 GB: "Glaetten" an, noch nichts geladen — die Pflichtkarte warnt,
        // geladen wird per Tipp auf E2B oder E4B.
        installSparse(ctx, ModelCatalog.SMALL)
        screen(ready.copy(totalRamBytes = 16L shl 30)) { SetupScreen(SetupRouter.STEP_ACCESS, it) }
        nachOben(hasText("Textverbesserung ohne Netz"), abstandPx = 40f)
        shot("textmodell-assistent-2b-auswahl")
    }

    @Test fun assistent2bE4bLaedt() {
        // E4B getippt: es laedt und ist gewaehlt, E2B wartet mit Hinweis.
        prefs.localLlmModel = "gemma4_e4b"
        installSparse(ctx, ModelCatalog.SMALL)
        ModelDownloads.update("gemma4_e4b", DownloadState.Running(1_829_765_120, TextModelCatalog.GEMMA4_E4B.bytes, 5_000_000))
        try {
            screen(ready.copy(totalRamBytes = 16L shl 30)) { SetupScreen(SetupRouter.STEP_ACCESS, it) }
            nachOben(hasText("Textverbesserung ohne Netz"), abstandPx = 40f)
            shot("textmodell-assistent-2b-laedt")
        } finally {
            ModelDownloads.clear("gemma4_e4b")
        }
    }

    @Test fun homeMitBanner() {
        screen(ready) { HomeScreen(it) }
        shot("textmodell-home-banner")
    }

    @Test fun assistent2bWahl() {
        prefs.refineMode = RefineMode.OFF // neue Nutzer: Stufe ab Werk aus, also die Wahl statt der Pflichtkarte
        installSparse(ctx, ModelCatalog.SMALL)
        screen(ready) { SetupScreen(SetupRouter.STEP_ACCESS, it) }
        nachOben(hasText("Textverbesserung ohne Netz"), abstandPx = 200f)
        shot("textmodell-assistent-2b")
    }

    @Test fun offlineModelleOhnePassendesTextmodell() {
        // 4-GB-Geraet: offline ja, Gemma nein — die Regel wirkt wie "Ueberspringen", mit Grund.
        installSparse(ctx, ModelCatalog.SMALL)
        screen(ready.copy(totalRamBytes = 4L shl 30)) { ModelsScreen(it) }
        nachOben(hasText("Textverbesserung bei Offline-Erkennung"), abstandPx = 400f)
        shot("textmodell-offline-modelle-zu-wenig-ram")
    }

    @Test fun assistent2bDownloadFehlgeschlagen() {
        // Nach "Lokales Textmodell" ist "Glaetten" an; der Download scheitert am Speicherplatz.
        installSparse(ctx, ModelCatalog.SMALL)
        ModelDownloads.update("gemma4_e2b", DownloadState.Failed("Nicht genug Speicherplatz", retryable = false))
        try {
            screen(ready) { SetupScreen(SetupRouter.STEP_ACCESS, it) }
            nachOben(hasText("Textverbesserung ohne Netz"), abstandPx = 200f)
            shot("textmodell-assistent-2b-fehlgeschlagen")
        } finally {
            ModelDownloads.clear("gemma4_e2b")
        }
    }

    @Test fun homeOnlineOhneNetzLokalMitZugangOhneTextmodell() {
        prefs.offlineRefine = OfflineRefineRule.ONLINE_LOCAL
        prefs.llmProviderId = "groq"
        prefs.llmKey = "gsk"
        screen(ready) { HomeScreen(it) }
        shot("textmodell-home-online-ohne-modell")
    }

    @Config(qualifiers = "w360dp-h891dp-xxhdpi")
    @Test fun pflichtkarteBei360dp() {
        screen(ready) { RecognitionScreen(it) }
        shot("textmodell-pflichtkarte-360dp")
    }
}
