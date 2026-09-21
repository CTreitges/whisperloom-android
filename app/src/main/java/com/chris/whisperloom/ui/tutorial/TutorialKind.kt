package com.chris.whisperloom.ui.tutorial

import com.chris.whisperloom.Prefs

/**
 * Welches Tutorial gezeigt wird. Zwei eigenstaendige Hefte mit eigenem Gesehen-Flag:
 * `tutorialSeen` heisst weiterhin "Einsteiger-Tutorial gesehen" und darf seine Bedeutung
 * nicht aendern — sonst bekaeme jeder Bestandsnutzer beim Update das Einsteiger-Tutorial
 * erneut oder gar nicht mehr.
 */
enum class TutorialKind(val key: String) {
    /** Nach der Einrichtung: Knopf, Tastatur, Sprachnachrichten, Ergebnis. */
    BASICS("basics"),

    /** Sprachauftrag: Widget, Server, aufnehmen, Antwort. Nur auf Wunsch. */
    AGENT("agent");

    fun seen(prefs: Prefs): Boolean = if (this == AGENT) prefs.agentTutorialSeen else prefs.tutorialSeen

    fun markSeen(prefs: Prefs) {
        if (this == AGENT) prefs.agentTutorialSeen = true else prefs.tutorialSeen = true
    }

    companion object {
        fun fromKey(key: String?): TutorialKind = entries.firstOrNull { it.key == key } ?: BASICS
    }
}
