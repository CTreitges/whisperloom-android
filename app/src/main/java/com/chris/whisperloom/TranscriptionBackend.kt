package com.chris.whisperloom

import android.util.Log
import com.chris.whisperloom.api.ApiAccess
import com.chris.whisperloom.api.ApiTranscriber
import com.chris.whisperloom.api.WavUpload

/** Rohtext einer Erkennung plus — wenn der Erkenner sie meldet — die erkannte Sprache. */
data class TranscriptResult(
    val text: String,
    val detectedLanguage: String? = null,
)

/**
 * Abstraktion ueber die Spracherkennung, damit Engine/IME/Share nicht wissen muessen,
 * ob ueber einen Anbieter oder auf dem Geraet erkannt wird.
 */
interface TranscriptionBackend {
    /** Kurzname fuer die Anzeige ("OpenAI", "Offline"). */
    val label: String

    /**
     * @param upload 16 kHz Mono PCM16 (Diktat oder ein Stueck einer geteilten Datei).
     * @param language ISO-Code ("de", "en", ...) oder "auto".
     */
    fun transcribe(upload: WavUpload, language: String): TranscriptResult
}

/** Erkennung ueber die OpenAI-kompatible API des konfigurierten Anbieters. */
class OnlineBackend(access: ApiAccess, prompt: String = "") : TranscriptionBackend {

    private val api = ApiTranscriber(access, prompt)

    override val label: String = access.provider.name

    override fun transcribe(upload: WavUpload, language: String): TranscriptResult =
        api.transcribe(upload, language)
}

/**
 * Fallback der Erkennung (3.9.1): scheitert [primary] (Netz, Fehlerstatus, Zeitlimit, kaputte Antwort),
 * erkennt [fallback] dieselbe Aufnahme. Danach bleibt es fuer diesen Auftrag beim Fallback — die
 * weiteren Stuecke einer geteilten Sprachnachricht warten nicht jedes Mal auf den ersten Anbieter.
 * Scheitern beide, geht der Fehler des ersten raus (die Fehlertexte und der Widget-Neuversuch
 * richten sich nach ihm), der des Fallbacks haengt als unterdrueckter dran.
 */
class FallbackBackend(
    private val primary: TranscriptionBackend,
    private val fallback: TranscriptionBackend,
) : TranscriptionBackend {

    /** Der erste ist in diesem Auftrag gescheitert, der Fallback hat (mindestens einmal) erkannt. */
    @Volatile var usedFallback: Boolean = false
        private set

    @Volatile private var primaryFailed = false

    /** Womit zuletzt erkannt wurde — nach einem Ausfall der Fallback. */
    override val label: String get() = if (usedFallback) fallback.label else primary.label

    /** Name des Fallback-Anbieters fuer den Hinweis ("Groq"). */
    val fallbackLabel: String get() = fallback.label

    override fun transcribe(upload: WavUpload, language: String): TranscriptResult {
        if (primaryFailed) return fallback.transcribe(upload, language).also { usedFallback = true }
        val error = try {
            return primary.transcribe(upload, language)
        } catch (e: Exception) {
            // Abgebrochen (Thread unterbrochen): kein zweiter Upload.
            if (Thread.currentThread().isInterrupted) throw e
            e
        }
        Log.w(TAG, "Erkennung gescheitert, versuche Fallback ${fallback.label}: ${error.message}", error)
        primaryFailed = true
        return try {
            fallback.transcribe(upload, language).also { usedFallback = true }
        } catch (e: Exception) {
            error.addSuppressed(e)
            throw error
        }
    }

    private companion object {
        const val TAG = "FallbackBackend"
    }
}
