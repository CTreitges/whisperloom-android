package com.chris.whisperloom.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-Tests fuer die Netz-Vorpruefung: Host-Einordnung und Entscheidung (Spec §1). */
class NetworkCheckTest {

    @Test fun serverImEigenenNetz() {
        for (url in listOf(
            "http://localhost:11434",
            "http://127.0.0.1:8000/v1",
            "http://192.168.1.10:11434",
            "http://10.0.0.5/v1",
            "http://172.16.0.1:8080/v1",
            "http://100.101.102.103:8000/v1", // Tailscale-IP
            "https://nas.tail1234.ts.net/v1", // Tailscale MagicDNS
            "http://homeserver.local:11434",
            "http://nas.lan/v1",
            "http://pi.home.arpa:8000/v1",
            "http://[::1]:11434",
            "http://[fd12::1]:8000/v1",
            " HTTP://Server.LOCAL:11434 ",
        )) {
            assertTrue(url, NetworkCheck.isOwnNetwork(url))
        }
    }

    @Test fun oeffentlicheServer() {
        for (url in listOf(
            "https://api.openai.com/v1",
            "https://generativelanguage.googleapis.com/v1beta/openai",
            "https://ollama.com",
            "https://whisper.example.de/v1",
            "http://8.8.8.8/v1",
            "https://ts.net.example.com/v1", // nur die Endung zaehlt
            "",
            "kein url",
        )) {
            assertFalse(url, NetworkCheck.isOwnNetwork(url))
        }
    }

    @Test fun ohneAktivesNetzNieVerfuegbar() {
        assertFalse(NetworkCheck.available(activeNetwork = false, validated = false, ownNetwork = false))
        assertFalse(NetworkCheck.available(activeNetwork = false, validated = false, ownNetwork = true))
        assertFalse(NetworkCheck.available(activeNetwork = false, validated = true, ownNetwork = true))
    }

    @Test fun internetBrauchtValidiertesNetz() {
        assertTrue(NetworkCheck.available(activeNetwork = true, validated = true, ownNetwork = false))
        assertFalse("Captive Portal / WLAN ohne Internet", NetworkCheck.available(activeNetwork = true, validated = false, ownNetwork = false))
    }

    @Test fun eigenesNetzReichtEinAktivesNetz() {
        assertTrue("Heim-WLAN ohne Internet", NetworkCheck.available(activeNetwork = true, validated = false, ownNetwork = true))
        assertTrue(NetworkCheck.available(activeNetwork = true, validated = true, ownNetwork = true))
    }
}
