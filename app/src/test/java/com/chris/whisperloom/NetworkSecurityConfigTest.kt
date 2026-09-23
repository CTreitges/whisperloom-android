package com.chris.whisperloom

import com.chris.whisperloom.api.ProviderCatalog
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URI

/**
 * Haelt die Network-Security-Config mit dem Anbieter-Katalog synchron: jeder Cloud-Host
 * muss in der https-Pflicht-Liste stehen, sonst waere Klartext dorthin still erlaubt.
 */
class NetworkSecurityConfigTest {

    private fun configFile(): File =
        listOf("src/main/res/xml/network_security_config.xml", "app/src/main/res/xml/network_security_config.xml")
            .map { File(it) }
            .first { it.exists() }

    private fun strictDomains(): List<String> =
        Regex("<domain[^>]*>([^<]+)</domain>").findAll(configFile().readText()).map { it.groupValues[1].trim() }.toList()

    @Test fun jederCloudAnbieterIstHttpsPflichtig() {
        val domains = strictDomains()
        assertTrue(domains.isNotEmpty())
        val hosts = ProviderCatalog.providers.filter { !it.needsUrl }
            .flatMap { listOfNotNull(it.baseUrl, it.sttPathOverride) }
            .map { URI(it).host }
        for (host in hosts) {
            assertTrue("$host fehlt in network_security_config.xml", domains.any { host == it || host.endsWith(".$it") })
        }
    }

    @Test fun klartextGlobalErlaubtUndCloudVerboten() {
        val xml = configFile().readText()
        assertTrue(xml.contains("<base-config cleartextTrafficPermitted=\"true\">"))
        assertTrue(xml.contains("<domain-config cleartextTrafficPermitted=\"false\">"))
    }
}
