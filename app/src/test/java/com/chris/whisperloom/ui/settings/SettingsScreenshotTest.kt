package com.chris.whisperloom.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.PolishCleanup
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import com.chris.whisperloom.SummarizeForm
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import java.io.File
import org.junit.Assert.assertEquals
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
 * Screenshots des Einstellungen-Hubs und seiner Seiten (3.9.0, mit Stufen-Seiten samt "Aus") zum Ansehen,
 * keine Pixel-Vergleiche (einzige Ausnahme: die Auswahl-Zone hat keine Toenung):
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

    private fun screen(status: SystemStatus = online, fontScale: Float? = null, content: @Composable (NavState) -> Unit) {
        val env = AppEnv(PrefsState(prefs), status) { status }
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        compose.setContent {
            WhisperLoomTheme {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalAppEnv provides env, LocalDensity provides Density(d.density, fontScale ?: d.fontScale)) {
                    content(nav)
                }
            }
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
        // Unterzeilen mit Abweichungen: Bereinigung, Absaetze, Form, eigenes Modell.
        anthropic()
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.READABLE)
        prefs.setParagraphsFor(RefineMode.BEAUTIFY, false)
        prefs.setLlmModelFor(RefineMode.SUMMARIZE, "claude-opus-5-5")
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        prefs.setSummarizeFormFor(RefineWay.SHARE, SummarizeForm.PROSE)
        screen { RefineScreen(it) }
        shot("textverbesserung-diktat")
        zoneOhneToenung("Verschönern für Diktat verwenden")
        nachOben(hasText("Bei geteilten Sprachnachrichten"))
        shot("textverbesserung-sprachnachrichten")
    }

    /** Schmalstes Zielgeraet und doppelte Schrift: die Textspalte neben der Zone ist nur noch ca. 200 dp breit. */
    @Test @Config(qualifiers = "w360dp-h891dp-xxhdpi")
    fun textverbesserungSchmalMitDoppelterSchrift() {
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.CLEAN)
        prefs.setParagraphsFor(RefineMode.POLISH, false)
        screen(fontScale = 2f) { RefineScreen(it) }
        nachOben(hasText("Beim Diktieren"), abstandPx = 30f)
        shot("textverbesserung-360dp-schrift200")
        // Der laengste Titel: das "›" bleibt neben "Zusammenfassen" und vor der Zone.
        nachOben(hasText("Zusammenfassen") and hasAnyAncestor(hasTestTag(DICTATION_REFINE_TAG)), abstandPx = 30f)
        shot("textverbesserung-360dp-schrift200-unten")
    }

    /**
     * Die Zone um den Punkt ist unsichtbar (User-Wunsch 2026-10-08): ueber dem Punkt dieselbe Farbe wie
     * die Karte links daneben, im Rand der Text-Spalte.
     */
    private fun zoneOhneToenung(name: String) {
        val bild = compose.onRoot().captureToImage().asAndroidBitmap()
        val zone = compose.onNodeWithContentDescription(name).fetchSemanticsNode().boundsInRoot
        val abstand = with(compose.density) { 10.dp.toPx() }
        val y = (zone.top + abstand).toInt()
        assertEquals("Zone ohne Toenung", bild.getPixel((zone.left - abstand).toInt(), y), bild.getPixel(zone.center.x.toInt(), y))
    }

    // --- Stufen-Seiten (3.9.0) ---------------------------------------------------------------

    @Test fun stufeGlaettenDiktat() {
        anthropic()
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.CLEAN)
        screen { StageScreen(RefineMode.POLISH, RefineWay.DICTATION, it) }
        shot("stufe-glaetten-diktat")
        nachOben(hasText("Absätze"))
        shot("stufe-glaetten-diktat-unten")
    }

    @Test fun stufeGlaettenSprachnachrichten() {
        // Noch nicht gewaehlt: oben der Knopf "Fuer Sprachnachrichten verwenden".
        anthropic()
        prefs.setPolishCleanupFor(RefineWay.SHARE, PolishCleanup.READABLE)
        screen { StageScreen(RefineMode.POLISH, RefineWay.SHARE, it) }
        shot("stufe-glaetten-sprachnachrichten")
        nachOben(hasText("Bereinigung"))
        shot("stufe-glaetten-sprachnachrichten-unten")
    }

    @Test fun stufeZusammenfassenDiktat() {
        anthropic()
        prefs.refineMode = RefineMode.SUMMARIZE
        prefs.setLlmModelFor(RefineMode.SUMMARIZE, "claude-opus-5-5")
        screen { StageScreen(RefineMode.SUMMARIZE, RefineWay.DICTATION, it) }
        shot("stufe-zusammenfassen-diktat")
    }

    @Test fun stufeOhneTextzugang() {
        prefs.sttProviderId = "elevenlabs"
        prefs.apiKey = "xi"
        screen { StageScreen(RefineMode.BEAUTIFY, RefineWay.DICTATION, it) }
        shot("stufe-verschoenern-ohne-zugang")
    }

    @Test fun stufeAusDiktat() {
        // Gewaehlt ist Glaetten: oben der Knopf "Fuer Diktat verwenden", unten der Stand der Regeln.
        prefs.apiPrompt = "Anna\nKubernetes"
        screen { StageScreen(RefineMode.OFF, RefineWay.DICTATION, it) }
        shot("stufe-aus-diktat")
    }

    @Test fun stufeAusSprachnachrichten() {
        // Ab Werk aus: oben "Bei Sprachnachrichten aktiv".
        screen { StageScreen(RefineMode.OFF, RefineWay.SHARE, it) }
        shot("stufe-aus-sprachnachrichten")
    }

    @Test fun textverbesserungMitOfflineErkennung() {
        // Offline ohne Textmodell (das Home-Banner): die Zeile "Bei Offline-Erkennung" sagt es.
        prefs.engine = Engine.OFFLINE
        prefs.shareRefineMode = RefineMode.POLISH
        prefs.setPolishCleanupFor(RefineWay.SHARE, PolishCleanup.READABLE)
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
        screen { StageScreen(RefineMode.BEAUTIFY, RefineWay.DICTATION, it) }
        compose.onNodeWithText("Modell").performClick()
        compose.waitForIdle()
        // Das Sheet ist ein eigener Dialog: die Seite aufnehmen und das Dialog-Fenster darueber zeichnen.
        val seite = compose.onAllNodes(isRoot()).filterToOne(hasAnyDescendant(hasText("Für Diktat verwenden")))
        val bitmap = seite.captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
        ShadowDialog.getLatestDialog().window!!.decorView.draw(Canvas(bitmap))
        save("stufe-modell-picker-standard", bitmap)
    }
}
