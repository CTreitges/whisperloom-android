package com.chris.whisperloom.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Native Ollama-API (lokal und ollama.com): POST /api/chat fuer die Textverbesserung,
 * GET /api/tags fuer die Modell-Liste. Alles hier ist rein (JVM-unit-testbar); die Anfragen
 * schicken [TextRefiner] (Chat) und [ModelLists.load] (Liste).
 *
 * Warum nicht /v1/chat/completions? Ollama Cloud dokumentiert nur /api, und ueber die native
 * Schnittstelle trennt Ollama das Nachdenken zuverlaessig in `message.thinking` ab.
 */
object OllamaApi {

    /**
     * Server-Wurzel aus einer eingetippten Adresse: Endungen /api und /v1 (so stehen sie in
     * vielen Anleitungen) werden abgeschnitten, damit "http://server:11434/v1" auch funktioniert.
     */
    fun root(baseUrl: String): String {
        var url = baseUrl.trim().trimEnd('/')
        while (true) {
            val stripped = url.removeSuffix("/api").removeSuffix("/v1").trimEnd('/')
            if (stripped == url) return url
            url = stripped
        }
    }

    fun chatUrl(baseUrl: String): String = root(baseUrl) + "/api/chat"

    fun tagsUrl(baseUrl: String): String = root(baseUrl) + "/api/tags"

    /**
     * Request-Body fuer /api/chat. `think` wird bewusst NICHT auf false gesetzt: live gemessen
     * (2026-09-24, glm-5.3-flash) schreibt ein Modell dann sein Nachdenken als Klartext in die
     * Antwort. Ohne Parameter landet es in `message.thinking` und bleibt aus dem Diktat.
     * Einzige Ausnahme gpt-oss: es kennt nur Stufen, "low" haelt die Wartezeit klein.
     */
    fun chatPayload(model: String, systemPrompt: String, userText: String): String =
        JSONObject().apply {
            put("model", model)
            put("stream", false)
            if (model.trim().lowercase().startsWith("gpt-oss")) put("think", "low")
            put("options", JSONObject().put("temperature", 0))
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userText)),
            )
        }.toString()

    /** Antworttext aus /api/chat — null, wenn keiner da ist (der Aufrufer faellt auf den Rohtext zurueck). */
    fun parseChat(body: String): String? =
        JSONObject(body).optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }

    /** Modellnamen aus /api/tags, alphabetisch und ohne Duplikate. */
    fun parseTags(body: String): List<String> {
        val models = JSONObject(body).optJSONArray("models") ?: return emptyList()
        return (0 until models.length())
            .mapNotNull { i ->
                val m = models.optJSONObject(i) ?: return@mapNotNull null
                m.optString("name").ifBlank { m.optString("model") }.takeIf { it.isNotBlank() }
            }
            .distinct()
            .sortedBy { it.lowercase() }
    }
}
