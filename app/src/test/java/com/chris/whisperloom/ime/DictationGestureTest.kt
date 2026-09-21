package com.chris.whisperloom.ime

import com.chris.whisperloom.ime.DictationGesture.Phase
import com.chris.whisperloom.ime.DictationGesture.Release
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wisch-Geste am Mikro-Knopf: rechts stellt fest, links verwirft, Loslassen ohne Wischen
 * sendet weiterhin sofort. Reines JUnit — die Logik ist bewusst Android-frei.
 */
class DictationGestureTest {

    private val arm = 56f
    private val hysteresis = 12f
    private val vertical = 64f

    private fun phase(dx: Float, dy: Float = 0f, current: Phase = Phase.RECORDING) =
        DictationGesture.phase(dx, dy, arm, hysteresis, vertical, current)

    // --- Einrasten -----------------------------------------------------------

    @Test fun ohneBewegungBleibtEsBeiDerAufnahme() {
        assertEquals(Phase.RECORDING, phase(0f))
    }

    @Test fun unterDerSchwelleRastetNichtsEin() {
        assertEquals(Phase.RECORDING, phase(arm - 1f))
        assertEquals(Phase.RECORDING, phase(-(arm - 1f)))
    }

    @Test fun rechtsWischenStelltFest() {
        assertEquals(Phase.LOCK_ARMED, phase(arm))
        assertEquals(Phase.LOCK_ARMED, phase(arm * 3))
    }

    @Test fun linksWischenVerwirft() {
        assertEquals(Phase.CANCEL_ARMED, phase(-arm))
        assertEquals(Phase.CANCEL_ARMED, phase(-arm * 3))
    }

    // --- Hysterese -----------------------------------------------------------

    @Test fun eingerastetesZielHaeltUeberDieHysterese() {
        // Zurueck unter die Einrast-Schwelle, aber noch ueber (arm - hysteresis): haelt.
        assertEquals(Phase.LOCK_ARMED, phase(arm - hysteresis, current = Phase.LOCK_ARMED))
        assertEquals(Phase.CANCEL_ARMED, phase(-(arm - hysteresis), current = Phase.CANCEL_ARMED))
    }

    @Test fun unterDerHystereseLoestDasZielWieder() {
        assertEquals(Phase.RECORDING, phase(arm - hysteresis - 1f, current = Phase.LOCK_ARMED))
        assertEquals(Phase.RECORDING, phase(-(arm - hysteresis - 1f), current = Phase.CANCEL_ARMED))
    }

    @Test fun richtungswechselVerlangtDenVollenWeg() {
        // Aus dem Feststellen nach links: die halbe (Hysterese-)Schwelle darf NICHT reichen.
        assertEquals(Phase.RECORDING, phase(-(arm - hysteresis), current = Phase.LOCK_ARMED))
        assertEquals(Phase.CANCEL_ARMED, phase(-arm, current = Phase.LOCK_ARMED))
    }

    // --- Senkrechte Abweichung ----------------------------------------------

    @Test fun zuSteilGezogenRastetNichtEin() {
        assertEquals(Phase.RECORDING, phase(arm * 2, dy = vertical + 1f))
        assertEquals(Phase.RECORDING, phase(-arm * 2, dy = -(vertical + 1f)))
    }

    @Test fun zuSteilGezogenNimmtEinEingerastetesZielZurueck() {
        assertEquals(Phase.RECORDING, phase(arm * 2, dy = vertical + 1f, current = Phase.LOCK_ARMED))
        assertEquals(
            Phase.RECORDING,
            phase(-arm * 2, dy = -(vertical + 1f), current = Phase.CANCEL_ARMED),
        )
    }

    @Test fun innerhalbDerToleranzRastetEsWeiterhinEin() {
        assertEquals(Phase.LOCK_ARMED, phase(arm, dy = vertical))
    }

    // --- Loslassen -----------------------------------------------------------

    @Test fun loslassenOhneWischenSendet() {
        assertEquals(Release.SEND, DictationGesture.release(Phase.RECORDING))
    }

    @Test fun loslassenMitEingerastetemZiel() {
        assertEquals(Release.LOCK, DictationGesture.release(Phase.LOCK_ARMED))
        assertEquals(Release.DISCARD, DictationGesture.release(Phase.CANCEL_ARMED))
    }

    @Test fun jedePhaseFuehrtZuEinemEigenenVerhalten() {
        val outcomes = Phase.values().map { DictationGesture.release(it) }
        assertEquals("jede Phase braucht ein eigenes Loslass-Verhalten", 3, outcomes.toSet().size)
    }

    // --- Masse ---------------------------------------------------------------

    @Test fun schwellenSindPlausibel() {
        // Die Hysterese muss kleiner sein als die Einrast-Schwelle, sonst haelt ein Ziel ewig.
        assertTrue(DictationGesture.RELEASE_HYSTERESIS_DP < DictationGesture.ARM_DISTANCE_DP)
        assertTrue(DictationGesture.ARM_DISTANCE_DP > 0)
        assertTrue(DictationGesture.VERTICAL_TOLERANCE_DP > 0)
    }

    @Test fun ohneHystereseBleibtDieSchwelleScharf() {
        assertEquals(Phase.RECORDING, DictationGesture.phase(arm - 1f, 0f, arm, 0f, vertical, Phase.LOCK_ARMED))
        assertEquals(Phase.LOCK_ARMED, DictationGesture.phase(arm, 0f, arm, 0f, vertical, Phase.LOCK_ARMED))
    }
}
