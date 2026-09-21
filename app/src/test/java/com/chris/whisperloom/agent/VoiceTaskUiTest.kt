package com.chris.whisperloom.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine JUnit-Tests der Widget-Logik: Pegel, Urteil, Tipp-Bedeutung, Zustand nach Neustart. */
class VoiceTaskUiTest {

    private fun ton(amplitude: Float, n: Int = 1600) = FloatArray(n) { i -> if (i % 2 == 0) amplitude else -amplitude }

    // --- Pegel ---------------------------------------------------------------

    @Test fun leereAufnahmeIstStill() {
        assertTrue(VoiceTaskUi.isSilent(FloatArray(0)))
    }

    @Test fun lauterNullenIstStill() {
        assertTrue("Stummgeschaltetes Mikrofon liefert exakt 0 — das ist der lautlose Fehlschlag", VoiceTaskUi.isSilent(FloatArray(16_000)))
    }

    @Test fun normalesRaumrauschenIstNichtStill() {
        assertFalse(VoiceTaskUi.isSilent(ton(0.01f)))
    }

    @Test fun knappUeberDerSchwelleGiltAlsTon() {
        assertFalse(VoiceTaskUi.isSilent(ton(VoiceTaskUi.SILENCE_RMS * 1.1f)))
    }

    @Test fun knappUnterDerSchwelleGiltAlsStille() {
        assertTrue(VoiceTaskUi.isSilent(ton(VoiceTaskUi.SILENCE_RMS * 0.9f)))
    }

    @Test fun rmsEinerRechteckschwingungIstDieAmplitude() {
        assertEquals(0.5f, VoiceTaskUi.rms(ton(0.5f)), 0.0001f)
    }

    // --- Urteil --------------------------------------------------------------

    @Test fun zuKurzSchlaegtStille() {
        // Beide Bedingungen treffen zu; "zu kurz" ist die praezisere Meldung.
        assertEquals(Verdict.TOO_SHORT, VoiceTaskUi.verdict(300, FloatArray(0)))
    }

    @Test fun langGenugAberOhneTonIstStille() {
        assertEquals(Verdict.SILENT, VoiceTaskUi.verdict(5_000, FloatArray(16_000)))
    }

    @Test fun langGenugMitTonIstInOrdnung() {
        assertEquals(Verdict.OK, VoiceTaskUi.verdict(5_000, ton(0.2f)))
    }

    @Test fun genauAnDerMindestdauerZaehltNochNicht() {
        assertEquals(Verdict.OK, VoiceTaskUi.verdict(VoiceTaskUi.MIN_DURATION_MS, ton(0.2f)))
        assertEquals(Verdict.TOO_SHORT, VoiceTaskUi.verdict(VoiceTaskUi.MIN_DURATION_MS - 1, ton(0.2f)))
    }

    // --- Tipp-Bedeutung ------------------------------------------------------

    @Test fun jederZustandHatEineEindeutigeTippBedeutung() {
        assertEquals(TapIntent.START, VoiceTaskUi.tap(VoiceTaskState.READY))
        assertEquals(TapIntent.START, VoiceTaskUi.tap(VoiceTaskState.SENT))
        assertEquals(TapIntent.STOP, VoiceTaskUi.tap(VoiceTaskState.RECORDING))
        assertEquals(TapIntent.RETRY, VoiceTaskUi.tap(VoiceTaskState.ERROR))
        assertEquals(TapIntent.SETUP, VoiceTaskUi.tap(VoiceTaskState.NO_MIC))
        assertEquals(TapIntent.SETUP, VoiceTaskUi.tap(VoiceTaskState.OFF))
        assertEquals(TapIntent.REFRESH, VoiceTaskUi.tap(VoiceTaskState.WORKING))
    }

    @Test fun waehrendGearbeitetWirdStartetEinTippNichtsNeues() {
        // Ein ungeduldiger Doppeltipp darf weder eine zweite Aufnahme noch einen zweiten
        // Versand ausloesen — er holt nur den echten Zustand.
        val tap = VoiceTaskUi.tap(VoiceTaskState.WORKING)
        assertEquals(TapIntent.REFRESH, tap)
        assertTrue(tap != TapIntent.START && tap != TapIntent.STOP && tap != TapIntent.RETRY)
    }

    @Test fun keinZustandFuehrtInsLeere() {
        // Sonst bliebe ein nach einem Neustart haengendes Widget fuer immer unbedienbar.
        VoiceTaskState.entries.forEach {
            assertTrue("$it hat keine Tippflaeche", VoiceTaskUi.tap(it) != TapIntent.NONE)
        }
    }

    // --- Zustand nach einem Neustart ----------------------------------------

    @Test fun ohneOffenenAuftragIstAllesBereit() {
        VoiceTaskState.entries.forEach {
            assertEquals("$it ohne Auftrag", VoiceTaskState.READY, VoiceTaskUi.afterRestart(it, hasWork = false))
        }
    }

    @Test fun abgerisseneAufnahmeWirdZumFehler() {
        assertEquals(VoiceTaskState.ERROR, VoiceTaskUi.afterRestart(VoiceTaskState.RECORDING, hasWork = true))
    }

    @Test fun laufenderAuftragBleibtLaufend() {
        assertEquals(VoiceTaskState.WORKING, VoiceTaskUi.afterRestart(VoiceTaskState.WORKING, hasWork = true))
    }

    @Test fun fehlerBleibtFehlerSolangeEtwasDaLiegt() {
        assertEquals(VoiceTaskState.ERROR, VoiceTaskUi.afterRestart(VoiceTaskState.ERROR, hasWork = true))
    }

    @Test fun jederAndereZustandMitOffenerArbeitIstEinFehler() {
        // "gesendet" oder "bereit" ueber einem liegengebliebenen Auftrag waere gelogen — und
        // beides ohne Weg zurueck. ERROR ist der einzige Zustand, dessen Tipp den Auftrag
        // erneut abschickt.
        listOf(VoiceTaskState.SENT, VoiceTaskState.READY, VoiceTaskState.NO_MIC, VoiceTaskState.OFF)
            .forEach { assertEquals("$it mit offener Arbeit", VoiceTaskState.ERROR, VoiceTaskUi.afterRestart(it, hasWork = true)) }
    }

    @Test fun dauerWirdAlsMinutenUndSekundenGezeigt() {
        assertEquals("0:07", VoiceTaskUi.timerText(7_000))
        assertEquals("1:05", VoiceTaskUi.timerText(65_000))
    }
}
