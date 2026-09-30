package com.chris.whisperloom.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.AppNav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Aufnahme-Notification: ein Tipp fuehrt zu den Pro Widgets. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceTaskNotificationTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun einTippFuehrtInDenTabProWidgets() {
        val ziel = shadowOf(VoiceTaskNotification.build(ctx).contentIntent).savedIntent
        assertEquals(AppNav.ROUTE_WIDGETS, ziel.getStringExtra(AppNav.EXTRA_ROUTE))
        assertFalse("Kein Editor — die Aufnahme laeuft ja", ziel.hasExtra(AppNav.EXTRA_PROFILE))
    }
}
