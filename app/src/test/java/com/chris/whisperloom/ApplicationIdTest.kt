package com.chris.whisperloom

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Paket-ID je Kanal: GitHub-/F-Droid-Builds behalten com.chris.whisperloom, sonst bekommen installierte Apps
 * kein Update mehr. Nur das Play-AAB heisst com.whisperloom (CI: -Pwhisperloom.applicationId=com.whisperloom);
 * das prueft die CI am fertigen Bundle (tools/check_package_id.sh).
 */
class ApplicationIdTest {

    @Test fun standardBuildBehaeltDiePaketIdDerInstalliertenApps() {
        // Rot, wenn whisperloom.applicationId versehentlich global gesetzt ist (z. B. in gradle.properties).
        assertEquals("com.chris.whisperloom", BuildConfig.APPLICATION_ID)
    }
}
