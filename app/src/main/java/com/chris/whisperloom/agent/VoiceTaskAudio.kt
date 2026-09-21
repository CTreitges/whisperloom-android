package com.chris.whisperloom.agent

import kotlin.math.roundToInt

/**
 * Umwandlung der Aufnahme fuers Zwischenspeichern auf der Platte. Die Samples duerfen NICHT
 * durch die WorkManager-Eingabedaten wandern: `Data` ist auf 10 240 Byte begrenzt, eine
 * Sekunde Audio hat bei 16 kHz schon 64 kB. Der Dienst legt sie also als Datei ab und gibt
 * dem Auftrag nur den Pfad mit.
 *
 * Format: rohes PCM16 little-endian, genau wie es [com.chris.whisperloom.AudioRecorder]
 * vom Mikrofon liest — die Float-Fassung ist daraus abgeleitet, der Umweg kostet also
 * keine Genauigkeit, spart aber die Haelfte des Platzes.
 */
object VoiceTaskAudio {

    fun toBytes(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            // Runden, nicht abschneiden: sonst verdoppelt sich der Quantisierungsfehler.
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).roundToInt()
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    fun fromBytes(raw: ByteArray): FloatArray {
        val count = raw.size / 2
        val out = FloatArray(count)
        for (i in 0 until count) {
            val lo = raw[i * 2].toInt() and 0xFF
            val hi = raw[i * 2 + 1].toInt() // Vorzeichen erhalten
            out[i] = ((hi shl 8) or lo) / 32768f
        }
        return out
    }
}
