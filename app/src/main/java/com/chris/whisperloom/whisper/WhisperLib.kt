package com.chris.whisperloom.whisper

import android.util.Log

/**
 * Direkte JNI-Bindings zu libwhisperloom.so. Die Methodennamen MUESSEN exakt zu den
 * `Java_com_chris_whisperloom_whisper_WhisperLib_*`-Symbolen in src/main/cpp/whisper_jni.cpp
 * passen — JniSymbolsTest und tools/check_jni_symbols.py pruefen das, weil lokal kein NDK laeuft.
 * R8: proguard-rules.pro haelt die Klasse samt Namen (die JNI-Symbole haengen daran).
 *
 * Nicht direkt benutzen — [WhisperContext] kapselt Thread-Sicherheit und Lebenszyklus.
 */
internal object WhisperLib {

    /**
     * false, wenn die CPU die gebaute ISA nicht kann ([OfflineSupport.isSupported]) oder die .so
     * fehlt (Build ohne NDK, JVM-Tests). Dann liefert die Engine [OfflineNotAvailableException].
     */
    val available: Boolean = OfflineSupport.isSupported && loadLibrary()

    private fun loadLibrary(): Boolean = try {
        System.loadLibrary("whisperloom")
        true
    } catch (e: UnsatisfiedLinkError) {
        Log.w("WhisperLib", "libwhisperloom.so nicht ladbar: ${e.message}")
        false
    }

    /** @return Zeiger auf whisper_context oder 0 bei Fehler. */
    external fun initContext(modelPath: String, flashAttn: Boolean): Long
    external fun freeContext(contextPtr: Long)

    /**
     * @param beamSize > 1 = Beam-Search mit dieser Breite, sonst Greedy
     * @param timestamps Zeitstempel-Tokens zulassen (no_timestamps=false) — siehe [WhisperContext.useTimestamps]
     * @return 0 bei Erfolg, sonst whisper_full-Fehlercode (auch nach [requestAbort])
     */
    external fun fullTranscribe(
        contextPtr: Long,
        numThreads: Int,
        language: String,
        initialPrompt: String?,
        beamSize: Int,
        suppressNst: Boolean,
        timestamps: Boolean,
        audioData: FloatArray,
    ): Int

    /** Bricht die laufende Erkennung ab; darf von jedem Thread kommen. */
    external fun requestAbort()
    external fun getTextSegmentCount(contextPtr: Long): Int
    external fun getTextSegment(contextPtr: Long, index: Int): String

    /** Erkannte Sprache des letzten Laufs ("de"); leer, wenn unbekannt. */
    external fun getDetectedLanguage(contextPtr: Long): String
    external fun getSystemInfo(): String

    /** Encoder-/Decoder-Zeiten nach logcat — fuer Messungen auf dem Geraet. */
    external fun printTimings(contextPtr: Long)
}
