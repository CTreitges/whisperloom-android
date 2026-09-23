package com.chris.whisperloom.api

import com.chris.whisperloom.RefineMode
import org.json.JSONObject

/**
 * Optionale zweite Runde: laesst ein Sprachmodell den Rohtext bearbeiten (glaetten,
 * verschoenern, zusammenfassen, in Absaetze gliedern). Kostet eine zusaetzliche
 * Anfrage und etwas Latenz — deshalb in den Einstellungen abschaltbar.
 *
 * Spricht POST /chat/completions des [ApiAccess] bzw. bei Ollama POST /api/chat — das kann
 * ein anderer Anbieter als bei der Transkription sein (z. B. Groq-STT + Ollama-LLM).
 */
class TextRefiner(private val access: ApiAccess) {

    /**
     * Liefert den bearbeiteten Text. Bei leerer Eingabe, [RefineMode.OFF] oder leerer
     * Antwort wird der Originaltext zurueckgegeben. Fehler des Sprachmodells (HTTP, Netz)
     * werden geworfen — [com.chris.whisperloom.TranscriptionEngine] faengt sie und faellt
     * auf den Rohtext zurueck: die Veredelung darf ein Diktat niemals verschlucken.
     *
     * @throws ApiNotConfiguredException wenn die Base-URL leer ist (eigener Server ohne URL) —
     *   sonst ginge die Anfrage an "/chat/completions" ohne Host.
     */
    fun refine(
        raw: String,
        language: String,
        mode: RefineMode,
        smartFillers: Boolean,
        paragraphs: Boolean = true,
    ): String {
        if (raw.isBlank() || mode == RefineMode.OFF) return raw
        if (access.baseUrl.isBlank()) throw ApiNotConfiguredException()

        val systemPrompt = RefinePrompt.build(mode, german = language == "de", smartFillers = smartFillers, paragraphs = paragraphs)
        val text = (if (access.provider.isOllama) ollama(systemPrompt, raw) else openAi(systemPrompt, raw))
            ?.let { stripThinking(it) }
            ?.trim()

        return if (text.isNullOrBlank()) raw else text
    }

    /** OpenAI-kompatibel: POST {baseUrl}/chat/completions. */
    private fun openAi(systemPrompt: String, raw: String): String? {
        val payload = ChatPayload.build(access = access, systemPrompt = systemPrompt, userText = raw)
        val body = Http.post(
            url = Http.endpoint(access.baseUrl, "/chat/completions"),
            apiKey = access.apiKey,
            contentType = "application/json",
            readTimeoutMs = access.readTimeoutMs,
        ) { os -> os.write(payload.toByteArray(Charsets.UTF_8)) }
        return JSONObject(body)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
    }

    /** Ollama (lokal oder ollama.com): POST {Wurzel}/api/chat, siehe [OllamaApi]. */
    private fun ollama(systemPrompt: String, raw: String): String? {
        val payload = OllamaApi.chatPayload(access.model, systemPrompt, raw)
        val body = Http.post(
            url = OllamaApi.chatUrl(access.baseUrl),
            apiKey = access.apiKey,
            contentType = "application/json",
            readTimeoutMs = access.readTimeoutMs,
        ) { os -> os.write(payload.toByteArray(Charsets.UTF_8)) }
        return OllamaApi.parseChat(body)
    }

    companion object {
        // Qwen3 & Co. schreiben ihr Nachdenken als <think>…</think> in den Text, wenn
        // der Server reasoning_effort ignoriert. Das gehoert nie ins Diktat.
        private val THINK_BLOCK = Regex("(?s)^\\s*<think>.*?</think>\\s*")

        fun stripThinking(content: String): String = THINK_BLOCK.replace(content, "")
    }
}
