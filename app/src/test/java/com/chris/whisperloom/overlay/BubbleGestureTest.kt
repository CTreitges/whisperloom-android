package com.chris.whisperloom.overlay

import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.overlay.BubbleGesture.Phase
import com.chris.whisperloom.overlay.BubbleGesture.Release
import com.chris.whisperloom.overlay.ModeCross.CrossTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gesten am schwebenden Knopf (UX-Spec §6.22, docs/design/bubble-textfeld.md §1/§5), ohne Android. */
class BubbleGestureTest {

    private val g = BubbleGesture()

    /** Halten mit Aufnahme, wie der Dienst es bei eingeschaltetem "Halten zum Sprechen" ausloest. */
    private fun holdAndRecord() {
        g.down()
        assertTrue(g.holdElapsed(idle = true))
        g.recordingStarted()
    }

    @Test fun kurzerTippBleibtUmschalten() {
        g.down()
        assertEquals(Release.TAP, g.up())
        assertFalse(g.held)
    }

    @Test fun haltenOhneWischenSendet() {
        holdAndRecord()
        assertEquals(Phase.HELD, g.phase)
        assertEquals(Release.SEND, g.up())
        assertEquals(Phase.NONE, g.phase)
    }

    @Test fun haltenUndWischenAufModusWaehltIhn() {
        holdAndRecord()
        g.aim(CrossTarget.Mode(RefineMode.SUMMARIZE))
        assertEquals(Release.PICK, g.up())
        assertEquals(CrossTarget.Mode(RefineMode.SUMMARIZE), g.armed)
    }

    @Test fun haltenUndWischenAufKreuzVerwirft() {
        holdAndRecord()
        g.aim(CrossTarget.Discard)
        assertEquals(Release.DISCARD, g.up())
    }

    @Test fun zurueckAufDenKnopfSendetWieHalten() {
        holdAndRecord()
        g.aim(CrossTarget.Mode(RefineMode.OFF))
        g.aim(null)
        assertEquals(Release.SEND, g.up())
    }

    @Test fun nachDerHaltezeitVerschiebtNichtsMehr() {
        holdAndRecord()
        assertFalse("kein Zug aus dem Halten", g.beyondSlop())
        assertEquals(Phase.HELD, g.phase)
        assertFalse(g.dragging)
    }

    @Test fun ohneAufnahmeIstLoslassenOhneZielEinTipp() {
        // Schalter aus oder nicht eingerichtet: Halten oeffnet nur das Kreuz.
        g.down()
        assertTrue(g.holdElapsed(idle = true))
        assertFalse(g.held)
        assertEquals(Release.TAP, g.up())
    }

    @Test fun ohneAufnahmeSetztLoslassenAufModusNurDenModus() {
        g.down()
        g.holdElapsed(idle = true)
        g.aim(CrossTarget.Mode(RefineMode.POLISH))
        assertEquals(Release.PICK, g.up())
        assertFalse(g.held)
    }

    @Test fun bewegungVorDerHaltezeitIstNurZiehen() {
        g.down()
        assertTrue(g.beyondSlop())
        assertFalse("kein Halten mehr nach dem Zug", g.holdElapsed(idle = true))
        assertFalse(g.held)
        assertEquals(Release.DROP, g.up())
    }

    @Test fun zielenOhneKreuzWirktNicht() {
        g.down()
        g.aim(CrossTarget.Discard)
        assertNull(g.armed)
        assertEquals(Release.TAP, g.up())
    }

    @Test fun abgefangenerTouchImHaltenSendetUndWaehltNichts() {
        holdAndRecord()
        g.aim(CrossTarget.Mode(RefineMode.BEAUTIFY))
        assertTrue(g.cancel())
        assertEquals(Phase.NONE, g.phase)
    }

    @Test fun abgefangenerTouchOhneHaltenSendetNicht() {
        g.down()
        assertFalse(g.cancel())
        g.down()
        g.beyondSlop()
        assertFalse(g.cancel())
        g.down()
        g.holdElapsed(idle = true)
        assertFalse("Kreuz ohne Aufnahme", g.cancel())
    }

    @Test fun waehrendDerAufnahmeBleibtLangerDruckEinTipp() {
        g.down()
        assertFalse(g.holdElapsed(idle = false))
        assertEquals(Release.TAP, g.up())
    }

    @Test fun neuerDruckVergisstHaltenUndZiel() {
        holdAndRecord()
        g.aim(CrossTarget.Discard)
        g.up()
        g.down()
        assertFalse(g.held)
        assertNull(g.armed)
        assertEquals(Release.TAP, g.up())
    }

    @Test fun langsamerTippErstAbEinerSekundeNachDerHaltezeitGesendet() {
        val hold = 400L
        assertTrue(BubbleGesture.isSlowTap(downTime = 1_000, upTime = 1_000 + hold + 999, holdTimeout = hold))
        assertFalse(BubbleGesture.isSlowTap(downTime = 1_000, upTime = 1_000 + hold + BubbleGesture.HOLD_MIN_MS, holdTimeout = hold))
    }

    @Test fun loslassenOhneDruck() {
        assertEquals(Release.NONE, g.up())
    }
}
