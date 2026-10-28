package com.chris.whisperloom.simple.realtime

import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64

/**
 * ElevenLabs `scribe_v2_realtime` (Konzept §7.1; Stichprobe am Original `stichprobe.md`, Live-Test
 * `live-test-realtime.md`): WebSocket `/v1/speech-to-text/realtime`, Anmeldung per Einmal-Token im Query-Parameter
 * `token`, Audio als `input_audio_chunk` (Base64-PCM16, `sample_rate`, `commit`), Antworten `partial_transcript`
 * und `committed_transcript`, Fehler mit `message_type` wie `quota_exceeded`.
 */
object ElevenLabsRealtime : RealtimeAdapter {

    const val DEFAULT_WS_BASE = "wss://api.elevenlabs.io"
    const val PATH = "/v1/speech-to-text/realtime"
    const val MODEL = "scribe_v2_realtime"
    const val AUDIO_FORMAT = "pcm_16000"

    /** Fehler-Nachrichtentypen laut API-Referenz (Recherche A). */
    val ERROR_TYPES: Set<String> = setOf(
        "error", "auth_error", "quota_exceeded", "commit_throttled", "unaccepted_terms", "rate_limited",
        "queue_overflow", "resource_exhausted", "session_time_limit_exceeded", "input_error", "invalid_request",
        "chunk_size_exceeded", "insufficient_audio_activity", "transcriber_error",
    )

    override fun url(wsBase: String, token: String, options: RealtimeOptions): String {
        val q = mutableListOf(
            "model_id" to MODEL,
            "token" to token,
            "audio_format" to AUDIO_FORMAT,
            "language_code" to options.language,
        )
        val vad = options.vadSilenceSeconds
        if (vad == null) {
            q += "commit_strategy" to "manual"
        } else {
            q += "commit_strategy" to "vad"
            q += "vad_silence_threshold_secs" to vad.toString()
        }
        for (term in options.keyterms) q += "keyterms" to term
        if (options.noVerbatim) q += "no_verbatim" to "true"
        return wsBase.trimEnd('/') + PATH + "?" + q.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
    }

    override fun audioMessage(pcm: ByteArray, sampleRate: Int, commit: Boolean): String =
        JSONObject()
            .put("message_type", "input_audio_chunk")
            .put("audio_base_64", Base64.getEncoder().encodeToString(pcm))
            .put("commit", commit)
            .put("sample_rate", sampleRate)
            .toString()

    override fun parse(text: String): RealtimeEvent {
        val json = try {
            JSONObject(text)
        } catch (_: JSONException) {
            return RealtimeEvent.Ignored
        }
        return when (val type = json.optString("message_type")) {
            "session_started" -> RealtimeEvent.Started
            "partial_transcript" -> RealtimeEvent.Partial(json.optString("text"))
            "committed_transcript" -> RealtimeEvent.Committed(json.optString("text"))
            in ERROR_TYPES -> RealtimeEvent.Error(type, json.optString("error").takeIf { it.isNotEmpty() })
            else -> RealtimeEvent.Ignored
        }
    }
}
