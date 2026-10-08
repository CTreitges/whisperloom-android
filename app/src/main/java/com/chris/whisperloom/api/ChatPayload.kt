package com.chris.whisperloom.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Request-Body fuer POST /chat/completions. Die Sampling-Entscheidung ist rein
 * (JVM-unit-testbar): Reasoning-Modelle (OpenAI gpt-5.x) lehnen `temperature` ab und
 * wollen `reasoning_effort` + `max_completion_tokens`; Ollama/Qwen3 denken ohne
 * `reasoning_effort: "none"` erst minutenlang nach. Claude 5 und DeepSeek schalten das
 * Nachdenken nur ueber `thinking` ab ([ModelOption.thinkingType]).
 */
object ChatPayload {

    /**
     * Deckel gegen Endlos-Ausgaben bei Reasoning-Modellen. Die Denk-Token zaehlen mit: 4096 reichten
     * Claude Haiku 5.5 fuer 7.700 Zeichen nicht (0 Zeichen Text, gemessen 2026-10-08), deutscher
     * Text braucht etwa 1 Token je 2,1 Zeichen. Abgerechnet wird nur, was verbraucht wird.
     */
    const val MAX_COMPLETION_TOKENS = 16384

    data class Sampling(
        val temperature: Int? = null,
        val reasoningEffort: String? = null,
        val maxCompletionTokens: Int? = null,
        val thinkingType: String? = null,
    )

    fun sampling(access: ApiAccess): Sampling {
        val option = access.modelOption
        // Unbekanntes Modell (frei eingetippt): klassisches Verhalten mit temperature 0.
        val temperatureOk = option?.temperatureSupported ?: true
        return Sampling(
            temperature = if (temperatureOk) 0 else null,
            reasoningEffort = if (access.provider.isCustom) "none" else option?.reasoningEffort,
            maxCompletionTokens = if (temperatureOk) null else MAX_COMPLETION_TOKENS,
            thinkingType = option?.thinkingType,
        )
    }

    fun build(access: ApiAccess, systemPrompt: String, userText: String): String {
        val s = sampling(access)
        return JSONObject().apply {
            put("model", access.model)
            s.temperature?.let { put("temperature", it) }
            s.reasoningEffort?.let { put("reasoning_effort", it) }
            s.maxCompletionTokens?.let { put("max_completion_tokens", it) }
            s.thinkingType?.let { put("thinking", JSONObject().put("type", it)) }
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userText)),
            )
        }.toString()
    }
}
