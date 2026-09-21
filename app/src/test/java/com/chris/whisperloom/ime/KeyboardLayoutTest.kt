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
            R.id.gesture_discard, R.id.gesture_lock,
        )) {
            assertNotNull("ID fehlt: ${ctx.resources.getResourceEntryName(id)}", v.findViewById<View>(id))
        }
        assertTrue(v.findViewById<View>(R.id.level) is LevelBandView)
        assertEquals(View.GONE, v.findViewById<View>(R.id.key_retry).visibility)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, v.findViewById<View>(R.id.status).accessibilityLiveRegion)
        // Die Wisch-Ziele zeigt erst die Geste — im Ruhezustand ist die Tastatur unveraendert.
        assertEquals(View.GONE, v.findViewById<View>(R.id.gesture_discard).visibility)
        assertEquals(View.GONE, v.findViewById<View>(R.id.gesture_lock).visibility)
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
