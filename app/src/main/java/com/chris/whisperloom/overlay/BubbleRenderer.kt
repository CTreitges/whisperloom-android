package com.chris.whisperloom.overlay

import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import com.chris.whisperloom.R
import kotlin.math.roundToInt

/**
 * Zeichnet eine [BubbleVisual] auf das Layout `floating_mic.xml` (UX-Spec §5.1/§5.4):
 * Fuellung (mit 200-ms-Crossfade), Ringe ([MicRings]), Icon ([MicIcon]), Label-Pille,
 * contentDescription. Kennt keine Zustandslogik — die bleibt im [FloatingMicService].
 */
class BubbleRenderer(root: View, private val reduceMotion: () -> Boolean) {

    /** 96-dp-Container (Puls-Ring, Zustandsring, Bogen, Knopf) — Bezugsrahmen fuer das Abbrechen-Ziel. */
    val frame: View = root.findViewById(R.id.bubble_frame)

    /** Der 68-dp-Knopf: Touch-Ziel und einziges TalkBack-Element. */
    val bubble: View = root.findViewById(R.id.bubble)

    private val ctx = root.context
    private val fillBack: View = root.findViewById(R.id.bubble_fill_back)
    private val fill: View = root.findViewById(R.id.bubble_fill)
    private val icon: ImageView = root.findViewById(R.id.bubble_icon)
    private val label: TextView = root.findViewById(R.id.bubble_label)
    private val rings = MicRings(
        pulse = root.findViewById(R.id.pulse_ring),
        ring = root.findViewById(R.id.state_ring),
        arc = root.findViewById(R.id.bubble_progress),
        reduceMotion = reduceMotion,
    )

    private var current: BubbleVisual? = null
    private var currentFillRes = R.drawable.bubble_fill_idle
    private var currentFillAlpha = BubbleVisuals.IDLE_FILL_ALPHA

    init {
        // Der Knopf-Container hat keinen Hintergrund (zwei Fuell-Ebenen darin) — fuer den
        // 8-dp-Schatten braucht er deshalb einen expliziten ovalen Outline.
        bubble.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
    }

    /** Pegel 0..1 fuer den Puls-Startradius. */
    var level: Float
        get() = rings.level
        set(value) { rings.level = value }

    fun render(visual: BubbleVisual, elapsedMs: Long) {
        val previous = current
        current = visual
        if (previous?.fill != visual.fill) setFill(visual.fill, animate = previous != null)
        if (previous?.icon != visual.icon || previous.iconTint != visual.iconTint ||
            previous.iconAlpha != visual.iconAlpha
        ) {
            MicIcon.apply(icon, visual, animate = previous != null && !reduceMotion())
        }
        if (previous?.ring != visual.ring) rings.show(visual.ring)
        renderLabel(visual, elapsedMs)
        describe(visual, elapsedMs)
    }

    /** Sekundentakt waehrend der Aufnahme: Timer-Text, Blink-Punkt, contentDescription. */
    fun updateTimer(elapsedMs: Long) {
        val visual = current ?: return
        if (visual.label != BubbleVisual.Label.TIMER) return
        label.text = timerSpan(elapsedMs)
        describe(visual, elapsedMs)
    }

    /** TalkBack: aktuellen Zustand ansagen (nur bei echtem Zustandswechsel aufrufen). */
    fun announce() {
        bubble.announceForAccessibility(bubble.contentDescription ?: return)
    }

    fun flashSuccess() = rings.flashSuccess()

    fun shake() {
        if (!reduceMotion()) BubbleAnimators.shake(frame).start()
    }

    fun haptic(kind: BubbleMotion.Haptic) {
        bubble.performHapticFeedback(BubbleMotion.hapticConstant(kind, Build.VERSION.SDK_INT))
    }

    /** Lage des 96-dp-Containers relativ zum Overlay-Fenster. */
    fun frameRect(): Rect = Rect(frame.left, frame.top, frame.right, frame.bottom)

    fun release() = rings.release()

    // --- Fuellung -------------------------------------------------------------

    private fun setFill(kind: BubbleVisual.Fill, animate: Boolean) {
        val res = fillRes(kind)
        val alpha = if (kind == BubbleVisual.Fill.SURFACE) BubbleVisuals.IDLE_FILL_ALPHA else 1f
        fill.animate().cancel()
        if (!animate || reduceMotion()) {
            fillBack.alpha = 0f
            fill.setBackgroundResource(res)
            fill.alpha = alpha
        } else {
            // Crossfade: die bisherige Fuellung liegt hinten, die neue blendet davor ein.
            fillBack.setBackgroundResource(currentFillRes)
            fillBack.alpha = currentFillAlpha
            fill.setBackgroundResource(res)
            fill.alpha = 0f
            fill.animate().alpha(alpha)
                .setDuration(BubbleMotion.FILL_CROSSFADE_MS)
                .withEndAction { fillBack.alpha = 0f }
                .start()
        }
        currentFillRes = res
        currentFillAlpha = alpha
    }

    private fun fillRes(kind: BubbleVisual.Fill): Int = when (kind) {
        BubbleVisual.Fill.SURFACE -> R.drawable.bubble_fill_idle
        BubbleVisual.Fill.RECORDING -> R.drawable.bubble_fill_recording
        BubbleVisual.Fill.PRIMARY_CONTAINER -> R.drawable.bubble_fill_sending
        BubbleVisual.Fill.ERROR_CONTAINER -> R.drawable.bubble_fill_error
    }

    // --- Label ----------------------------------------------------------------

    private fun renderLabel(visual: BubbleVisual, elapsedMs: Long) {
        when (visual.label) {
            BubbleVisual.Label.NONE -> {
                label.visibility = View.GONE
                return
            }
            BubbleVisual.Label.TIMER -> label.text = timerSpan(elapsedMs)
            BubbleVisual.Label.SENDING -> label.setText(R.string.float_sending)
            BubbleVisual.Label.REFINING -> label.setText(R.string.float_refining)
            BubbleVisual.Label.RETRY_HINT -> label.setText(R.string.float_retry_hint)
            BubbleVisual.Label.COPIED -> label.setText(R.string.float_copied_short)
        }
        val error = visual.labelStyle == BubbleVisual.LabelStyle.ERROR
        label.setBackgroundResource(if (error) R.drawable.label_bg_error else R.drawable.label_bg)
        // Neutrale Pille 92 % (§5.1); die Fehler-Pille bleibt deckend.
        label.background.mutate().alpha = if (error) 255 else LABEL_BG_ALPHA
        label.setTextColor(
            ctx.getColor(
                when (visual.labelStyle) {
                    BubbleVisual.LabelStyle.NEUTRAL -> R.color.loom_onSurfaceVariant
                    BubbleVisual.LabelStyle.RECORDING -> R.color.loom_recordingText
                    BubbleVisual.LabelStyle.ERROR -> R.color.loom_onErrorContainer
                },
            ),
        )
        label.visibility = View.VISIBLE
    }

    /** "● m:ss" — der Punkt blinkt mit 1 Hz (Aus-Phase transparent), Text in recordingText. */
    private fun timerSpan(elapsedMs: Long): CharSequence {
        val dotColor = if (BubbleUi.dotVisible(elapsedMs)) ctx.getColor(R.color.loom_recording) else Color.TRANSPARENT
        return SpannableString(BubbleUi.timerText(elapsedMs)).apply {
            setSpan(ForegroundColorSpan(dotColor), 0, BubbleUi.DOT.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    // --- Barrierefreiheit -----------------------------------------------------

    private fun describe(visual: BubbleVisual, elapsedMs: Long) {
        bubble.contentDescription = when (visual.description) {
            BubbleVisual.Description.IDLE -> ctx.getString(R.string.cd_bubble_idle)
            BubbleVisual.Description.RECORDING ->
                ctx.getString(R.string.cd_bubble_recording, BubbleUi.formatDuration(elapsedMs))
            BubbleVisual.Description.SENDING -> ctx.getString(R.string.cd_bubble_sending)
            BubbleVisual.Description.REFINING -> ctx.getString(R.string.cd_bubble_refining)
            BubbleVisual.Description.ERROR -> ctx.getString(R.string.cd_bubble_error)
        }
    }

    companion object {
        /** 92 % von 255. */
        val LABEL_BG_ALPHA = (0.92f * 255).roundToInt()
    }
}
