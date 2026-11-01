package com.chris.whisperloom.agent

import android.content.Context
import com.chris.whisperloom.R
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.Refinement
import org.json.JSONArray
import org.json.JSONObject

/**
 * Aussehen und Verhalten einer Widget-Instanz. Mehrere Widgets koennen dasselbe Profil nutzen;
 * welches Widget welches Profil zeigt, haelt [WidgetProfileStore] fest.
 *
 * Gespeichert als JSON, tolerant gelesen: fehlt ein Feld, gilt der Default; unbekannte Felder
 * werden ignoriert. So nehmen spaetere Felder (Textstufe ...) keinen Migrationsschritt.
 *
 * Das Server-Token steht im Klartext in `whisperloom_widgets.xml` (wie frueher `agent_token` in den
 * Einstellungen; die Datei ist aus jedem Backup ausgeschlossen). [toString] zeigt es nie.
 */
data class WidgetProfile(
    /** [DEFAULT_ID] oder eine UUID. */
    val id: String,
    /** Leer = [displayName] zeigt [R.string.widget_label]. Getrimmt, hoechstens [NAME_MAX] Zeichen. */
    val name: String = "",
    val icon: ProfileIcon = ProfileIcon.DEFAULT,
    /** false = Tippen startet, Tippen stoppt (Verhalten ohne Profil). */
    val autoStop: Boolean = false,
    /** Stille nach erkannter Sprache, die die Aufnahme beendet; zaehlt nur mit [autoStop]. */
    val pause: SpeechPause = SpeechPause.NORMAL,
    val kind: WidgetKind = WidgetKind.VOICE_COMMAND,
    /** Name unter der Kachel auf dem Startbildschirm anzeigen. */
    val showName: Boolean = true,
    /** Nur [WidgetKind.CLAUDE_CODE]: Modell der neuen Session. */
    val claudeModel: ClaudeModel = ClaudeModel.OPUS,
    /** Nur [WidgetKind.CLAUDE_CODE]: Effort der neuen Session. */
    val claudeEffort: ClaudeEffort = ClaudeEffort.XHIGH,
    /** Nur [WidgetKind.CLAUDE_CODE]: Session mit Ultracode (Workflow mit mehreren Agenten). */
    val ultracode: Boolean = false,
    /** Base-URL der Bridge ohne Pfad, z. B. https://bridge.example.de (wie der Platzhalter im Editor). */
    val serverUrl: String = "",
    /** Bearer-Token der Bridge. */
    val serverToken: String = "",
    /**
     * Erkennungssprache dieses Widgets ("auto", ISO-Code oder [com.chris.whisperloom.Prefs.LANG_DE_EN]);
     * "" = wie in der App ([com.chris.whisperloom.Prefs.language]).
     */
    val language: String = "",
) {
    val isDefault: Boolean get() = id == DEFAULT_ID

    /**
     * Brauchbarer Server — erst dann kann dieses Widget ueberhaupt etwas senden. Die Adresse wird
     * geprueft, nicht nur auf "nicht leer": eine Adresse ohne Schema haette das Widget sonst auf
     * "bereit" gestellt, und der Fehler waere erst nach Aufnahme UND bezahlter Transkription aufgefallen.
     */
    val serverReady: Boolean get() = serverToken.isNotBlank() && AgentUrlCheck.isValid(serverUrl)

    fun displayName(ctx: Context): String = name.ifEmpty { ctx.getString(R.string.widget_label) }

    /** Ohne Token im Klartext: ein `Log.d("$profile")` oder ein Absturzbericht darf es nie zeigen. */
    override fun toString(): String =
        "WidgetProfile(id=$id, name=$name, icon=$icon, autoStop=$autoStop, pause=$pause, kind=$kind, " +
            "showName=$showName, claudeModel=$claudeModel, claudeEffort=$claudeEffort, ultracode=$ultracode, " +
            "language=$language, serverUrl=$serverUrl, serverToken=${if (serverToken.isEmpty()) "" else "***"})"

    /**
     * Was die Bridge ausser dem Transkript braucht: fuer [WidgetKind.CLAUDE_CODE] Modell, Effort
     * und Ultracode (claude-bridge, Feld `options`), sonst nichts — der Sprach-Command-Auftrag
     * an die hermes-bridge bleibt so byte-gleich. Im Profil, damit Worker und "Verbindung pruefen"
     * dieselbe Quelle nutzen.
     */
    fun bridgeOptions(): JSONObject? =
        if (kind != WidgetKind.CLAUDE_CODE) null
        else JSONObject().put("model", claudeModel.key).put("effort", claudeEffort.key).put("ultracode", ultracode)

    /**
     * Feste Textverbesserung des Widgets statt der Diktat-Stufe: [WidgetKind.CLAUDE_CODE] formt
     * jeden Auftrag zu einem Prompt — auch wenn das Diktat eine andere Stufe hat oder "Prompt" dort
     * nicht eingeschaltet ist. null = wie beim Diktat.
     */
    fun fixedRefinement(): Refinement? =
        if (kind == WidgetKind.CLAUDE_CODE) Refinement(RefineMode.PROMPT) else null

    fun toJson(): JSONObject = JSONObject()
        .put(K_ID, id)
        .put(K_NAME, name)
        .put(K_ICON, ProfileIcon.encode(icon))
        .put(K_AUTO_STOP, autoStop)
        .put(K_PAUSE, pause.key)
        .put(K_KIND, kind.key)
        .put(K_SHOW_NAME, showName)
        .put(K_CLAUDE_MODEL, claudeModel.key)
        .put(K_CLAUDE_EFFORT, claudeEffort.key)
        .put(K_ULTRACODE, ultracode)
        .put(K_SERVER_URL, serverUrl)
        .put(K_SERVER_TOKEN, serverToken)
        .put(K_LANGUAGE, language)

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
                kind = WidgetKind.fromKey(text(o, K_KIND)),
                showName = o.optBoolean(K_SHOW_NAME, true),
                claudeModel = ClaudeModel.fromKey(text(o, K_CLAUDE_MODEL)),
                claudeEffort = ClaudeEffort.fromKey(text(o, K_CLAUDE_EFFORT)),
                ultracode = o.optBoolean(K_ULTRACODE, false),
                serverUrl = text(o, K_SERVER_URL).trim(),
                serverToken = text(o, K_SERVER_TOKEN),
                language = text(o, K_LANGUAGE).trim(),
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
        private const val K_KIND = "kind"
        private const val K_SHOW_NAME = "showName"
        private const val K_CLAUDE_MODEL = "claudeModel"
        private const val K_CLAUDE_EFFORT = "claudeEffort"
        private const val K_ULTRACODE = "ultracode"
        private const val K_SERVER_URL = "serverUrl"
        private const val K_SERVER_TOKEN = "serverToken"
        private const val K_LANGUAGE = "language"
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

/**
 * Modell der Claude-Code-Session ([WidgetKind.CLAUDE_CODE]). [key] ist zugleich der Wert, den die
 * claude-bridge in `options.model` erwartet; gespeichert wird er, nicht der Enum-Name.
 */
enum class ClaudeModel(val key: String) {
    OPUS("opus"),
    FABLE("fable"),
    SONNET("sonnet"),
    HAIKU("haiku");

    companion object {
        /** Unbekannt oder fehlend = [OPUS]. */
        fun fromKey(key: String?): ClaudeModel = entries.firstOrNull { it.key == key } ?: OPUS
    }
}

/** Effort der Claude-Code-Session; [key] = `options.effort` der claude-bridge. */
enum class ClaudeEffort(val key: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    XHIGH("xhigh"),
    MAX("max");

    companion object {
        /** Unbekannt oder fehlend = [XHIGH]. */
        fun fromKey(key: String?): ClaudeEffort = entries.firstOrNull { it.key == key } ?: XHIGH
    }
}
