package com.chris.whisperloom.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetwork
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

/** Robolectric: liest die Android-Implementierung wirklich activeNetwork und NET_CAPABILITY_VALIDATED? */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidNetworkCheckTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val cm = ctx.getSystemService(ConnectivityManager::class.java)
    private val cloud = "https://api.openai.com/v1"
    private val lan = "http://192.168.1.10:11434"

    private fun capabilities(validated: Boolean) {
        val network = cm.activeNetwork
        assertNotNull("Robolectric startet mit aktivem Netz", network)
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(cm).setNetworkCapabilities(network, caps)
    }

    @Test fun validiertesNetzReichtFuerAlles() {
        capabilities(validated = true)
        val check = AndroidNetworkCheck(ctx)
        assertTrue(check.availableFor(cloud))
        assertTrue(check.availableFor(lan))
    }

    @Test fun nichtValidiertNurFuersEigeneNetz() {
        capabilities(validated = false)
        val check = AndroidNetworkCheck(ctx)
        assertFalse(check.availableFor(cloud))
        assertTrue(check.availableFor(lan))
    }

    // --- VPN (Review c2) -----------------------------------------------------------------------

    /**
     * Aktives Netz ist ein VPN, das wie ab Werk ohne eigene Pruefung als VALIDATED gilt; darunter
     * ein WLAN mit [below] — oder gar kein Netz (Funkloch, U-Bahn).
     */
    @Suppress("DEPRECATION") // NetworkInfo: so baut Robolectric Netze auf
    private fun vpnOver(below: NetworkCapabilities?) {
        val shadow = shadowOf(cm)
        shadow.clearAllNetworks()
        fun info(type: Int) = ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, type, 0, true, NetworkInfo.State.CONNECTED)
        val vpnInfo = info(ConnectivityManager.TYPE_VPN)
        val vpn = ShadowNetwork.newInstance(ConnectivityManager.TYPE_VPN)
        shadow.addNetwork(vpn, vpnInfo)
        shadow.setActiveNetworkInfo(vpnInfo)
        shadow.setNetworkCapabilities(vpn, caps(NetworkCapabilities.TRANSPORT_VPN, validated = true).also {
            shadowOf(it).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        })
        if (below != null) {
            val wifi = ShadowNetwork.newInstance(ConnectivityManager.TYPE_WIFI)
            shadow.addNetwork(wifi, info(ConnectivityManager.TYPE_WIFI))
            shadow.setNetworkCapabilities(wifi, below)
        }
        assertEquals("Robolectric: das VPN ist das aktive Netz", vpn, cm.activeNetwork)
    }

    private fun caps(transport: Int, validated: Boolean): NetworkCapabilities {
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(transport)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (validated) shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        if (transport != NetworkCapabilities.TRANSPORT_VPN) shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        return caps
    }

    @Test fun vpnOhneNetzDarunterIstKeinNetz() {
        vpnOver(below = null)
        val check = AndroidNetworkCheck(ctx)
        assertFalse("Funkloch: das VPN meldet trotzdem VALIDATED", check.availableFor(cloud))
        assertFalse(check.availableFor(lan))
    }

    @Test fun vpnUeberValidiertemNetzReichtFuerAlles() {
        vpnOver(below = caps(NetworkCapabilities.TRANSPORT_WIFI, validated = true))
        val check = AndroidNetworkCheck(ctx)
        assertTrue(check.availableFor(cloud))
        assertTrue(check.availableFor(lan))
    }

    @Test fun vpnUeberNichtValidiertemNetzNurFuersEigeneNetz() {
        vpnOver(below = caps(NetworkCapabilities.TRANSPORT_WIFI, validated = false))
        val check = AndroidNetworkCheck(ctx)
        assertFalse(check.availableFor(cloud))
        assertTrue("Heim-WLAN ohne Internet, Server im eigenen Netz", check.availableFor(lan))
    }

    @Test fun ohneNetzNichts() {
        shadowOf(cm).setActiveNetworkInfo(null)
        val check = AndroidNetworkCheck(ctx)
        assertFalse(check.availableFor(cloud))
        assertFalse(check.availableFor(lan))
    }
}
