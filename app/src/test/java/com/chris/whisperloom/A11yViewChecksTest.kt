package com.chris.whisperloom

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.agent.VoiceTaskState
import com.chris.whisperloom.agent.VoiceTaskWidgetView
import com.chris.whisperloom.ime.WhisperLoomInputMethodService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Der a11y-Pruef-Helfer selbst (je Regel ein Fall) und seine Anwendung auf die IME-Tastatur und das
 * Widget. Den verdrahteten schwebenden Knopf prueft `overlay/FloatingMicA11yTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class A11yViewChecksTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    /** Eine Zeile mit festen Kindern — ohne Gewichte, damit die Groessen genau so ankommen. */
    private fun reihe(vararg kinder: View) = LinearLayout(ctx).apply { kinder.forEach { addView(it) } }

    private fun knopf(text: String = "OK", breite: Int = 48, hoehe: Int = 48) = Button(ctx).apply {
        this.text = text
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(dp(breite), dp(hoehe))
        setOnClickListener { }
    }

    private fun befunde(root: View, actions: Map<View, List<String>> = emptyMap()): List<String> {
        A11yViewChecks.layout(root)
        return A11yViewChecks.findings(root, actions)
    }

    // --- Regeln ---------------------------------------------------------------

    @Test fun beschrifteterKnopfMit48DpIstInOrdnung() {
        assertEquals(emptyList<String>(), befunde(reihe(knopf())))
    }

    @Test fun zuSchmalOderZuNiedrigZaehltBeides() {
        val b = befunde(reihe(knopf(breite = 44), knopf(hoehe = 40)))
        assertEquals(2, b.size)
        assertTrue(b[0], b[0].endsWith("44 × 48 dp < 48 dp"))
        assertTrue(b[1], b[1].endsWith("48 × 40 dp < 48 dp"))
    }

    @Test fun ohneTextUndBeschreibung() {
        val b = befunde(reihe(knopf(text = "")))
        assertEquals(listOf("Button: ohne Text und Beschreibung"), b)
    }

    @Test fun beschreibungReichtOhneText() {
        val k = knopf(text = "").apply { contentDescription = "Senden" }
        assertEquals(emptyList<String>(), befunde(reihe(k)))
    }

    @Test fun touchListenerOhneKlickpfad() {
        val v = View(ctx).apply {
            contentDescription = "Mikrofon"
            layoutParams = LinearLayout.LayoutParams(dp(88), dp(88))
            setOnTouchListener { _, _ -> true }
        }
        val root = reihe(v)
        assertEquals(listOf("View: OnTouchListener ohne Klickpfad"), befunde(root))
        v.setOnClickListener { }
        assertEquals(emptyList<String>(), befunde(root))
    }

    @Test fun fehlendeGestenAktion() {
        val k = knopf()
        val root = reihe(k)
        assertEquals(listOf("Button: Aktion „Verwerfen“ fehlt"), befunde(root, mapOf(k to listOf("Verwerfen"))))
        ViewCompat.addAccessibilityAction(k, "Verwerfen") { _, _ -> true }
        assertEquals(emptyList<String>(), befunde(root, mapOf(k to listOf("Verwerfen"))))
    }

    @Test fun verborgeneElementeZaehlenNicht() {
        val klein = knopf(text = "", breite = 10).apply { visibility = View.GONE }
        val inUnsichtbaremEltern = FrameLayout(ctx).apply {
            visibility = View.INVISIBLE
            addView(knopf(text = "", breite = 10))
        }
        assertEquals(emptyList<String>(), befunde(reihe(knopf(), klein, inUnsichtbaremEltern)))
    }

    @Test fun schriftgroesseGiltNurImBlock() {
        val skala = A11yViewChecks.withFontScale(2f) { ctx.resources.configuration.fontScale }
        assertEquals(2f, skala)
        assertEquals(1f, ctx.resources.configuration.fontScale)
    }

    // --- Anwendung ------------------------------------------------------------

    /** Ruhezustand der Tastatur bei 360 dp, je Schriftgroesse. */
    private fun tastaturBefunde(scale: Float): List<String> = A11yViewChecks.withFontScale(scale) {
        val controller = Robolectric.buildService(WhisperLoomInputMethodService::class.java).create()
        try {
            befunde(controller.get().onCreateInputView())
        } finally {
            controller.destroy()
        }
    }

    /**
     * Bekannte Luecke, bis die Tastenreihe ueberarbeitet ist (Plan CTR-272 B3, PR 1b): die Tasten
     * sind schmaler als 48 dp (T3), und die Statuszeile ist als Tippziel (Warnung -> Assistent)
     * niedriger. B3 ersetzt diesen Test durch "keine Befunde". Alles andere waere neu und faellt auf.
     */
    private val bekannteLuecke = setOf(
        "status", "key_globe", "key_comma", "key_space", "key_period", "key_backspace", "key_enter", "key_settings",
    )

    @Test fun tastaturHatNurDieBekannteLuecke() {
        for (scale in A11yViewChecks.FONT_SCALES) {
            val neu = tastaturBefunde(scale).filterNot { it.substringBefore(':') in bekannteLuecke && it.endsWith("< 48 dp") }
            assertEquals("fontScale $scale", emptyList<String>(), neu)
        }
        // Der Helfer sieht die Luecke wirklich (Rot-Lauf ohne diese Ausnahme: siehe PR).
        val heute = tastaturBefunde(1f)
        assertTrue(heute.toString(), heute.contains("key_space: 36 × 48 dp < 48 dp"))
        assertTrue(heute.toString(), heute.contains("key_comma: 44 × 48 dp < 48 dp"))
    }

    /** Das Widget in jeder Lage, platziert in seiner Startgroesse 110 × 110 dp. */
    @Test fun widget() {
        for (scale in A11yViewChecks.FONT_SCALES) {
            A11yViewChecks.withFontScale(scale) {
                for (state in VoiceTaskState.entries) {
                    val parent = FrameLayout(ctx)
                    parent.addView(VoiceTaskWidgetView.build(ctx, state, 7_000, "Grund").apply(ctx, parent))
                    A11yViewChecks.layout(parent, 110, 110)
                    assertEquals("fontScale $scale, $state", emptyList<String>(), A11yViewChecks.findings(parent))
                }
            }
        }
    }
}
