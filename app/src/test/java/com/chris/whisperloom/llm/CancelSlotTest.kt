package com.chris.whisperloom.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CancellationException

/**
 * Der Abbruch gehoert genau einer Rechnung (Review c4): er geht weder verloren, wenn er kurz vor
 * dem Rechenstart kommt, noch trifft er die Rechnung des naechsten Auftrags.
 */
class CancelSlotTest {

    private val aborted = mutableListOf<String>()
    private val slot = CancelSlot<String> { aborted += it }

    @Test fun abbruchVorDemEintragenGehtNichtVerloren() {
        // Tipp "ohne KI" zwischen der Pruefung des Halters und dem Rechenstart: cancel() findet
        // noch nichts — das Eintragen muss den Abbruch dann selbst sehen.
        slot.cancel()
        try {
            slot.enter("A") { true }
            fail("CancellationException erwartet")
        } catch (e: CancellationException) {
            slot.cancel()
            assertEquals("nichts eingetragen, nichts abzubrechen", emptyList<String>(), aborted)
        }
    }

    @Test fun abbruchTrifftNieDenNaechstenAuftrag() {
        var aAbgebrochen = false
        slot.enter("A") { aAbgebrochen }
        slot.leave()
        slot.enter("B") { false } // B ist nicht abgebrochen
        aAbgebrochen = true // der Tipp fuer A kommt erst jetzt an

        slot.cancel()

        assertEquals(emptyList<String>(), aborted)
    }

    @Test fun abbruchTrifftDieEingetrageneRechnungIhresAuftrags() {
        var abgebrochen = false
        slot.enter("A") { abgebrochen }
        slot.cancel()
        assertEquals("nicht abgebrochen", emptyList<String>(), aborted)

        abgebrochen = true
        slot.cancel()
        assertEquals(listOf("A"), aborted)

        var geschlossen = false
        slot.leave { geschlossen = true }
        slot.cancel()
        assertEquals("ausgetragen: kein weiterer Abbruch", listOf("A"), aborted)
        assertEquals(true, geschlossen)
    }
}
