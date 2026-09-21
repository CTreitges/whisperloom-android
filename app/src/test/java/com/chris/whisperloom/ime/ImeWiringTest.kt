package com.chris.whisperloom.ime

import android.Manifest
import android.app.Application
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Verdrahtung der Diktier-Tastatur am echten Dienst.
 *
 * Der wichtigste Fall ist eine Regression: die Mikro-Taste hatte nur einen
 * `OnTouchListener` und war damit mit TalkBack faktisch unbedienbar — Gedrueckthalten
 * kommt dort nicht an, und `performClick()` lief ins Leere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImeWiringTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun inputView(): View =
        Robolectric.buildService(WhisperLoomInputMethodService::class.java).create().get().onCreateInputView()

    @Test fun mikroTasteIstMitTalkBackBedienbar() {
        val mic = inputView().findViewById<ImageButton>(R.id.mic)
        assertTrue("Mikro-Taste ohne OnClickListener — mit TalkBack unbedienbar", mic.hasOnClickListeners())
    }

    @Test fun mikroTasteNenntDasAntippen() {
        val mic = inputView().findViewById<ImageButton>(R.id.mic)
        assertEquals(app.getString(R.string.cd_mic), mic.contentDescription)
        assertTrue("Beschreibung muss das Antippen nennen", mic.contentDescription.contains("antippen"))
    }

    @Test fun wischZieleSindVerdrahtetAberImRuhezustandWeg() {
        val v = inputView()
        for (id in listOf(R.id.gesture_discard, R.id.gesture_lock)) {
            val target = v.findViewById<ImageButton>(id)
            assertTrue(
                "Wisch-Ziel ${app.resources.getResourceEntryName(id)} ohne OnClickListener",
                target.hasOnClickListeners(),
            )
            assertEquals(View.GONE, target.visibility)
            assertFalse("Ziel darf im Ruhezustand nicht bedienbar sein", target.isClickable)
        }
    }

    @Test fun klickOhneMikrofonBerechtigungFuehrtInDenAssistenten() {
        // Beweist, dass der Klick wirklich ankommt (nicht nur ein gesetzter Listener ist).
        val v = inputView()
        v.findViewById<ImageButton>(R.id.mic).performClick()
        assertEquals(
            app.getString(R.string.kb_need_permission),
            v.findViewById<TextView>(R.id.status).text.toString(),
        )
        assertNotNull("Klick muss in den Einrichtungs-Assistenten fuehren", shadowOf(app).nextStartedActivity)
    }

    @Test fun klickOhneZugangFuehrtInDieEinrichtung() {
        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val v = inputView()
        v.findViewById<ImageButton>(R.id.mic).performClick()
        assertEquals(
            app.getString(R.string.kb_not_configured),
            v.findViewById<TextView>(R.id.status).text.toString(),
        )
        assertNotNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun dieStatuszeileStimmtSchonBeimAufbau() {
        // Ohne Mikrofon-Berechtigung steht die Warnung sofort da — nicht erst der
        // Ruhe-Hinweis aus dem Layout, bis das erste Eingabefeld kommt.
        val ohne = inputView().findViewById<TextView>(R.id.status)
        assertEquals(app.getString(R.string.kb_need_permission), ohne.text.toString())

        shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        Prefs(app).apply {
            engine = Engine.ONLINE
            sttProviderId = "custom"
            apiBaseUrl = "http://127.0.0.1:1/v1"
        }
        // Eingerichtet: der Ruhe-Hinweis. Die Geste zeigt sich erst beim Ziehen.
        val mit = inputView().findViewById<TextView>(R.id.status)
        assertEquals(app.getString(R.string.kb_hint_hold), mit.text.toString())
    }
}
