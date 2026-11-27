package com.chris.whisperloom.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.StringRes
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.overlay.ModeCross.CrossTarget

/**
 * Modus-Kreuz als eigenes, nicht beruehrbares Overlay-Fenster (Muster [CancelTarget]): nur waehrend
 * der Geste angehaengt, die Beruehrung bleibt beim Knopf-Fenster. Zeichnet die Ziele aus
 * [ModeCross.layout] (Bildschirm-Pixel) — Pillen mit Kurznamen der Tastatur-Leiste, dazu das ✕.
 * Deckkraft folgt dem Finger ([ModeCross.alpha]), keine Animation. Fuer TalkBack unsichtbar: dort
 * gibt es die Aktionen am Knopf ([BubbleActions]).
 */
class ModeCrossWindow(private val ctx: Context, private val wm: WindowManager) {

    private var view: CrossView? = null

    /** Lage der offenen Geste; null = Kreuz zu. */
    val layout: ModeCross.Layout? get() = view?.layout

    val isShown: Boolean get() = view != null

    fun show(layout: ModeCross.Layout, current: RefineMode) {
        hide()
        val v = CrossView(ctx, layout, current).apply {
            alpha = ModeCross.FAINT_ALPHA
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = WINDOW_TITLE
            // Vollflaechig und nicht beruehrbar: ueber 0,8 Deckkraft blockte Android 12+ Beruehrungen anderer
            // Apps darunter (untrusted touch occlusion). 0,8 laesst sie durch.
            alpha = MAX_OBSCURING_ALPHA
        }
        runCatching { wm.addView(v, lp) }
        view = v
    }

    fun update(alpha: Float, armed: CrossTarget?) {
        val v = view ?: return
        v.alpha = alpha
        if (v.armed != armed) {
            v.armed = armed
            v.invalidate()
        }
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private class CrossView(ctx: Context, val layout: ModeCross.Layout, private val current: RefineMode) : View(ctx) {

        var armed: CrossTarget? = null

        private val dp = resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val close: Drawable? = ctx.getDrawable(R.drawable.ic_close)?.mutate()
        private val labels = layout.targets.associate { placed ->
            placed.target to ((placed.target as? CrossTarget.Mode)?.let { ctx.getString(labelRes(it.mode)) } ?: "")
        }
        private val rect = RectF()
        private val origin = IntArray(2)

        private fun color(id: Int) = context.getColor(id)

        override fun onDraw(canvas: Canvas) {
            getLocationOnScreen(origin)
            for ((target, p) in layout.targets) {
                val x = p.x - origin[0]
                val y = p.y - origin[1]
                if (target == CrossTarget.Discard) drawDiscard(canvas, x, y) else drawPill(canvas, target, x, y)
            }
        }

        private fun drawPill(canvas: Canvas, target: CrossTarget, x: Float, y: Float) {
            val hit = target == armed
            val scale = if (hit) HIT_SCALE else 1f
            val label = labels[target].orEmpty()
            text.textSize = textSize(label, ModeCross.PILL_W_DP * dp - TEXT_PADDING_DP * 2 * dp)
            // Bei sehr grosser Schrift waechst die Pille mit, statt den Namen unter 12 sp zu druecken.
            val width = maxOf(ModeCross.PILL_W_DP * dp, text.measureText(label) + TEXT_PADDING_DP * 2 * dp)
            val hw = width / 2 * scale
            val hh = ModeCross.PILL_H_DP / 2 * dp * scale
            rect.set(x - hw, y - hh, x + hw, y + hh)
            val isCurrent = (target as? CrossTarget.Mode)?.mode == current
            fill.color = color(
                when {
                    hit -> R.color.loom_primaryContainer
                    isCurrent -> R.color.loom_secondaryContainer
                    else -> R.color.loom_surfaceContainerHigh
                },
            )
            canvas.drawRoundRect(rect, hh, hh, fill)
            stroke.color = color(if (isCurrent) R.color.loom_primary else R.color.loom_outline)
            stroke.strokeWidth = (if (isCurrent) 2f else 1f) * dp
            canvas.drawRoundRect(rect, hh, hh, stroke)

            text.color = color(
                when {
                    hit -> R.color.loom_onPrimaryContainer
                    isCurrent -> R.color.loom_onSecondaryContainer
                    else -> R.color.loom_onSurface
                },
            )
            canvas.drawText(label, x, y - (text.ascent() + text.descent()) / 2, text)
        }

        /** 14 sp; passt der Name nicht in die Pille, kleiner bis 12 sp (UX-Spec: kein Text unter 12 sp). */
        private fun textSize(label: String, maxWidth: Float): Float {
            val metrics = resources.displayMetrics
            var size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, TEXT_SP, metrics)
            val min = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, MIN_TEXT_SP, metrics)
            text.textSize = size
            val width = text.measureText(label)
            if (width > maxWidth) size = maxOf(min, size * maxWidth / width)
            return size
        }

        private fun drawDiscard(canvas: Canvas, x: Float, y: Float) {
            val hit = armed == CrossTarget.Discard
            val r = ModeCross.DISCARD_DP / 2 * dp * (if (hit) HIT_SCALE else 1f)
            fill.color = color(if (hit) R.color.loom_errorContainer else R.color.loom_surfaceContainerHigh)
            canvas.drawCircle(x, y, r, fill)
            stroke.color = color(R.color.loom_outline)
            stroke.strokeWidth = dp
            canvas.drawCircle(x, y, r, stroke)
            val icon = close ?: return
            val half = (ICON_DP / 2 * dp).toInt()
            icon.setTint(color(if (hit) R.color.loom_onErrorContainer else R.color.loom_onSurface))
            icon.setBounds(x.toInt() - half, y.toInt() - half, x.toInt() + half, y.toInt() + half)
            icon.draw(canvas)
        }
    }

    companion object {
        /** Fenstertitel in `dumpsys window` (Emulator-Pruefung tools/bubble_emulator_check.py). */
        const val WINDOW_TITLE = "WhisperLoomModeCross"

        /** Hoechste Fenster-Deckkraft, bei der Android Beruehrungen darunter durchlaesst (InputManager-Standard). */
        private const val MAX_OBSCURING_ALPHA = 0.8f
        private const val HIT_SCALE = 1.08f
        private const val TEXT_SP = 14f
        private const val MIN_TEXT_SP = 12f
        private const val TEXT_PADDING_DP = 6f
        private const val ICON_DP = 24f

        /** Kurznamen wie in der Tastatur-Leiste, damit beide Wege gleich heissen. */
        @StringRes
        fun labelRes(mode: RefineMode): Int = when (mode) {
            RefineMode.OFF -> R.string.kb_level_off
            RefineMode.POLISH, RefineMode.READABLE -> R.string.kb_level_smooth
            RefineMode.BEAUTIFY -> R.string.kb_level_beautify
            RefineMode.SUMMARIZE -> R.string.kb_level_summarize
            RefineMode.PROMPT -> R.string.kb_level_prompt
        }
    }
}
