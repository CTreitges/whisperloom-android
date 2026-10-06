package com.chris.whisperloom.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.URI

/**
 * Netz-Vorpruefung vor einer Online-Textverbesserung: ohne Netz entscheidet die Regel sofort
 * (lokal oder ohne KI), statt dass das Diktat im Connect-Timeout haengt. Interface, damit Tests
 * das Netz faken koennen; die reinen Teile ([available], [isOwnNetwork]) sind JVM-testbar.
 */
fun interface NetworkCheck {

    /** Ist fuer eine Anfrage an [baseUrl] gerade Netz da? Schickt selbst nichts los. */
    fun availableFor(baseUrl: String): Boolean

    companion object {

        /**
         * Internet zaehlt erst validiert (Captive Portal, WLAN ohne Internet zaehlen nicht). Fuer einen
         * Server im eigenen Netz reicht ein aktives Netz: ein Heim-WLAN ohne Internet oder ein VPN
         * validiert nicht unbedingt, der Server ist trotzdem erreichbar.
         */
        fun available(activeNetwork: Boolean, validated: Boolean, ownNetwork: Boolean): Boolean =
            activeNetwork && (validated || ownNetwork)

        /** Server im eigenen Netz: private Adressen wie in [ServerUrlCheck.isPrivateHost], dazu Tailscale-Namen (*.ts.net). */
        fun isOwnNetwork(baseUrl: String): Boolean {
            val host = runCatching { URI(baseUrl.trim()).host }.getOrNull()?.lowercase() ?: return false
            return ServerUrlCheck.isPrivateHost(host) || host.endsWith(".ts.net")
        }
    }
}

/** Android: aktives Netz und NET_CAPABILITY_VALIDATED (Berechtigung ACCESS_NETWORK_STATE). */
class AndroidNetworkCheck(context: Context) : NetworkCheck {

    private val cm: ConnectivityManager? = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun availableFor(baseUrl: String): Boolean {
        val manager = cm ?: return false
        val network = manager.activeNetwork ?: return false
        val validated = manager.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        return NetworkCheck.available(activeNetwork = true, validated = validated, ownNetwork = NetworkCheck.isOwnNetwork(baseUrl))
    }
}
