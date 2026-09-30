package com.chris.whisperloom.api

import com.chris.whisperloom.TranscriptResult
import com.chris.whisperloom.WavEncoder
import org.json.JSONObject
import java.io.OutputStream

/**
 * Transkribiert per OpenAI-kompatibler HTTP-API (POST /audio/transcriptions,
 * multipart/form-data). Funktioniert mit OpenAI, Groq, Mistral & Co. und selbst
 * gehosteten Whisper-Servern — Endpunkt, Felder und Key kommen aus dem [ApiAccess].
 *
 * Hochgeladen wird immer WAV: die dokumentierten Formate der API sind mp3, mp4, mpeg,
 * mpga, m4a, wav und webm — ogg/opus (WhatsApp-Sprachnachrichten) ist NICHT dabei.
 * Deshalb wird geteiltes Audio vorher lokal dekodiert, statt es durchzureichen.
 *
 * ElevenLabs ([ApiStyle.ELEVENLABS]) spricht ein eigenes Protokoll: Felder aus [ElevenLabsStt],
 * Key als xi-api-key, rohes PCM statt WAV.
 *
 * Das aufgenommene Audio wird an den Anbieter gesendet.
 */
class ApiTranscriber(
    private val access: ApiAccess,
    /**
     * Optionaler Kontext fuer die Erkennung (Eigennamen, Fachbegriffe, Stil der
     * Zeichensetzung). Die API nimmt ihn als `prompt` entgegen; er kostet nichts
     * extra und verbessert vor allem Namen und Schreibweisen spuerbar. ElevenLabs bekommt
     * die Begriffe als `keyterms` (dort mit rund 20 % Aufpreis).
     */
    private val prompt: String = "",
) {

    /**
     * @throws ApiNotConfiguredException wenn der Anbieter einen Key braucht und keiner da ist.
     */
    fun transcribe(upload: WavUpload, language: String): TranscriptResult {
        if (access.provider.needsKey && access.apiKey.isBlank()) throw ApiNotConfiguredException()
        return if (access.provider.api == ApiStyle.ELEVENLABS) elevenLabs(upload, language) else openAi(upload, language)
    }

    private fun openAi(upload: WavUpload, language: String): TranscriptResult {
        val body = multipart(
            url = TranscriptionRequest.url(access),
            fields = TranscriptionRequest.fields(access, language, prompt),
            fileName = "audio.wav",
            fileType = "audio/wav",
        ) { os ->
            os.write(WavEncoder.header(upload.pcmByteCount, upload.sampleRate))
            upload.writePcm(os)
        }

        val json = JSONObject(body)
        return TranscriptResult(
            text = json.optString("text", "").trim(),
            // Nur verbose_json/eigene Server liefern die erkannte Sprache; sonst null.
            detectedLanguage = json.optString("language", "").takeIf { it.isNotBlank() },
        )
    }

    private fun elevenLabs(upload: WavUpload, language: String): TranscriptResult {
        val body = try {
            multipart(
                url = ElevenLabsStt.url(access),
                fields = ElevenLabsStt.fields(access, language, prompt),
                fileName = ElevenLabsStt.FILE_NAME,
                fileType = ElevenLabsStt.FILE_TYPE,
                authHeader = ElevenLabsStt.AUTH_HEADER,
                // Den eigenen Key-Header streift eine Weiterleitung nicht ab (siehe Http.post).
                followRedirects = false,
                writeFile = upload.writePcm,
            )
        } catch (e: ApiHttpException) {
            // Gleicher Fehler, aber mit den Hinweisen fuer ElevenLabs (Berechtigung statt /v1).
            throw ApiHttpException(e.code, e.detail, ApiStyle.ELEVENLABS)
        }

        val json = JSONObject(body)
        return TranscriptResult(
            text = json.optString("text", "").trim(),
            detectedLanguage = json.optString("language_code", "").takeIf { it.isNotBlank() }
                ?.let { ElevenLabsStt.isoLanguage(it) },
        )
    }

    /** multipart/form-data: erst die Textfelder, zuletzt die Datei. */
    private fun multipart(
        url: String,
        fields: List<Pair<String, String>>,
        fileName: String,
        fileType: String,
        authHeader: String? = null,
        followRedirects: Boolean = true,
        writeFile: (OutputStream) -> Unit,
    ): String {
        val boundary = "----whisperloom${System.nanoTime()}"
        return Http.post(
            url = url,
            apiKey = access.apiKey,
            contentType = "multipart/form-data; boundary=$boundary",
            readTimeoutMs = access.readTimeoutMs,
            followRedirects = followRedirects,
            authHeader = authHeader,
        ) { os ->
            for ((name, value) in fields) {
                os.write("--$boundary\r\n".toByteArray())
                os.write(
                    "Content-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(),
                )
            }

            os.write("--$boundary\r\n".toByteArray())
            os.write(
                "Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"\r\n".toByteArray(),
            )
            os.write("Content-Type: $fileType\r\n\r\n".toByteArray())
            writeFile(os)
            os.write("\r\n--$boundary--\r\n".toByteArray())
        }
    }
}
