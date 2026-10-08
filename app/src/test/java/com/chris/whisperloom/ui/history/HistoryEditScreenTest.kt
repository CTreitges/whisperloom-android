package com.chris.whisperloom.ui.history

import android.content.ClipboardManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.chris.whisperloom.history.History
import com.chris.whisperloom.history.Processing
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bearbeiten-Fenster (Plan §6.4): Speichern ersetzt und markiert, Ursprung wird "Bearbeitet", Verwerfen fragt. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class HistoryEditScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val ui = HistoryUi(compose)

    @Before fun setUp() = ui.setUp()

    @After fun tearDown() = ui.tearDown()

    private fun bearbeiten(id: String, processing: Processing, show: String? = null): NavState =
        ui.show(Screen.Home, Screen.History, Screen.HistoryDetail(id, show), Screen.HistoryEdit(id, processing))

    private fun feld() = compose.onNode(hasSetTextAction())

    @Test fun speichernErsetztDieFassungUndMarkiertSieBearbeitet() {
        val entry = ui.record()
        val nav = bearbeiten(entry.id, Processing.SUMMARIZE)
        ui.waitFor("Fassung: Zusammenfassen")
        feld().assertTextEquals("Komme morgen später.")
        compose.onNodeWithText("Speichern").assertIsNotEnabled()

        feld().performTextReplacement("Komme morgen erst um zehn.")
        compose.onNodeWithText("Speichern").performClick()

        ui.waitUntil { nav.current is Screen.HistoryDetail }
        assertEquals(Screen.HistoryDetail(entry.id, "summarize"), nav.current)
        val version = History.get(ui.ctx, entry.id)!!.versions.getValue(Processing.SUMMARIZE)
        assertEquals("Komme morgen erst um zehn.", version.text)
        assertTrue(version.edited)
        assertEquals("Modell bleibt als Anzeige", "Claude Sonnet 5.5", version.model)
    }

    @Test fun ursprungBearbeitenErzeugtDieFassungBearbeitetUndDerRohtextBleibt() {
        val entry = ui.record()
        val nav = bearbeiten(entry.id, Processing.EDITED, Screen.HistoryDetail.ORIGIN)
        ui.waitFor("Fassung: Ursprung")
        feld().assertTextEquals(entry.raw)

        feld().performTextReplacement("Ich komme morgen später.")
        compose.onNodeWithText("Speichern").performClick()

        ui.waitUntil { nav.current is Screen.HistoryDetail }
        assertEquals(Screen.HistoryDetail(entry.id, "edited"), nav.current)
        val saved = History.get(ui.ctx, entry.id)!!
        assertEquals(entry.raw, saved.raw)
        assertEquals("Ich komme morgen später.", saved.versions.getValue(Processing.EDITED).text)
    }

    @Test fun gibtEsBearbeitetSchonGehtEsDortWeiter() {
        val entry = ui.record()
        History.edit(ui.ctx, entry.id, Processing.EDITED, "Schon einmal bearbeitet.")
        bearbeiten(entry.id, Processing.EDITED)
        ui.waitFor("Fassung: Bearbeitet")
        feld().assertTextEquals("Schon einmal bearbeitet.")
    }

    @Test fun schliessenMitAenderungenFragtUndVerwerfenLaesstAllesWieEsWar() {
        val entry = ui.record()
        val nav = bearbeiten(entry.id, Processing.SUMMARIZE)
        ui.waitFor("Fassung: Zusammenfassen")
        feld().performTextReplacement("Verworfen.")

        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.onNodeWithText("Änderungen verwerfen?").assertIsDisplayed()
        compose.onNodeWithText("Weiter bearbeiten").performClick()
        compose.waitForIdle()
        assertTrue(nav.current is Screen.HistoryEdit)
        feld().assertTextEquals("Verworfen.")

        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.onNodeWithText("Verwerfen").performClick()
        compose.waitForIdle()

        assertEquals(Screen.HistoryDetail(entry.id), nav.current)
        assertEquals(entry, History.get(ui.ctx, entry.id))
    }

    @Test fun schliessenOhneAenderungenFragtNicht() {
        val entry = ui.record()
        val nav = bearbeiten(entry.id, Processing.SUMMARIZE)
        ui.waitFor("Fassung: Zusammenfassen")

        compose.onNodeWithContentDescription("Schließen").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Änderungen verwerfen?").assertDoesNotExist()
        assertEquals(Screen.HistoryDetail(entry.id), nav.current)
    }

    @Test fun rueckgaengigUndWiederholen() {
        val entry = ui.record()
        bearbeiten(entry.id, Processing.SUMMARIZE)
        ui.waitFor("Fassung: Zusammenfassen")
        compose.onNodeWithContentDescription("Rückgängig").assertIsNotEnabled()

        feld().performTextInput(" Bis dann.")
        compose.onNodeWithContentDescription("Rückgängig").assertIsEnabled().performClick()
        feld().assertTextEquals("Komme morgen später.")

        compose.onNodeWithContentDescription("Wiederholen").assertIsEnabled().performClick()
        feld().assertTextEquals("Komme morgen später. Bis dann.")
    }

    @Test fun kopierenNimmtDenGeradeBearbeitetenText() {
        val entry = ui.record()
        bearbeiten(entry.id, Processing.SUMMARIZE)
        ui.waitFor("Fassung: Zusammenfassen")
        feld().performTextReplacement("Noch nicht gespeichert.")

        compose.onNodeWithText("Kopieren").performClick()

        val clip = ui.ctx.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
        assertEquals("Noch nicht gespeichert.", clip)
        assertFalse("Kopieren speichert nicht", History.get(ui.ctx, entry.id)!!.versions.getValue(Processing.SUMMARIZE).edited)
    }

    @Test fun eintragWegWaehrendDesBearbeitensFuehrtZurueck() {
        val nav = bearbeiten("1791456000000-0badcafe", Processing.SUMMARIZE)
        ui.waitUntil { nav.current !is Screen.HistoryEdit }
        assertNull(History.get(ui.ctx, "1791456000000-0badcafe"))
    }
}
