package com.chris.whisperloom.ime

import android.content.Context
import android.graphics.drawable.LevelListDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import com.chris.whisperloom.overlay.BubbleState
import com.chris.whisperloom.overlay.BubbleVisual
import com.chris.whisperloom.overlay.BubbleVisuals
import com.chris.whisperloom.overlay.MicIcon
import com.chris.whisperloom.overlay.MicRings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * keyboard_view.xml laesst sich inflaten (inkl. LevelBandView), alle IDs sind da, keine
 * Emoji-Texte, jede Taste hat eine contentDescription; Mikro-Taste zeigt die vier Zustaende.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeyboardLayoutTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun inflate(): View = LayoutInflater.from(ctx).inflate(R.layout.keyboard_view, null)

    @Test fun alleIdsVorhanden() {
        val v = inflate()
        for (id in listOf(
            R.id.status, R.id.level, R.id.mic_zone, R.id.mic_pulse, R.id.mic_ring, R.id.mic_progress, R.id.mic,
            R.id.key_row, R.id.key_globe, R.id.key_comma, R.id.key_space, R.id.key_period,
            R.id.key_backspace, R.id.key_enter, R.id.key_retry, R.id.key_settings,
            R.id.gesture_discard, R.id.gesture_lock, R.id.key_refine, R.id.refine_row,
            R.id.refine_off, R.id.refine_polish, R.id.refine_beautify, R.id.refine_summarize, R.id.refine_prompt,
        )) {
            assertNotNull("ID fehlt: ${ctx.resources.getResourceEntryName(id)}", v.findViewById<View>(id))
        }
        assertTrue(v.findViewById<View>(R.id.level) is LevelBandView)
        assertEquals(View.GONE, v.findViewById<View>(R.id.key_retry).visibility)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, v.findViewById<View>(R.id.status).accessibilityLiveRegion)
        // Die Wisch-Ziele zeigt erst die Geste — im Ruhezustand ist die Tastatur unveraendert.
        assertEquals(View.GONE, v.findViewById<View>(R.id.gesture_discard).visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.gesture_lock).visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.refine_row).visibility)
    }

    @Test fun wischZieleSindBedienbarGross() {
        val v = inflate()
        val d = ctx.resources.displayMetrics.density
        for (id in listOf(R.id.gesture_discard, R.id.gesture_lock)) {
            val target = v.findViewById<View>(id)
            val name = ctx.resources.getResourceEntryName(id)
            // 48 dp ist das Mindest-Touch-Ziel (UX-Spec §5.6); die Ziele sind 56.
            assertTrue("$name zu schmal", (target.layoutParams.width / d).toInt() >= 48)
            assertTrue("$name zu niedrig", (target.layoutParams.height / d).toInt() >= 48)
            assertFalse("$name ohne contentDescription", target.contentDescription.isNullOrBlank())
        }
    }

    @Test fun keineEmojiUndJedeTasteHatEineBeschreibung() {
        val row = inflate().findViewById<ViewGroup>(R.id.key_row)
        val emoji = listOf("🌐", "⌫", "⏎", "↻", "⚙", "✕")
        for (i in 0 until row.childCount) {
            val key = row.getChildAt(i)
            assertFalse("Taste $i ohne contentDescription", key.contentDescription.isNullOrBlank())
            if (key is Button) {
                val text = key.text.toString()
                assertTrue("Emoji auf Taste: $text", emoji.none { text.contains(it) })
            } else {
                assertTrue("Icon-Taste erwartet", key is ImageButton)
            }
        }
        assertEquals(8, row.childCount)
    }

    /** Misst die Tastatur so breit, wie sie auf einem Geraet mit [breiteDp] waere. */
    private fun messen(v: View, breiteDp: Int) {
        val d = ctx.resources.displayMetrics.density
        v.measure(
            View.MeasureSpec.makeMeasureSpec((breiteDp * d).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }

    @Test fun derSchnellzugriffMachtDieTastaturNichtHoeher() {
        // Gegen das Budget der Spec (§5.3, rund 240 dp), nicht gegen sich selbst — ein
        // Vergleich "eingeklappt == eingeklappt" kann gar nicht fehlschlagen.
        val v = inflate()
        val d = ctx.resources.displayMetrics.density
        messen(v, 360)
        // 8 paddingTop + 41 status + 28 level + 112 mic_zone + 8 marginTop + 52 key_row
        // + 12 paddingBottom. Die Spec nennt "rund 240 dp"; die Statuszeile ist hier
        // zweizeilig, weil der Ruhe-Hinweis umbricht — ein kuerzerer Text macht die
        // Tastatur entsprechend flacher (siehe PR-Hinweis).
        assertEquals("Ruhehoehe der Tastatur", 261, (v.measuredHeight / d).toInt())

        v.findViewById<View>(R.id.refine_row).visibility = View.VISIBLE
        messen(v, 360)
        assertTrue("Ausgeklappt muss die Tastatur hoeher werden", (v.measuredHeight / d).toInt() > 261)
    }

    /**
     * Die Tastenreihe ist bei 360 dp — der haeufigsten Android-Breite — knapp. Die Leertaste
     * ist das einzige Kind mit Gewicht und bekommt, was uebrig bleibt: bei einer Taste zu viel
     * misst LinearLayout sie mit 0 dp, und das letzte Kind ragt aus der Reihe heraus.
     */
    @Test fun dieTastenreihePasstAufEinSchmalesGeraet() {
        val v = inflate()
        val d = ctx.resources.displayMetrics.density
        messen(v, 360)
        val row = v.findViewById<ViewGroup>(R.id.key_row)
        val space = v.findViewById<View>(R.id.key_space)

        assertTrue("Leertaste auf 0 dp gedrueckt — eine Taste zu viel in key_row", space.width > 0)
        val letztes = (0 until row.childCount)
            .map { row.getChildAt(it) }
            .last { it.visibility != View.GONE }
        assertTrue(
            "Letzte Taste ragt aus der Reihe: ${letztes.right} > ${row.width}",
            letztes.right <= row.width,
        )
        // Festgehalten, damit ein Wachsen der Reihe auffaellt: die Leertaste liegt hier schon
        // unter dem 48-dp-Touchziel der Spec §5.6 — Altbestand, nicht durch den Schnellzugriff.
        assertEquals("Leertaste bei 360 dp", 36, (space.width / d).toInt())
    }

    @Test fun masseNachSpec() {
        val v = inflate()
        val d = ctx.resources.displayMetrics.density
        fun dp(id: Int) = (v.findViewById<View>(id).layoutParams.width / d).toInt()
        assertEquals(88, dp(R.id.mic))
        assertEquals(104, dp(R.id.mic_pulse))
        assertEquals(92, dp(R.id.mic_ring))
        assertEquals(96, dp(R.id.mic_progress))
        assertEquals(112, (v.findViewById<View>(R.id.mic_zone).layoutParams.height / d).toInt())
        assertEquals(28, (v.findViewById<View>(R.id.level).layoutParams.height / d).toInt())
        assertEquals(48, (v.findViewById<View>(R.id.key_enter).layoutParams.height / d).toInt())
        assertEquals(52, (v.findViewById<View>(R.id.key_row).layoutParams.height / d).toInt())
        assertEquals(ctx.getString(R.string.cd_mic), v.findViewById<View>(R.id.mic).contentDescription)
    }

    @Test fun mikroTasteZeigtVierZustaende() {
        val v = inflate()
        val mic = v.findViewById<ImageButton>(R.id.mic)
        assertTrue(mic.background is LevelListDrawable)
        val rings = MicRings(
            v.findViewById(R.id.mic_pulse), v.findViewById(R.id.mic_ring), v.findViewById(R.id.mic_progress),
        ) { false }
        for (state in BubbleState.values()) {
            val visual = BubbleVisuals.visualFor(state)
            mic.background.level = ImeMetrics.micFillLevel(visual)
            assertEquals(visual.fill.ordinal, mic.background.level)
            MicIcon.apply(mic, visual, animate = false)
            assertEquals(visual.iconAlpha, mic.alpha)
            rings.show(visual.ring)
        }
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.mic_ring).visibility) // ERROR: statischer Ring
        rings.show(BubbleVisual.Ring.PULSE)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.mic_pulse).visibility)
        rings.show(BubbleVisual.Ring.ARC)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.mic_progress).visibility)
        rings.flashSuccess()
        rings.release()
    }

    @Test fun pegelbandStartetUndStoppt() {
        val band = inflate().findViewById<LevelBandView>(R.id.level)
        assertFalse(band.isActive)
        band.setLevel(0.5f)
        band.start()
        assertTrue(band.isActive)
        band.stop()
        assertFalse(band.isActive)
    }

    @Test fun statuszeileNutztTokens() {
        val status = inflate().findViewById<TextView>(R.id.status)
        assertEquals(ctx.getColor(R.color.loom_onSurfaceVariant), status.currentTextColor)
        assertEquals(ctx.getString(R.string.kb_hint_hold), status.text.toString())
    }
}
