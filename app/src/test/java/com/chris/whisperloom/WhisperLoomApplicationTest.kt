package com.chris.whisperloom

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.agent.WidgetProfile
import com.chris.whisperloom.agent.WidgetProfileStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Der App-Start uebernimmt den alten Widget-Server — vor jedem Widget-Broadcast und Worker. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WhisperLoomApplicationTest {

    private val app: WhisperLoomApplication = ApplicationProvider.getApplicationContext()
    private val prefs get() = app.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    private val widgets get() = app.getSharedPreferences(WidgetProfileStore.FILE, Context.MODE_PRIVATE)

    @Before fun leeren() {
        prefs.edit().clear().commit()
        widgets.edit().clear().commit()
        // Wie ein Bestandsnutzer von 3.7.0: ein Server fuer alle Widgets in den Einstellungen.
        prefs.edit().putString("agent_url", "https://alt.example.de").putString("agent_token", "alt-token").commit()
    }

    @Test fun derAppStartUebernimmtDenAltenServerInsStandardprofil() {
        app.onCreate()

        val standard = WidgetProfileStore(app).get(WidgetProfile.DEFAULT_ID)!!
        assertEquals("https://alt.example.de", standard.serverUrl)
        assertEquals("alt-token", standard.serverToken)
        assertEquals("Das materialisierte Standardprofil hat einen Namen", app.getString(R.string.widget_label), standard.name)
        assertTrue("Die alten Schluessel sind weg", !prefs.contains("agent_url") && !prefs.contains("agent_token"))
    }

    @Test fun einKaputterProfilSpeicherVerhindertDenStartNicht() {
        // Falscher Typ unter dem Schluessel: getString wirft ClassCastException.
        widgets.edit().putInt("profiles", 3).commit()

        app.onCreate()

        assertTrue("Ohne gespeicherte Profile bleibt der alte Server fuer den naechsten Start", prefs.contains("agent_url"))
    }
}
