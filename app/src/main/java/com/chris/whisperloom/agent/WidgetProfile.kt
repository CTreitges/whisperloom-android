package com.chris.whisperloom.agent

import android.content.Context
import com.chris.whisperloom.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Aussehen und Verhalten einer Widget-Instanz. Mehrere Widgets koennen dasselbe Profil nutzen;
 * welches Widget welches Profil zeigt, haelt [WidgetProfileStore] fest.
 *
 * Gespeichert als JSON, tolerant gelesen: fehlt ein Feld, gilt der Default; unbekannte Felder
 * werden ignoriert. So nehmen spaetere Felder (Ziel-Bridge, Textstufe ...) keinen Migrationsschritt.
 */
data class WidgetProfile(
    /** [DEFAULT_ID] oder eine UUID. */
    val id: String,
    /** Leer = [displayName] zeigt "Sprachauftrag". Getrimmt, hoechstens [NAME_MAX] Zeichen. */
    val name: String = "",
    val icon: ProfileIcon = ProfileIcon.DEFAULT,
    /** false = Tippen startet, Tippen stoppt (Verhalten ohne Profil). */
    val autoStop: Boolean = false,
    /** Stille nach erkannter Sprache, die die Aufnahme beendet; zaehlt nur mit [autoStop]. */
    val pause: SpeechPause = SpeechPause.NORMAL,
) {
    val isDefault: Boolean get() = id == DEFAULT_ID

    fun displayName(ctx: Context): String = name.ifEmpty { ctx.getString(R.string.widget_label) }

    fun toJson(): JSONObject = JSONObject()
        .put(K_ID, id)
        .put(K_NAME, name)
        .put(K_ICON, ProfileIcon.encode(icon))
        .put(K_AUTO_STOP, autoStop)
        .put(K_PAUSE, pause.key)

    companion object {
        /** Das Standardprofil: nicht loeschbar, faengt jede unbekannte Profil-Id auf. */
        const val DEFAULT_ID = "default"
        const val NAME_MAX = 24

        /** Das Standardprofil, solange es nie gespeichert wurde — sieht aus wie das Widget ohne Profile. */
        val DEFAULT = WidgetProfile(DEFAULT_ID)

        /** Trimmen und auf [NAME_MAX] kuerzen, ohne ein Emoji (Surrogat-Paar) zu zerschneiden. */
        fun cleanName(raw: String): String = clip(raw.trim()).trimEnd()

        /**
         * Auf [NAME_MAX] kuerzen, ohne zu trimmen — fuer das Namensfeld waehrend des Tippens. Eine
         * allein stehende Emoji-Haelfte am Ende faellt weg, auch wenn der Text nicht zu lang ist.
         */
        fun clip(raw: String): String {
            val cut = raw.take(NAME_MAX)
            return if (cut.lastOrNull()?.isHighSurrogate() == true) cut.dropLast(1) else cut
        }

        /** Ein Eintrag; ohne Id unbrauchbar (null). */
        fun fromJson(o: JSONObject): WidgetProfile? {
            val id = text(o, K_ID)
            if (id.isEmpty()) return null
            return WidgetProfile(
                id = id,
                name = cleanName(text(o, K_NAME)),
                icon = ProfileIcon.decode(text(o, K_ICON)),
                autoStop = o.optBoolean(K_AUTO_STOP, false),
                pause = SpeechPause.fromKey(text(o, K_PAUSE)),
            )
        }

        /**
         * Die gespeicherte Liste lesen. Das Standardprofil steht immer vorn — gespeichert oder
         * [DEFAULT]. Kaputtes JSON ergibt nur das Standardprofil, doppelte Ids zaehlen einmal.
         */
        fun decodeAll(json: String?): List<WidgetProfile> {
            val arr = json?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return listOf(DEFAULT)
            val byId = LinkedHashMap<String, WidgetProfile>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i)?.let(::fromJson) ?: continue
                if (p.id !in byId) byId[p.id] = p
            }
            val default = byId.remove(DEFAULT_ID) ?: DEFAULT
            return listOf(default) + byId.values
        }

        fun encodeAll(profiles: List<WidgetProfile>): String =
            JSONArray().apply { profiles.forEach { put(it.toJson()) } }.toString()

        /** optString macht aus JSON-null den Text "null" — hier wird daraus "". */
        private fun text(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key, "")

        private const val K_ID = "id"
        private const val K_NAME = "name"
        private const val K_ICON = "icon"
        private const val K_AUTO_STOP = "autoStop"
        private const val K_PAUSE = "pause"
    }
}

/**
 * Symbol eines Profils. Kodiert als Text: `b:<key>` fuer ein eingebautes Symbol aus
 * [WidgetIcons], `p:<datei>` fuer ein Foto unter `filesDir/widget_icons/` ([WidgetPhoto]).
 */
sealed interface ProfileIcon {
    data class BuiltIn(val key: String) : ProfileIcon
    data class Photo(val fileName: String) : ProfileIcon

    companion object {
        val DEFAULT: ProfileIcon = BuiltIn(WidgetIcons.DEFAULT_KEY)

        fun encode(icon: ProfileIcon): String = when (icon) {
            is BuiltIn -> PREFIX_BUILT_IN + icon.key
            is Photo -> PREFIX_PHOTO + icon.fileName
        }

        /** Unbekanntes Symbol, unbrauchbarer Dateiname oder fremdes Format ergibt [DEFAULT]. */
        fun decode(raw: String): ProfileIcon {
            val value = raw.substring(minOf(raw.length, PREFIX_BUILT_IN.length))
            return when {
                raw.startsWith(PREFIX_BUILT_IN) && WidgetIcons.isKnown(value) -> BuiltIn(value)
                raw.startsWith(PREFIX_PHOTO) && WidgetPhoto.isValidName(value) -> Photo(value)
                else -> DEFAULT
            }
        }

        private const val PREFIX_BUILT_IN = "b:"
        private const val PREFIX_PHOTO = "p:"
    }
}

/**
 * Wie lange Stille nach erkannter Sprache die Aufnahme beendet (Auto-Stopp). Gespeichert wird
 * [key], nicht der Enum-Name: ein Umbenennen im Code darf gespeicherte Profile nicht aendern.
 */
enum class SpeechPause(val key: String, val ms: Long) {
    SHORT("short", 1200),
    NORMAL("normal", 2000),
    LONG("long", 3500);

    companion object {
        /** Unbekannt oder fehlend = [NORMAL]. */
        fun fromKey(key: String?): SpeechPause = entries.firstOrNull { it.key == key } ?: NORMAL
    }
}
