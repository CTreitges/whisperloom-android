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
 * Screenshots von Text-Hub und Unterseiten (3.8.6) zum Ansehen, keine Pixel-Vergleiche: PNGs nach
 * app/build/reports/screenshots/. Wie TextModelScreenshotTest: braucht Robolectrics Native-Graphics,
 * auf linux-aarch64 uebersprungen, lokal per x86_64-JVM unter qemu ausfuehrbar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TextScreenshotTest {

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
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub, Screen.TextSettings))
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

    /** Geraet ohne Offline-Erkennung: der Hub hat vier Zeilen. */
    private val online = SystemStatus(micGranted = true, canDrawOverlays = true, a11yRunning = true, offlineSupported = false)

    private fun anthropic() {
        prefs.llmProviderId = "anthropic"
        prefs.llmKey = "sk-ant"
    }

    @Test fun hub() {
        prefs.polishReadable = true
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        screen { TextSettingsScreen(it) }
        shot("text-hub")
    }

    @Test fun hubOffline() {
        // Offline ohne Textmodell (das Home-Banner): die Zeile sagt es, ohne eigenen Zugang kein Online-Zugang.
        prefs.engine = Engine.OFFLINE
        screen(SystemStatus(installedModels = setOf("small"), totalRamBytes = 16L shl 30)) { TextSettingsScreen(it) }
        shot("text-hub-offline")
    }

    @Test fun diktat() {
        prefs.polishReadable = true
        screen { TextDictationScreen(it) }
        shot("text-diktat")
    }

    @Test fun sprachnachrichtenLesbar() {
        prefs.shareRefineMode = RefineMode.POLISH
        prefs.sharePolishReadable = true
        screen { TextShareScreen(it) }
        shot("text-sprachnachrichten-lesbar")
    }

    @Test fun zugangUndModelleJeStufe() {
        anthropic()
        prefs.setLlmModelFor(RefineMode.SUMMARIZE, "claude-opus-5-5")
        screen { TextAccessScreen(it) }
        shot("text-zugang-oben")
        nachOben(hasText("Modell je Stufe"))
        shot("text-zugang-modelle")
    }

    @Test fun modelleGesperrtBeiElevenLabs() {
        prefs.sttProviderId = "elevenlabs"
        prefs.apiKey = "xi"
        screen { TextAccessScreen(it) }
        nachOben(hasText("Modell je Stufe"), abstandPx = 200f)
        shot("text-modelle-gesperrt")
    }

    @Test fun modellPickerDerStufeMitStandard() {
        anthropic()
        screen { TextAccessScreen(it) }
        nachOben(hasText("Modell je Stufe"))
        compose.onNodeWithText("Verschönern").performClick()
        compose.waitForIdle()
        // Das Sheet ist ein eigener Dialog: die Seite aufnehmen und das Dialog-Fenster darueber zeichnen.
        val seite = compose.onAllNodes(isRoot()).filterToOne(hasAnyDescendant(hasText("Modell je Stufe")))
        val bitmap = seite.captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
        ShadowDialog.getLatestDialog().window!!.decorView.draw(Canvas(bitmap))
        save("text-modelle-picker-standard", bitmap)
    }
}
