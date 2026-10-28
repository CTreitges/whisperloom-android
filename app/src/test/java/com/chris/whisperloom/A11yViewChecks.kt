package com.chris.whisperloom

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import kotlin.math.roundToInt

/**
 * a11y-Pruef-Helfer fuer View-Baeume, ohne neue Abhaengigkeit (UX-Spec §5.6, §6.20). Geprueft
 * wird, was ein Bildschirmleser bzw. eine Bedienung ohne Ziehen braucht:
 *
 * - Jedes sichtbare klickbare Element hat Text oder `contentDescription`.
 * - Es ist mindestens 48 × 48 dp gross — Breite **und** Hoehe.
 * - Ein `OnTouchListener` hat einen Klickpfad (`OnClickListener`); TalkBacks Doppeltipp und Switch
 *   Access kommen nur ueber den Klick an.
 * - Gesten-Funktionen (Verwerfen, Senden, Feststellen) stehen als Bedienungshilfe-Aktion in
 *   `createAccessibilityNodeInfo().actionList`.
 *
 * Aktionen und Listener setzt oft erst der Dienst — deshalb den verdrahteten View-Baum pruefen,
 * nicht das blosse Layout.
 */
object A11yViewChecks {

    const val MIN_TOUCH_DP = 48

    /** Haeufigste schmale Android-Breite. */
    const val WIDTH_DP = 360

    /** Normale, grosse und groesste uebliche Schrift. */
    val FONT_SCALES = listOf(1f, 1.3f, 2f)

    // Robolectric 4.16.1: RuntimeEnvironment.setFontScale(float) — per javap gegen
    // robolectric-4.16.1.jar geprueft (Context7 war in dieser Session nicht verfuegbar).
    /** [block] bei Schriftgroesse [scale]; danach wieder 1. Vor dem Inflaten bzw. Dienst-Start setzen. */
    fun <T> withFontScale(scale: Float, block: () -> T): T {
        RuntimeEnvironment.setFontScale(scale)
        try {
            return block()
        } finally {
            RuntimeEnvironment.setFontScale(1f)
        }
    }

    /** Misst [root] auf [widthDp] Breite und [heightDp] Hoehe (null = nach Bedarf) und legt ihn aus. */
    fun layout(root: View, widthDp: Int = WIDTH_DP, heightDp: Int? = null) {
        val d = root.resources.displayMetrics.density
        root.measure(
            View.MeasureSpec.makeMeasureSpec((widthDp * d).roundToInt(), View.MeasureSpec.EXACTLY),
            if (heightDp == null) {
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            } else {
                View.MeasureSpec.makeMeasureSpec((heightDp * d).roundToInt(), View.MeasureSpec.EXACTLY)
            },
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
    }

    /**
     * Befunde unter [root] (vorher [layout]); leer = in Ordnung. [actions] nennt je View die
     * Beschriftungen der Bedienungshilfe-Aktionen, die eine Geste ersetzen muessen.
     */
    fun findings(root: View, actions: Map<View, List<String>> = emptyMap()): List<String> {
        val out = mutableListOf<String>()
        visibleViews(root).forEach { v -> out += findingsFor(v) }
        for ((v, labels) in actions) {
            val present = v.createAccessibilityNodeInfo().actionList.mapNotNull { it.label?.toString() }
            labels.filter { it !in present }.forEach { out += "${name(v)}: Aktion „$it“ fehlt" }
        }
        return out
    }

    private fun findingsFor(v: View): List<String> {
        val out = mutableListOf<String>()
        val touch = shadowOf(v).onTouchListener != null
        if (touch && !v.hasOnClickListeners()) out += "${name(v)}: OnTouchListener ohne Klickpfad"
        if (!v.isClickable && !v.hasOnClickListeners() && !touch) return out
        val text = (v as? TextView)?.text
        if (text.isNullOrBlank() && v.contentDescription.isNullOrBlank()) out += "${name(v)}: ohne Text und Beschreibung"
        val d = v.resources.displayMetrics.density
        val w = (v.width / d).roundToInt()
        val h = (v.height / d).roundToInt()
        if (w < MIN_TOUCH_DP || h < MIN_TOUCH_DP) out += "${name(v)}: $w × $h dp < $MIN_TOUCH_DP dp"
        return out
    }

    /** Alle Views, die mit ihren Eltern sichtbar sind (View.isShown braucht ein Fenster). */
    private fun visibleViews(v: View): List<View> {
        if (v.visibility != View.VISIBLE) return emptyList()
        val out = mutableListOf(v)
        if (v is ViewGroup) for (i in 0 until v.childCount) out += visibleViews(v.getChildAt(i))
        return out
    }

    private fun name(v: View): String =
        if (v.id != View.NO_ID) {
            runCatching { v.resources.getResourceEntryName(v.id) }.getOrDefault(v.javaClass.simpleName)
        } else {
            v.javaClass.simpleName
        }
}
