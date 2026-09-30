package com.chris.whisperloom.whisper

import com.chris.whisperloom.AudioUtils
import com.chris.whisperloom.TranscriptResult
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * Huelle um EINEN nativen whisper_context (aus einer Modell-Datei). whisper.cpp ist nur
 * thread-sicher, solange derselbe Kontext nicht von mehreren Threads gleichzeitig benutzt wird —
 * deshalb laufen Laden, Erkennen und Freigeben ausschliesslich auf dem eigenen
 * Single-Thread-Executor. [abort] darf dagegen von jedem Thread kommen (atomares Flag in der JNI).
 */
internal class WhisperContext private constructor() {

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "whisper-worker") }

    @Volatile private var ptr = 0L
    @Volatile private var abortRequested = false

    private fun init(file: File, flashAttn: Boolean) {
        ptr = onWorker { WhisperLib.initContext(file.absolutePath, flashAttn) }
        if (ptr == 0L) {
            worker.shutdown()
            throw OfflineNotAvailableException(OfflineNotAvailableException.MSG_LOAD_FAILED)
        }
    }

    /**
     * Blockiert den Aufrufer, bis whisper fertig ist.
     * @param language ISO-Code oder "auto" (dann ist [TranscriptResult.detectedLanguage] gesetzt)
     * @param beamSize > 1 = Beam-Search, sonst Greedy
     * @throws OfflineTranscriptionException wenn whisper_full scheitert (ohne Abbruch)
     */
    fun transcribe(samples: FloatArray, language: String, initialPrompt: String, beamSize: Int, threads: Int): TranscriptResult {
        if (samples.isEmpty()) return TranscriptResult("")
        val audio = padToMinimum(samples)
        return onWorker {
            val ctx = ptr
            check(ctx != 0L) { "WhisperContext bereits freigegeben" }
            // Erst hier zuruecksetzen, nicht beim Einreihen: ein [abort] gilt nur dem gerade laufenden
            // Job. Sonst verschluckt der Abbruch der Share-Ansicht ein gleichzeitig eingereihtes Diktat
            // (liefert still ""), oder ein eingereihter Job hebt den Abbruch des laufenden wieder auf.
            abortRequested = false
            val rc = WhisperLib.fullTranscribe(
                ctx, threads, language, initialPrompt.ifBlank { null }, beamSize, true, useTimestamps(audio.size), audio,
            )
            if (rc != 0) {
                if (abortRequested) return@onWorker TranscriptResult("")
                throw OfflineTranscriptionException(rc)
            }
            val count = WhisperLib.getTextSegmentCount(ctx)
            // Segmente beginnen mit einem Leerzeichen — am Ende einmal trimmen.
            val text = buildString { for (i in 0 until count) append(WhisperLib.getTextSegment(ctx, i)) }.trim()
            val detected = if (language == "auto") WhisperLib.getDetectedLanguage(ctx).takeIf { it.isNotBlank() } else null
            TranscriptResult(text, detected)
        }
    }

    /** Die GERADE LAUFENDE Erkennung abbrechen: whisper_full liefert dann einen Fehlercode, [transcribe] "". Eingereihte Jobs laufen danach normal. */
    fun abort() {
        abortRequested = true
        WhisperLib.requestAbort()
    }

    /**
     * Gibt den nativen Kontext frei — nach einer eventuell noch laufenden Erkennung.
     * @param wait auf die Freigabe warten (Modellwechsel: erst frei, dann neu laden)
     */
    fun release(wait: Boolean) {
        val done = worker.submit {
            if (ptr != 0L) {
                WhisperLib.freeContext(ptr)
                ptr = 0L
            }
        }
        worker.shutdown()
        if (wait) runCatching { done.get() }
    }

    /** Fuehrt [block] auf dem Worker aus und reicht Exceptions unverpackt weiter. */
    private fun <T> onWorker(block: () -> T): T = try {
        worker.submit(Callable { block() }).get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }

    companion object {
        /** whisper warnt unter 100 ms und rechnet ohnehin ein 30-s-Fenster: auffuellen auf 1 s. */
        private const val MIN_SAMPLES = AudioUtils.SAMPLE_RATE

        internal fun padToMinimum(samples: FloatArray): FloatArray =
            if (samples.size >= MIN_SAMPLES) samples else samples.copyOf(MIN_SAMPLES)

        /** whisper.cpp erkennt in Fenstern von 30 s (WHISPER_CHUNK_SIZE). */
        internal const val WINDOW_SAMPLES = 30 * AudioUtils.SAMPLE_RATE

        /**
         * Zeitstempel-Tokens nur bei Audio ueber einem Fenster. Ohne sie (no_timestamps) rueckt
         * whisper.cpp stur um 30 s weiter und schneidet alle 30 s mitten im Wort (v1.9.3
         * src/whisper.cpp Z.7418-7420); mit ihnen beginnt das naechste Fenster am letzten
         * Zeitstempel (Z.7377-7390, Z.7558). Bis 30 s gibt es nur ein Fenster: dort bleibt es bei
         * no_timestamps (weniger Tokens, schneller). Der Segmenttext enthaelt die Zeitstempel nicht.
         */
        internal fun useTimestamps(sampleCount: Int): Boolean = sampleCount > WINDOW_SAMPLES

        /** Laedt das Modell (blockierend, Sekunden). @throws OfflineNotAvailableException wenn whisper es ablehnt */
        fun load(file: File, flashAttn: Boolean = true): WhisperContext =
            WhisperContext().apply { init(file, flashAttn) }
    }
}
