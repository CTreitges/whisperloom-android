package com.chris.whisperloom.whisper

import android.content.Context
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.TranscriptResult
import com.chris.whisperloom.TranscriptionBackend
import com.chris.whisperloom.api.WavUpload

/**
 * Offline gewaehlt, aber nicht einsatzbereit: kein vollstaendiges Modell, CPU ohne FP16/DotProd
 * oder die native Bibliothek fehlt. Nicht retryable — IME/Overlay zeigen [message].
 */
class OfflineNotAvailableException(message: String = MSG_NO_MODEL) : RuntimeException(message) {
    companion object {
        const val MSG_NO_MODEL = "Kein Offline-Modell geladen — unter Spracherkennung laden"
        const val MSG_UNSUPPORTED = "Offline-Erkennung wird von diesem Gerät nicht unterstützt (CPU ohne FP16/DotProd)"
        const val MSG_LOAD_FAILED = "Offline-Modell konnte nicht geladen werden — unter Spracherkennung löschen und neu laden"
    }
}

/** whisper_full hat einen Fehler gemeldet (kein Abbruch). */
class OfflineTranscriptionException(rc: Int) : RuntimeException("Offline-Erkennung fehlgeschlagen (whisper_full=$rc)")

object OfflineStatus {
    /** Liegt das in den Einstellungen gewaehlte Modell vollstaendig in filesDir/models? */
    fun isModelAvailable(context: Context): Boolean {
        val app = context.applicationContext
        WhisperEngine.init(app) // idempotent — Sicherheitsnetz, falls WhisperLoomApplication nicht gelaufen ist
        return ModelStore(app).isInstalled(Prefs(app).offlineModel)
    }
}

/**
 * Erkennung auf dem Geraet mit whisper.cpp ueber die prozessweite [WhisperEngine].
 * @param accurate Beam-Search 5 (genauer, langsamer) statt Greedy
 * @param initialPrompt Kontext aus den Einstellungen (Eigennamen, Fachbegriffe) — wie `prompt` bei der API
 */
class OfflineBackend(
    private val modelId: String,
    private val accurate: Boolean,
    private val initialPrompt: String,
) : TranscriptionBackend {

    override val label: String = "Offline · " + (ModelCatalog.find(modelId)?.label ?: modelId)

    /** Bei "auto" liefert whisper die erkannte Sprache mit ([TranscriptResult.detectedLanguage]). */
    override fun transcribe(upload: WavUpload, language: String): TranscriptResult =
        WhisperEngine.transcribe(modelId, upload.readSamples(), language, initialPrompt, accurate)
}
