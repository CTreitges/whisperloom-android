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

/** Tutorial (T): vier Seiten, Blaettern per "Weiter", Beenden setzt tutorialSeen und ruft onFinish. */
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
        // Bildtext der Illustration (TalkBack)
        compose.onNodeWithContentDescription(
            "Chat mit einer Sprachnachricht, die lange gedrückt wird; darüber der Menüpunkt „Teilen“ und daneben ein Teilen-Blatt, in dem die WhisperLoom-Kachel hervorgehoben ist.",
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

    // --- Zweites Heft: Sprachauftrag ----------------------------------------

    @Test fun dasSprachauftragHeftZeigtSeineEigenenSeiten() {
        show(kind = TutorialKind.AGENT)
        compose.onNodeWithText("Das Widget auf den Startbildschirm").assertIsDisplayed()
        compose.onNodeWithContentDescription("Seite 1 von 4").assertIsDisplayed()
    }

    @Test fun dasSprachauftragHeftSetztNurSeinEigenesFlag() {
        show(kind = TutorialKind.AGENT)
        compose.onNodeWithText("Überspringen").performClick()
        compose.waitForIdle()
        val prefs = Prefs(ctx)
        assertTrue(prefs.agentTutorialSeen)
        // tutorialSeen bedeutet weiterhin "Einsteiger-Tutorial gesehen" — sonst bekaeme ein
        // Bestandsnutzer das Einsteiger-Tutorial nie wieder bzw. beim Update erneut.
        assertFalse("tutorialSeen darf seine Bedeutung nicht aendern", prefs.tutorialSeen)
        assertEquals(1, finished)
    }

    @Test fun dasEinsteigerHeftSetztNichtDasSprachauftragFlag() {
        show()
        compose.onNodeWithText("Überspringen").performClick()
        compose.waitForIdle()
        val prefs = Prefs(ctx)
        assertTrue(prefs.tutorialSeen)
        assertFalse(prefs.agentTutorialSeen)
    }
}
