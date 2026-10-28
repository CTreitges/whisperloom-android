package com.chris.whisperloom.overlay

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.A11yViewChecks
import com.chris.whisperloom.R
import com.chris.whisperloom.TranscriptionBackend
import com.chris.whisperloom.TranscriptionEngine
import com.chris.whisperloom.api.ApiHttpException
import com.chris.whisperloom.llm.OfflineRefineFixture
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowAccessibilityManager
import org.robolectric.shadows.ShadowAudioRecord
import org.robolectric.shadows.ShadowSettings
import org.robolectric.shadows.ShadowToast
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

/**
 * Der schwebende Knopf ohne Ziehen und mit verlaesslichen Ansagen (UX-Spec §6.20), am echten,
 * vom Dienst verdrahteten Knopf: Verwerfen und Erneut senden als Bedienungshilfe-Aktion, Zustand
 * als `stateDescription` mit Live-Region statt `announceForAccessibility`, "nimmt auf" vor dem
 * Mikrofon, ein Label, das mit der Schrift waechst.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FloatingMicA11yTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val fixture = OfflineRefineFixture(app)
    private val originalBackend = TranscriptionEngine.backendFactory
    private var controller: ServiceController<FloatingMicService>? = null
    private lateinit var root: View
    private lateinit var bubble: View
    private lateinit var label: TextView

    private val verwerfen get() = app.getString(R.string.cd_kb_discard)
    private val erneut get() = app.getString(R.string.cd_kb_retry)

    @Before fun aufbau() {
        fixture.setUp()
        ShadowSettings.setCanDrawOverlays(true)
    }

    @After fun abbau() {
        controller?.destroy()
        TranscriptionEngine.backendFactory = originalBackend
        fixture.tearDown()
    }

    private fun starten() {
        controller?.destroy()
        controller = Robolectric.buildService(FloatingMicService::class.java).create().also {
            val wm = it.get().getSystemService(Context.WINDOW_SERVICE)
            root = Shadow.extract<ShadowWindowManagerImpl>(wm).views.single()
        }
        bubble = root.findViewById(R.id.bubble)
        label = root.findViewById(R.id.bubble_label)
    }

    private fun aufnehmen() {
        bubble.performClick()
        fixture.awaitRecorded()
    }

    private fun warten(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    /** Bis der Knopf [desc] als Zustand meldet; der io-Thread postet zurueck auf den Main-Looper. */
    private fun bisZustand(desc: String) {
        val until = System.currentTimeMillis() + 5_000
        while (bubble.stateDescription?.toString() != desc) {
            if (System.currentTimeMillis() > until) fail("Zustand bleibt ${bubble.stateDescription}, erwartet $desc")
            warten(10)
            Thread.sleep(10)
        }
    }

    /** Uebertragung scheitert so, dass ein zweiter Versuch Sinn hat: der Knopf geht in ERROR. */
    private fun fehlerZustand() {
        TranscriptionEngine.backendFactory = { _, _ ->
            object : TranscriptionBackend {
                override val label = "Online"
                override fun transcribe(upload: com.chris.whisperloom.api.WavUpload, language: String) =
                    throw ApiHttpException(503, "Server weg")
            }
        }
        aufnehmen()
        bubble.performClick()
        bisZustand(app.getString(R.string.cd_bubble_error))
    }

    private fun aktion(label: String) =
        bubble.createAccessibilityNodeInfo().actionList.firstOrNull { it.label?.toString() == label }

    // --- Pruef-Helfer am verdrahteten Knopf ------------------------------------

    @Test fun knopfOhneBefundInJederSchriftgroesse() {
        for (scale in A11yViewChecks.FONT_SCALES) {
            A11yViewChecks.withFontScale(scale) {
                starten()
                A11yViewChecks.layout(root)
                assertEquals("IDLE, fontScale $scale", emptyList<String>(), A11yViewChecks.findings(root))
                aufnehmen()
                A11yViewChecks.layout(root)
                assertEquals(
                    "RECORDING, fontScale $scale",
                    emptyList<String>(),
                    A11yViewChecks.findings(root, mapOf(bubble to listOf(verwerfen))),
                )
                bubble.performAccessibilityAction(aktion(verwerfen)?.id ?: 0, null)
            }
        }
    }

    // --- Aktionen statt Ziehen (O1) --------------------------------------------

    @Test fun inDerAufnahmeVerwirftDieAktion() {
        starten()
        assertNull("Ohne Aufnahme gibt es nichts zu verwerfen", aktion(verwerfen))
        aufnehmen()
        val a = aktion(verwerfen) ?: return fail("Aktion „$verwerfen“ fehlt in RECORDING")
        assertNull("Erneut senden nur im Fehler", aktion(erneut))

        assertTrue(bubble.performAccessibilityAction(a.id, null))
        warten(0)
        assertEquals(app.getString(R.string.cd_bubble_idle), bubble.stateDescription?.toString())
        assertEquals(app.getString(R.string.float_discarded), ShadowToast.getTextOfLatestToast())
        assertNull("Nach dem Verwerfen keine Aktion mehr", aktion(verwerfen))
    }

    @Test fun imFehlerErneutSendenOderVerwerfen() {
        starten()
        fehlerZustand()
        assertNotNull(aktion(erneut))
        val v = aktion(verwerfen) ?: return fail("Aktion „$verwerfen“ fehlt in ERROR")

        assertTrue(bubble.performAccessibilityAction(v.id, null))
        warten(0)
        assertEquals(app.getString(R.string.cd_bubble_idle), bubble.stateDescription?.toString())
        assertNull(aktion(erneut))
    }

    @Test fun erneutSendenSchicktDasGepufferteAudio() {
        starten()
        fehlerZustand()
        TranscriptionEngine.backendFactory = originalBackend
        fixture.setUp() // wieder ein funktionierender Fake-Zugang
        val a = aktion(erneut) ?: return fail("Aktion „$erneut“ fehlt in ERROR")
        assertTrue(bubble.performAccessibilityAction(a.id, null))
        bisZustand(app.getString(R.string.cd_bubble_copied))
    }

    // --- Zustand und Ansage (O2, O6) -------------------------------------------

    @Test fun nameFestZustandAlsStateDescription() {
        starten()
        assertEquals(app.getString(R.string.cd_bubble), bubble.contentDescription)
        assertEquals(app.getString(R.string.cd_bubble_idle), bubble.stateDescription?.toString())
        aufnehmen()
        assertEquals(app.getString(R.string.cd_bubble_timer, "0:00"), bubble.contentDescription)
        assertEquals(app.getString(R.string.cd_bubble_recording), bubble.stateDescription?.toString())
        bubble.performClick()
        assertEquals(app.getString(R.string.cd_bubble_sending), bubble.stateDescription?.toString())
        // Ohne Bedienungshilfe landet der Text in der Zwischenablage — der Knopf sagt das.
        bisZustand(app.getString(R.string.cd_bubble_copied))
        assertEquals(app.getString(R.string.cd_bubble), bubble.contentDescription)
        warten(BubbleMotion.COPIED_HINT_MS)
        assertEquals(app.getString(R.string.cd_bubble_idle), bubble.stateDescription?.toString())
        assertEquals(
            "Der Hinweis laeuft ab, ohne dass TalkBack ungefragt etwas ansagt",
            View.ACCESSIBILITY_LIVE_REGION_NONE,
            bubble.accessibilityLiveRegion,
        )
    }

    @Test fun keineAnsageUeberAnnounceForAccessibility() {
        val am = app.getSystemService(AccessibilityManager::class.java)
        val shadowAm: ShadowAccessibilityManager = Shadow.extract(am)
        shadowAm.setEnabled(true)
        starten()
        aufnehmen()
        bubble.performClick()
        // Auf das Ergebnis warten, nicht auf einen Zustandstext — der Test soll an der Ansage scheitern.
        val until = System.currentTimeMillis() + 5_000
        while (app.getSystemService(ClipboardManager::class.java).primaryClip == null) {
            if (System.currentTimeMillis() > until) fail("Text nicht eingefuegt")
            warten(10)
            Thread.sleep(10)
        }
        warten(0)
        val ansagen = shadowAm.sentAccessibilityEvents.filter { it.eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT }
        assertEquals("announceForAccessibility ist seit API 36 abgekuendigt", emptyList<String>(), ansagen.map { it.text.toString() })
    }

    @Test fun liveRegionJeZustand() {
        starten()
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, bubble.accessibilityLiveRegion)
        aufnehmen()
        assertEquals("Der Aufnahmestart wird angesagt", View.ACCESSIBILITY_LIVE_REGION_POLITE, bubble.accessibilityLiveRegion)
        warten(1_500)
        assertEquals("Der Sekundentakt nicht", View.ACCESSIBILITY_LIVE_REGION_NONE, bubble.accessibilityLiveRegion)
        // Die Zeit laeuft im Namen weiter; der Zustand bleibt gleich — sonst liest TalkBack ihn am
        // fokussierten Knopf jede Sekunde vor, auch ohne Live-Region.
        val name = bubble.contentDescription.toString()
        assertTrue("Zeit laeuft im Namen: $name", name.matches(Regex("Mikrofon, 0:0[1-9]")))
        assertEquals(app.getString(R.string.cd_bubble_recording), bubble.stateDescription?.toString())
        bubble.performAccessibilityAction(aktion(verwerfen)?.id ?: 0, null)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, bubble.accessibilityLiveRegion)
    }

    @Test fun fehlerIstAssertive() {
        starten()
        fehlerZustand()
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE, bubble.accessibilityLiveRegion)
    }

    // --- "nimmt auf" vor dem Mikrofon (O3) -------------------------------------

    @Test fun nimmtAufStehtVorDemOeffnenDesMikrofons() {
        starten()
        val beimErstenLesen = AtomicReference<String>()
        ShadowAudioRecord.setSourceProvider {
            object : ShadowAudioRecord.AudioRecordSource {
                override fun readInShortArray(data: ShortArray, offset: Int, size: Int, blocking: Boolean): Int {
                    beimErstenLesen.compareAndSet(null, bubble.stateDescription?.toString() ?: "(keine)")
                    return 0
                }
            }
        }
        bubble.performClick()
        val until = System.currentTimeMillis() + 5_000
        while (beimErstenLesen.get() == null) {
            if (System.currentTimeMillis() > until) fail("Mikrofon nie gelesen")
            Thread.sleep(5)
        }
        assertEquals(app.getString(R.string.cd_bubble_recording), beimErstenLesen.get())
    }

    // --- Label waechst mit der Schrift (O7) ------------------------------------

    @Test fun labelWirdBeiGrosserSchriftNichtAbgeschnitten() {
        A11yViewChecks.withFontScale(2f) {
            starten()
            aufnehmen()
            A11yViewChecks.layout(root)
            val noetig = label.layout.height + label.paddingTop + label.paddingBottom
            assertTrue("Label ${label.height} px hoch, Text braucht $noetig px", label.height >= noetig)
        }
    }

    // --- Hoechstdauer online (Plan CTR-272 B2, O4) -----------------------------

    private val minute = 60_000L
    private val hoechstens get() = com.chris.whisperloom.Formats.duration(com.chris.whisperloom.ime.DictationSession.MAX_MS)

    /** Online-Aufnahme: die Grenze gilt nur fuer die Online-Erkennung (Fixture rechnet offline). */
    private fun onlineAufnehmen() {
        starten()
        aufnehmen()
        com.chris.whisperloom.Prefs(app).engine = com.chris.whisperloom.Engine.ONLINE
    }

    @Test fun onlineHinweisAbZehnMinuten() {
        onlineAufnehmen()
        warten(10 * minute + 500)
        assertEquals(app.getString(R.string.cd_bubble_recording_long, hoechstens), bubble.stateDescription?.toString())
        assertTrue(label.text.toString(), label.text.endsWith("/ $hoechstens"))
    }

    @Test fun onlineWarnungBei1140UndSendenBei1200() {
        onlineAufnehmen()
        warten(11 * minute + 40_000 + 500)
        assertEquals(app.getString(R.string.cd_bubble_recording_ending, hoechstens), bubble.stateDescription?.toString())
        // Die Warnung wird angesagt (einmal), danach schweigt die Region wieder.
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, bubble.accessibilityLiveRegion)
        assertEquals(app.getColor(R.color.loom_warning), label.currentTextColor)
        warten(1_000)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_NONE, bubble.accessibilityLiveRegion)
        warten(20_000)
        val zustand = bubble.stateDescription?.toString()
        assertTrue(
            "Bei 12:00 nicht gesendet: $zustand",
            zustand != app.getString(R.string.cd_bubble_recording_ending, hoechstens) &&
                zustand != app.getString(R.string.cd_bubble_recording),
        )
    }

    @Test fun offlineKeineHoechstdauer() {
        starten()
        aufnehmen()
        warten(13 * minute)
        assertEquals(app.getString(R.string.cd_bubble_recording), bubble.stateDescription?.toString())
    }
}
