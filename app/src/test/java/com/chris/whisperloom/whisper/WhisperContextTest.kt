package com.chris.whisperloom.whisper

import com.chris.whisperloom.AudioUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine JVM-Tests fuer die Entscheidungen vor whisper_full (kein Geraet, keine .so noetig). */
class WhisperContextTest {

    private val sekunde = AudioUtils.SAMPLE_RATE

    @Test fun fensterIstGenau30Sekunden() {
        assertEquals(480_000, WhisperContext.WINDOW_SAMPLES)
    }

    @Test fun bisEinschliesslich30SekundenOhneZeitstempel() {
        assertFalse(WhisperContext.useTimestamps(0))
        assertFalse(WhisperContext.useTimestamps(sekunde))
        assertFalse(WhisperContext.useTimestamps(29 * sekunde))
        assertFalse(WhisperContext.useTimestamps(30 * sekunde)) // genau ein Fenster: kein Schnitt moeglich
    }

    @Test fun ueber30SekundenMitZeitstempeln() {
        assertTrue(WhisperContext.useTimestamps(30 * sekunde + 1))
        assertTrue(WhisperContext.useTimestamps(31 * sekunde))
        assertTrue(WhisperContext.useTimestamps(5 * 60 * sekunde)) // geteilte Sprachnachricht, 5-min-Stueck
    }

    @Test fun kurzesAudioWirdAufgefuelltOhneZeitstempel() {
        // Auffuellen auf 1 s darf die Entscheidung nicht kippen.
        val audio = WhisperContext.padToMinimum(FloatArray(100))
        assertEquals(sekunde, audio.size)
        assertFalse(WhisperContext.useTimestamps(audio.size))
    }
}
