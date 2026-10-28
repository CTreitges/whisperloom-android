package com.chris.whisperloom.simple

import com.chris.whisperloom.simple.SimpleKeyboardState.DeleteAction
import com.chris.whisperloom.simple.SimpleKeyboardState.Phase
import com.chris.whisperloom.simple.SimpleKeyboardState.RecordAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zustandsmaschine der Ein-Knopf-Tastatur (Konzept §4.2): Doppeltreffer-Sperre, Loeschen waehrend/nach der Aufnahme. */
class SimpleKeyboardStateTest {

    private var now = 100_000L
    private val state = SimpleKeyboardState(lockMs = 500) { now }

    private fun aufnahmeLaeuft() {
        assertEquals(RecordAction.START, state.tapRecord())
        now += 600
        assertTrue(state.connected())
        now += 600
        assertEquals(Phase.RECORDING, state.phase)
    }

    @Test fun tippenStartetTippenBeendet() {
        assertEquals(Phase.READY, state.phase)
        aufnahmeLaeuft()
        assertEquals(RecordAction.FINISH, state.tapRecord())
        assertEquals(Phase.INSERTING, state.phase)
        state.inserted(12)
        assertEquals(Phase.INSERTED, state.phase)
        assertEquals(12, state.lastInserted)
        state.ready()
        assertEquals(Phase.READY, state.phase)
    }

    @Test fun doppeltrefferSperreNachZustandswechsel() {
        assertEquals(RecordAction.START, state.tapRecord())
        // Zweiter Tipp 200 ms spaeter (Zittern, doppelter Schalterdruck): zaehlt nicht.
        now += 200
        assertEquals(RecordAction.NONE, state.tapRecord())
        assertEquals(DeleteAction.NONE, state.tapDelete(confirm = false))
        assertEquals(Phase.CONNECTING, state.phase)
        // Waehrend der Aufnahme: der Tipp direkt nach dem Verbinden zaehlt auch nicht ...
        now += 400
        assertTrue(state.connected())
        now += 100
        assertEquals(RecordAction.NONE, state.tapRecord())
        assertEquals(Phase.RECORDING, state.phase)
        // ... nach Ablauf der Sperre schon.
        now += 500
        assertEquals(RecordAction.FINISH, state.tapRecord())
    }

    @Test fun ohneSperreZaehltJederTipp() {
        val sofort = SimpleKeyboardState(lockMs = 0) { now }
        assertEquals(RecordAction.START, sofort.tapRecord())
        assertTrue(sofort.connected())
        assertEquals(RecordAction.FINISH, sofort.tapRecord())
    }

    @Test fun waehrendDesVerbindensUndEinfuegensIstAufnehmenGesperrt() {
        assertEquals(RecordAction.START, state.tapRecord())
        now += 1_000
        assertEquals("Verbinden: gesperrt", RecordAction.NONE, state.tapRecord())
        assertTrue(state.connected())
        now += 1_000
        assertEquals(RecordAction.FINISH, state.tapRecord())
        now += 1_000
        assertEquals("Einfuegen: gesperrt", RecordAction.NONE, state.tapRecord())
        assertEquals(DeleteAction.NONE, state.tapDelete(confirm = false))
    }

    @Test fun loeschenWaehrendDesVerbindensBrichtAb() {
        assertEquals(RecordAction.START, state.tapRecord())
        now += 1_000
        assertEquals(DeleteAction.CANCEL_CONNECT, state.tapDelete(confirm = true))
        assertEquals(Phase.READY, state.phase)
        assertFalse("Abgebrochen: connected() darf nicht mehr greifen", state.connected())
    }

    @Test fun loeschenWaehrendDerAufnahmeVerwirft() {
        aufnahmeLaeuft()
        assertEquals(DeleteAction.DISCARD_RECORDING, state.tapDelete(confirm = true))
        assertEquals(Phase.READY, state.phase)
    }

    @Test fun loeschenNachDemDiktatEntferntDasLetzteSofortOhneBestaetigung() {
        aufnahmeLaeuft()
        state.tapRecord()
        state.inserted(20)
        now += 1_000
        assertEquals(DeleteAction.REMOVE_LAST, state.tapDelete(confirm = false))
        assertEquals(20, state.lastInserted)
        state.lastInserted = 0
        now += 1_000
        assertEquals("Nichts mehr zu entfernen", DeleteAction.NONE, state.tapDelete(confirm = false))
    }

    @Test fun loeschenMitBestaetigungBrauchtZweitenTippUndKeinenDoppeltreffer() {
        aufnahmeLaeuft()
        state.tapRecord()
        state.inserted(20)
        state.ready()
        now += 1_000
        assertEquals(DeleteAction.ASK_CONFIRM, state.tapDelete(confirm = true))
        assertTrue(state.confirmPending)
        // Doppeltreffer auf Loeschen innerhalb der Sperre: loescht NICHT.
        now += 200
        assertEquals(DeleteAction.NONE, state.tapDelete(confirm = true))
        now += 500
        assertEquals(DeleteAction.REMOVE_LAST, state.tapDelete(confirm = true))
        assertFalse(state.confirmPending)
    }

    @Test fun bestaetigungVerfaelltNachVierSekunden() {
        aufnahmeLaeuft()
        state.tapRecord()
        state.inserted(20)
        now += 1_000
        assertEquals(DeleteAction.ASK_CONFIRM, state.tapDelete(confirm = true))
        now += SimpleKeyboardState.CONFIRM_MS + 1
        assertFalse(state.confirmPending)
        assertEquals("Neue Nachfrage statt loeschen", DeleteAction.ASK_CONFIRM, state.tapDelete(confirm = true))
    }

    @Test fun fehlerLoeschenVerwirftUndAufnehmenWiederholt() {
        assertEquals(RecordAction.START, state.tapRecord())
        now += 1_000
        state.failed()
        assertEquals(Phase.ERROR, state.phase)
        now += 1_000
        assertEquals("Fehler + Aufnehmen = wiederholen", RecordAction.START, state.tapRecord())
        now += 1_000
        state.failed()
        now += 1_000
        assertEquals(DeleteAction.DISCARD_ERROR, state.tapDelete(confirm = true))
        assertEquals(Phase.READY, state.phase)
    }

    @Test fun fertigOhneTippNurAusDerAufnahme() {
        state.finish()
        assertEquals(Phase.READY, state.phase)
        aufnahmeLaeuft()
        state.finish()
        assertEquals(Phase.INSERTING, state.phase)
    }
}
