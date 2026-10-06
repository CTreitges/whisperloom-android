package com.chris.whisperloom.api

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

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

    @Test fun ohneNetzNichts() {
        shadowOf(cm).setActiveNetworkInfo(null)
        val check = AndroidNetworkCheck(ctx)
        assertFalse(check.availableFor(cloud))
        assertFalse(check.availableFor(lan))
    }
}
