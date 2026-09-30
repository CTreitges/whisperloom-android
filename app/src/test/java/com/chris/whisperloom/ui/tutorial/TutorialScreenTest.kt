package com.chris.whisperloom.ui.tutorial

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.hasIllustration
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.LocalAppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tutorial (T): Einsteiger vier Seiten, Pro Widgets sechs; Blaettern per "Weiter", Beenden setzt
 * das Flag des Heftes und ruft onFinish.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class TutorialScreenTest {

    // Android-Rule statt createComposeRule(): fuer den Back-Test wird die Activity gebraucht.
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private var finished = 0

    @Before fun setUp() {
        ctx.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        finished = 0
    }

    private fun show(startPage: Int = 0, kind: TutorialKind = TutorialKind.BASICS) {
        val env = AppEnv(PrefsState(Prefs(ctx)), SystemStatus()) { SystemStatus() }
        compose.setContent {
            WhisperLoomTheme {
                CompositionLocalProvider(LocalAppEnv provides env) {
                    TutorialScreen(startPage = startPage, kind = kind, onFinish = { finished++ })
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun startetAufSeite1MitVierPunkten() {
        show()
        compose.onNodeWithText("Diktieren mit dem Knopf").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seite 1 von 4").assertIsDisplayed()
        compose.onNodeWithText("Überspringen").assertIsDisplayed()
        compose.onNodeWithText("Weiter").assertIsDisplayed()
        compose.onNodeWithText("Los geht's").assertDoesNotExist()
        assertFalse(Prefs(ctx).tutorialSeen)
    }

    @Test fun weiterBlaettertDurchAlleSeitenBisLosGehts() {
        show()
        compose.onNodeWithText("Weiter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Diktier-Tastatur").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seite 2 von 4").assertIsDisplayed()
        compose.onNodeWithText("Weiter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sprachnachrichten abtippen").assertIsDisplayed()
        compose.onNodeWithText("Weiter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Der Text ist da").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seite 4 von 4").assertIsDisplayed()
        // Letzte Seite: kein Ueberspringen mehr, Hauptaktion heisst "Los geht's".
        compose.onNodeWithText("Los geht's").assertIsDisplayed()
        compose.onNodeWithText("Überspringen").assertDoesNotExist()
        compose.onNodeWithText("Weiter").assertDoesNotExist()
        assertEquals(0, finished)
    }

    @Test fun ueberspringenSetztTutorialSeenUndRuftOnFinish() {
        show()
        compose.onNodeWithText("Überspringen").performClick()
        compose.waitForIdle()
        assertTrue(Prefs(ctx).tutorialSeen)
        assertEquals(1, finished)
    }

    @Test fun losGehtsSetztTutorialSeenUndRuftOnFinish() {
        show(startPage = 3)
        compose.onNodeWithText("Los geht's").performClick()
        compose.waitForIdle()
        assertTrue(Prefs(ctx).tutorialSeen)
        assertEquals(1, finished)
    }

    @Test fun startPageOeffnetSeiteSprachnachrichten() {
        show(startPage = 2)
        compose.onNodeWithText("Sprachnachrichten abtippen").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seite 3 von 4").assertIsDisplayed()
        // Illustration mit Bildtext (TalkBack)
        compose.onNode(
            hasIllustration(
                R.drawable.ill_tutorial_share,
                "Chat mit einer Sprachnachricht, die lange gedrückt wird; darüber der Menüpunkt „Teilen“ und daneben ein Teilen-Blatt, in dem die WhisperLoom-Kachel hervorgehoben ist.",
            ),
        ).assertExists()
    }

    @Test fun zurueckBlaettertUndUeberspringtAufSeite1() {
        show(startPage = 1)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Diktieren mit dem Knopf").assertIsDisplayed()
        assertEquals(0, finished)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertTrue(Prefs(ctx).tutorialSeen)
        assertEquals(1, finished)
    }

    // --- Zweites Heft: Pro Widgets ------------------------------------------

    /** Eine Tutorial-Seite, wie sie zu sehen sein muss: Titel, Illustration mit Bildtext, Seitentext. */
    private data class Seite(val titel: String, val bild: Int, val bildtext: Int, val text: Int)

    @Test fun dasProWidgetsHeftHatSechsSeitenMitEigenemBild() {
        show(kind = TutorialKind.PRO_WIDGETS)
        // Titel, Illustration, Bildtext (TalkBack) und Text je Seite, in dieser Reihenfolge (Spec §8.3).
        val seiten = listOf(
            Seite("Pro Widgets freischalten", R.drawable.ill_pro_features, R.string.img_pro_features, R.string.tutorial_pro_p1_body),
            Seite("Widget anlegen", R.drawable.ill_pro_widgets, R.string.img_pro_widgets, R.string.tutorial_pro_p2_body),
            Seite("Server eintragen", R.drawable.ill_agent_server, R.string.img_agent_server, R.string.tutorial_pro_p3_body),
            Seite("Auf den Startbildschirm", R.drawable.ill_agent_widget, R.string.img_agent_widget, R.string.tutorial_pro_p4_body),
            Seite("Tippen, sprechen, tippen", R.drawable.ill_agent_record, R.string.img_agent_record, R.string.tutorial_pro_p5_body),
            Seite("Die Antwort kommt im Chat", R.drawable.ill_agent_answer, R.string.img_agent_answer, R.string.tutorial_pro_p6_body),
        )
        seiten.forEachIndexed { i, s ->
            compose.onNodeWithText(s.titel).assertIsDisplayed()
            compose.onNode(hasIllustration(s.bild, ctx.getString(s.bildtext))).assertIsDisplayed()
            compose.onNodeWithText(ctx.getString(s.text)).assertIsDisplayed()
            compose.onNodeWithContentDescription("Seite ${i + 1} von 6").assertIsDisplayed()
            if (i < seiten.lastIndex) {
                compose.onNodeWithText("Weiter").performClick()
                compose.waitForIdle()
            }
        }
        compose.onNodeWithText("Los geht's").assertIsDisplayed()
        compose.onNodeWithText("Überspringen").assertDoesNotExist()
        assertEquals(0, finished)
    }

    @Test fun dasProWidgetsHeftSetztNurSeinEigenesFlag() {
        show(kind = TutorialKind.PRO_WIDGETS)
        compose.onNodeWithText("Überspringen").performClick()
        compose.waitForIdle()
        val prefs = Prefs(ctx)
        assertTrue(prefs.agentTutorialSeen)
        // tutorialSeen bedeutet weiterhin "Einsteiger-Tutorial gesehen" — sonst bekaeme ein
        // Bestandsnutzer das Einsteiger-Tutorial nie wieder bzw. beim Update erneut.
        assertFalse("tutorialSeen darf seine Bedeutung nicht aendern", prefs.tutorialSeen)
        assertEquals(1, finished)
    }

    @Test fun losGehtsAufDerLetztenProSeiteSetztNurDasProFlag() {
        show(startPage = 5, kind = TutorialKind.PRO_WIDGETS)
        compose.onNodeWithText("Die Antwort kommt im Chat").assertIsDisplayed()
        compose.onNodeWithText("Los geht's").performClick()
        compose.waitForIdle()
        assertTrue(Prefs(ctx).agentTutorialSeen)
        assertFalse(Prefs(ctx).tutorialSeen)
        assertEquals(1, finished)
    }

    @Test fun derSchluesselAgentUndSeinFlagBleibenAus370() {
        // Gespeicherte Back-Stacks ("tutorial:0:0:agent") und das Gesehen-Flag stammen aus 3.7.0.
        assertEquals("agent", TutorialKind.PRO_WIDGETS.key)
        assertEquals(TutorialKind.PRO_WIDGETS, TutorialKind.fromKey("agent"))
        val prefs = Prefs(ctx)
        assertFalse(TutorialKind.PRO_WIDGETS.seen(prefs))
        prefs.agentTutorialSeen = true
        assertTrue(TutorialKind.PRO_WIDGETS.seen(prefs))
        assertFalse(TutorialKind.BASICS.seen(prefs))
    }

    @Test fun dasEinsteigerHeftSetztNichtDasProWidgetsFlag() {
        show()
        compose.onNodeWithText("Überspringen").performClick()
        compose.waitForIdle()
        val prefs = Prefs(ctx)
        assertTrue(prefs.tutorialSeen)
        assertFalse(prefs.agentTutorialSeen)
    }
}
