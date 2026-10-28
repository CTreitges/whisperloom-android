package com.chris.whisperloom.overlay

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperloom.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Die Overlay-Layouts lassen sich inflaten (alle Drawables/Farben aufloesbar), tragen die
 * IDs, die Service/Renderer/CancelTarget suchen, und der Renderer zeichnet jeden Zustand
 * ohne Exception. Robolectric LEGACY-Grafik: keine Pixel, nur Struktur.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverlayLayoutsTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun inflate(res: Int): View = LayoutInflater.from(ctx).inflate(res, null)

    @Test fun floatingMicHatAlleIds() {
        val v = inflate(R.layout.floating_mic)
        for (id in listOf(
            R.id.bubble_frame, R.id.pulse_ring, R.id.state_ring, R.id.bubble_progress,
            R.id.bubble, R.id.bubble_fill_back, R.id.bubble_fill, R.id.bubble_icon, R.id.bubble_label,
        )) {
            assertNotNull("ID fehlt: ${ctx.resources.getResourceEntryName(id)}", v.findViewById<View>(id))
        }
        // Nur der Knopf ist fokussierbar; Label und Ringe sind fuer TalkBack unsichtbar.
        val bubble = v.findViewById<View>(R.id.bubble)
        assertTrue(bubble.isClickable)
        assertTrue(bubble.isFocusable)
        assertEquals(ctx.getString(R.string.cd_bubble), bubble.contentDescription)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, v.findViewById<View>(R.id.bubble_label).importantForAccessibility)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, v.findViewById<View>(R.id.pulse_ring).importantForAccessibility)
    }

    @Test fun floatingMicMasseNachSpec() {
        val v = inflate(R.layout.floating_mic)
        val d = ctx.resources.displayMetrics.density
        fun dp(id: Int) = (v.findViewById<View>(id).layoutParams.width / d).toInt()
        assertEquals(96, dp(R.id.bubble_frame))
        assertEquals(84, dp(R.id.pulse_ring))
        assertEquals(72, dp(R.id.state_ring))
        assertEquals(76, dp(R.id.bubble_progress))
        assertEquals(68, dp(R.id.bubble))
        assertEquals(28, dp(R.id.bubble_icon))
        assertEquals("Label waechst mit der Schrift (§6.20)", ViewGroup.LayoutParams.WRAP_CONTENT, v.findViewById<View>(R.id.bubble_label).layoutParams.height)
        assertEquals(22, (v.findViewById<View>(R.id.bubble_label).minimumHeight / d).toInt())
        assertEquals("tnum", v.findViewById<TextView>(R.id.bubble_label).fontFeatureSettings)
    }

    @Test fun rendererZeichnetJedenZustandOhneException() {
        val v = inflate(R.layout.floating_mic)
        val r = BubbleRenderer(v) { false }
        val label = v.findViewById<TextView>(R.id.bubble_label)
        val bubble = v.findViewById<View>(R.id.bubble)

        r.render(BubbleVisuals.visualFor(BubbleState.IDLE), 0)
        assertEquals(View.GONE, label.visibility)
        assertEquals(ctx.getString(R.string.cd_bubble_idle), bubble.stateDescription?.toString())

        r.render(BubbleVisuals.visualFor(BubbleState.RECORDING), 7_000)
        assertEquals(View.VISIBLE, label.visibility)
        assertEquals("● 0:07", label.text.toString())
        assertEquals(ctx.getString(R.string.cd_bubble_recording), bubble.stateDescription?.toString())
        assertEquals(ctx.getString(R.string.cd_bubble_timer, "0:07"), bubble.contentDescription)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.pulse_ring).visibility)
        r.updateTimer(65_000)
        assertEquals("● 1:05", label.text.toString())
        assertEquals(ctx.getString(R.string.cd_bubble_recording), bubble.stateDescription?.toString())
        assertEquals(ctx.getString(R.string.cd_bubble_timer, "1:05"), bubble.contentDescription)

        r.render(BubbleVisuals.visualFor(BubbleState.SENDING), 0)
        assertEquals(ctx.getString(R.string.float_sending), label.text.toString())
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.bubble_progress).visibility)
        assertEquals(ctx.getString(R.string.cd_bubble_sending), bubble.stateDescription?.toString())

        r.render(BubbleVisuals.visualFor(BubbleState.ERROR), 0)
        assertEquals(ctx.getString(R.string.float_retry_hint), label.text.toString())
        assertEquals(ctx.getString(R.string.cd_bubble_error), bubble.stateDescription?.toString())
        assertEquals(View.GONE, v.findViewById<View>(R.id.bubble_progress).visibility)

        r.render(BubbleVisuals.visualFor(BubbleState.IDLE, copiedHint = true), 0)
        assertEquals(ctx.getString(R.string.float_copied_short), label.text.toString())
        assertEquals(ctx.getString(R.string.cd_bubble_copied), bubble.stateDescription?.toString())

        r.render(BubbleVisuals.visualFor(BubbleState.IDLE, insertedHint = true), 0)
        assertEquals(ctx.getString(R.string.float_inserted_short), label.text.toString())
        assertEquals(ctx.getString(R.string.cd_bubble_inserted), bubble.stateDescription?.toString())
        assertEquals("Der Name bleibt fest", ctx.getString(R.string.cd_bubble), bubble.contentDescription)

        r.flashSuccess()
        r.haptic(BubbleMotion.Haptic.SUCCESS)
        r.shake()
        r.release()
    }

    @Test fun rendererMitReduceMotionZeigtStatischenRing() {
        val v = inflate(R.layout.floating_mic)
        val r = BubbleRenderer(v) { true }
        r.render(BubbleVisuals.visualFor(BubbleState.RECORDING, reduceMotion = true), 0)
        assertEquals(View.INVISIBLE, v.findViewById<View>(R.id.pulse_ring).visibility)
        assertEquals(View.VISIBLE, v.findViewById<View>(R.id.state_ring).visibility)
        r.release()
    }

    @Test fun floatingCancelHatAlleIdsUndMasse() {
        val v = inflate(R.layout.floating_cancel)
        val d = ctx.resources.displayMetrics.density
        val target = v.findViewById<View>(R.id.cancel_target)
        assertNotNull(target)
        assertNotNull(v.findViewById<View>(R.id.cancel_ring))
        assertNotNull(v.findViewById<ImageView>(R.id.cancel_icon))
        val label = v.findViewById<TextView>(R.id.cancel_label)
        assertEquals(72, (target.layoutParams.width / d).toInt())
        assertEquals(28, (v.findViewById<View>(R.id.cancel_icon).layoutParams.width / d).toInt())
        assertEquals(ctx.getString(R.string.cd_cancel), target.contentDescription)
        assertEquals(ctx.getString(R.string.float_cancel_label), label.text.toString())
        // Kein Emoji-Glyph mehr als Text — das Icon ist ic_close.
        assertFalse(v is TextView)
    }
}
