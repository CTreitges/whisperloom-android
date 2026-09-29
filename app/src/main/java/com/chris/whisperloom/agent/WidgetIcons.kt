package com.chris.whisperloom.agent

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.chris.whisperloom.R

/**
 * Die eingebauten Symbole fuer Widget-Profile.
 *
 * Gespeichert wird nur der [WidgetIcon.key] — NIE die R.drawable-Zahl: die aendert sich mit
 * jedem Build, ein gespeichertes Profil zeigte danach ein beliebiges anderes Bild. Die direkte
 * R-Referenz hier schuetzt die Drawables zugleich vor dem Resource-Shrinking.
 *
 * Reihenfolge = Reihenfolge im Symbolraster: zuerst das Standardsymbol, dann die
 * Alltagssymbole, dann die technischen.
 */
data class WidgetIcon(val key: String, @DrawableRes val drawable: Int, @StringRes val label: Int)

object WidgetIcons {

    const val DEFAULT_KEY = "mic"

    val all: List<WidgetIcon> = listOf(
        WidgetIcon(DEFAULT_KEY, R.drawable.ic_mic, R.string.widget_icon_mic),
        WidgetIcon("shopping_cart", R.drawable.ic_shopping_cart, R.string.widget_icon_shopping_cart),
        WidgetIcon("home", R.drawable.ic_home, R.string.widget_icon_home),
        WidgetIcon("work", R.drawable.ic_work, R.string.widget_icon_work),
        WidgetIcon("lightbulb", R.drawable.ic_lightbulb, R.string.widget_icon_lightbulb),
        WidgetIcon("event", R.drawable.ic_event, R.string.widget_icon_event),
        WidgetIcon("edit_note", R.drawable.ic_edit_note, R.string.widget_icon_edit_note),
        WidgetIcon("directions_car", R.drawable.ic_directions_car, R.string.widget_icon_directions_car),
        WidgetIcon("favorite", R.drawable.ic_favorite, R.string.widget_icon_favorite),
        WidgetIcon("star", R.drawable.ic_star, R.string.widget_icon_star),
        WidgetIcon("chat", R.drawable.ic_chat, R.string.widget_icon_chat),
        WidgetIcon("voicemail", R.drawable.ic_voicemail, R.string.widget_icon_voicemail),
        WidgetIcon("graphic_eq", R.drawable.ic_graphic_eq, R.string.widget_icon_graphic_eq),
        WidgetIcon("send", R.drawable.ic_send, R.string.widget_icon_send),
        WidgetIcon("checklist", R.drawable.ic_checklist, R.string.widget_icon_checklist),
        WidgetIcon("schedule", R.drawable.ic_schedule, R.string.widget_icon_schedule),
        WidgetIcon("notifications", R.drawable.ic_notifications, R.string.widget_icon_notifications),
        WidgetIcon("build", R.drawable.ic_build, R.string.widget_icon_build),
        WidgetIcon("cloud", R.drawable.ic_cloud, R.string.widget_icon_cloud),
        WidgetIcon("language", R.drawable.ic_language, R.string.widget_icon_language),
        WidgetIcon("auto_fix_high", R.drawable.ic_auto_fix_high, R.string.widget_icon_auto_fix_high),
        WidgetIcon("key", R.drawable.ic_key, R.string.widget_icon_key),
    )

    private val byKey = all.associateBy { it.key }

    fun isKnown(key: String): Boolean = key in byKey

    /** Symbol zum Schluessel; ein unbekannter Schluessel ergibt das Mikrofon. */
    fun of(key: String): WidgetIcon = byKey[key] ?: byKey.getValue(DEFAULT_KEY)
}
