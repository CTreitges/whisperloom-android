package com.chris.whisperloom.api

import com.chris.whisperloom.api.ServerUrlCheck.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-Unit-Tests fuer die URL-Pruefung des eigenen Servers. */
class ServerUrlCheckTest {

    private val custom = ProviderCatalog.custom
    private val openai = ProviderCatalog.openai

    @Test fun privateAdressen() {
        for (h in listOf(
            "localhost", "LOCALHOST", "127.0.0.1", "10.0.0.5", "172.16.0.1", "172.31.255.255",
            "192.168.1.50", "169.254.1.1", "100.64.0.1", "100.127.255.254",
            "server.local", "nas.lan", "pi.home.arpa", "gw.internal", "[::1]", "::1", "fd12::1", "fe80::1",
        )) {
            assertTrue(h, ServerUrlCheck.isPrivateHost(h))
        }
    }

    @Test fun oeffentlicheAdressen() {
        for (h in listOf(
            "api.openai.com", "example.de", "8.8.8.8", "172.15.0.1", "172.32.0.1", "100.63.255.255",
            "100.128.0.1", "192.169.0.1", "1.2.3", "300.1.1.1", "", "2001:db8::1",
        )) {
            assertFalse(h, ServerUrlCheck.isPrivateHost(h))
        }
    }

    @Test fun lanUrlIstInOrdnung() {
        assertNull(ServerUrlCheck.check("http://192.168.1.50:8000/v1", custom))
        assertNull(ServerUrlCheck.check("http://100.101.102.103:8000/v1/", custom))
        assertNull(ServerUrlCheck.check("https://whisper.example.de/v1", custom))
        assertNull(ServerUrlCheck.check(" http://server.local:8080/v1 ", custom))
    }

    @Test fun httpInsInternetWirdGewarnt() {
        val p = ServerUrlCheck.check("http://whisper.example.de/v1", custom)!!
        assertEquals(Severity.WARNING, p.severity)
        assertEquals(ServerUrlCheck.MSG_PUBLIC_HTTP, p.message)
    }

    @Test fun cloudAnbieterLehntHttpAb() {
        val p = ServerUrlCheck.check("http://api.openai.com/v1", openai)!!
        assertEquals(Severity.ERROR, p.severity)
        assertEquals(ServerUrlCheck.MSG_HTTPS_REQUIRED, p.message)
        // ... auch zu privaten Adressen: der Katalog-Anbieter spricht nur https.
        assertEquals(ServerUrlCheck.MSG_HTTPS_REQUIRED, ServerUrlCheck.check("http://192.168.1.1/v1", openai)!!.message)
    }

    @Test fun cloudUrlOhneV1IstKeinProblem() {
        assertNull(ServerUrlCheck.check("https://api.deepseek.com", ProviderCatalog.byId("deepseek")))
        assertNull(ServerUrlCheck.check("https://generativelanguage.googleapis.com/v1beta/openai", ProviderCatalog.byId("gemini")))
    }

    @Test fun v1HinweisNurBeimEigenenServer() {
        val p = ServerUrlCheck.check("http://192.168.1.50:8000", custom)!!
        assertEquals(Severity.WARNING, p.severity)
        assertEquals(ServerUrlCheck.MSG_V1, p.message)
        assertEquals(ServerUrlCheck.MSG_V1, ServerUrlCheck.check("http://192.168.1.50:8000/api/", custom)!!.message)
    }

    @Test fun ollamaImHeimnetzOhneV1Hinweis() {
        val ollama = ProviderCatalog.byId(ProviderCatalog.OLLAMA_ID)
        assertNull(ServerUrlCheck.check("http://192.168.1.10:11434", ollama))
        assertNull(ServerUrlCheck.check("http://homeserver.local:11434", ollama))
        assertEquals(ServerUrlCheck.Severity.WARNING, ServerUrlCheck.check("http://example.com:11434", ollama)?.severity)
    }

    @Test fun ollamaCloudVerlangtHttps() {
        val cloud = ProviderCatalog.byId(ProviderCatalog.OLLAMA_CLOUD_ID)
        assertEquals(ServerUrlCheck.MSG_HTTPS_REQUIRED, ServerUrlCheck.check("http://ollama.com", cloud)?.message)
    }

    @Test fun ungueltigeEingaben() {
        assertEquals(ServerUrlCheck.MSG_INVALID, ServerUrlCheck.check("http://bad url/v1", custom)!!.message)
        assertEquals(ServerUrlCheck.MSG_SCHEME, ServerUrlCheck.check("192.168.1.50:8000/v1", custom)!!.message)
        assertEquals(ServerUrlCheck.MSG_SCHEME, ServerUrlCheck.check("ftp://server/v1", custom)!!.message)
        assertEquals(ServerUrlCheck.MSG_NO_HOST, ServerUrlCheck.check("http:///v1", custom)!!.message)
        assertEquals(Severity.ERROR, ServerUrlCheck.check("", custom)!!.severity)
    }
}
