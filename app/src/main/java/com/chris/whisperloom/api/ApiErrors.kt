package com.chris.whisperloom.api

import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Zugang unvollstaendig (Base-URL leer oder Key fehlt, obwohl der Anbieter einen braucht). */
class ApiNotConfiguredException :
    RuntimeException("Anbieter nicht eingerichtet — Base-URL und API-Key in den Einstellungen prüfen")

/**
 * Das Sprachmodell hat geantwortet, aber offensichtlich nicht das Verlangte getan — etwa die
 * diktierte Bitte erfuellt, statt sie umzuformulieren. Der Aufrufer faellt auf den Rohtext zurueck.
 */
class RefineRejectedException(message: String) : RuntimeException(message)

/**
 * Der Server hat mit einem Fehlerstatus geantwortet. Bei den typischen Stolperfallen
 * eines eigenen Servers haengt ein Hinweis an der Meldung.
 */
class ApiHttpException(val code: Int, val detail: String) :
    RuntimeException(message(code, detail)) {

    companion object {
        fun hint(code: Int, detail: String): String? = when {
            code == 401 || code == 403 -> "Server verlangt einen (anderen) API-Key"
            // OpenAI & Co. antworten auf unbekannte Modell-IDs ebenfalls mit 404 — dann ist die
            // Base-URL (Preset, nicht editierbar) nicht das Problem.
            code == 404 && detail.contains("model", ignoreCase = true) -> "Modell-ID prüfen"
            code == 404 -> "Endpunkt nicht gefunden — Base-URL muss auf /v1 enden"
            code == 400 && detail.contains("failed to read audio data", ignoreCase = true) ->
                "Server konnte das WAV nicht lesen"
            else -> null
        }

        private fun message(code: Int, detail: String): String {
            val base = "API-Fehler $code: $detail"
            return hint(code, detail)?.let { "$base — $it" } ?: base
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
