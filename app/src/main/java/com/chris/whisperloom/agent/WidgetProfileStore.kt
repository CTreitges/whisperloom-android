package com.chris.whisperloom.agent

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.chris.whisperloom.Prefs
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
 * - Ein Foto, das kein Profil mehr nutzt (Profil geloescht, Symbol gewechselt), wird geloescht;
 *   Reste abgebrochener Importe raeumt [sweepPhotos] weg.
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

    /**
     * Wurde diese Instanz schon einmal konfiguriert? Jede Konfiguration schreibt eine Bindung,
     * und Bestands-Widgets bekommen ihre ueber [adopt].
     */
    fun isBound(widgetId: Int): Boolean = sp.contains(bindingKey(widgetId))

    /**
     * Instanzen ohne Bindung an das Standardprofil binden. Sie zeigen danach dasselbe wie vorher,
     * gelten aber als konfiguriert: "Neu konfigurieren" zeigt dann die Auswahl, statt sie fuer
     * eine Erstplatzierung zu halten. Nur fuer Momente, in denen sicher nichts platziert wird
     * (App-Update, Wiederherstellung). Vorhandene Bindungen bleiben unberuehrt.
     */
    fun adopt(widgetIds: IntArray) {
        val loose = widgetIds.filterNot(::isBound)
        if (loose.isEmpty()) return
        sp.edit { loose.forEach { putString(bindingKey(it), WidgetProfile.DEFAULT_ID) } }
    }

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
        sp.edit {
            putString(KEY_PROFILES, WidgetProfile.encodeAll(profiles - gone))
            rebound.forEach { putString(bindingKey(it), WidgetProfile.DEFAULT_ID) }
        }
        (gone.icon as? ProfileIcon.Photo)?.let { WidgetPhoto.delete(app, it.fileName) }
        return rebound.size
    }

    fun bind(widgetId: Int, profileId: String) {
        sp.edit { putString(bindingKey(widgetId), profileId) }
    }

    fun unbind(widgetIds: IntArray) {
        sp.edit { widgetIds.forEach { remove(bindingKey(it)) } }
    }

    /**
     * Nach einer Wiederherstellung vergibt der Launcher neue Ids; die Bindungen ziehen paarweise
     * mit. Ueberschneiden sich alte und neue Ids, gewinnt die neue Zuordnung.
     */
    fun remap(oldIds: IntArray, newIds: IntArray) {
        val current = bindings()
        sp.edit {
            oldIds.forEach { remove(bindingKey(it)) }
            // Nach den remove-Aufrufen: im selben Editor gewinnt der letzte Aufruf je Schluessel.
            oldIds.zip(newIds).forEach { (old, new) -> current[old]?.let { putString(bindingKey(new), it) } }
        }
    }

    /** Bindungen verschwundener Widgets entfernen — nicht jeder Launcher meldet onDeleted. */
    fun retain(liveIds: IntArray) {
        val live = liveIds.toSet()
        val dead = bindings().keys.filter { it !in live }
        if (dead.isEmpty()) return
        sp.edit { dead.forEach { remove(bindingKey(it)) } }
    }

    /** Fotos loeschen, die kein Profil nutzt und die kein laufender Import mehr braucht ([WidgetPhoto.sweep]). */
    fun sweepPhotos() {
        WidgetPhoto.sweep(app, all().mapNotNull { (it.icon as? ProfileIcon.Photo)?.fileName }.toSet())
    }

    /**
     * Einmalig nach dem Update auf 3.7.1: bis 3.7.0 teilten sich alle Widgets EINEN Server aus den
     * Einstellungen ([prefs] = `whisperloom.xml`). Jedes Profil ohne eigenen Server bekommt ihn, das
     * virtuelle Standardprofil wird dabei gespeichert (mit [defaultName], falls es keinen Namen hat).
     * Die alten Schluessel verschwinden erst, NACHDEM die Profile geschrieben sind — scheitert das
     * Schreiben, versucht es der naechste Start erneut.
     *
     * Idempotent: ohne alte Werte passiert nichts, der Speicher bleibt unberuehrt ("Lesen schreibt nie").
     */
    @SuppressLint("UseKtx") // edit {} liefert kein Ergebnis; hier entscheidet das von commit() ueber das Weitermachen.
    fun migrateLegacyServer(prefs: SharedPreferences, defaultName: String) {
        val url = prefs.getString(Prefs.LEGACY_KEY_AGENT_URL, "").orEmpty().trim()
        val token = prefs.getString(Prefs.LEGACY_KEY_AGENT_TOKEN, "").orEmpty()
        if (url.isEmpty() && token.isEmpty()) return
        val migrated = all().map { p ->
            val named = if (p.isDefault && p.name.isEmpty()) p.copy(name = WidgetProfile.cleanName(defaultName)) else p
            if (p.serverUrl.isEmpty() && p.serverToken.isEmpty()) named.copy(serverUrl = url, serverToken = token) else named
        }
        if (!sp.edit().putString(KEY_PROFILES, WidgetProfile.encodeAll(migrated)).commit()) return
        prefs.edit {
            remove(Prefs.LEGACY_KEY_AGENT_URL)
            remove(Prefs.LEGACY_KEY_AGENT_TOKEN)
        }
    }

    /** Wie viele Widgets ausdruecklich an dieses Profil gebunden sind (ungebundene zaehlen nicht). */
    fun boundCount(profileId: String): Int = bindings().values.count { it == profileId }

    private fun bindings(): Map<Int, String> = sp.all.mapNotNull { (key, value) ->
        val widgetId = if (key.startsWith(PREFIX_BINDING)) key.removePrefix(PREFIX_BINDING).toIntOrNull() else null
        if (widgetId != null && value is String) widgetId to value else null
    }.toMap()

    private fun write(profiles: List<WidgetProfile>) {
        sp.edit { putString(KEY_PROFILES, WidgetProfile.encodeAll(profiles)) }
    }

    companion object {
        const val FILE = "whisperloom_widgets"

        private const val KEY_PROFILES = "profiles"
        private const val PREFIX_BINDING = "w_"

        private fun bindingKey(widgetId: Int) = "$PREFIX_BINDING$widgetId"
    }
}
