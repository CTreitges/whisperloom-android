package com.chris.whisperloom.ui.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chris.whisperloom.history.History
import com.chris.whisperloom.ui.nav.NavState
import com.chris.whisperloom.ui.nav.Screen
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Verlauf-Einstellungen (Plan §6.6): speichern an/aus, Groesse mit Rueckfrage beim Verkleinern, alles loeschen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class HistorySettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val ui = HistoryUi(compose)

    @Before fun setUp() = ui.setUp()

    @After fun tearDown() = ui.tearDown()

    private fun einstellungen(): NavState = ui.show(Screen.Home, Screen.History, Screen.HistorySettings)

    private fun zwoelfEintraege() = (0 until 12).forEach { ui.record(raw = "Diktat $it", at = ui.at(0, "08:%02d".format(it))) }

    @Test fun verkleinernFragtWieVieleDerAeltestenGehen() {
        zwoelfEintraege()
        einstellungen()
        ui.waitFor("12 Einträge")
        compose.onNodeWithText("50 Einträge").assertIsSelected()

        compose.onNodeWithText("10 Einträge").performClick()
        ui.waitFor("Die 2 ältesten Einträge werden gelöscht.")
        compose.onNodeWithText("Abbrechen").performClick()
        compose.waitForIdle()
        assertEquals(12, History.count(ui.ctx))
        assertEquals(50, ui.prefs.historySize)

        compose.onNodeWithText("10 Einträge").performClick()
        compose.onNodeWithText("Verkleinern").performClick()
        ui.waitUntil { History.count(ui.ctx) == 10 }
        assertEquals(10, ui.prefs.historySize)
        assertEquals("die neuesten bleiben", (2 until 12).map { "Diktat $it" }.reversed(), History.list(ui.ctx).map { it.raw })
        ui.waitUntil { compose.onAllNodes(hasText("10 Einträge")).fetchSemanticsNodes().size == 2 }
    }

    @Test fun vergroessernFragtNicht() {
        zwoelfEintraege()
        einstellungen()
        ui.waitFor("12 Einträge")

        compose.onNodeWithText("100 Einträge").performClick()

        ui.waitUntil { ui.prefs.historySize == 100 }
        compose.onNodeWithText("Verlauf verkleinern?").assertDoesNotExist()
        assertEquals(12, History.count(ui.ctx))
    }

    @Test fun ausschaltenFragtUndLoeschtAlles() {
        zwoelfEintraege()
        einstellungen()
        ui.waitFor("12 Einträge")
        compose.onNode(isToggleable()).assertIsOn()

        compose.onNodeWithText("Verlauf speichern").performClick()
        compose.onNodeWithText("Verlauf ausschalten?").assertIsDisplayed()
        compose.onNodeWithText("Ausschalten").performClick()

        ui.waitUntil { History.count(ui.ctx) == 0 }
        assertFalse(ui.prefs.historyEnabled)
        compose.waitForIdle()
        compose.onNode(isToggleable()).assertIsOff()

        // Einschalten fragt nicht.
        compose.onNodeWithText("Verlauf speichern").performClick()
        ui.waitUntil { ui.prefs.historyEnabled }
        compose.onNodeWithText("Verlauf ausschalten?").assertDoesNotExist()
    }

    @Test fun alleLoeschenMitBestaetigung() {
        zwoelfEintraege()
        einstellungen()
        ui.waitFor("12 Einträge").performClick()

        compose.onNodeWithText("Alle Einträge löschen?").assertIsDisplayed()
        compose.onNode(hasText("Alle löschen") and hasAnyAncestor(isDialog())).performClick()

        ui.waitUntil { History.count(ui.ctx) == 0 }
        assertTrue("der Verlauf bleibt an", ui.prefs.historyEnabled)
    }

    @Test fun hinweisNurAufDiesemGeraet() {
        einstellungen()
        compose.onNodeWithText("Der Verlauf liegt nur auf diesem Gerät", substring = true).assertIsDisplayed()
    }
}
