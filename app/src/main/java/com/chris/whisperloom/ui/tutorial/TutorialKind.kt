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

    /**
     * Pro Widgets: freischalten, anlegen, Server, platzieren, aufnehmen, Antwort. Nur auf Wunsch.
     * Schluessel "agent" und Flag `agentTutorialSeen` stammen aus 3.7.0 (Sprachauftrag) und
     * bleiben, damit alte Routen und das Gesehen-Flag weiter gelten.
     */
    PRO_WIDGETS("agent");

    fun seen(prefs: Prefs): Boolean = when (this) {
        BASICS -> prefs.tutorialSeen
        PRO_WIDGETS -> prefs.agentTutorialSeen
    }

    fun markSeen(prefs: Prefs) {
        when (this) {
            BASICS -> prefs.tutorialSeen = true
            PRO_WIDGETS -> prefs.agentTutorialSeen = true
        }
    }

    companion object {
        fun fromKey(key: String?): TutorialKind = entries.firstOrNull { it.key == key } ?: BASICS
    }
}
