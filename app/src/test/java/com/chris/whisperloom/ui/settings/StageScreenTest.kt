package com.chris.whisperloom.ui.settings

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stufen-Seiten (3.9.0, Plan §3.2): je Stufe und Weg genau ihre Optionen, und jede Seite schreibt nur
 * die Einstellung ihres Wegs. Das Modell je Stufe gilt fuer beide Wege; die Seiten der
 * Sprachnachrichten nennen es nur und fuehren zur Diktat-Seite. "Aus" hat keine Optionen, nur was ohne
 * KI geschieht und den Weg zu Woerterbuch & Regeln.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class StageScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    @Before fun setUp() {
        ctx.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
    }

    private var gezeigt by mutableStateOf<Screen.Stage?>(null)
    private lateinit var nav: NavState

    /** Zeigt die Seite; ein zweiter Aufruf wechselt sie in derselben Composition (setContent nur einmal). */
    private fun seite(stage: RefineMode, way: RefineWay): NavState {
        val s = Screen.Stage(stage, way)
        if (gezeigt == null) {
            val status = SystemStatus()
            val env = AppEnv(PrefsState(prefs), status) { status }
            nav = NavState(listOf(Screen.Home, Screen.SettingsHub, Screen.Refine, s))
            gezeigt = s
            compose.setContent {
                WhisperLoomTheme {
                    CompositionLocalProvider(LocalAppEnv provides env) {
                        gezeigt?.let { key(it.key) { StageScreen(it.stage, it.way, nav) } }
                    }
                }
            }
        } else {
            gezeigt = s
        }
        compose.waitForIdle()
        return nav
    }

    private fun click(node: SemanticsNodeInteraction) {
        node.performClick()
        compose.waitForIdle()
    }

    private fun click(text: String) = click(compose.onNodeWithText(text))

    private fun option(text: String) = compose.onNode(isSelectable() and hasText(text))

    private fun anthropic() {
        prefs.llmProviderId = "anthropic"
        prefs.llmKey = "sk-ant"
    }

    /** Was auf der Seite steht: Bereinigung, Form, Absaetze (Schalter oder Hinweis), Modell. */
    private fun abschnitte(): List<String> = listOf("Bereinigung", "Form", "Absätze", "Modell")
        .filter { compose.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty() }

    // --- Kopf: Kurzbeschreibung und Verwenden ------------------------------------------------

    @Test fun fuerDiktatVerwendenWaehltNurDasDiktat() {
        seite(RefineMode.BEAUTIFY, RefineWay.DICTATION)
        compose.onNodeWithText("Formuliert flüssiger und klarer, behält Inhalt, Ton und deine Wörter.").assertExists()
        compose.onNodeWithText("Beim Diktat aktiv").assertDoesNotExist()
        click("Für Diktat verwenden")
        assertEquals(RefineMode.BEAUTIFY, Prefs(ctx).refineMode)
        assertEquals(RefineMode.OFF, Prefs(ctx).shareRefineMode)
        compose.onNodeWithText("Beim Diktat aktiv").assertExists()
        compose.onNodeWithText("Für Diktat verwenden").assertDoesNotExist()
    }

    @Test fun fuerSprachnachrichtenVerwendenWaehltNurDieSprachnachrichten() {
        prefs.refineMode = RefineMode.POLISH
        seite(RefineMode.SUMMARIZE, RefineWay.SHARE)
        compose.onNodeWithText("Bei geteilten Sprachnachrichten").assertExists()
        click("Für Sprachnachrichten verwenden")
        assertEquals(RefineMode.SUMMARIZE, Prefs(ctx).shareRefineMode)
        assertEquals(RefineMode.POLISH, Prefs(ctx).refineMode)
        compose.onNodeWithText("Bei Sprachnachrichten aktiv").assertExists()
    }

    @Test fun ausFuerDiktatVerwendenLaesstDieSprachnachrichtenInRuhe() {
        prefs.refineMode = RefineMode.POLISH
        prefs.shareRefineMode = RefineMode.SUMMARIZE
        seite(RefineMode.OFF, RefineWay.DICTATION)
        compose.onNodeWithText("Nur die Regeln ohne KI, keine zweite Anfrage.").assertExists()
        click("Für Diktat verwenden")
        assertEquals(RefineMode.OFF, Prefs(ctx).refineMode)
        assertEquals(RefineMode.SUMMARIZE, Prefs(ctx).shareRefineMode)
        compose.onNodeWithText("Beim Diktat aktiv").assertExists()
    }

    @Test fun ausFuerSprachnachrichtenVerwendenLaesstDasDiktatInRuhe() {
        prefs.refineMode = RefineMode.BEAUTIFY
        prefs.shareRefineMode = RefineMode.POLISH
        seite(RefineMode.OFF, RefineWay.SHARE)
        compose.onNodeWithText("Wortgetreu, Füllwörter per Schalter ausblendbar. Keine zweite Anfrage.").assertExists()
        click("Für Sprachnachrichten verwenden")
        assertEquals(RefineMode.OFF, Prefs(ctx).shareRefineMode)
        assertEquals(RefineMode.BEAUTIFY, Prefs(ctx).refineMode)
        compose.onNodeWithText("Bei Sprachnachrichten aktiv").assertExists()
    }

    // --- Aus: was ohne KI geschieht --------------------------------------------------------------

    /** Diktat: die festen Regeln wirken; der Link nennt ihren Stand wie der Fusslink der Textverbesserung. */
    @Test fun ausBeimDiktatNenntDieFestenRegelnUndFuehrtZuIhnen() {
        prefs.removeFillers = true
        prefs.autoCapitalize = false
        val nav = seite(RefineMode.OFF, RefineWay.DICTATION)
        compose.onNodeWithText("Ohne KI gelten nur die festen Regeln", substring = true).assertExists()
        compose.onNodeWithText("Noch keine Begriffe · Füllwörter").assertExists()
        click("Wörterbuch & Regeln")
        assertEquals(Screen.Dictionary, nav.current)
    }

    /** Sprachnachrichten: wortgetreu ohne feste Regeln; von dort wirkt nur die Fuellwort-Liste (Schalter im Fenster). */
    @Test fun ausBeiSprachnachrichtenIstWortgetreuUndFuehrtZurFuellwortListe() {
        val nav = seite(RefineMode.OFF, RefineWay.SHARE)
        compose.onNodeWithText("Die Nachricht bleibt wortgetreu, ohne KI und ohne die festen Regeln", substring = true).assertExists()
        compose.onNodeWithText("Ohne KI gelten nur die festen Regeln", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Füllwort-Liste fürs Ausblenden im Fenster").assertExists()
        click("Wörterbuch & Regeln")
        assertEquals(Screen.Dictionary, nav.current)
    }

    // --- Optionen je Stufe und Weg (Tabelle Plan §3.2) ----------------------------------------

    @Test fun jedeSeiteZeigtGenauIhreOptionen() {
        val erwartet = mapOf(
            Screen.Stage(RefineMode.OFF, RefineWay.DICTATION) to emptyList(),
            Screen.Stage(RefineMode.OFF, RefineWay.SHARE) to emptyList(),
            Screen.Stage(RefineMode.POLISH, RefineWay.DICTATION) to listOf("Bereinigung", "Absätze", "Modell"),
            Screen.Stage(RefineMode.BEAUTIFY, RefineWay.DICTATION) to listOf("Absätze", "Modell"),
            Screen.Stage(RefineMode.SUMMARIZE, RefineWay.DICTATION) to listOf("Form", "Modell"),
            Screen.Stage(RefineMode.PROMPT, RefineWay.DICTATION) to listOf("Modell"),
            Screen.Stage(RefineMode.POLISH, RefineWay.SHARE) to listOf("Bereinigung", "Absätze", "Modell"),
            Screen.Stage(RefineMode.BEAUTIFY, RefineWay.SHARE) to listOf("Absätze", "Modell"),
            Screen.Stage(RefineMode.SUMMARIZE, RefineWay.SHARE) to listOf("Form", "Modell"),
        )
        erwartet.forEach { (s, abschnitte) ->
            seite(s.stage, s.way)
            assertEquals("$s", abschnitte, abschnitte())
            // Absaetze sind nur beim Diktat ein Schalter; Sprachnachrichten werden immer gegliedert.
            val schalter = compose.onAllNodes(isToggleable()).fetchSemanticsNodes().size
            assertEquals("$s", if (s.way == RefineWay.DICTATION && "Absätze" in abschnitte) 1 else 0, schalter)
            val hinweis = compose.onAllNodes(hasText("Sprachnachrichten werden immer in Absätze gegliedert.")).fetchSemanticsNodes()
            assertEquals("$s", s.way == RefineWay.SHARE && "Absätze" in abschnitte, hinweis.isNotEmpty())
        }
    }

    @Test fun bereinigungDesDiktatsLaesstDieSprachnachrichtenInRuhe() {
        seite(RefineMode.POLISH, RefineWay.DICTATION)
        option("Nur Zeichensetzung").assertIsSelected()
        click(option("Ohne Füllwörter"))
        assertEquals(PolishCleanup.CLEAN, Prefs(ctx).polishCleanupFor(RefineWay.DICTATION))
        assertEquals(PolishCleanup.PLAIN, Prefs(ctx).polishCleanupFor(RefineWay.SHARE))
        click(option("Lesbar"))
        assertEquals(PolishCleanup.READABLE, Prefs(ctx).polishCleanupFor(RefineWay.DICTATION))
        option("Lesbar").assertIsSelected()
        // Die Stufe selbst waehlt nur der Kopf der Seite.
        assertEquals(RefineMode.OFF, Prefs(ctx).refineMode)
    }

    @Test fun bereinigungDerSprachnachrichtenLaesstDasDiktatInRuhe() {
        prefs.setPolishCleanupFor(RefineWay.DICTATION, PolishCleanup.READABLE)
        seite(RefineMode.POLISH, RefineWay.SHARE)
        option("Nur Zeichensetzung").assertIsSelected()
        click(option("Ohne Füllwörter"))
        assertEquals(PolishCleanup.CLEAN, Prefs(ctx).polishCleanupFor(RefineWay.SHARE))
        assertEquals(PolishCleanup.READABLE, Prefs(ctx).polishCleanupFor(RefineWay.DICTATION))
        prefs.shareRefineMode = RefineMode.POLISH
        assertEquals("die KI entscheidet ueber Fuellwoerter", true, Prefs(ctx).refinementFor(RefineWay.SHARE).smartFillers)
    }

    @Test fun absaetzeGeltenNurFuerIhreStufe() {
        seite(RefineMode.POLISH, RefineWay.DICTATION)
        val schalter = compose.onNode(isToggleable() and hasText("Absätze")).assertIsOn()
        click(schalter)
        schalter.assertIsOff()
        assertEquals(false, Prefs(ctx).paragraphsFor(RefineMode.POLISH))
        assertEquals("Verschoenern behaelt seine Absaetze", true, Prefs(ctx).paragraphsFor(RefineMode.BEAUTIFY))
        assertEquals(SummarizeForm.AUTO, Prefs(ctx).summarizeFormFor(RefineWay.DICTATION))
    }

    @Test fun absaetzeBeimVerschoenern() {
        seite(RefineMode.BEAUTIFY, RefineWay.DICTATION)
        click(compose.onNode(isToggleable() and hasText("Absätze")))
        assertEquals(false, Prefs(ctx).paragraphsFor(RefineMode.BEAUTIFY))
        assertEquals(true, Prefs(ctx).paragraphsFor(RefineMode.POLISH))
    }

    @Test fun formDesDiktatsUndDerSprachnachrichtenGetrennt() {
        seite(RefineMode.SUMMARIZE, RefineWay.DICTATION)
        option("Automatisch").assertIsSelected()
        click(option("Fließtext"))
        assertEquals(SummarizeForm.PROSE, Prefs(ctx).summarizeFormFor(RefineWay.DICTATION))
        assertEquals(SummarizeForm.AUTO, Prefs(ctx).summarizeFormFor(RefineWay.SHARE))

        seite(RefineMode.SUMMARIZE, RefineWay.SHARE)
        option("Automatisch").assertIsSelected()
        click(option("Fließtext"))
        click(option("Automatisch"))
        click(option("Fließtext"))
        assertEquals(SummarizeForm.PROSE, Prefs(ctx).summarizeFormFor(RefineWay.SHARE))
        assertEquals(SummarizeForm.PROSE, Prefs(ctx).summarizeFormFor(RefineWay.DICTATION))
    }

    // --- Modell: eins je Stufe fuer beide Wege ------------------------------------------------

    @Test fun dasModellAufDerDiktatSeiteGiltAuchFuerSprachnachrichten() {
        anthropic()
        seite(RefineMode.BEAUTIFY, RefineWay.DICTATION)
        compose.onNodeWithText("Standard · Claude Sonnet 5.5").assertExists()
        click("Modell")
        compose.onNodeWithText("Modell für Verschönern").assertExists()
        click(option("Claude Opus 5.5"))
        assertEquals("claude-opus-5-5", Prefs(ctx).llmModelFor(RefineMode.BEAUTIFY))
        assertEquals("die anderen Stufen bleiben", "", Prefs(ctx).llmModelFor(RefineMode.POLISH))
        compose.onNodeWithText("Claude Opus 5.5").assertExists()

        seite(RefineMode.BEAUTIFY, RefineWay.SHARE)
        compose.onNodeWithText("Wie beim Diktat · Claude Opus 5.5").assertExists()
    }

    @Test fun dieSprachnachrichtenNennenDasModellUndFuehrenZurDiktatSeite() {
        anthropic()
        val nav = seite(RefineMode.POLISH, RefineWay.SHARE)
        compose.onNodeWithText("Wie beim Diktat · Claude Haiku 5.5").assertExists()
        click("Modell")
        assertEquals(Screen.Stage(RefineMode.POLISH, RefineWay.DICTATION), nav.current)
        compose.onNodeWithText("Modell für Glätten").assertDoesNotExist()
    }

    @Test fun eigenesModellUeberDieSeite() {
        anthropic()
        seite(RefineMode.PROMPT, RefineWay.DICTATION)
        click("Modell")
        click("Eigenes Modell …")
        compose.onNode(hasSetTextAction() and hasText("Modell-ID")).performTextInput("claude-fable-6")
        click("Übernehmen")
        assertEquals("claude-fable-6", Prefs(ctx).llmModelFor(RefineMode.PROMPT))
        compose.onNodeWithText("claude-fable-6").assertExists()
    }

    @Test fun ohneTextZugangEinHinweisMitWegZumKiZugang() {
        prefs.sttProviderId = "elevenlabs"
        prefs.apiKey = "xi"
        val nav = seite(RefineMode.POLISH, RefineWay.DICTATION)
        compose.onNodeWithText("Ohne Online-Zugang für die Textverbesserung gibt es hier nichts zu wählen.").assertExists()
        compose.onNodeWithText("Standard ·", substring = true).assertDoesNotExist()
        click("KI-Zugang")
        assertEquals(Screen.LlmAccess, nav.current)
    }

    @Test fun ohneModellDesZugangsEinHinweis() {
        prefs.llmProviderId = "custom"
        prefs.llmUrl = "http://127.0.0.1:1/v1"
        seite(RefineMode.SUMMARIZE, RefineWay.DICTATION)
        compose.onNodeWithText("Trag unter „KI-Zugang“ zuerst ein Modell ein", substring = true).assertExists()
        // Sprachnachrichten: ohne Modell nur "Wie beim Diktat".
        seite(RefineMode.SUMMARIZE, RefineWay.SHARE)
        compose.onNodeWithText("Wie beim Diktat").assertExists()
    }
}
