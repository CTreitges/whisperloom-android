package com.chris.whisperloom

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Die Simple-APK hat eine FESTE Paket-ID (Konzept §11.1): sie wird registriert und ist nach der ersten
 * Installation nicht mehr aenderbar. Insbesondere haengt sie nicht an der Gradle-Property
 * whisperloom.applicationId des Play-Builds — die CI baut beide Flavors auch mit -Pwhisperloom.applicationId=com.whisperloom.
 */
class ApplicationIdTest {

    @Test fun simpleHatDieFestePaketIdUnabhaengigVonDerPlayProperty() {
        assertEquals("com.chris.whisperloom.simple", BuildConfig.APPLICATION_ID)
    }
}
