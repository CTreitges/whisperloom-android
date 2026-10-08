package com.chris.whisperloom

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Nimmt Mikrofon-Audio als 16 kHz Mono PCM16 auf und liefert es als FloatArray
 * (Whisper-Format, [-1, 1]). Push-to-talk: [start] beim Druecken, [stop] beim Loslassen.
 *
 * [pause] gibt das Mikrofon wirklich frei (Aufnahme-Thread endet, [AudioRecord] wird
 * freigegeben), behaelt aber das bisher Aufgenommene; [resume] haengt mit einer neuen Instanz
 * an. So geht der Mikrofon-Punkt (Android 12+) in der Pause aus, und ein Anruf bekommt das
 * Mikrofon. Waehrend der Pause wird nichts gesammelt — aufgenommene Stille braechte Whisper
 * zum Halluzinieren.
 *
 * Braucht die RECORD_AUDIO-Berechtigung (wird im Einrichtungs-Assistenten angefragt).
 */
class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16_000 // == AudioUtils.SAMPLE_RATE
        private const val TAG = "Loom-AudioRecorder"
        private val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    @Volatile private var recording = false
    @Volatile private var paused = false

    /** Der lesende Thread; ein ueberholter Thread (nach [pause] + [resume]) hoert damit von selbst auf. */
    @Volatile private var thread: Thread? = null
    private val pcm = ByteArrayOutputStream()

    /** Optionaler Pegel-Callback (0..1) fuer eine simple Waveform-Anzeige. */
    var onAmplitude: ((Float) -> Unit)? = null

    /** Nimmt gerade auf (nicht in der Pause). */
    val isRecording: Boolean get() = recording

    val isPaused: Boolean get() = paused

    /** Es gibt eine Aufnahme, die noch gesendet oder verworfen werden muss — auch pausiert. */
    val hasSession: Boolean get() = recording || paused

    /** Bisher aufgenommene Zeit, ueber alle Teile summiert (aus den Samples, nicht der Uhr). */
    val recordedMs: Long get() = synchronized(pcm) { pcm.size() / 2 }.toLong() * 1000 / SAMPLE_RATE

    /** Neue Aufnahme; das Aufgenommene einer vorigen wird verworfen. */
    fun start(): Boolean {
        if (recording) return true
        synchronized(pcm) { pcm.reset() }
        paused = false
        return capture()
    }

    /**
     * Mikrofon freigeben, Aufgenommenes behalten. Wartet kurz auf den Aufnahme-Thread (ein
     * Lesevorgang, um 80 ms) — danach ist das Mikrofon sicher frei, und es kommt nichts mehr hinzu.
     */
    fun pause() {
        if (!recording) return
        recording = false
        paused = true
        awaitThread(1_000)
    }

    /** Aus der Pause weiter aufnehmen; haengt an das Bisherige an. false = Mikrofon nicht verfuegbar, Pause bleibt. */
    fun resume(): Boolean {
        if (!paused) return recording
        if (!capture()) return false
        paused = false
        return true
    }

    @SuppressLint("MissingPermission") // Aufrufer stellt Berechtigung sicher (Einrichtungs-Assistent).
    private fun capture(): Boolean {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            Log.e(TAG, "getMinBufferSize fehlgeschlagen: $minBuf")
            return false
        }
        val bufferSize = minBuf * 2
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, CHANNEL, ENCODING, bufferSize,
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Keine RECORD_AUDIO-Berechtigung", e)
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord nicht initialisiert")
            recorder.release()
            return false
        }

        recording = true
        recorder.startRecording()

        // Erst zuweisen, dann starten: der Thread prueft sich gegen [thread].
        val reader = Thread {
            val buf = ShortArray(bufferSize / 2)
            val bytes = ByteArray(buf.size * 2)
            try {
                while (recording && thread === Thread.currentThread()) {
                    val n = recorder.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var peak = 0
                    for (i in 0 until n) {
                        val s = buf[i].toInt()
                        val a = abs(s)
                        if (a > peak) peak = a
                        // little-endian PCM16
                        bytes[i * 2] = (s and 0xFF).toByte()
                        bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    synchronized(pcm) { pcm.write(bytes, 0, n * 2) }
                    onAmplitude?.invoke(peak / 32768f)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Aufnahme-Loop-Fehler", e)
            } finally {
                try { recorder.stop() } catch (_: Exception) {}
                recorder.release()
            }
        }
        thread = reader
        reader.start()
        return true
    }

    /** Stoppt die Aufnahme — auch aus der Pause — und liefert alle gesammelten Samples als FloatArray. */
    fun stop(): FloatArray {
        if (!hasSession) return FloatArray(0)
        recording = false
        paused = false
        awaitThread(2_000)

        val raw = synchronized(pcm) { pcm.toByteArray() }
        val sampleCount = raw.size / 2
        val out = FloatArray(sampleCount)
        for (i in 0 until sampleCount) {
            val lo = raw[i * 2].toInt() and 0xFF
            val hi = raw[i * 2 + 1].toInt() // Vorzeichen erhalten
            val sample = (hi shl 8) or lo
            out[i] = sample / 32768f
        }
        return out
    }

    /** Aufnahme abbrechen — auch aus der Pause —, Samples verwerfen. */
    fun cancel() {
        recording = false
        paused = false
        awaitThread(1_000)
        synchronized(pcm) { pcm.reset() }
    }

    private fun awaitThread(millis: Long) {
        try { thread?.join(millis) } catch (_: InterruptedException) {}
        thread = null
    }
}
