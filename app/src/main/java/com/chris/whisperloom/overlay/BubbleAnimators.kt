package com.chris.whisperloom.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator

/**
 * Baut die Animatoren aus den Werten in [BubbleMotion] (UX-Spec §5.4). Knopf und IME-Taste
 * teilen sie sich. Alle Dauern skaliert das System ueber die Animator-Dauer-Skalierung;
 * bei Reduce-Motion (Skalierung 0) entscheiden die Aufrufer per [reduceMotion] vorher.
 */
object BubbleAnimators {

    /** Reduce-Motion: Animator-Dauer-Skalierung 0 (Bedienungshilfe "Animationen entfernen"). */
    fun reduceMotion(ctx: Context): Boolean = BubbleMotion.isReduceMotion(
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f),
    )

    /**
     * Haptik an [view] — fuer Knopf und IME-Taste gleich. Der Erfolg ist unter API 34 ein
     * Doppelimpuls: der zweite folgt nach [BubbleMotion.SUCCESS_PULSE_GAP_MS] (UX-Spec §6.20).
     */
    fun haptic(view: View, kind: BubbleMotion.Haptic) {
        val sdk = Build.VERSION.SDK_INT
        val constant = BubbleMotion.hapticConstant(kind, sdk)
        view.performHapticFeedback(constant)
        for (i in 1 until BubbleMotion.hapticPulses(kind, sdk)) {
            view.postDelayed({ view.performHapticFeedback(constant) }, i * BubbleMotion.SUCCESS_PULSE_GAP_MS)
        }
    }

    /** Material "LinearOutSlowIn" = cubic-bezier(0, 0, 0.2, 1). */
    fun linearOutSlowIn() = PathInterpolator(0f, 0f, 0.2f, 1f)

    /**
     * Puls-Ring: Alpha 0,45 -> 0, Skalierung 1 -> 1,35 (Start bis +15 % je Pegel), 1200 ms,
     * Restart. [level] wird bei jedem Neustart gelesen.
     */
    fun pulse(ring: View, level: () -> Float): Animator =
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = BubbleMotion.PULSE_DURATION_MS
            interpolator = linearOutSlowIn()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            var startLevel = level()
            addUpdateListener { a ->
                val f = a.animatedFraction
                val scale = BubbleMotion.pulseScale(f, startLevel)
                ring.alpha = BubbleMotion.pulseAlpha(f)
                ring.scaleX = scale
                ring.scaleY = scale
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationRepeat(animation: Animator) {
                    startLevel = level()
                }
            })
        }

    /** Sende-Bogen: eine Umdrehung pro Sekunde, linear, endlos. */
    fun spin(view: View): Animator =
        ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f).apply {
            duration = BubbleMotion.ARC_ROTATION_MS
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
        }

    /** Fehler-Shake: ±6 dp, drei Halbschwingungen, 300 ms. */
    fun shake(view: View): Animator {
        val density = view.resources.displayMetrics.density
        val px = BubbleMotion.shakeOffsetsDp().map { it * density }.toFloatArray()
        return ObjectAnimator.ofFloat(view, View.TRANSLATION_X, *px).apply {
            duration = BubbleMotion.SHAKE_DURATION_MS
        }
    }

    /** Abbrechen-Ziel einblenden: fadeIn + scaleIn(0,8), 200 ms. */
    fun appear(view: View) {
        view.alpha = 0f
        view.scaleX = BubbleMotion.CANCEL_SCALE_START
        view.scaleY = BubbleMotion.CANCEL_SCALE_START
        view.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(BubbleMotion.CANCEL_APPEAR_MS)
            .setInterpolator(linearOutSlowIn())
            .start()
    }
}
