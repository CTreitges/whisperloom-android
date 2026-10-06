package com.chris.whisperloom.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.chris.whisperloom.R
import kotlin.math.roundToInt

/**
 * Abbrechen-Ziel (UX-Spec §5.2): eigenes, nicht beruehrbares Overlay-Fenster unten mittig
 * plus Scrim-Fenster (Verlauf ueber dem unteren Bildschirmviertel). Erscheint beim Ziehen in
 * RECORDING/ERROR; im Magnet-Radius waechst der Kreis auf 1,12 und faerbt sich errorContainer.
 */
class CancelTarget(private val ctx: Context, private val wm: WindowManager) {

    private var root: View? = null
    private var scrim: View? = null
    private var circle: View? = null
    private var icon: ImageView? = null
    private var label: TextView? = null
    private var circleTop = 0

    /** Ob der Knopf gerade im Magnet-Radius liegt (Optik "Loslassen zum Verwerfen"). */
    var isHit = false
        private set

    val isShown: Boolean get() = root != null

    @SuppressLint("InflateParams") // Overlay-Fenster ohne Elternteil; die LayoutParams setzt wm.addView.
    fun show() {
        if (root != null) return
        val dm = ctx.resources.displayMetrics
        val size = (BubblePosition.CANCEL_SIZE_DP * dm.density).roundToInt()
        showScrim(dm.heightPixels / 4)

        val v = LayoutInflater.from(ctx).inflate(R.layout.floating_cancel, null)
        circle = v.findViewById(R.id.cancel_target)
        icon = v.findViewById(R.id.cancel_icon)
        label = v.findViewById(R.id.cancel_label)
        circle?.background?.mutate()?.alpha = FILL_ALPHA

        // Kreis-Unterkante 96 dp ueber dem Rand; das Label haengt darunter in den Rand hinein.
        circleTop = dm.heightPixels - (BubblePosition.CANCEL_MARGIN_DP * dm.density).roundToInt() - size
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Horizontal zentriert das Fenster selbst — so stimmt die Mitte auch bei breitem Label.
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = circleTop
        }
        runCatching { wm.addView(v, lp) }
        root = v
        isHit = false
        BubbleAnimators.appear(v)
    }

    /** Kreis in Bildschirm-Pixeln (fuer BubblePosition.isOverCancel). Null, wenn nicht sichtbar. */
    fun circleRect(): Rect? {
        if (root == null) return null
        val dm = ctx.resources.displayMetrics
        val size = (BubblePosition.CANCEL_SIZE_DP * dm.density).roundToInt()
        val left = (dm.widthPixels - size) / 2
        return Rect(left, circleTop, left + size, circleTop + size)
    }

    fun setHit(hit: Boolean) {
        if (hit == isHit) return
        isHit = hit
        val c = circle ?: return
        val scale = if (hit) BubbleMotion.CANCEL_HIT_SCALE else 1f
        c.animate().scaleX(scale).scaleY(scale).setDuration(HIT_SCALE_MS).start()
        c.setBackgroundResource(if (hit) R.drawable.cancel_fill_hit else R.drawable.cancel_fill)
        c.background.mutate().alpha = if (hit) 255 else FILL_ALPHA
        icon?.imageTintList = ColorStateList.valueOf(
            ctx.getColor(if (hit) R.color.loom_onErrorContainer else R.color.loom_onSurface),
        )
        label?.setText(if (hit) R.string.float_cancel_release else R.string.float_cancel_label)
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        circle = null
        icon = null
        label = null
        scrim?.let { runCatching { wm.removeView(it) } }
        scrim = null
        isHit = false
    }

    private fun showScrim(heightPx: Int) {
        val v = View(ctx).apply {
            setBackgroundResource(R.drawable.cancel_scrim)
            alpha = 0f
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.BOTTOM }
        runCatching { wm.addView(v, lp) }
        v.animate().alpha(1f).setDuration(BubbleMotion.CANCEL_APPEAR_MS).start()
        scrim = v
    }

    companion object {
        /** Fuellung 92 % (§5.2). */
        val FILL_ALPHA = (0.92f * 255).roundToInt()
        private const val HIT_SCALE_MS = 100L
    }
}
