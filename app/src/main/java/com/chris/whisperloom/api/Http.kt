package com.chris.whisperloom.api

import com.chris.whisperloom.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimaler HTTP-Helfer fuer die Aufrufe der App (Transkription + optionale Textveredelung,
 * OpenAI-kompatibel, Ollama oder ElevenLabs). Bewusst auf HttpURLConnection statt
 * einer HTTP-Bibliothek — die App haelt sich frei von Dritt-Abhaengigkeiten.
 */
internal object Http {

    const val CONNECT_TIMEOUT_MS = 15_000
    const val DEFAULT_READ_TIMEOUT_MS = 90_000

    /**
     * Eigener User-Agent statt des Android-Defaults "Dalvik/2.1.0 (…)": ollama.com beantwortet
     * JEDEN Request mit Dalvik-Kennung mit 403 (live gemessen 2026-09-24, auch mit gueltigem Key)
     * — in der App sah das aus wie "Key ungueltig".
     */
    val USER_AGENT = "WhisperLoom/${BuildConfig.VERSION_NAME} (Android)"

    fun endpoint(baseUrl: String, path: String): String =
        baseUrl.trim().trimEnd('/') + path

    /**
     * Fuehrt den Request aus und liefert den Antwort-Body.
     *
     * @param apiKey leer = kein Authorization-Header. Eigene Server (Ollama, whisper.cpp)
     *   brauchen keinen Key; ein leeres "Bearer " werten manche als ungueltig.
     * @param readTimeoutMs CPU-Server brauchen fuer lange Stuecke Minuten (eigener Server: 600 s).
     * @param followRedirects false laesst den Authorization-Header nicht auf einen anderen Host
     *   wandern — sinnvoll ueberall dort, wo der Endpunkt keine Weiterleitungen kennt.
     * @param authHeader Name eines eigenen Key-Headers, der den Key ohne "Bearer " traegt
     *   (ElevenLabs: xi-api-key); null = `Authorization: Bearer …`. Einen eigenen Header streift
     *   die JVM bei einer Weiterleitung nicht ab — dafuer immer [followRedirects] = false.
     * @param connectTimeoutMs kuerzer, wenn bei Fehlschlag ein Ersatz bereitsteht (lokales Textmodell).
     * @param write schreibt den Request-Body.
     * @throws ApiNetworkException wenn die Verbindung scheitert.
     * @throws ApiHttpException bei Status != 2xx.
     */
    fun post(
        url: String,
        apiKey: String,
        contentType: String,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        followRedirects: Boolean = true,
        authHeader: String? = null,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        write: (java.io.OutputStream) -> Unit,
    ): String = execute(url, "POST", apiKey, readTimeoutMs, followRedirects, authHeader, emptyMap(), connectTimeoutMs) { conn ->
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", contentType)
        conn.outputStream.use(write)
    }

    /**
     * GET ohne Body (Modell-Listen, siehe [ModelLists]). Parameter und Fehler wie [post].
     *
     * @param headers weitere Header (Anthropic: anthropic-version). Sie wandern bei einer
     *   Weiterleitung mit wie ein eigener Key-Header — dann [followRedirects] = false.
     */
    fun get(
        url: String,
        apiKey: String,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        followRedirects: Boolean = true,
        authHeader: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): String = execute(url, "GET", apiKey, readTimeoutMs, followRedirects, authHeader, headers) {}

    private fun execute(
        url: String,
        method: String,
        apiKey: String,
        readTimeoutMs: Int,
        followRedirects: Boolean,
        authHeader: String?,
        headers: Map<String, String>,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        send: (HttpURLConnection) -> Unit,
    ): String {
        val conn = try {
            (URL(url).openConnection() as? HttpURLConnection
                ?: throw IOException("Keine http(s)-URL: $url")).apply {
                requestMethod = method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = followRedirects
                setRequestProperty("User-Agent", USER_AGENT)
                if (apiKey.isNotBlank()) {
                    if (authHeader == null) setRequestProperty("Authorization", "Bearer $apiKey")
                    else setRequestProperty(authHeader, apiKey)
                }
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
        } catch (e: IOException) {
            throw ApiNetworkException(e)
        }

        try {
            send(conn)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiHttpException(code, errorDetail(body))
            return body
        } catch (e: IOException) {
            throw ApiNetworkException(e)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Zieht die lesbare Meldung aus einer Fehlerantwort, damit im UI nicht roher JSON landet:
     * OpenAI-Form {"error":{"message":…}}, Ollama-Form {"error":"…"} oder ElevenLabs-Form
     * {"detail":{"message":…}} / {"detail":[{"msg":…}]} (Validierung) / {"detail":"…"}.
     */
    internal fun errorDetail(body: String): String {
        val fallback = body.take(200)
        if (body.isBlank()) return "keine Antwort"
        return try {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: (json.opt("error") as? String)?.takeIf { it.isNotBlank() }
                ?: detailMessage(json.opt("detail"))
                ?: fallback
        } catch (e: org.json.JSONException) {
            fallback
        }
    }

    private fun detailMessage(detail: Any?): String? = when (detail) {
        is JSONObject -> detail.optString("message")
        is JSONArray -> (0 until detail.length())
            .mapNotNull { detail.optJSONObject(it)?.optString("msg")?.takeIf { msg -> msg.isNotBlank() } }
            .joinToString("; ")
        is String -> detail
        else -> null
    }?.takeIf { it.isNotBlank() }
}
