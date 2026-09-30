package com.chris.whisperloom

import android.content.Context
import com.chris.whisperloom.api.ApiAccess
import com.chris.whisperloom.api.ModelKind
import com.chris.whisperloom.api.ModelLists
import com.chris.whisperloom.api.RemoteModel
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Zuletzt geladene Modell-Listen je Anbieter, Zweck und Adresse ("Modelle vom Server", Pro).
 *
 * Eigene Datei (`whisperloom_models.xml`) statt der Einstellungen: der Einstellungs-Horcher in
 * [com.chris.whisperloom.ui.state.PrefsState] laedt bei jeder Aenderung dort alle Felder neu.
 * Gespeichert werden nur die Modelle und der Zeitpunkt — nie ein Key. Die Datei bleibt trotzdem
 * wie die anderen aus jedem Backup ausgeschlossen.
 *
 * @param now Uhr in ms (Tests setzen sie).
 */
class ModelCache(context: Context, private val now: () -> Long = System::currentTimeMillis) {

    /** Eine geladene Liste; [fetchedAt] in ms seit 1970. */
    data class Entry(val fetchedAt: Long, val models: List<RemoteModel>)

    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun get(access: ApiAccess, kind: ModelKind): Entry? = get(access.provider.id, kind, access.baseUrl)

    fun get(providerId: String, kind: ModelKind, baseUrl: String): Entry? =
        sp.getString(key(providerId, kind, baseUrl), null)?.let(::decode)

    /** Legt die Liste mit dem aktuellen Zeitpunkt ab (ersetzt die alte). */
    fun put(access: ApiAccess, kind: ModelKind, models: List<RemoteModel>): Entry {
        val entry = Entry(now(), models)
        sp.edit().putString(key(access.provider.id, kind, access.baseUrl), encode(entry)).apply()
        return entry
    }

    /**
     * Laedt die Liste vom Server und legt sie ab. Blockierend — aus einem Hintergrund-Thread aufrufen.
     * Fehler wie [ModelLists.load]; die alte Liste bleibt dann stehen.
     */
    fun refresh(access: ApiAccess, kind: ModelKind): Entry = put(access, kind, ModelLists.load(access, kind))

    /** Wie alt die Liste ist, in ms. */
    fun ageMs(entry: Entry): Long = now() - entry.fetchedAt

    /** Keine Liste, aelter als [MAX_AGE_MS] oder aus der Zukunft (Uhr verstellt): neu laden. */
    fun isStale(entry: Entry?): Boolean = entry == null || ageMs(entry) !in 0..MAX_AGE_MS

    companion object {
        const val FILE = "whisperloom_models"

        /** Aelter als ein Tag = automatisch neu laden (UI-Seite, Spec §2). */
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000

        /** `<providerId>|<stt|llm>|<baseUrl>`, die Adresse ohne Leerraum und ohne Schraegstrich am Ende. */
        fun key(providerId: String, kind: ModelKind, baseUrl: String): String =
            "$providerId|${kind.key}|${baseUrl.trim().trimEnd('/')}"

        fun encode(entry: Entry): String = JSONObject()
            .put("fetchedAt", entry.fetchedAt)
            .put(
                "models",
                JSONArray().apply {
                    entry.models.forEach { m ->
                        put(
                            JSONObject().put("id", m.id)
                                .putOpt("label", m.label)
                                .putOpt("note", m.note)
                                .putOpt("temperatureSupported", m.temperatureSupported)
                                .putOpt("reasoningEffort", m.reasoningEffort),
                        )
                    }
                },
            ).toString()

        /** null bei kaputtem Eintrag — dann gilt die Liste als nicht vorhanden. */
        fun decode(json: String): Entry? = try {
            val root = JSONObject(json)
            val models = root.getJSONArray("models")
            Entry(
                fetchedAt = root.getLong("fetchedAt"),
                models = (0 until models.length()).mapNotNull { i ->
                    val o = models.optJSONObject(i) ?: return@mapNotNull null
                    RemoteModel(
                        id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                        label = o.textOrNull("label"),
                        note = o.textOrNull("note"),
                        temperatureSupported = if (o.isNull("temperatureSupported")) null else o.optBoolean("temperatureSupported"),
                        reasoningEffort = o.textOrNull("reasoningEffort"),
                    )
                },
            )
        } catch (e: JSONException) {
            null
        }

        private fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else optString(key)
    }
}
