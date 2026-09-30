package com.chris.whisperloom.api

import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Zugang unvollstaendig (Base-URL leer oder Key fehlt, obwohl der Anbieter einen braucht) —
 * oder der Anbieter kann die verlangte Aufgabe gar nicht (siehe [TextRefiner.MSG_NO_LLM]).
 */
class ApiNotConfiguredException(
    message: String = "Anbieter nicht eingerichtet — Base-URL und API-Key in den Einstellungen prüfen",
) : RuntimeException(message)

/**
 * Das Sprachmodell hat geantwortet, aber offensichtlich nicht das Verlangte getan — etwa die
 * diktierte Bitte erfuellt, statt sie umzuformulieren. Der Aufrufer faellt auf den Rohtext zurueck.
 */
class RefineRejectedException(message: String) : RuntimeException(message)

/**
 * Die Modell-Liste des Anbieters liess sich nicht lesen (kein JSON, keine Liste) — meist steht hinter
 * der Adresse kein passender Server. Der Aufrufer behaelt die eingebauten Empfehlungen.
 */
class ModelListException(cause: Throwable? = null) :
    RuntimeException("Antwort des Servers ist keine Modell-Liste", cause)

/**
 * Der Server hat mit einem Fehlerstatus geantwortet. Bei den typischen Stolperfallen
 * eines eigenen Servers haengt ein Hinweis an der Meldung.
 *
 * @param style Protokoll des Anbieters: ElevenLabs bekommt eigene Hinweise (Berechtigung statt /v1).
 */
class ApiHttpException(val code: Int, val detail: String, style: ApiStyle = ApiStyle.OPENAI) :
    RuntimeException(message(code, detail, style)) {

    companion object {
        fun hint(code: Int, detail: String, style: ApiStyle = ApiStyle.OPENAI): String? = when {
            // ElevenLabs-Keys sind auf Endpunkte beschraenkt; fehlt die Freigabe, kommt ebenfalls 401.
            style == ApiStyle.ELEVENLABS && (code == 401 || code == 403) ->
                "Key oder Berechtigung „Speech to Text“ prüfen"
            code == 401 || code == 403 -> "Server verlangt einen (anderen) API-Key"
            code == 402 -> "Guthaben aufgebraucht — beim Anbieter aufladen"
            // OpenAI & Co. antworten auf unbekannte Modell-IDs ebenfalls mit 404 — dann ist die
            // Base-URL (Preset, nicht editierbar) nicht das Problem.
            code == 404 && detail.contains("model", ignoreCase = true) -> "Modell-ID prüfen"
            // Der Pfad-Hinweis gilt fuer OpenAI-kompatible Server, nicht fuer ElevenLabs.
            code == 404 && style != ApiStyle.ELEVENLABS -> "Endpunkt nicht gefunden — Base-URL muss auf /v1 enden"
            code == 400 && detail.contains("failed to read audio data", ignoreCase = true) ->
                "Server konnte das WAV nicht lesen"
            else -> null
        }

        private fun message(code: Int, detail: String, style: ApiStyle): String {
            val base = "API-Fehler $code: $detail"
            return hint(code, detail, style)?.let { "$base — $it" } ?: base
        }
    }
}

/**
 * Die Anfrage kam gar nicht durch (kein Netz, Timeout, DNS …). Die Meldung ist fuer
 * Menschen — die rohe IOException-Meldung landet sonst direkt im UI.
 */
class ApiNetworkException(cause: IOException) :
    RuntimeException(describe(cause), cause) {

    /** Eine kaputte/fehlende Base-URL bleibt beim zehnten Versuch genauso kaputt. */
    val retryable: Boolean = cause !is MalformedURLException

    companion object {
        fun describe(e: IOException): String = when {
            e is MalformedURLException -> "Base-URL fehlt oder ist ungültig — in den Einstellungen prüfen"
            e is UnknownHostException -> "Server nicht gefunden — Hostname/IP prüfen"
            e is ConnectException ->
                "Server nicht erreichbar — läuft er, stimmt der Port, gleiches WLAN/VPN?"
            e is SocketTimeoutException && e.message?.contains("connect", ignoreCase = true) == true ->
                "Server nicht erreichbar — läuft er, stimmt der Port, gleiches WLAN/VPN?"
            e is SocketTimeoutException -> "Zeitüberschreitung — Server zu langsam oder Verbindung schlecht"
            e is SSLException -> "TLS-Fehler — Zertifikat des Servers ungültig"
            e.message?.contains("Cleartext HTTP traffic", ignoreCase = true) == true ->
                "Unverschlüsseltes http:// ist zu dieser Adresse nicht erlaubt — https:// oder lokale Adresse nutzen"
            else -> e.message ?: "Netzwerkfehler"
        }
    }
}

/**
 * Ob ein erneuter Versuch ueberhaupt Sinn hat. Netzprobleme und serverseitige
 * Aussetzer sind voruebergehend; ein falscher Key oder ein unbekanntes Modell
 * bleiben auch beim zehnten Versuch falsch.
 */
fun Throwable.isRetryable(): Boolean = when (this) {
    is ApiNetworkException -> retryable
    is ApiHttpException -> code == 408 || code == 429 || code >= 500
    else -> false
}
