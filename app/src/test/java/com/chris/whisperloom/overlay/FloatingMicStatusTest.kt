package com.chris.whisperloom.overlay

import android.app.Application
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.ui.WhisperLoomApp
import com.chris.whisperloom.ui.nav.SystemStatus
import com.chris.whisperloom.ui.state.AppEnv
import com.chris.whisperloom.ui.state.PrefsState
import com.chris.whisperloom.ui.theme.WhisperLoomTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

/**
 * Laufstatus des schwebenden Knopfs am echten Dienst (CTR-197, CTR-205):
 * - Beendet die Benachrichtigung den Dienst, waehrend die App offen ist, wechselt Home ohne
 *   onResume von "beenden" auf "starten" (vorher blieb "Mikro-Knopf beenden" stehen).
 * - Der Dienst startet nach einem Prozess-Kill nicht von selbst neu (kein START_STICKY).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class FloatingMicStatusTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private var controller: ServiceController<FloatingMicService>? = null

    @Before fun aufbau() {
        app.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowSettings.setCanDrawOverlays(true)
    }

    @After fun abbau() {
        controller?.destroy()
    }

    private fun startService(): FloatingMicService {
        val c = Robolectric.buildService(FloatingMicService::class.java).create()
        controller = c
        return c.get()
    }

    /** Wie die Benachrichtigungs-Aktion "Beenden": ACTION_STOP, danach zerstoert das System den Dienst. */
    private fun stopViaNotification() {
        val c = controller!!
        c.withIntent(Intent(app, FloatingMicService::class.java).setAction(FloatingMicService.ACTION_STOP)).startCommand(0, 2)
        c.destroy()
        controller = null
    }

    @Test fun laufstatusFolgtDemDienst() {
        assertFalse(FloatingMicService.isRunning)
        startService()
        assertTrue(FloatingMicService.isRunning)
        stopViaNotification()
        assertFalse(FloatingMicService.isRunning)
    }

    @Test fun homeZeigtStartenSobaldDieBenachrichtigungDenKnopfBeendet() {
        val prefs = Prefs(app)
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "test-key-not-real-1"
        prefs.tutorialSeen = true
        startService()
        // Nur bubbleRunning kommt aus dem Dienst (wie in SystemStatus.read); der Rest ist "eingerichtet".
        val reader = {
            SystemStatus(
                micGranted = true,
                canDrawOverlays = true,
                a11yRunning = true,
                bubbleRunning = FloatingMicService.isRunning,
            )
        }
        val env = AppEnv(PrefsState(prefs), reader(), reader)
        compose.setContent { WhisperLoomTheme { WhisperLoomApp(env) } }
        compose.waitForIdle()
        compose.onNodeWithText("Mikro-Knopf beenden").assertIsDisplayed()

        stopViaNotification() // kein onResume, kein refreshStatus von aussen
        compose.waitForIdle()

        compose.onNodeWithText("Mikro-Knopf starten").assertIsDisplayed()
        assertFalse(env.status.bubbleRunning)
    }

    @Test fun dienstStartetNachProzessKillNichtVonSelbstNeu() {
        val service = startService()
        val result = service.onStartCommand(Intent(app, FloatingMicService::class.java), 0, 1)
        assertEquals(Service.START_NOT_STICKY, result)
    }
}
