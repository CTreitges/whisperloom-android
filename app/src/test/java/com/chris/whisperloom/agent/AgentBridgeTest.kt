package com.chris.whisperloom.agent

import com.chris.whisperloom.api.ApiHttpException
import com.chris.whisperloom.api.ApiNetworkException
import com.chris.whisperloom.api.ApiNotConfiguredException
import com.chris.whisperloom.api.ServerUrlCheck
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ConnectException

/** Robolectric wegen org.json; kein Netz — der Poster ist eine Naht. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AgentBridgeTest {

    private val aufrufe = mutableListOf<Triple<String, String, String>>()
    private val poster = BridgePoster { url, token, body ->
        aufrufe += Triple(url, token, body)
        """{"status":"accepted"}"""
    }

    private fun bridge(url: String = "https://bridge.example.de", token: String = "geheim") =
        AgentBridge(url, token, poster)

    @Test fun derAuftragGehtAnDenEinenEndpunkt() {
        bridge().send("abc", "Kauf Milch", "2026-09-21T20:00:00Z", 4200)
        assertEquals("https://bridge.example.de/v1/task", aufrufe.single().first)
        assertEquals("geheim", aufrufe.single().second)
    }

    @Test fun derSchraegstrichAmEndeStoertNicht() {
        AgentBridge("https://bridge.example.de/", "t", poster).send("a", "x", "", 0)
        assertEquals("https://bridge.example.de/v1/task", aufrufe.single().first)
    }

    @Test fun derKoerperTraegtGenauDieFelderDerBridge() {
        bridge().send("abc-123", "Kauf Milch", "2026-09-21T20:00:00Z", 4200)
        val json = JSONObject(aufrufe.single().third)
        assertEquals("Kauf Milch", json.getString("transcript"))
        assertEquals("abc-123", json.getString("request_id"))
        assertEquals("2026-09-21T20:00:00Z", json.getString("recorded_at"))
        assertEquals(4200, json.getInt("duration_ms"))
        assertEquals("widget", json.getString("source"))
    }

    @Test fun ohneAdresseOderTokenWirdNichtsGesendet() {
        assertFalse(AgentBridge("", "t", poster).configured)
        assertFalse(AgentBridge("https://x", "", poster).configured)
        assertThrows(ApiNotConfiguredException::class.java) { AgentBridge("", "t", poster).send("a", "b", "", 0) }
        assertTrue("Nichts darf rausgegangen sein", aufrufe.isEmpty())
    }

    // --- Verbindung pruefen --------------------------------------------------

    @Test fun vierhundertBedeutetVerbindungStehtUndTokenStimmt() {
        // Die Bridge prueft das Token VOR dem leeren Transkript — 400 heisst also "durchgelassen".
        val b = AgentBridge("https://bridge.example.de", "geheim") { _, _, _ ->
            throw ApiHttpException(400, "Das Transkript ist leer")
        }
        b.check()
    }

    @Test fun einundvierzigWirdDurchgereicht() {
        val b = AgentBridge("https://bridge.example.de", "falsch") { _, _, _ ->
            throw ApiHttpException(401, "Token fehlt oder stimmt nicht")
        }
        assertEquals(401, assertThrows(ApiHttpException::class.java) { b.check() }.code)
    }

    @Test fun netzfehlerBeimPruefenKommtDurch() {
        val b = AgentBridge("https://bridge.example.de", "t") { _, _, _ ->
            throw ApiNetworkException(ConnectException("nein"))
        }
        assertThrows(ApiNetworkException::class.java) { b.check() }
    }

    @Test fun diePruefungSchicktEinLeeresTranskript() {
        bridge().check()
        // Leeres Transkript = die Bridge legt weder einen Auftrag an noch verbraucht sie Rate-Limit.
        assertEquals("", JSONObject(aufrufe.single().third).getString("transcript"))
    }

    @Test fun diePruefungBrauchtAdresseUndToken() {
        assertThrows(ApiNotConfiguredException::class.java) { AgentBridge("https://x", "", poster).check() }
    }
}

/** Die Adress-Pruefung des Sprachauftrags — dieselben Regeln wie beim eigenen Erkennungs-Server, ohne /v1-Zwang. */
class AgentUrlCheckTest {

    @Test fun httpsIstImmerInOrdnung() {
        assertEquals(null, AgentUrlCheck.check("https://bridge.example.de"))
        assertTrue(AgentUrlCheck.isValid("https://bridge.example.de"))
    }

    @Test fun keinHinweisAufSlashV1() {
        // Anders als beim OpenAI-kompatiblen Server: die Bridge haengt ihren Pfad selbst an.
        assertEquals(null, AgentUrlCheck.check("https://bridge.example.de"))
    }

    @Test fun leerIstEinFehler() {
        assertEquals(ServerUrlCheck.Severity.ERROR, AgentUrlCheck.check("")?.severity)
        assertEquals(ServerUrlCheck.Severity.ERROR, AgentUrlCheck.check("   ")?.severity)
        assertFalse(AgentUrlCheck.isValid(""))
    }

    @Test fun ohneSchemaIstEinFehler() {
        assertEquals(ServerUrlCheck.MSG_SCHEME, AgentUrlCheck.check("bridge.example.de")?.message)
    }

    @Test fun klartextImHeimnetzIstErlaubt() {
        assertEquals(null, AgentUrlCheck.check("http://192.168.1.10:8093"))
    }

    @Test fun klartextInsInternetIstNurEineWarnung() {
        val problem = AgentUrlCheck.check("http://bridge.example.de")
        assertEquals(ServerUrlCheck.Severity.WARNING, problem?.severity)
        assertTrue("Eine Warnung darf das Einrichten nicht blockieren", AgentUrlCheck.isValid("http://bridge.example.de"))
    }
}
