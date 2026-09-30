package com.chris.whisperloom.agent

import androidx.annotation.LayoutRes
import com.chris.whisperloom.R
import kotlin.math.ceil

/**
 * Die drei Layout-Varianten des Widgets mit ihrer Idealgroesse in dp. Jede Variante ist eine
 * Kachel mit dem Namen darunter; die Idealgroesse schliesst den Namen ein (ausgeblendet ist die
 * Variante nur kleiner, die Kachel fuellt dann die Flaeche).
 *
 * Die Idealgroessen sind so gewaehlt, dass die Abstandsregel des AOSP-Codes und die
 * Flaechenregel aus dem Javadoc ueberall dasselbe Layout waehlen: ROW und STACK sind gleich
 * breit, ICON ist am schmalsten und hat die kleinste Flaeche. Mit dem Namen ist ICON zwar hoeher
 * als ROW (58 zu 48 dp) — nach Abstand laege ICON aber erst bei h > 8w - 587 vor einer passenden
 * ROW, und die passt erst ab w > 119, also bei ueber 365 dp Hoehe, wo laengst STACK passt und
 * gewinnt. Welche der beiden Regeln ein kuenftiges Android umsetzt, spielt damit keine Rolle.
 */
enum class WidgetLayout(@LayoutRes val layoutRes: Int, val w: Float, val h: Float) {
    /** 1x1 hochkant: das Symbol in der Kachel, der Name darunter; die ganze Flaeche ist die Tippflaeche. */
    ICON(R.layout.widget_task_icon, 40f, 58f),

    /** Flach und breit: Symbol und Status nebeneinander in der Kachel, der Name darunter. */
    ROW(R.layout.widget_task_row, 120f, 48f),

    /** Ab 2x2: Symbol und Status untereinander in der Kachel, der Name darunter. */
    STACK(R.layout.widget_task, 120f, 110f),
}

/**
 * Welche Variante bei welcher Groesse. Rein, damit die ganze Zellmass-Tabelle auf der JVM
 * pruefbar ist.
 *
 * Ab Android 12 waehlt das System selbst aus der Groessen-Map ([WidgetLayout.w]/[WidgetLayout.h]);
 * [pick] bildet genau diesen Code nach und dient darunter ([legacy]) als eigene Auswahl.
 */
object WidgetLayouts {

    /** Die Variante fuer eine Widget-Groesse in dp. */
    fun pick(w: Float, h: Float): WidgetLayout =
        bestFit(WidgetLayout.entries, w, h, WidgetLayout::w, WidgetLayout::h)

    /**
     * Nachbau von `RemoteViews.findBestFitLayout` (AOSP android-31 und main): passend ist ein
     * Layout, wenn `ceil(groesse) + 1` in beiden Achsen darueber liegt; unter den passenden
     * gewinnt die kleinste quadratische Distanz; passt keins, das Layout mit der kleinsten Flaeche.
     * Generisch, damit die Regel gegen erfundene Groessen pruefbar ist.
     */
    internal fun <T> bestFit(candidates: List<T>, w: Float, h: Float, width: (T) -> Float, height: (T) -> Float): T {
        val fitting = candidates.filter { ceil(w) + 1 > width(it) && ceil(h) + 1 > height(it) }
        return fitting.minByOrNull { square(width(it) - w) + square(height(it) - h) }
            ?: candidates.minBy { width(it) * height(it) }
    }

    /**
     * Unter Android 12 gibt es keine Groessen-Map; der Launcher meldet nur Spannen. Hochformat
     * nutzt die schmalste Breite und die groesste Hoehe, Querformat umgekehrt (so steht es in
     * der Doku zu `OPTION_APPWIDGET_MIN/MAX_*`). Meldet der Launcher gar nichts, bleibt es bei
     * der gewohnten Variante [WidgetLayout.STACK].
     */
    fun legacy(minW: Int, maxW: Int, minH: Int, maxH: Int): LegacyLayouts {
        if (minW == 0 && maxW == 0 && minH == 0 && maxH == 0) {
            return LegacyLayouts(WidgetLayout.STACK, WidgetLayout.STACK)
        }
        return LegacyLayouts(
            portrait = pick(minW.toFloat(), maxH.toFloat()),
            landscape = pick(maxW.toFloat(), minH.toFloat()),
        )
    }

    /**
     * Nur bereit und Fehler zeigen das Symbol des Profils; waehrend Aufnahme und Senden steht
     * dort das Status-Symbol. So traegt der Sekunden-Takt der Aufnahme nie ein Foto mit.
     */
    fun showsProfileIcon(state: VoiceTaskState): Boolean =
        state == VoiceTaskState.READY || state == VoiceTaskState.ERROR

    private fun square(x: Float) = x * x
}

/** Auswahl unter Android 12: je Ausrichtung eine Variante. */
data class LegacyLayouts(val portrait: WidgetLayout, val landscape: WidgetLayout)
