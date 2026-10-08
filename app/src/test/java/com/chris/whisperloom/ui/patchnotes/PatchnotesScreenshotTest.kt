package com.chris.whisperloom.ui.patchnotes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.ui.home.HomeScreen
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.settings.SettingsHubScreen
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
 * Screenshots der Patchnotes zum Ansehen (keine Pixel-Vergleiche): PNGs nach app/build/reports/screenshots/,
 * in CI im Artefakt "unit-and-lint-reports". Braucht Robolectrics Native-Graphics; die gibt es nicht fuer
 * linux-aarch64 (Dev-VPS) — dort uebersprungen, lokal per x86_64-JVM unter qemu ausfuehrbar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PatchnotesScreenshotTest {

    companion object {
        /** Der Fehler kommt schon beim Sandbox-Setup, also vor @Before: nur @BeforeClass greift rechtzeitig. */
        @BeforeClass @JvmStatic fun nurMitNativeRuntime() {
            val linuxArm = System.getProperty("os.name").orEmpty().startsWith("Linux") && System.getProperty("os.arch") == "aarch64"
            Assume.assumeFalse("Robolectric-Native-Graphics fehlt auf Linux aarch64", linuxArm)
        }
    }

    private val hero = hasContentDescription("Was ist neu in Version", substring = true)

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        save(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    private fun save(name: String, bitmap: Bitmap) {
        val file = File("build/reports/screenshots/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun themed(fontScale: Float? = null, content: @Composable () -> Unit) {
        compose.setContent {
            WhisperLoomTheme {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale ?: d.fontScale)) { content() }
            }
        }
    }

    private fun patchnotes(fontScale: Float? = null) {
        val nav = NavState(listOf(Screen.Home, Screen.Patchnotes))
        themed(fontScale) { PatchnotesScreen(nav) }
        // Unter qemu (emulierte x86_64-JVM) dauert das Laden der Assets deutlich laenger als nativ.
        // Der Hero ist immer komponiert (erstes Item); "Vollstaendiges Changelog" liegt bei 891 dp ausserhalb der LazyColumn.
        compose.waitUntil(60_000) { compose.onAllNodes(hero).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Scrollt die Liste so, dass [matcher] knapp unter ihrem oberen Rand steht. */
    private fun nachOben(matcher: SemanticsMatcher, abstandPx: Float = 60f) {
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(matcher)
        compose.waitForIdle()
        val listTop = list.fetchSemanticsNode().boundsInRoot.top
        val top = compose.onNode(matcher).fetchSemanticsNode().boundsInRoot.top
        list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, top - listTop - abstandPx) }
        compose.waitForIdle()
    }

    private fun aufklappen(zeile: String) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(zeile))
        compose.onNodeWithText(zeile).performClick()
        compose.waitForIdle()
    }

    private fun env(): AppEnv {
        val prefs = Prefs(ctx).apply {
            engine = Engine.ONLINE
            apiKey = "sk-test"
            tutorialSeen = true
        }
        val status = SystemStatus(micGranted = true, canDrawOverlays = true, a11yRunning = true)
        return AppEnv(PrefsState(prefs), status) { status }
    }

    @Test fun startzustand() {
        patchnotes()
        shot("patchnotes-oben")
    }

    @Test fun heroAufgeklappt() {
        patchnotes()
        // Bei vielen Highlights liegt der Knopf unter dem sichtbaren Bereich; ein Klick dort ginge ins Leere.
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("im Detail", substring = true))
        compose.onNode(hasText("im Detail", substring = true)).performClick()
        compose.waitForIdle()
        nachOben(hasText("Weniger anzeigen"))
        shot("patchnotes-hero-details")
    }

    @Test fun version370MitUnterpunktenCodeUndLink() {
        patchnotes()
        aufklappen("Version 3.7.0")
        nachOben(hasText("Sprachauftrag-Widget blieb", substring = true), abstandPx = 120f)
        shot("patchnotes-370-offen")
    }

    @Test fun version300MitIntroEntferntUndBekanntenPunkten() {
        patchnotes()
        aufklappen("Version 3.0.0")
        nachOben(hasText("Version 3.0.0"))
        shot("patchnotes-300-offen")
        nachOben(hasContentDescription("Entfernt", substring = true))
        shot("patchnotes-300-ende")
    }

    @Test fun vorVersion3() {
        patchnotes()
        nachOben(hasText("VOR VERSION 3"), abstandPx = 200f)
        shot("patchnotes-vor-v3")
    }

    @Test fun schrift200Prozent() {
        patchnotes(fontScale = 2f)
        shot("patchnotes-200prozent")
    }

    @Config(qualifiers = "w360dp-h891dp-xxhdpi")
    @Test fun fusszeileBei360dp() {
        val nav = NavState(listOf(Screen.Home))
        val env = env()
        themed { CompositionLocalProvider(LocalAppEnv provides env) { HomeScreen(nav) } }
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Einrichtung erneut öffnen"))
        shot("fusszeile-360dp")
    }

    @Test fun ueberSheet() {
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        val env = env()
        themed { CompositionLocalProvider(LocalAppEnv provides env) { SettingsHubScreen(nav) } }
        compose.waitForIdle()
        compose.onNodeWithText("Über WhisperLoom").performClick()
        compose.waitForIdle()
        // Das Sheet ist ein eigener Dialog. captureToImage liefert unter Robolectric auch fuer dessen Wurzel nur
        // das Activity-Fenster: den Hub aufnehmen und das Dialog-Fenster darueber zeichnen.
        val hub = compose.onAllNodes(isRoot()).filterToOne(hasAnyDescendant(hasText("Spracherkennung")))
        val bitmap = hub.captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
        ShadowDialog.getLatestDialog().window!!.decorView.draw(Canvas(bitmap))
        save("about-sheet", bitmap)
    }
}
