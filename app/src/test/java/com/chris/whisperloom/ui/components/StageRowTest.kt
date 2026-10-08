package com.chris.whisperloom.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stufen-Zeile mit zwei Zielen (3.9.0): der Text oeffnet (erkennbar am "›"), die unsichtbare Zone um
 * den Punkt waehlt — zwei Knoten fuer TalkBack, die Zone gross genug zum Treffen. Schmales Geraet
 * (360 dp) wie im Plan. Dass die Zone keine Toenung hat, prueft SettingsScreenshotTest am Bild.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class StageRowTest {

    @get:Rule
    val compose = createComposeRule()

    private var opened = 0
    private var selections = 0
    private var selected by mutableStateOf(false)
    private var schrift by mutableStateOf(1f)

    private val beschreibung = "Zeichensetzung und Groß-/Kleinschreibung. Inhalt unverändert · Lesbar · Claude Opus 5.5"

    private fun zeile() {
        compose.setContent {
            WhisperLoomTheme {
                val d = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(d.density, schrift)) {
                    // Wie auf der Seite: 20 dp Rand der Spalte plus 20 dp der Karte.
                    Column(Modifier.padding(horizontal = 40.dp).stageList(2)) {
                        StageRow(
                            headline = "Glätten",
                            supporting = beschreibung,
                            selected = selected,
                            selectLabel = "Glätten für Diktat verwenden",
                            index = 1,
                            onOpen = { opened++ },
                            onSelect = {
                                selections++
                                selected = true
                            },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun text() = compose.onNode(hasText("Glätten") and hasClickAction())
    private fun zone() = compose.onNodeWithContentDescription("Glätten für Diktat verwenden")

    @Test fun derTextOeffnetDieSeiteUndWaehltNicht() {
        zeile()
        text().performClick()
        compose.waitForIdle()
        assertEquals(1, opened)
        assertEquals(0, selections)
        zone().assertIsNotSelected()
    }

    @Test fun dieZoneWaehltUndOeffnetNicht() {
        zeile()
        zone().assertIsNotSelected().performClick()
        compose.waitForIdle()
        assertEquals(1, selections)
        assertEquals(0, opened)
        zone().assertIsSelected()
    }

    @Test fun zweiKnotenMitRolleUndZustand() {
        zeile()
        assertEquals("genau zwei Ziele", 2, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
        // Text: Schaltflaeche, Doppeltipp "Einstellungen oeffnen", kein Auswahl-Zustand.
        text()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
            .assert(hasText(beschreibung))
        assertEquals("Einstellungen öffnen", text().fetchSemanticsNode().config[SemanticsActions.OnClick].label)
        // Zone: Optionsfeld mit Zustand und eigenem Namen.
        zone()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assert(isSelectable())
            .assertIsNotSelected()
    }

    @Test fun dieZoneIstGrossGenugUndSoHochWieDieZeile() {
        zeile()
        // Material: Touch-Ziel mindestens 48 dp; Plan: Zone 64–72 dp breit.
        zone().assertWidthIsAtLeast(64.dp).assertHeightIsAtLeast(48.dp).assertTouchWidthIsEqualTo(StageZoneWidth)
        val z = zone().getUnclippedBoundsInRoot()
        val t = text().getUnclippedBoundsInRoot()
        assertEquals("Zone 72 dp", 72f, (z.right - z.left).value, 0.5f)
        assertEquals("volle Zeilenhoehe", t.height.value, z.height.value, 0.5f)
        assertTrue("Zone rechts neben dem Text", z.left >= t.right)
    }

    @Test fun mitDoppelterSchriftBleibtDieZoneUndDieZeileWaechst() {
        zeile()
        val normal = text().getUnclippedBoundsInRoot().height
        schrift = 2f
        compose.waitForIdle()
        val z = zone().getUnclippedBoundsInRoot()
        val t = text().getUnclippedBoundsInRoot()
        assertEquals(72f, (z.right - z.left).value, 0.5f)
        assertTrue("Text ueberlappt die Zone nicht", t.right <= z.left)
        // Ohne Native-Graphics misst Robolectric Text nur grob; das echte Umbrechen zeigt der Screenshot
        // "textverbesserung-360dp-schrift200" (SettingsScreenshotTest).
        assertTrue("die Zeile waechst mit der Schrift: $normal -> ${t.height}", t.height > normal)
        assertEquals("die Zone waechst mit", t.height.value, z.height.value, 0.5f)
    }

    /** Das "›" sagt, dass der Text die Einstellungen oeffnet: hinter dem Titel, auf seiner Hoehe, vor der Zone. */
    @Test fun dasChevronStehtHinterDemTitelUndVorDerZone() {
        zeile()
        chevronNebenDemTitel()
        // 200 % Schrift bei 360 dp: das "›" bricht nicht allein um und ragt nicht in die Zone.
        schrift = 2f
        compose.waitForIdle()
        chevronNebenDemTitel()
    }

    private fun chevronNebenDemTitel() {
        val c = compose.onNodeWithTag(STAGE_CHEVRON_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val titel = compose.onNode(hasText("Glätten"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val t = text().getUnclippedBoundsInRoot()
        val z = zone().getUnclippedBoundsInRoot()
        assertEquals("20 dp", 20f, (c.right - c.left).value, 0.5f)
        assertTrue("hinter dem Titel", c.left >= titel.right)
        assertTrue("in der Zeile des Titels", c.top >= titel.top && c.bottom <= titel.bottom)
        assertEquals("auf die Titelhoehe zentriert", (titel.top + titel.bottom).value / 2, (c.top + c.bottom).value / 2, 0.5f)
        assertTrue("Teil des Ziels, das oeffnet", c.right <= t.right)
        assertTrue("nicht in der Zone", c.right <= z.left)
    }

    @Test fun dieListeninfoStehtAnDerZone() {
        zeile()
        val info = zone().fetchSemanticsNode().config[SemanticsProperties.CollectionItemInfo]
        assertEquals(1, info.rowIndex)
        assertEquals(0, info.columnIndex)
        val liste = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo)).fetchSemanticsNode()
        assertEquals(2, liste.config[SemanticsProperties.CollectionInfo].rowCount)
        // Der Text ist kein Listeneintrag, sonst zaehlte TalkBack jede Stufe doppelt.
        text().assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.CollectionItemInfo))
    }
}
