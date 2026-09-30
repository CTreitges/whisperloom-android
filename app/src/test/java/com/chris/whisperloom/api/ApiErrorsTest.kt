package com.chris.whisperloom.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * JVM-Unit-Tests fuer die Wiederholbarkeit von Fehlern und die lesbaren Meldungen. Falsch
 * eingestuft heisst: entweder haengt der Knopf dauerhaft im Fehlerzustand (bei echtem
 * Konfigurationsfehler) oder ein langes Diktat geht bei einem kurzen Netzhaenger verloren.
 */
class ApiErrorsTest {

    @Test fun netzfehlerDarfWiederholtWerden() {
        assertTrue(ApiNetworkException(IOException("timeout")).isRetryable())
    }

    @Test fun serverfehlerDarfWiederholtWerden() {
        assertTrue(ApiHttpException(500, "boom").isRetryable())
        assertTrue(ApiHttpException(503, "unavailable").isRetryable())
        assertTrue(ApiHttpException(429, "rate limit").isRetryable())
        assertTrue(ApiHttpException(408, "timeout").isRetryable())
    }

    @Test fun falscherKeyBleibtFalsch() {
        assertFalse(ApiHttpException(401, "invalid api key").isRetryable())
        assertFalse(ApiHttpException(403, "forbidden").isRetryable())
    }

    @Test fun unbekanntesModellBleibtUnbekannt() {
        assertFalse(ApiHttpException(404, "model not found").isRetryable())
        assertFalse(ApiHttpException(400, "bad request").isRetryable())
    }

    @Test fun kaputteBaseUrlIstKeinWiederholungsfall() {
        // Review API-4: URL("/chat/completions") -> MalformedURLException; Retry waere sinnlos.
        val e = ApiNetworkException(MalformedURLException("no protocol: /chat/completions"))
        assertFalse(e.isRetryable())
        assertTrue(e.message!!.contains("Base-URL"))
        assertFalse(e.message!!.contains("no protocol"))
    }

    @Test fun fehlenderKeyIstKeinWiederholungsfall() {
        assertFalse(ApiNotConfiguredException().isRetryable())
    }

    @Test fun fehlendesOfflineModellIstKeinWiederholungsfall() {
        assertFalse(com.chris.whisperloom.whisper.OfflineNotAvailableException().isRetryable())
    }

    // --- lesbare Meldungen fuer eigene Server -----------------------------------

    @Test fun netzfehlerWerdenUebersetzt() {
        assertTrue(ApiNetworkException.describe(UnknownHostException("x")).contains("nicht gefunden"))
        assertTrue(ApiNetworkException.describe(ConnectException("refused")).contains("nicht erreichbar"))
        assertTrue(ApiNetworkException.describe(SocketTimeoutException("Read timed out")).contains("Zeitüberschreitung"))
        // Review TST-1: es gibt keine Timeout-Einstellung in der Oberflaeche — nicht dorthin schicken.
        assertFalse(ApiNetworkException.describe(SocketTimeoutException("Read timed out")).contains("Einstellungen"))
        assertTrue(ApiNetworkException.describe(SocketTimeoutException("connect timed out")).contains("nicht erreichbar"))
        assertTrue(ApiNetworkException.describe(SSLHandshakeException("bad cert")).contains("TLS"))
        assertTrue(
            ApiNetworkException.describe(IOException("Cleartext HTTP traffic to 1.2.3.4 not permitted"))
                .contains("http://"),
        )
        assertEquals("irgendwas", ApiNetworkException.describe(IOException("irgendwas")))
        assertEquals("Netzwerkfehler", ApiNetworkException.describe(IOException()))
    }

    @Test fun meldungDerExceptionIstDieUebersetzung() {
        val e = ApiNetworkException(UnknownHostException("api.example"))
        assertTrue(e.message!!.contains("nicht gefunden"))
        assertTrue(e.cause is UnknownHostException)
    }

    @Test fun statusHinweiseFuerEigeneServer() {
        assertTrue(ApiHttpException(401, "x").message!!.contains("API-Key"))
        assertTrue(ApiHttpException(403, "x").message!!.contains("API-Key"))
        assertTrue(ApiHttpException(404, "x").message!!.contains("/v1"))
        assertTrue(ApiHttpException(404, "not here").message!!.contains("/v1"))
        // Review API-6: OpenAI antwortet auf falsche Modell-IDs mit 404 — dann liegt es nicht an der Base-URL.
        val model404 = ApiHttpException(404, "The model `gpt-xyz` does not exist").message!!
        assertTrue(model404.contains("Modell-ID"))
        assertFalse(model404.contains("/v1"))
        assertTrue(ApiHttpException(400, "failed to read audio data").message!!.contains("WAV"))
        assertNull(ApiHttpException.hint(400, "bad request"))
        assertNull(ApiHttpException.hint(500, "boom"))
        assertEquals("API-Fehler 500: boom", ApiHttpException(500, "boom").message)
        assertEquals(401, ApiHttpException(401, "x").code)
    }

    @Test fun guthabenLeerBekommtEinenHinweis() {
        assertTrue(ApiHttpException(402, "insufficient credits").message!!.contains("Guthaben"))
        assertFalse(ApiHttpException(402, "x").isRetryable())
    }

    @Test fun elevenLabsHinweiseNennenDieBerechtigungStattV1() {
        val el = ApiStyle.ELEVENLABS
        assertTrue(ApiHttpException(401, "Invalid API key", el).message!!.contains("„Speech to Text“"))
        assertTrue(ApiHttpException(403, "missing permission", el).message!!.contains("Berechtigung"))
        assertTrue(ApiHttpException(402, "x", el).message!!.contains("Guthaben"))
        // Der /v1-Hinweis gilt fuer eigene OpenAI-kompatible Server, nicht fuer ElevenLabs.
        assertNull(ApiHttpException.hint(404, "Not Found", el))
        assertEquals("API-Fehler 404: Not Found", ApiHttpException(404, "Not Found", el).message)
        // Ohne Stil bleibt alles wie bisher.
        assertTrue(ApiHttpException(401, "x").message!!.contains("API-Key"))
        assertTrue(ApiHttpException(404, "x").message!!.contains("/v1"))
    }
}
