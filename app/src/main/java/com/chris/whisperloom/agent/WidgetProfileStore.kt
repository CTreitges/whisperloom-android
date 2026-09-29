package com.chris.whisperloom.agent

import android.content.Context
import java.util.UUID

/**
 * Profile und die Zuordnung Widget-Instanz → Profil.
 *
 * Eigene Datei (`whisperloom_widgets.xml`) statt der Einstellungen: der Einstellungs-Horcher in
 * [com.chris.whisperloom.ui.state.PrefsState] soll davon nichts mitbekommen, und die Datei
 * bleibt wie die anderen aus jedem Backup ausgeschlossen.
 *
 * Invarianten:
 * - [all] liefert das Standardprofil immer zuerst, gespeichert oder virtuell. Lesen schreibt nie.
 * - [forWidget] liefert fuer eine ungebundene oder verwaiste Instanz das Standardprofil.
 * - Das Standardprofil ist nicht loeschbar; wer an einem geloeschten Profil hing, zeigt danach
 *   das Standardprofil.
 * - Ein Foto, das kein Profil mehr nutzt (Profil geloescht, Symbol gewechselt), wird geloescht.
 */
class WidgetProfileStore(context: Context) {

    private val app = context.applicationContext
    private val sp = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun all(): List<WidgetProfile> = WidgetProfile.decodeAll(sp.getString(KEY_PROFILES, null))

    fun get(id: String): WidgetProfile? = all().firstOrNull { it.id == id }

    fun forWidget(widgetId: Int): WidgetProfile {
        val profiles = all()
        val id = sp.getString(bindingKey(widgetId), null)
        return profiles.firstOrNull { it.id == id } ?: profiles.first()
    }

    /** Wurde diese Instanz schon einmal konfiguriert? Jede Konfiguration schreibt eine Bindung. */
    fun isBound(widgetId: Int): Boolean = sp.contains(bindingKey(widgetId))

    /** Neues Profil mit Defaults hinten anfuegen. */
    fun create(name: String): WidgetProfile {
        val p = WidgetProfile(id = UUID.randomUUID().toString(), name = WidgetProfile.cleanName(name))
        write(all() + p)
        return p
    }

    /** Anlegen oder per Id ersetzen. Loest es ein Foto ab, wird die alte Datei geloescht. */
    fun save(profile: WidgetProfile) {
        val p = profile.copy(name = WidgetProfile.cleanName(profile.name))
        val profiles = all()
        val old = profiles.firstOrNull { it.id == p.id }
        write(if (old == null) profiles + p else profiles.map { if (it.id == p.id) p else it })
        val oldIcon = old?.icon
        if (oldIcon is ProfileIcon.Photo && oldIcon != p.icon) WidgetPhoto.delete(app, oldIcon.fileName)
    }

    /**
     * Profil loeschen; daran gebundene Widgets zeigen danach das Standardprofil, sein Foto wird
     * geloescht. Das Standardprofil wird abgelehnt (nichts passiert, Ergebnis 0).
     *
     * @return Anzahl der umgestellten Widgets
     */
    fun delete(id: String): Int {
        if (id == WidgetProfile.DEFAULT_ID) return 0
        val profiles = all()
        val gone = profiles.firstOrNull { it.id == id } ?: return 0
        val rebound = bindings().filterValues { it == id }.keys
        val e = sp.edit().putString(KEY_PROFILES, WidgetProfile.encodeAll(profiles - gone))
        rebound.forEach { e.putString(bindingKey(it), WidgetProfile.DEFAULT_ID) }
        e.apply()
        (gone.icon as? ProfileIcon.Photo)?.let { WidgetPhoto.delete(app, it.fileName) }
        return rebound.size
    }

    fun bind(widgetId: Int, profileId: String) {
        sp.edit().putString(bindingKey(widgetId), profileId).apply()
    }

    fun unbind(widgetIds: IntArray) {
        val e = sp.edit()
        widgetIds.forEach { e.remove(bindingKey(it)) }
        e.apply()
    }

    /**
     * Nach einer Wiederherstellung vergibt der Launcher neue Ids; die Bindungen ziehen paarweise
     * mit. Ueberschneiden sich alte und neue Ids, gewinnt die neue Zuordnung.
     */
    fun remap(oldIds: IntArray, newIds: IntArray) {
        val current = bindings()
        val e = sp.edit()
        oldIds.forEach { e.remove(bindingKey(it)) }
        // Nach den remove-Aufrufen: im selben Editor gewinnt der letzte Aufruf je Schluessel.
        oldIds.zip(newIds).forEach { (old, new) -> current[old]?.let { e.putString(bindingKey(new), it) } }
        e.apply()
    }

    /** Bindungen verschwundener Widgets entfernen — nicht jeder Launcher meldet onDeleted. */
    fun retain(liveIds: IntArray) {
        val live = liveIds.toSet()
        val dead = bindings().keys.filter { it !in live }
        if (dead.isEmpty()) return
        val e = sp.edit()
        dead.forEach { e.remove(bindingKey(it)) }
        e.apply()
    }

    /** Wie viele Widgets ausdruecklich an dieses Profil gebunden sind (ungebundene zaehlen nicht). */
    fun boundCount(profileId: String): Int = bindings().values.count { it == profileId }

    private fun bindings(): Map<Int, String> = sp.all.mapNotNull { (key, value) ->
        val widgetId = if (key.startsWith(PREFIX_BINDING)) key.removePrefix(PREFIX_BINDING).toIntOrNull() else null
        if (widgetId != null && value is String) widgetId to value else null
    }.toMap()

    private fun write(profiles: List<WidgetProfile>) {
        sp.edit().putString(KEY_PROFILES, WidgetProfile.encodeAll(profiles)).apply()
    }

    companion object {
        const val FILE = "whisperloom_widgets"

        private const val KEY_PROFILES = "profiles"
        private const val PREFIX_BINDING = "w_"

        private fun bindingKey(widgetId: Int) = "$PREFIX_BINDING$widgetId"
    }
}
