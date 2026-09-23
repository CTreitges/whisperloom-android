package com.chris.whisperloom.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Native Ollama-API (lokal und ollama.com): POST /api/chat fuer die Textverbesserung,
 * GET /api/tags fuer die Modell-Liste. Payload und Parser sind rein (JVM-unit-testbar);
 * nur [listModels] spricht ueber [Http] mit dem Server.
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

    /**
     * Holt die Modelle, die der Server anbietet (lokal: installierte, Cloud: verfuegbare).
     * Blockierend — aus einem Hintergrund-Thread aufrufen.
     *
     * @throws ApiNotConfiguredException wenn keine Server-Adresse eingetragen ist.
     * @throws ApiNetworkException / [ApiHttpException] wie [Http.get].
     */
    fun listModels(access: ApiAccess): List<String> {
        if (access.baseUrl.isBlank()) throw ApiNotConfiguredException()
        return parseTags(Http.get(tagsUrl(access.baseUrl), access.apiKey, readTimeoutMs = Http.CONNECT_TIMEOUT_MS))
    }
}
