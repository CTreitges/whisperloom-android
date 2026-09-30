package com.chris.whisperloom.ui.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chris.whisperloom.agent.VoiceTaskWidget
import com.chris.whisperloom.agent.WidgetProfile
import com.chris.whisperloom.agent.WidgetProfileStore

/** Ein Widget auf dem Startbildschirm und das Profil, das es gerade zeigt. */
data class PlacedWidget(val widgetId: Int, val profile: WidgetProfile)

/**
 * Compose-Spiegel des [WidgetProfileStore] fuer das Untermenue "Widgets" (und die Profilwahl
 * beim Platzieren). Wie [PrefsState]: jeder Schreibvorgang geht SOFORT in den Speicher, danach
 * werden die Spiegel neu gelesen und die Widgets neu gezeichnet — kein Speichern-Knopf.
 */
class WidgetProfilesState(context: Context) {

    private val app = context.applicationContext
    private val store = WidgetProfileStore(app)

    /** Alle Profile, das Standardprofil zuerst. */
    var profiles: List<WidgetProfile> by mutableStateOf(store.all())
        private set

    /** Die Widgets auf dem Startbildschirm, in der Reihenfolge ihrer Ids (= Reihenfolge des Hinzufuegens). */
    var placed: List<PlacedWidget> by mutableStateOf(readPlaced())
        private set

    fun profile(id: String): WidgetProfile? = profiles.firstOrNull { it.id == id }

    /** Wie viele Widgets dieses Profil ausdruecklich zeigen (fuer die Rueckfrage beim Loeschen). */
    fun boundCount(profileId: String): Int = store.boundCount(profileId)

    fun create(name: String): WidgetProfile = store.create(name).also { changed(redraw = false) }

    /**
     * Speichern. [redraw] = false fuer Tastendruecke im Namensfeld: der Name steht sofort im
     * Speicher, das Neuzeichnen entprellt der Aufrufer ([redraw]).
     */
    fun save(profile: WidgetProfile, redraw: Boolean = true) {
        store.save(profile)
        changed(redraw)
    }

    /** @return Anzahl der Widgets, die jetzt das Standardprofil zeigen */
    fun delete(id: String): Int = store.delete(id).also { changed(redraw = true) }

    fun bind(widgetId: Int, profileId: String) {
        store.bind(widgetId, profileId)
        changed(redraw = true)
    }

    /**
     * Beim Oeffnen und nach jeder Rueckkehr in die App: Widgets koennen inzwischen hinzugekommen
     * oder entfernt worden sein. Bindungen verschwundener Widgets fallen dabei weg — nicht jeder
     * Launcher meldet `onDeleted`.
     */
    fun reload() {
        store.retain(widgetIds())
        profiles = store.all()
        placed = readPlaced()
    }

    fun redraw() = VoiceTaskWidget.refresh(app)

    private fun changed(redraw: Boolean) {
        profiles = store.all()
        placed = readPlaced()
        if (redraw) redraw()
    }

    private fun readPlaced(): List<PlacedWidget> =
        widgetIds().sorted().map { PlacedWidget(it, store.forWidget(it)) }

    private fun widgetIds(): IntArray = VoiceTaskWidget.placedIds(app)
}
