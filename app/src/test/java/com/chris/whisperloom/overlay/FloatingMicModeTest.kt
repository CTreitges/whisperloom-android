package com.chris.whisperloom.overlay

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.llm.OfflineRefineFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.time.Duration

/**
 * Halten und Wischen am echten, vom Dienst verdrahteten Knopf (docs/design/bubble-textfeld.md §1–§4):
 * Modus-Kreuz, Moduswahl mit und ohne Sprache, ✕, Schalter aus, TalkBack-Aktionen.
 * Der Druckpunkt liegt in der Bildschirmmitte, der Bogen oeffnet also nach oben: rechts = Kuerzen,
 * links = Aus, unten = ✕.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingMicModeTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val fixture = OfflineRefineFixture(app)
    private var controller: ServiceController<FloatingMicService>? = null
    private lateinit var bubble: View
    private lateinit var wm: Any
    private val holdMs = ViewConfiguration.getLongPressTimeout().toLong()
    private val prefs get() = Prefs(app)

    private val cx get() = app.resources.displayMetrics.widthPixels / 2f
    private val cy get() = app.resources.displayMetrics.heightPixels / 2f
    private val reach get() = 110 * app.resources.displayMetrics.density

    @Before fun aufbau() {
        fixture.setUp()
        ShadowSettings.setCanDrawOverlays(true)
    }

    @After fun abbau() {
        controller?.destroy()
        fixture.tearDown()
    }

    private fun starten() {
        val c = Robolectric.buildService(FloatingMicService::class.java).create()
        controller = c
        wm = c.get().getSystemService(Context.WINDOW_SERVICE)
        bubble = windows().first().findViewById(R.id.bubble)
    }

    private fun windows(): List<View> = Shadow.extract<ShadowWindowManagerImpl>(wm).views

    private var downAt = 0L

    private fun touch(action: Int, x: Float = cx, y: Float = cy, at: Long = SystemClock.uptimeMillis()) {
        if (action == MotionEvent.ACTION_DOWN) downAt = at
        val e = MotionEvent.obtain(downAt, at, action, x, y, 0)
        bubble.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun warten(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    /** Halten bis zur Aufnahme, dann in Schritten zum Ziel in Richtung ([dx], [dy]) und dort loslassen. */
    private fun haltenUndWischen(dx: Float, dy: Float, recording: Boolean = true) {
        touch(MotionEvent.ACTION_DOWN)
        warten(holdMs + 50)
        if (recording) fixture.awaitRecorded()
        for (step in 1..4) touch(MotionEvent.ACTION_MOVE, cx + dx * reach * step / 4, cy + dy * reach * step / 4)
        touch(MotionEvent.ACTION_UP, cx + dx * reach, cy + dy * reach, at = downAt + holdMs + 2_000)
    }

    private fun zustand(): String? {
        val cd = bubble.contentDescription?.toString() ?: return null
        val recording = app.getString(R.string.cd_bubble_recording, "\u0000").substringBefore('\u0000')
        return if (cd.startsWith(recording)) app.getString(R.string.cd_bubble_recording) else cd
    }

    private fun clip(): String? =
        app.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()

    private fun modeToast(mode: RefineMode) =
        app.getString(R.string.float_mode_set, app.getString(ModeCrossWindow.labelRes(mode)))

    @Test fun kreuzErscheintKaumSichtbarUndVerschwindetBeimLoslassen() {
        starten()
        touch(MotionEvent.ACTION_DOWN)
        warten(holdMs + 50)
        fixture.awaitRecorded()
        val cross = windows().singleOrNull { it !== bubble.rootView }
        assertNotNull("Kreuz-Fenster fehlt", cross)
        assertEquals(ModeCross.FAINT_ALPHA, cross!!.alpha, 0.001f)
        touch(MotionEvent.ACTION_MOVE, cx + reach, cy)
        assertEquals("deutlich beim Wegbewegen", 1f, cross.alpha, 0.001f)
        touch(MotionEvent.ACTION_UP, cx + reach, cy, at = downAt + holdMs + 2_000)
        assertEquals(listOf(bubble.rootView), windows())
        // Das Loslassen auf "Kuerzen" sendet: abwarten, sonst laeuft die Uebertragung in den naechsten Test.
        fixture.waitFor("Senden nicht beendet") { zustand() == app.getString(R.string.cd_bubble_idle) }
    }

    @Test fun wischenAufModusSetztIhnDauerhaftUndSendetMitIhm() {
        starten()
        haltenUndWischen(dx = 1f, dy = 0f)
        fixture.waitFor("Diktat mit gewaehltem Modus nicht gesendet") { clip() != null }
        assertEquals(RefineMode.SUMMARIZE, prefs.refineMode)
        assertEquals(1, fixture.transcriptions.get())
        assertEquals("Lokal verbessert.", clip())
    }

    @Test fun ohneSpracheNurModuswechselKeineAnfrage() {
        fixture.level = 40 // Raumrauschen, ~0,001 vom Vollausschlag
        starten()
        haltenUndWischen(dx = -1f, dy = 0f)
        fixture.waitFor("Knopf nicht zurueck in Ruhe") { zustand() == app.getString(R.string.cd_bubble_idle) }
        assertEquals(RefineMode.OFF, prefs.refineMode)
        assertEquals(0, fixture.transcriptions.get())
        assertNull(clip())
        assertEquals(modeToast(RefineMode.OFF), ShadowToast.getTextOfLatestToast())
    }

    @Test fun wischenAufKreuzVerwirftUndLaesstDenModus() {
        starten()
        haltenUndWischen(dx = 0f, dy = 1f)
        fixture.waitFor("Knopf nicht zurueck in Ruhe") { zustand() == app.getString(R.string.cd_bubble_idle) }
        assertEquals(RefineMode.POLISH, prefs.refineMode)
        assertEquals(0, fixture.transcriptions.get())
        assertNull(clip())
        assertEquals(app.getString(R.string.float_discarded), ShadowToast.getTextOfLatestToast())
    }

    @Test fun abbruchAufKreuzVerwirftNichtWennSchonGesendetWird() {
        starten()
        touch(MotionEvent.ACTION_DOWN)
        warten(holdMs + 50)
        fixture.awaitRecorded()
        for (step in 1..4) touch(MotionEvent.ACTION_MOVE, cx, cy + reach * step / 4)
        // Aufnahme endet von aussen, waehrend der Finger auf dem ✕ liegt (hier per Klick wie TalkBack;
        // im Betrieb totes Mikrofon oder 12-min-Grenze): der Dienst steht in SENDING.
        bubble.performClick()
        touch(MotionEvent.ACTION_CANCEL, cx, cy + reach)
        assertNotEquals("Senden zurueckgesetzt", app.getString(R.string.cd_bubble_idle), zustand())
        assertNotEquals(app.getString(R.string.float_discarded), ShadowToast.getTextOfLatestToast())
        fixture.waitFor("laufende Uebertragung wurde abgebrochen") { clip() != null }
        assertEquals(1, fixture.transcriptions.get())
    }

    @Test fun modusAusDerTastaturLeisteAktualisiertTalkBack() {
        starten()
        prefs.refineMode = RefineMode.SUMMARIZE // z. B. in der Leiste der eigenen Tastatur
        prefs.promptLevelEnabled = true
        warten(0)
        val info = bubble.createAccessibilityNodeInfo()
        assertEquals(modeToast(RefineMode.SUMMARIZE), info.stateDescription?.toString())
        val labels = info.actionList.mapNotNull { it.label?.toString() }
        val prompt = app.getString(R.string.cd_bubble_action_mode, app.getString(ModeCrossWindow.labelRes(RefineMode.PROMPT)))
        assertTrue("$prompt fehlt in $labels", prompt in labels)
    }

    @Test fun haltenOhneSpracheSendetNichts() {
        fixture.level = 40
        starten()
        touch(MotionEvent.ACTION_DOWN)
        warten(holdMs + 50)
        fixture.awaitRecorded()
        touch(MotionEvent.ACTION_UP, at = downAt + holdMs + 2_000)
        fixture.waitFor("Knopf nicht zurueck in Ruhe") { zustand() == app.getString(R.string.cd_bubble_idle) }
        assertEquals(0, fixture.transcriptions.get())
    }

    @Test fun schalterAusKreuzOhneAufnahmeSetztNurDenModus() {
        prefs.holdToTalk = false
        starten()
        haltenUndWischen(dx = 1f, dy = 0f, recording = false)
        warten(100)
        assertEquals(RefineMode.SUMMARIZE, prefs.refineMode)
        assertEquals(app.getString(R.string.cd_bubble_idle), zustand())
        assertEquals(0, fixture.transcriptions.get())
    }

    /** Ausweg bei Tremor: Halten ohne Bewegung wirkt mit Schalter aus wie ein (langsamer) Tipp. */
    @Test fun schalterAusLangsamerTippStartetDieAufnahme() {
        prefs.holdToTalk = false
        starten()
        touch(MotionEvent.ACTION_DOWN)
        warten(holdMs + 50)
        assertEquals(app.getString(R.string.cd_bubble_idle), zustand())
        touch(MotionEvent.ACTION_UP, at = downAt + holdMs + 200)
        warten(0)
        assertEquals(app.getString(R.string.cd_bubble_recording), zustand())
    }

    @Test fun schalterAusKreuzAufgezogenUndZurueckSchliesstNur() {
        prefs.holdToTalk = false
        starten()
        touch(MotionEvent.ACTION_DOWN)
        warten(holdMs + 50)
        touch(MotionEvent.ACTION_MOVE, cx + reach, cy)
        touch(MotionEvent.ACTION_MOVE, cx, cy)
        touch(MotionEvent.ACTION_UP, at = downAt + holdMs + 2_000)
        warten(0)
        assertEquals(app.getString(R.string.cd_bubble_idle), zustand())
        assertEquals(RefineMode.POLISH, prefs.refineMode)
    }

    @Test fun talkBackJeModusEineAktionMitAktivemModus() {
        prefs.promptLevelEnabled = true
        starten()
        val info = bubble.createAccessibilityNodeInfo()
        val labels = info.actionList.mapNotNull { it.label?.toString() }
        for (mode in RefineMode.settings(promptEnabled = true)) {
            val name = app.getString(ModeCrossWindow.labelRes(mode))
            val label = if (mode == RefineMode.POLISH) {
                app.getString(R.string.cd_bubble_action_mode_active, name)
            } else {
                app.getString(R.string.cd_bubble_action_mode, name)
            }
            assertTrue("$label fehlt in $labels", label in labels)
        }
        assertTrue(app.getString(R.string.cd_hide) in labels)
        assertEquals(modeToast(RefineMode.POLISH), info.stateDescription?.toString())

        val summarize = app.getString(R.string.cd_bubble_action_mode, app.getString(R.string.kb_level_summarize))
        val id = info.actionList.single { it.label?.toString() == summarize }.id
        assertTrue(bubble.performAccessibilityAction(id, null))
        warten(0)
        assertEquals(RefineMode.SUMMARIZE, prefs.refineMode)
        assertEquals("nichts gestartet", app.getString(R.string.cd_bubble_idle), zustand())
        val after = bubble.createAccessibilityNodeInfo().actionList.mapNotNull { it.label?.toString() }
        assertTrue(app.getString(R.string.cd_bubble_action_mode_active, app.getString(R.string.kb_level_summarize)) in after)
    }
}
