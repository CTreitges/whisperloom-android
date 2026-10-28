package com.chris.whisperloom.simple.realtime

import java.io.IOException

/**
 * Rueckfall nach Netzabbruch (Konzept §6.4): das Audio seit dem letzten festen Text per Batch nachreichen
 * (`/v1/speech-to-text`, Weg in `api/ElevenLabsStt.kt`). Etappe 1: nur Schnittstelle — der Batch-Weg braucht
 * einen Key bzw. ein Batch-Token aus dem Token-Dienst (Etappe 2).
 */
interface BatchFallback {
    /** @param pcm16 16 kHz mono PCM16 little-endian; @return erkannter Text (leer = nichts). */
    @Throws(IOException::class)
    fun transcribe(pcm16: ByteArray): String
}

object NoBatchFallback : BatchFallback {
    override fun transcribe(pcm16: ByteArray): String = throw IOException("Kein Batch-Rueckfall (Etappe 1)")
}
