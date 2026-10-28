package com.chris.whisperloom.simple

import android.app.Application
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bedienungshilfen-Semantik der Ein-Knopf-Tastatur (Konzept §4.1, §4.5): genau eine Klick-Aktion je Knopf
 * (TalkBack-Doppeltipp, Schalterzugang ohne Aktionsmenue, Voice Access), nicht als Tastaturtaste markiert,
 * Zustand als stateDescription, Pane-Titel, grosse Ziele in EINER Zeile.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SimpleKeyboardA11yTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun inputView(): View =
        Robolectric.buildService(SimpleInputMethodService::class.java).create().get().onCreateInputView()

    private fun knoepfe(v: View) = listOf(R.id.simple_record, R.id.simple_delete, R.id.simple_switch).map { v.findViewById<Button>(it) }

    @Test fun jederKnopfHatGenauEineKlickAktion() {
        for (b in knoepfe(inputView())) {
            val name = app.resources.getResourceEntryName(b.id)
            assertTrue("$name ohne OnClickListener", b.hasOnClickListeners())
            assertTrue("$name nicht klickbar", b.isClickable)
            assertFalse("$name darf nicht lang-klickbar sein", b.isLongClickable)
            assertFalse("$name darf nicht kontext-klickbar sein", b.isContextClickable)
            // Kein Touch-Listener (Halten/Wischen) und kein eigener Delegate mit Zusatzaktionen: ein normaler Knopf.
            assertNull("$name mit eigenem AccessibilityDelegate", b.accessibilityDelegate)
            // (Robolectric fuellt createAccessibilityNodeInfo() nicht mit den Klick-Aktionen — die View-Flags sind die Quelle.)
            // Eigene (App-)Aktionen tragen Ressourcen-IDs aus dem 0x7f-Namensraum; Systemaktionen liegen in android.R.id.
            val actions = b.createAccessibilityNodeInfo().actionList.map { it.id }
            assertFalse("$name mit ACTION_LONG_CLICK", AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK.id in actions)
            assertTrue("$name mit Zusatzaktionen: $actions", actions.none { (it ushr 24) == 0x7f })
        }
    }

    @Test fun knoepfeSindKeineTastaturtasten() {
        // TalkBack hat fuer Tasten einen eigenen Tippmodus; als normaler Knopf loest Erkunden nichts aus (§4.5).
        for (b in knoepfe(inputView())) {
            assertFalse(app.resources.getResourceEntryName(b.id), b.createAccessibilityNodeInfo().isTextEntryKey)
        }
    }

    @Test fun zustandStehtInDerStateDescription() {
        val record = inputView().findViewById<Button>(R.id.simple_record)
        assertEquals(app.getString(R.string.simple_state_ready), record.stateDescription)
        assertEquals(app.getString(R.string.simple_btn_record), record.text.toString())
    }

    @Test fun fensterHatEinenPaneTitel() {
        val v = inputView()
        assertEquals(app.getString(R.string.simple_pane_title), v.accessibilityPaneTitle)
        assertTrue(app.getString(R.string.simple_pane_title).contains("Tastatur"))
    }

    @Test fun zieleSindGrossUndInEinerZeile() {
        val v = inputView()
        val d = app.resources.displayMetrics.density
        val record = v.findViewById<Button>(R.id.simple_record)
        val delete = v.findViewById<Button>(R.id.simple_delete)
        assertTrue("Aufnehmen < 96 dp", record.minHeight / d >= 96)
        assertTrue("Loeschen < 64 dp", delete.minWidth / d >= 64 && delete.minHeight / d >= 64)
        assertEquals("Aufnehmen und Loeschen in derselben Zeile", record.parent, delete.parent)
        assertEquals("Aufnehmen ist das erste Element der Zeile", 0, (record.parent as android.view.ViewGroup).indexOfChild(record))
    }

    @Test fun vorschauzeileIstKeineLiveRegionStatuszeileSchon() {
        val v = inputView()
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_NONE, v.findViewById<View>(R.id.simple_preview).accessibilityLiveRegion)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, v.findViewById<View>(R.id.simple_status).accessibilityLiveRegion)
    }
}
