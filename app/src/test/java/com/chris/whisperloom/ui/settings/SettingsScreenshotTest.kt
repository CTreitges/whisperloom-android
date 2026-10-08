package com.chris.whisperloom.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
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
import java.io.File
import org.junit.Assume
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * Screenshots des Einstellungen-Hubs und seiner Seiten (3.9.0) zum Ansehen, keine Pixel-Vergleiche:
 * PNGs nach app/build/reports/screenshots/. Wie TextModelScreenshotTest: braucht Robolectrics
 * Native-Graphics, auf linux-aarch64 uebersprungen, lokal per x86_64-JVM unter qemu ausfuehrbar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsScreenshotTest {

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
            engine = Engine.ONLINE
            apiKey = "sk-test"
            refineMode = RefineMode.POLISH
        }
    }

    private fun save(name: String, bitmap: Bitmap) {
        val file = File("build/reports/screenshots/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        save(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun screen(status: SystemStatus = online, content: @Composable (NavState) -> Unit) {
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

    /** Geraet ohne Offline-Erkennung: die Textverbesserung hat keine Zeile "Bei Offline-Erkennung". */
    private val online = SystemStatus(micGranted = true, canDrawOverlays = true, a11yRunning = true, offlineSupported = false)

    private fun anthropic() {
        prefs.llmProviderId = "anthropic"
        prefs.llmKey = "sk-ant"
    }

    @Test fun hub() {
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        prefs.apiPrompt = "Anna\nKubernetes"
        screen { SettingsHubScreen(it) }
        shot("einstellungen-hub")
    }

    @Test fun hubOffline() {
        // Offline ohne eigenen Zugang: der KI-Zugang sagt "Kein Online-Zugang".
        prefs.engine = Engine.OFFLINE
        screen(SystemStatus(installedModels = setOf("small"), totalRamBytes = 16L shl 30)) { SettingsHubScreen(it) }
        shot("einstellungen-hub-offline")
    }

    @Test fun textverbesserung() {
        prefs.polishReadable = true
        screen { RefineScreen(it) }
        shot("textverbesserung-diktat")
        nachOben(hasText("Bei geteilten Sprachnachrichten"))
        shot("textverbesserung-sprachnachrichten")
    }

    @Test fun textverbesserungMitOfflineErkennung() {
        // Offline ohne Textmodell (das Home-Banner): die Zeile "Bei Offline-Erkennung" sagt es.
        prefs.engine = Engine.OFFLINE
        prefs.shareRefineMode = RefineMode.POLISH
        prefs.sharePolishReadable = true
        screen(SystemStatus(installedModels = setOf("small"), totalRamBytes = 16L shl 30)) { RefineScreen(it) }
        nachOben(hasText("Bei geteilten Sprachnachrichten"))
        shot("textverbesserung-offline")
    }

    @Test fun woerterbuchUndRegeln() {
        prefs.apiPrompt = "Anna\nKubernetes"
        screen { DictionaryScreen(it) }
        shot("woerterbuch-regeln")
    }

    @Test fun spracherkennung() {
        screen { RecognitionScreen(it) }
        shot("spracherkennung")
    }

    @Test fun knopfUndTastatur() {
        screen { ButtonKeyboardScreen(it) }
        nachOben(hasText("Schwebender Knopf"))
        shot("knopf-tastatur-einstellungen")
        nachOben(hasText("Einfügen"))
        shot("knopf-tastatur-einfuegen")
    }

    @Test fun kiZugangUndModelleJeStufe() {
        anthropic()
        prefs.setLlmModelFor(RefineMode.SUMMARIZE, "claude-opus-5-5")
        screen { LlmAccessScreen(it) }
        shot("ki-zugang-oben")
        nachOben(hasText("Modell je Stufe"))
        shot("ki-zugang-modelle")
    }

    @Test fun modelleGesperrtBeiElevenLabs() {
        prefs.sttProviderId = "elevenlabs"
        prefs.apiKey = "xi"
        screen { LlmAccessScreen(it) }
        nachOben(hasText("Modell je Stufe"), abstandPx = 200f)
        shot("ki-zugang-modelle-gesperrt")
    }

    @Test fun modellPickerDerStufeMitStandard() {
        anthropic()
        screen { LlmAccessScreen(it) }
        nachOben(hasText("Modell je Stufe"))
        compose.onNodeWithText("Verschönern").performClick()
        compose.waitForIdle()
        // Das Sheet ist ein eigener Dialog: die Seite aufnehmen und das Dialog-Fenster darueber zeichnen.
        val seite = compose.onAllNodes(isRoot()).filterToOne(hasAnyDescendant(hasText("Modell je Stufe")))
        val bitmap = seite.captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
        ShadowDialog.getLatestDialog().window!!.decorView.draw(Canvas(bitmap))
        save("ki-zugang-modelle-picker-standard", bitmap)
    }
}
