package com.chris.whisperloom.ui.state

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chris.whisperloom.R
import com.chris.whisperloom.agent.ProfileIcon
import com.chris.whisperloom.agent.Tier
import com.chris.whisperloom.agent.VoiceTaskStore
import com.chris.whisperloom.agent.VoiceTaskWidget
import com.chris.whisperloom.agent.VoiceTaskWork
import com.chris.whisperloom.agent.WidgetKind
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

    /**
     * Ob ein Auftrag auf den Versand wartet (Karte "Offener Auftrag"). Ein Spiegel wie die anderen:
     * Loeschen kann ihn mitnehmen ([delete]), der Worker ihn im Hintergrund senden ([reload]).
     */
    var hasWork: Boolean by mutableStateOf(VoiceTaskStore(app).hasWork)
        private set

    /** Nur die Profile dieser Stufe — fuer die Tabs "Widgets" und "Pro Widgets". */
    fun profiles(tier: Tier): List<WidgetProfile> = profiles.filter { it.kind.tier == tier }

    fun profile(id: String): WidgetProfile? = profiles.firstOrNull { it.id == id }

    /** Wie viele Widgets dieses Profil ausdruecklich zeigen (fuer die Rueckfrage beim Loeschen). */
    fun boundCount(profileId: String): Int = store.boundCount(profileId)

    fun create(name: String): WidgetProfile = store.create(name).also { changed(redraw = false) }

    /**
     * "Neues Widget": heisst "Sprach-Command n" mit der kleinsten freien Nummer ab 2 (das
     * Standardprofil ist die 1) bzw. "Claude Code n" ab 1. Aus der Anzahl gebildet, entstuende
     * nach dem Loeschen ein Doppelname — zwei gleiche Eintraege mit gleichem Symbol, auch fuer
     * TalkBack nicht zu unterscheiden.
     *
     * Der Server kommt vom ersten Profil DESSELBEN Typs, das einen brauchbaren hat: meist nutzen
     * diese Widgets dieselbe Bridge, und ohne Server ginge das neue Widget erst einmal auf "fehlt".
     * Nie quer ueber die Typen — das erste Claude-Code-Widget erbte sonst die hermes-bridge, und
     * seine Auftraege landeten dort statt bei der claude-bridge. Das Symbol kommt vom Typ
     * ([WidgetKind.iconKey]).
     */
    fun createNew(kind: WidgetKind = WidgetKind.VOICE_COMMAND): WidgetProfile {
        val (nameRes, first) = when (kind) {
            WidgetKind.VOICE_COMMAND -> R.string.widget_profile_default_name to 2
            WidgetKind.CLAUDE_CODE -> R.string.widget_profile_default_name_claude to 1
        }
        val name = generateSequence(first) { it + 1 }
            .map { app.getString(nameRes, it) }
            .first { n -> profiles.none { it.name == n } }
        val server = profiles.firstOrNull { it.kind == kind && it.serverReady }
        val p = store.create(name).copy(
            kind = kind,
            icon = ProfileIcon.BuiltIn(kind.iconKey),
            serverUrl = server?.serverUrl.orEmpty(),
            serverToken = server?.serverToken.orEmpty(),
        )
        save(p, redraw = false)
        return p
    }

    /**
     * Speichern. [redraw] = false fuer Tastendruecke im Namensfeld: der Name steht sofort im
     * Speicher, das Neuzeichnen entprellt der Aufrufer ([redraw]).
     */
    fun save(profile: WidgetProfile, redraw: Boolean = true) {
        store.save(profile)
        changed(redraw)
    }

    /**
     * Loeschen. Wartet noch ein Auftrag dieses Profils, geht er mit: sein Server ist weg, und an den
     * eines anderen Widgets darf er nicht gehen.
     *
     * @return Anzahl der Widgets, die jetzt das Standardprofil zeigen
     */
    fun delete(id: String): Int {
        val task = VoiceTaskStore(app)
        // Das Standardprofil ist nicht loeschbar — sein Auftrag bleibt.
        if (id != WidgetProfile.DEFAULT_ID && task.profileId == id) {
            VoiceTaskWork.cancel(app)
            task.clear()
        }
        return store.delete(id).also { changed(redraw = true) }
    }

    /** Den offenen Auftrag verwerfen, egal von welchem Widget er kommt. */
    fun discardWork() {
        VoiceTaskWork.cancel(app)
        VoiceTaskStore(app).clear()
        changed(redraw = true)
    }

    fun bind(widgetId: Int, profileId: String) {
        store.bind(widgetId, profileId)
        changed(redraw = true)
    }

    /**
     * Beim Oeffnen und nach jeder Rueckkehr in die App: Widgets koennen inzwischen hinzugekommen
     * oder entfernt worden sein. Bindungen verschwundener Widgets fallen dabei weg — nicht jeder
     * Launcher meldet `onDeleted`. Ebenso Fotos, deren Import abgebrochen wurde.
     */
    fun reload() {
        store.retain(widgetIds())
        store.sweepPhotos()
        profiles = store.all()
        placed = readPlaced()
        hasWork = VoiceTaskStore(app).hasWork
    }

    fun redraw() = VoiceTaskWidget.refresh(app)

    private fun changed(redraw: Boolean) {
        profiles = store.all()
        placed = readPlaced()
        hasWork = VoiceTaskStore(app).hasWork
        if (redraw) redraw()
    }

    private fun readPlaced(): List<PlacedWidget> =
        widgetIds().sorted().map { PlacedWidget(it, store.forWidget(it)) }

    private fun widgetIds(): IntArray = VoiceTaskWidget.placedIds(app)
}
