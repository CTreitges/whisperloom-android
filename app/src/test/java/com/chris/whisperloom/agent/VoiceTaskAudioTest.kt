package com.chris.whisperloom.agent

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Das Zwischenspeichern der Aufnahme: PCM16 hin und zurueck. */
class VoiceTaskAudioTest {

    @Test fun hinUndZurueckBleibtPraktischGleich() {
        val original = FloatArray(1000) { i -> kotlin.math.sin(i / 20.0).toFloat() * 0.8f }
        val zurueck = VoiceTaskAudio.fromBytes(VoiceTaskAudio.toBytes(original))
        assertEquals(original.size, zurueck.size)
        // PCM16 loest rund 3e-5 auf; mehr als ein Schritt Abweichung waere ein Fehler.
        original.indices.forEach { i ->
            assertEquals("Sample $i", original[i], zurueck[i], 1e-4f)
        }
    }

    @Test fun zweiBytesProSample() {
        assertEquals(2000, VoiceTaskAudio.toBytes(FloatArray(1000)).size)
    }

    @Test fun uebersteuertesWirdGekappt() {
        val bytes = VoiceTaskAudio.toBytes(floatArrayOf(5f, -5f))
        val zurueck = VoiceTaskAudio.fromBytes(bytes)
        assertEquals(0.99997f, zurueck[0], 0.0001f)
        assertEquals(-0.99997f, zurueck[1], 0.0001f)
    }

    @Test fun vorzeichenBleibtErhalten() {
        // Ohne die Vorzeichen-Behandlung beim High-Byte kaemen negative Samples als grosse positive zurueck.
        val zurueck = VoiceTaskAudio.fromBytes(VoiceTaskAudio.toBytes(floatArrayOf(-0.5f)))
        assertEquals(-0.5f, zurueck[0], 0.001f)
    }

    @Test fun leereAufnahmeBleibtLeer() {
        assertArrayEquals(FloatArray(0), VoiceTaskAudio.fromBytes(ByteArray(0)), 0f)
        assertEquals(0, VoiceTaskAudio.toBytes(FloatArray(0)).size)
    }

    @Test fun ungeradeByteZahlKipptNicht() {
        // Abgeschnittene Datei (Absturz waehrend des Schreibens): letztes halbes Sample faellt weg.
        assertEquals(1, VoiceTaskAudio.fromBytes(ByteArray(3)).size)
    }
}
