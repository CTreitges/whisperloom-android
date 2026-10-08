package com.chris.whisperloom.ui.history

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
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
import androidx.compose.ui.unit.Density
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.Refinement
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.HistorySource
import com.chris.whisperloom.history.HistoryVersion
import com.chris.whisperloom.history.Processing
import com.chris.whisperloom.ui.home.HomeScreen
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import com.chris.whisperloom.ui.settings.SettingsHubScreen
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
import org.robolectric.shadows.ShadowDialog

/**
 * Screenshots des Verlaufs (3.9.0) zum Ansehen, keine Pixel-Vergleiche: Liste, leer, Eintrag, Sheet
 * "Andere Stufe …", Bearbeiten, Verlauf-Einstellungen, Hub mit VERLAUF und Home-Titelleiste — dazu
 * 360 dp mit doppelter Schrift. PNGs nach app/build/reports/screenshots/. Wie SettingsScreenshotTest:
 * auf linux-aarch64 uebersprungen, unter qemu (x86_64-JVM) ausfuehrbar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class HistoryScreenshotTest {

    companion object {
        @BeforeClass @JvmStatic fun nurMitNativeRuntime() {
            val linuxArm = System.getProperty("os.name").orEmpty().startsWith("Linux") && System.getProperty("os.arch") == "aarch64"
            Assume.assumeFalse("Robolectric-Native-Graphics fehlt auf Linux aarch64", linuxArm)
        }
    }

    @get:Rule
    val compose = createComposeRule()

    private val ui = HistoryUi(compose)

    @Before fun setUp() = ui.setUp()

    @After fun tearDown() = ui.tearDown()

    private fun save(name: String, bitmap: Bitmap) {
        val file = File("build/reports/screenshots/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        save(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    /** Wie [HistoryUi.show], mit waehlbarer Schriftgroesse. */
    private fun show(vararg stack: Screen, fontScale: Float? = null): NavState {
        val nav = NavState(stack.toList())
        val env = ui.env()
        compose.setContent {
            ui.Themed(env) {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale ?: d.fontScale)) { ui.Router(nav) }
            }
        }
        compose.waitForIdle()
        return nav
    }

    /** Ein typischer Verlauf: heute drei (einer ohne KI, einer vom Knopf), gestern einer, aelter einer mit "Aus". */
    private fun verlauf(): String {
        ui.record(
            raw = "ähm also ich wollte kurz sagen dass ich morgen ein bisschen später komme weil der Zug mal wieder ausfällt und ich den nächsten nehmen muss",
            text = "Komme morgen später, mein Zug fällt aus.", at = ui.at(0, "14:32"),
        )
        ui.record(
            raw = "Hi Anna wegen Samstag wollte ich fragen ob du den Kuchen mitbringst", text = "Hi Anna, wegen Samstag: Bringst du den Kuchen mit?",
            refinement = Refinement(RefineMode.POLISH), at = ui.at(0, "11:05"), source = HistorySource.BUBBLE, durationMs = 22_000,
        )
        ui.record(
            raw = "einkaufsliste milch brot und äh eier", text = "Einkaufsliste Milch Brot und Eier", refinement = Refinement(RefineMode.BEAUTIFY),
            model = null, skipped = "Kein Netz für den Online-Zugang", at = ui.at(0, "09:41"), durationMs = 6_000,
        )
        ui.record(raw = "Termin beim Zahnarzt am Dienstag um zehn verschieben", text = "Termin beim Zahnarzt am Dienstag um zehn verschieben.",
            refinement = Refinement(RefineMode.POLISH, smartFillers = true), at = ui.at(1, "18:02"), durationMs = 9_000)
        return ui.record(raw = "kurze Notiz ohne Stufe", text = "Kurze Notiz ohne Stufe", refinement = Refinement.OFF, model = null,
            at = ui.at(9, "07:30"), durationMs = 4_000).id
    }

    /** Eintrag mit vielen Fassungen (eine bearbeitet) fuer die Chips. */
    private fun vieleFassungen(): String {
        val entry = ui.record(
            raw = "ähm also ich wollte kurz sagen dass ich morgen später komme weil der Zug ausfällt",
            text = "Komme morgen später, mein Zug fällt aus.", at = ui.at(0, "14:32"),
        )
        History.setVersion(ui.ctx, entry.id, Processing.POLISH_PLAIN, HistoryVersion("Also, ich wollte kurz sagen, dass ich morgen später komme, weil der Zug ausfällt.", 0L, "Claude Haiku 5.5"))
        History.setVersion(ui.ctx, entry.id, Processing.POLISH_READABLE, HistoryVersion("Ich komme morgen später, weil der Zug ausfällt.", 0L, "Claude Haiku 5.5"))
        History.setVersion(ui.ctx, entry.id, Processing.BEAUTIFY, HistoryVersion("Kurze Info: Ich komme morgen etwas später – mein Zug fällt aus.", 0L, "Claude Sonnet 5.5"))
        History.edit(ui.ctx, entry.id, Processing.EDITED, "Ich komme morgen später, der Zug fällt aus.")
        return entry.id
    }

    @Test fun liste() {
        verlauf()
        show(Screen.Home, Screen.History)
        ui.waitFor("Hi Anna", substring = true)
        shot("verlauf-liste")
    }

    @Test @Config(qualifiers = "w360dp-h891dp-xxhdpi")
    fun liste360dpMitDoppelterSchrift() {
        verlauf()
        show(Screen.Home, Screen.History, fontScale = 2f)
        ui.waitFor("ähm also", substring = true)
        shot("verlauf-liste-360dp-schrift200")
    }

    @Test fun leerUndAus() {
        show(Screen.Home, Screen.History)
        ui.waitFor("Noch keine Diktate")
        shot("verlauf-leer")
        History.setEnabled(ui.ctx, false)
        ui.waitFor("Verlauf ist aus")
        shot("verlauf-aus")
    }

    @Test fun eintrag() {
        val id = vieleFassungen()
        show(Screen.Home, Screen.History, Screen.HistoryDetail(id))
        ui.waitFor("Komme morgen später, mein Zug fällt aus.")
        shot("verlauf-eintrag")
        compose.onNodeWithText("Ursprung").performClick()
        shot("verlauf-eintrag-ursprung")
    }

    @Test fun eintragOhneKi() {
        val entry = ui.record(raw = "einkaufsliste milch brot und äh eier", text = "Einkaufsliste Milch Brot und Eier",
            refinement = Refinement(RefineMode.BEAUTIFY), model = null, skipped = "Kein Netz für den Online-Zugang")
        show(Screen.Home, Screen.History, Screen.HistoryDetail(entry.id))
        ui.waitFor("Einkaufsliste Milch Brot und Eier")
        shot("verlauf-eintrag-ohne-ki")
    }

    @Test @Config(qualifiers = "w360dp-h891dp-xxhdpi")
    fun eintrag360dpMitDoppelterSchrift() {
        val id = vieleFassungen()
        show(Screen.Home, Screen.History, Screen.HistoryDetail(id), fontScale = 2f)
        ui.waitFor("Komme morgen später, mein Zug fällt aus.")
        shot("verlauf-eintrag-360dp-schrift200")
    }

    @Test fun andereStufe() {
        val id = vieleFassungen()
        show(Screen.Home, Screen.History, Screen.HistoryDetail(id))
        ui.waitFor("Komme morgen später, mein Zug fällt aus.")
        compose.onNodeWithText("Andere Stufe …").performClick()
        compose.waitForIdle()
        // Das Sheet ist ein eigenes Fenster: den Eintrag aufnehmen und das Fenster darueber zeichnen.
        val seite = compose.onAllNodes(isRoot()).filterToOne(hasAnyDescendant(hasText("Andere Stufe …")))
        val bitmap = seite.captureToImage().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
        ShadowDialog.getLatestDialog().window!!.decorView.draw(Canvas(bitmap))
        save("verlauf-andere-stufe", bitmap)
    }

    @Test fun bearbeiten() {
        val id = vieleFassungen()
        show(Screen.Home, Screen.History, Screen.HistoryDetail(id), Screen.HistoryEdit(id, Processing.BEAUTIFY))
        ui.waitFor("Fassung: Verschönern")
        shot("verlauf-bearbeiten")
    }

    @Test fun einstellungen() {
        verlauf()
        show(Screen.Home, Screen.History, Screen.HistorySettings)
        ui.waitFor("5 Einträge")
        shot("verlauf-einstellungen")
    }

    @Test fun hubMitVerlauf() {
        verlauf()
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        compose.setContent { ui.Themed(ui.env()) { SettingsHubScreen(nav) } }
        ui.waitFor("An · 5 von 50")
        val liste = compose.onNode(hasScrollAction())
        liste.performScrollToNode(hasText("Über WhisperLoom"))
        compose.waitForIdle()
        val top = compose.onNodeWithText("Knopf & Tastatur").fetchSemanticsNode().boundsInRoot.top
        val listTop = liste.fetchSemanticsNode().boundsInRoot.top
        liste.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, top - listTop - 200f) }
        shot("einstellungen-hub-verlauf")
    }

    @Test fun homeTitelleiste() {
        ui.prefs.apiKey = "sk-test"
        val nav = NavState(listOf(Screen.Home))
        compose.setContent { ui.Themed(ui.env()) { HomeScreen(nav) } }
        shot("home-titelleiste")
    }
}
