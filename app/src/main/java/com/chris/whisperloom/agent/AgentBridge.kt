package com.chris.whisperloom.agent

import com.chris.whisperloom.api.ApiHttpException
import com.chris.whisperloom.api.ApiNotConfiguredException
import com.chris.whisperloom.api.Http
import com.chris.whisperloom.api.ServerUrlCheck
import org.json.JSONObject

/** Der HTTP-Aufruf als eigene Naht: Tests kommen so ohne Netz aus. */
fun interface BridgePoster {
    /**
     * @return Antwort-Body.
     * @throws com.chris.whisperloom.api.ApiNetworkException wenn die Verbindung scheitert.
     * @throws ApiHttpException bei Status != 2xx.
     */
    fun post(url: String, token: String, body: String): String
}

/**
 * Die Gegenstelle des Sprachauftrags: ein eigener kleiner Dienst (hermes-bridge), der das
 * Transkript entgegennimmt und an den Agenten weiterreicht. Genau ein Endpunkt, ein
 * Bearer-Token, Antwort 202 — die App wartet nicht auf das Ergebnis, das kommt spaeter
 * ueber den Messenger.
 *
 * Der Prompt-Rahmen ("Sprachauftrag von …, per WhisperLoom transkribiert") sitzt bewusst
 * auf der Server-Seite: er gehoert zum Agenten, nicht zum Telefon, und laesst sich dort
 * aendern, ohne eine neue App-Version auszuliefern.
 */
class AgentBridge(
    baseUrl: String,
    private val token: String,
    private val poster: BridgePoster = DEFAULT_POSTER,
) {

    private val base = baseUrl.trim()

    /** Ohne Adresse oder Token kann gar nichts gesendet werden — das faengt die UI vorher ab. */
    val configured: Boolean get() = base.isNotEmpty() && token.isNotBlank()

    /**
     * Schickt einen Auftrag. [requestId] bleibt ueber alle Wiederholungen gleich; die Bridge
     * fuehrt jede Id nur einmal aus.
     *
     * @throws ApiNotConfiguredException wenn Adresse oder Token fehlen.
     */
    fun send(requestId: String, transcript: String, recordedAt: String, durationMs: Long) {
        if (!configured) throw ApiNotConfiguredException()
        poster.post(endpoint(), token, body(requestId, transcript, recordedAt, durationMs))
    }

    /**
     * "Verbindung pruefen", ohne einen echten Auftrag auszuloesen: ein leeres Transkript
     * beantwortet die Bridge mit 400 — und zwar ERST, nachdem sie das Token geprueft hat.
     * Also heisst 400 "erreichbar und Token stimmt", 401 "Token falsch", alles andere ist
     * ein echtes Problem. Weder Idempotenz-Speicher noch Rate-Limit werden dabei beruehrt.
     */
    fun check() {
        if (!configured) throw ApiNotConfiguredException()
        try {
            poster.post(endpoint(), token, body("check", "", "", 0))
        } catch (e: ApiHttpException) {
            if (e.code != 400) throw e
        }
    }

    private fun endpoint(): String = Http.endpoint(base, PATH)

    private fun body(requestId: String, transcript: String, recordedAt: String, durationMs: Long): String =
        JSONObject()
            .put("transcript", transcript)
            .put("request_id", requestId)
            .put("recorded_at", recordedAt)
            .put("duration_ms", durationMs)
            .put("source", SOURCE)
            .toString()

    companion object {
        const val PATH = "/v1/task"
        const val SOURCE = "widget"
        const val CONTENT_TYPE = "application/json; charset=utf-8"

        /** Die Bridge antwortet sofort mit 202; sie wartet nicht auf den Agenten. */
        const val READ_TIMEOUT_MS = 30_000

        private val DEFAULT_POSTER = BridgePoster { url, token, body ->
            Http.post(url, token, CONTENT_TYPE, READ_TIMEOUT_MS) { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
    }
}

/**
 * Pruefung der Bridge-Adresse. Dieselben Regeln wie fuer den Erkennungs-Server
 * ([ServerUrlCheck]) — nur ohne den /v1-Hinweis: die Bridge haengt den Pfad selbst an.
 */
object AgentUrlCheck {

    fun check(url: String): ServerUrlCheck.Problem? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ServerUrlCheck.Problem(ServerUrlCheck.Severity.ERROR, ServerUrlCheck.MSG_INVALID)
        return ServerUrlCheck.check(trimmed, PROVIDER)
    }

    fun isValid(url: String): Boolean = check(url)?.severity != ServerUrlCheck.Severity.ERROR

    /** Eigener Server, nicht aus dem Katalog: http:// erlaubt (LAN/VPN), kein /v1-Zwang. */
    private val PROVIDER = com.chris.whisperloom.api.Provider(
        id = "hermes-bridge",
        name = "Bridge",
        baseUrl = "",
        needsKey = true,
        allowsHttp = true,
    )
}
