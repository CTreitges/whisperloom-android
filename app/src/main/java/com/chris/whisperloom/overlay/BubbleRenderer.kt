package com.chris.whisperloom.overlay

import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.chris.whisperloom.Formats
import com.chris.whisperloom.R
import com.chris.whisperloom.RecordingLimit
import com.chris.whisperloom.ime.DictationSession
import kotlin.math.roundToInt

/**
 * Zeichnet eine [BubbleVisual] auf das Layout `floating_mic.xml` (UX-Spec §5.1/§5.4):
 * Fuellung (mit 200-ms-Crossfade), Ringe ([MicRings]), Icon ([MicIcon]), Label-Pille,
 * stateDescription und Live-Region. Kennt keine Zustandslogik — die bleibt im [FloatingMicService].
 */
class BubbleRenderer(root: View, private val reduceMotion: () -> Boolean) {

    /** 96-dp-Container (Puls-Ring, Zustandsring, Bogen, Knopf) — Bezugsrahmen fuer das Abbrechen-Ziel. */
    val frame: View = root.findViewById(R.id.bubble_frame)

    /** Der 68-dp-Knopf: Touch-Ziel und einziges TalkBack-Element (Name fest `cd_bubble`, Layout). */
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

    /** [quiet]: der Wechsel wird nicht angesagt (z. B. ein Hinweis laeuft ab, der Zustand bleibt IDLE). */
    fun render(visual: BubbleVisual, elapsedMs: Long, quiet: Boolean = false) {
        val previous = current
        current = visual
        limitStage = RecordingLimit.Stage.OK
        if (previous?.fill != visual.fill) setFill(visual.fill, animate = previous != null)
        if (previous?.icon != visual.icon || previous.iconTint != visual.iconTint ||
            previous.iconAlpha != visual.iconAlpha
        ) {
            MicIcon.apply(icon, visual, animate = previous != null && !reduceMotion())
        }
        if (previous?.ring != visual.ring) rings.show(visual.ring)
        renderLabel(visual, elapsedMs)
        // Erst die Live-Region, dann der Text: so wird der Wechsel noch angesagt.
        announceAs(visual, quiet)
        describe(visual, elapsedMs)
    }

    /**
     * Sekundentakt waehrend der Aufnahme: Timer-Text, Blink-Punkt, Zeit im Namen. Ab der ersten
     * vollen Sekunde schweigt die Live-Region — sonst laese TalkBack jede Sekunde in das eigene
     * Diktat hinein (wie die Statuszeile der IME). Den Aufnahmestart hat sie davor noch gemeldet.
     */
    fun updateTimer(elapsedMs: Long, stage: RecordingLimit.Stage = RecordingLimit.Stage.OK) {
        val visual = current ?: return
        if (visual.label != BubbleVisual.Label.TIMER) return
        // Hoechstdauer online (Plan CTR-272 B2): Hinweis und Warnung stehen im Zustand und werden
        // je einmal angesagt — nur fuer diesen einen Takt ist die Region wieder an.
        val announce = stage != limitStage && stage != RecordingLimit.Stage.OK
        limitStage = stage
        bubble.accessibilityLiveRegion = when {
            announce -> View.ACCESSIBILITY_LIVE_REGION_POLITE
            elapsedMs >= 1_000 -> View.ACCESSIBILITY_LIVE_REGION_NONE
            else -> bubble.accessibilityLiveRegion
        }
        label.text = timerSpan(elapsedMs)
        label.setTextColor(
            ctx.getColor(if (stage == RecordingLimit.Stage.WARN) R.color.loom_warning else R.color.loom_recordingText),
        )
        describe(visual, elapsedMs)
    }

    /** Zuletzt gezeigte Stufe der Hoechstdauer; ausserhalb der Aufnahme OK. */
    var limitStage = RecordingLimit.Stage.OK
        private set

    fun flashSuccess() = rings.flashSuccess()

    fun shake() {
        if (!reduceMotion()) BubbleAnimators.shake(frame).start()
    }

    fun haptic(kind: BubbleMotion.Haptic) = BubbleAnimators.haptic(bubble, kind)

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
            BubbleVisual.Label.INSERTED -> label.setText(R.string.float_inserted_short)
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
        // Ab dem Hinweis steht die Grenze dabei: "● 10:03 / 12:00".
        val text = BubbleUi.timerText(elapsedMs) +
            if (limitStage == RecordingLimit.Stage.OK) "" else " / ${max()}"
        return SpannableString(text).apply {
            setSpan(ForegroundColorSpan(dotColor), 0, BubbleUi.DOT.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    // --- Barrierefreiheit -----------------------------------------------------

    /**
     * Ansage ohne das abgekuendigte `announceForAccessibility` (UX-Spec §6.20): Der Knopf ist eine
     * Live-Region, im Fehler `assertive`. Sie meldet nur echte Wechsel, denn die stateDescription
     * aendert sich nur dann — gleicher Text, kein Ereignis.
     */
    private fun announceAs(visual: BubbleVisual, quiet: Boolean) {
        bubble.accessibilityLiveRegion = when {
            quiet -> View.ACCESSIBILITY_LIVE_REGION_NONE
            visual.description == BubbleVisual.Description.ERROR -> View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
            else -> View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
    }

    /**
     * Was gerade passiert und was ein Tipp tut, sagt die stateDescription; der Name ist "Mikrofon".
     * Die laufende Zeit steht im Namen, nicht im Zustand: Aendert sich die stateDescription des
     * fokussierten Elements, liest TalkBack sie vor (wie bei einem Schieberegler) — mit der Zeit darin
     * waere das eine Ansage pro Sekunde, Live-Region hin oder her.
     */
    private fun describe(visual: BubbleVisual, elapsedMs: Long) {
        bubble.contentDescription = if (visual.description == BubbleVisual.Description.RECORDING) {
            ctx.getString(R.string.cd_bubble_timer, BubbleUi.formatDuration(elapsedMs))
        } else {
            ctx.getString(R.string.cd_bubble)
        }
        ViewCompat.setStateDescription(
            bubble,
            when (visual.description) {
                BubbleVisual.Description.IDLE -> ctx.getString(R.string.cd_bubble_idle)
                BubbleVisual.Description.RECORDING -> when (limitStage) {
                    RecordingLimit.Stage.HINT -> ctx.getString(R.string.cd_bubble_recording_long, max())
                    RecordingLimit.Stage.WARN -> ctx.getString(R.string.cd_bubble_recording_ending, max())
                    else -> ctx.getString(R.string.cd_bubble_recording)
                }
                BubbleVisual.Description.SENDING -> ctx.getString(R.string.cd_bubble_sending)
                BubbleVisual.Description.REFINING -> ctx.getString(R.string.cd_bubble_refining)
                BubbleVisual.Description.ERROR -> ctx.getString(R.string.cd_bubble_error)
                BubbleVisual.Description.COPIED -> ctx.getString(R.string.cd_bubble_copied)
                BubbleVisual.Description.INSERTED -> ctx.getString(R.string.cd_bubble_inserted)
            },
        )
    }

    private fun max() = Formats.duration(DictationSession.MAX_MS)

    companion object {
        /** 92 % von 255. */
        val LABEL_BG_ALPHA = (0.92f * 255).roundToInt()
    }
}
