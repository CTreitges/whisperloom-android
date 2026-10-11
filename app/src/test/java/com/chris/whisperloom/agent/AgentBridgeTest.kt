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
        AgentBridge(url, token, poster = poster)

    @Test fun derAuftragGehtAnDenEinenEndpunkt() {
        bridge().send("abc", "Kauf Milch", "2026-09-21T20:00:00Z", 4200)
        assertEquals("https://bridge.example.de/v1/task", aufrufe.single().first)
        assertEquals("geheim", aufrufe.single().second)
    }

    @Test fun derSchraegstrichAmEndeStoertNicht() {
        AgentBridge("https://bridge.example.de/", "t", poster = poster).send("a", "x", "", 0)
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

    // --- Optionen (claude-bridge) ------------------------------------------------

    private fun optionen() = JSONObject().put("model", "sonnet").put("effort", "high").put("ultracode", true)

    @Test fun ohneOptionenBleibtDerKoerperGenauDerDerHermesBridge() {
        // Sprach-Command: kein "options"-Feld, auch kein leeres oder null.
        bridge().send("abc-123", "Kauf Milch", "2026-09-21T20:00:00Z", 4200)
        val json = JSONObject(aufrufe.single().third)
        assertEquals(setOf("transcript", "request_id", "recorded_at", "duration_ms", "source"), json.keys().asSequence().toSet())
    }

    @Test fun mitOptionenTraegtDerKoerperSieAlsEigenesFeld() {
        AgentBridge("https://claude.example.de", "geheim", optionen(), poster).send("abc-123", "Bau das Widget", "2026-10-11T08:00:00Z", 4200)
        val json = JSONObject(aufrufe.single().third)
        val options = json.getJSONObject("options")
        assertEquals("sonnet", options.getString("model"))
        assertEquals("high", options.getString("effort"))
        assertTrue(options.getBoolean("ultracode"))
        assertEquals("Der Rest bleibt wie bei der hermes-bridge", "Bau das Widget", json.getString("transcript"))
        assertEquals("abc-123", json.getString("request_id"))
        assertEquals("widget", json.getString("source"))
    }

    @Test fun derKoerperDesClaudeWidgetsIstGenauDieser() {
        // Ultracode als JSON-Wahrheitswert, nicht als Text: getBoolean naehme auch "true" an,
        // ein Test darueber saehe den Unterschied nie. Feldreihenfolge = Einfuegereihenfolge.
        val options = WidgetProfile("c", kind = WidgetKind.CLAUDE_CODE, ultracode = true).bridgeOptions()
        AgentBridge("https://claude.example.de", "geheim", options, poster).send("abc-123", "Bau das Widget", "2026-10-11T08:00:00Z", 4200)
        assertEquals(
            """{"transcript":"Bau das Widget","request_id":"abc-123","recorded_at":"2026-10-11T08:00:00Z",""" +
                """"duration_ms":4200,"source":"widget","options":{"model":"opus","effort":"xhigh","ultracode":true}}""",
            aufrufe.single().third,
        )
    }

    @Test fun dieAppWartetLaengerAlsDieBridgeSynchronArbeitet() {
        // #10, H2: die Bridge arbeitet bis zu 2 x HERMES_TIMEOUT_S (30 s, hermes-bridge config.py),
        // bevor sie 202 sagt. Wartete die App kuerzer, saehe sie einen Timeout und wiederholte,
        // waehrend der Auftrag laengst ausgefuehrt wird. Bewusst gegen den Bridge-Wert als Literal:
        // READ_TIMEOUT_MS ist aus dem Budget abgeleitet, ein Vergleich der beiden waere immer wahr.
        val hermesTimeoutMs = 30_000
        assertTrue(AgentBridge.BRIDGE_SYNC_BUDGET_MS >= 2 * hermesTimeoutMs)
        assertTrue(AgentBridge.READ_TIMEOUT_MS > 2 * hermesTimeoutMs)
    }

    @Test fun ohneAdresseOderTokenWirdNichtsGesendet() {
        assertFalse(AgentBridge("", "t", poster = poster).configured)
        assertFalse(AgentBridge("https://x", "", poster = poster).configured)
        assertThrows(ApiNotConfiguredException::class.java) { AgentBridge("", "t", poster = poster).send("a", "b", "", 0) }
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

    @Test fun diePruefungSchicktDieOptionenMit() {
        // So lehnt die claude-bridge ein unbekanntes Modell schon beim Pruefen ab, nicht erst beim Auftrag.
        AgentBridge("https://claude.example.de", "geheim", optionen(), poster).check()
        val json = JSONObject(aufrufe.single().third)
        assertEquals("", json.getString("transcript"))
        assertEquals("sonnet", json.getJSONObject("options").getString("model"))
    }

    // --- Die claude-bridge weist sich beim Pruefen aus ----------------------------------

    /** Live gemessen: so antwortet die claude-bridge auf den Pruef-Koerper bei richtigem Token. */
    private val claudeBridge400 = """{"status":"error","detail":"Das Transkript ist leer","bridge":"claude-bridge"}"""

    /** Die hermes-bridge: gleicher Status, gleicher Text — nur ohne Kennung. */
    private val hermesBridge400 = """{"status":"error","detail":"Das Transkript ist leer"}"""

    private fun pruefer(antwort: String, options: JSONObject? = optionen()) =
        AgentBridge("https://claude.example.de", "geheim", options) { _, _, _ ->
            throw ApiHttpException(400, "Das Transkript ist leer", body = antwort)
        }

    @Test fun einClaudeWidgetErkenntDieClaudeBridge() {
        pruefer(claudeBridge400).check()
    }

    @Test fun einClaudeWidgetAnEinerAnderenBridgeScheitertBeimPruefen() {
        // Ohne diese Pruefung meldete "Verbindung pruefen" ok, und jeder Auftrag ginge an den
        // falschen Agenten — die hermes-bridge ignoriert das Feld "options" einfach.
        assertThrows(NotClaudeBridgeException::class.java) { pruefer(hermesBridge400).check() }
        assertThrows(NotClaudeBridgeException::class.java) { pruefer("""{"bridge":"hermes-bridge"}""").check() }
        assertThrows(NotClaudeBridgeException::class.java) { pruefer("<html>Bad Request</html>").check() }
        assertThrows(NotClaudeBridgeException::class.java) { pruefer("").check() }
    }

    @Test fun derSprachCommandPrueftWieBisher() {
        // Ohne Optionen zaehlt jede 400 als "erreichbar und Token stimmt" — mit oder ohne Kennung.
        pruefer(hermesBridge400, options = null).check()
        pruefer(claudeBridge400, options = null).check()
        pruefer("<html>Bad Request</html>", options = null).check()
    }

    @Test fun einundvierzigBleibtAuchMitOptionenEinTokenFehler() {
        val b = AgentBridge("https://claude.example.de", "falsch", optionen()) { _, _, _ ->
            throw ApiHttpException(401, "Token fehlt oder stimmt nicht", body = """{"status":"error","detail":"Token fehlt oder stimmt nicht"}""")
        }
        assertEquals(401, assertThrows(ApiHttpException::class.java) { b.check() }.code)
    }

    @Test fun diePruefungBrauchtAdresseUndToken() {
        assertThrows(ApiNotConfiguredException::class.java) { AgentBridge("https://x", "", poster = poster).check() }
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
