package com.chris.whisperloom.overlay

import com.chris.whisperloom.overlay.BubbleGesture.Phase
import com.chris.whisperloom.overlay.BubbleGesture.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Halten zum Sprechen am schwebenden Knopf (UX-Spec §6.21), ohne Android. */
class BubbleGestureTest {

    private val g = BubbleGesture()

    @Test fun kurzerTippBleibtUmschalten() {
        g.down()
        assertEquals(Release.TAP, g.up())
        assertFalse(g.held)
    }

    @Test fun haltenStartetUndLoslassenSendet() {
        g.down()
        assertTrue(g.holdElapsed(enabled = true, idle = true))
        assertEquals(Phase.HELD, g.phase)
        assertEquals(Release.SEND, g.up())
        assertEquals(Phase.NONE, g.phase)
    }

    @Test fun haltenUndZiehenBleibtGehalten() {
        g.down()
        g.holdElapsed(enabled = true, idle = true)
        assertTrue("Zug beginnt", g.beyondSlop())
        assertFalse("nur einmal Beginn", g.beyondSlop())
        assertTrue(g.held)
        assertEquals(Release.DROP, g.up())
    }

    @Test fun bewegungVorDerHaltezeitIstNurZiehen() {
        g.down()
        assertTrue(g.beyondSlop())
        assertFalse("kein Halten mehr nach dem Zug", g.holdElapsed(enabled = true, idle = true))
        assertFalse(g.held)
        assertEquals(Release.DROP, g.up())
    }

    @Test fun abgefangenerTouchImHaltenSendet() {
        g.down()
        g.holdElapsed(enabled = true, idle = true)
        assertTrue(g.cancel())
        assertEquals(Phase.NONE, g.phase)
    }

    @Test fun abgefangenerTouchOhneHaltenSendetNicht() {
        g.down()
        assertFalse(g.cancel())
        g.down()
        g.beyondSlop()
        assertFalse(g.cancel())
    }

    @Test fun schalterAusNieHalten() {
        g.down()
        assertFalse(g.holdElapsed(enabled = false, idle = true))
        assertEquals(Release.TAP, g.up())
    }

    @Test fun waehrendDerAufnahmeBleibtLangerDruckEinTipp() {
        g.down()
        assertFalse(g.holdElapsed(enabled = true, idle = false))
        assertEquals(Release.TAP, g.up())
    }

    @Test fun neuerDruckVergisstDasHalten() {
        g.down()
        g.holdElapsed(enabled = true, idle = true)
        g.up()
        g.down()
        assertFalse(g.held)
        assertEquals(Release.TAP, g.up())
    }

    @Test fun loslassenOhneDruck() {
        assertEquals(Release.NONE, g.up())
    }
}
