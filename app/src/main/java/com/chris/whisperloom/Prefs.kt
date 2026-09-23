package com.chris.whisperloom

import android.content.Context
import android.content.SharedPreferences
import com.chris.whisperloom.agent.AgentUrlCheck
import com.chris.whisperloom.api.AccessResolver
import com.chris.whisperloom.api.ApiAccess
import com.chris.whisperloom.api.Provider
import com.chris.whisperloom.api.ProviderCatalog

/**
 * Womit erkannt wird: ueber einen Anbieter (API) oder auf dem Geraet (whisper.cpp).
 * "Noch nicht gewaehlt" ist ein eigener Zustand (null in [Prefs.engine]) — dann zeigt
 * die App den Setup-Screen.
 */
enum class Engine(val key: String) {
    ONLINE("online"),
    OFFLINE("offline");

    companion object {
        fun fromKey(key: String?): Engine? = entries.firstOrNull { it.key == key }
    }
}

/**
 * Was das Sprachmodell nach der Erkennung mit dem Text tun soll.
 * [PARAGRAPHS] ist nicht in den Einstellungen waehlbar (nur fuer geteilte Audios).
 */
enum class RefineMode(val key: String) {
    OFF("off"),
    POLISH("polish"),
    BEAUTIFY("beautify"),
    SUMMARIZE("summarize"),
    PARAGRAPHS("paragraphs");

    companion object {
        /** Reihenfolge im Einstellungs-Dropdown. */
        val SETTINGS = listOf(OFF, POLISH, BEAUTIFY, SUMMARIZE)

        fun fromKey(key: String?): RefineMode = entries.firstOrNull { it.key == key } ?: OFF
    }
}

/**
 * Duenner SharedPreferences-Wrapper fuer die App-Einstellungen.
 *
 * Die Zugangs-Getter liefern die ROHEN gespeicherten Werte ("" = nicht gesetzt); die
 * Anbieter-Defaults zieht erst [sttAccess]/[llmAccess] ueber [AccessResolver].
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("whisperloom", Context.MODE_PRIVATE)

    init {
        migrate()
    }

    /**
     * Horcht auf Aenderungen, die NICHT ueber diese Instanz laufen: die Diktat-Tastatur ist
     * ein eigener Dienst und schreibt mit ihrer eigenen [Prefs] in dieselbe Datei.
     *
     * SharedPreferences haelt Horcher nur SCHWACH — der Aufrufer muss eine harte Referenz
     * behalten, sonst raeumt der Speicherbereiniger ihn weg und die Aenderungen kommen
     * stillschweigend nicht mehr an.
     */
    fun observe(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unobserve(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sp.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /**
     * v2 -> v3, laeuft genau einmal (prefs_version) und ist idempotent:
     *  - der Schalter "KI glaetten" (llm_polish) wird zum Modus refine_mode
     *  - v2 hatte eine freie api_url ohne Anbieter: passt sie zu einem Katalog-Preset, wird
     *    dieser Anbieter gesetzt, sonst "Eigener Server" (sonst bliebe ein LAN-Server unter
     *    dem Label OpenAI mit https-Pflicht haengen — der Assistent kaeme nie zu "fertig")
     *  - Bestandsnutzer (API-Key da bzw. eigener Server, keine Engine gewaehlt) bleiben online
     */
    private fun migrate() {
        if (sp.getInt(KEY_PREFS_VERSION, 0) >= PREFS_VERSION) return
        val e = sp.edit()
        if (!sp.contains(KEY_REFINE_MODE) && sp.getBoolean(KEY_LLM_POLISH_LEGACY, false)) {
            e.putString(KEY_REFINE_MODE, RefineMode.POLISH.key)
        }
        val legacyUrl = sp.getString(KEY_API_URL, "").orEmpty().trim()
        var provider = ProviderCatalog.openai
        if (!sp.contains(KEY_STT_PROVIDER) && legacyUrl.isNotEmpty()) {
            provider = providerForLegacyUrl(legacyUrl)
            if (provider.id != ProviderCatalog.OPENAI_ID) e.putString(KEY_STT_PROVIDER, provider.id)
        }
        val hasKey = !sp.getString(KEY_API_KEY, "").isNullOrBlank()
        if (sp.getString(KEY_ENGINE, "").isNullOrBlank() && (hasKey || !provider.needsKey)) {
            e.putString(KEY_ENGINE, Engine.ONLINE.key)
        }
        e.putInt(KEY_PREFS_VERSION, PREFS_VERSION).apply()
    }

    /** Katalog-Anbieter mit genau dieser Base-URL, sonst der eigene Server. */
    private fun providerForLegacyUrl(url: String): Provider {
        val wanted = url.trimEnd('/')
        return ProviderCatalog.sttProviders.firstOrNull { !it.isCustom && it.baseUrl.trimEnd('/') == wanted }
            ?: ProviderCatalog.custom
    }

    /** Erkennungssprache: "auto" oder ISO-Code ("de", "en", "es", "fr", "it"). Default: Deutsch. */
    var language: String
        get() = sp.getString(KEY_LANGUAGE, "de") ?: "de"
        set(v) = sp.edit().putString(KEY_LANGUAGE, v).apply()

    /** null = noch nicht gewaehlt (Setup zeigen). */
    var engine: Engine?
        get() = Engine.fromKey(sp.getString(KEY_ENGINE, null))
        set(v) {
            if (v == null) sp.edit().remove(KEY_ENGINE).apply()
            else sp.edit().putString(KEY_ENGINE, v.key).apply()
        }

    // --- Transkriptions-API --------------------------------------------------

    /** Provider-ID aus [ProviderCatalog]; Nutzer von vor v3 haben keine -> OpenAI. */
    var sttProviderId: String
        get() = sp.getString(KEY_STT_PROVIDER, ProviderCatalog.OPENAI_ID) ?: ProviderCatalog.OPENAI_ID
        set(v) = sp.edit().putString(KEY_STT_PROVIDER, v).apply()

    val sttProvider: Provider get() = ProviderCatalog.byId(sttProviderId)

    var apiBaseUrl: String
        get() = sp.getString(KEY_API_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_API_URL, v).apply()

    var apiKey: String
        get() = sp.getString(KEY_API_KEY, "") ?: ""
        set(v) = sp.edit().putString(KEY_API_KEY, v).apply()

    var apiModel: String
        get() = sp.getString(KEY_API_MODEL, "") ?: ""
        set(v) = sp.edit().putString(KEY_API_MODEL, v).apply()

    /**
     * Vokabular fuer die Erkennung (Eigennamen, Fachbegriffe, gewuenschte Schreibweisen), ein
     * Eintrag pro Zeile ([Vocabulary]). Geht zusammen mit [vocabFileUri] als `prompt` an die API
     * (offline: initial_prompt) — kostet nichts extra.
     */
    var apiPrompt: String
        get() = sp.getString(KEY_API_PROMPT, "") ?: ""
        set(v) = sp.edit().putString(KEY_API_PROMPT, v).apply()

    /** content://-URI der verknuepften .md/.txt-Datei ("" = keine), siehe [VocabularySource]. */
    var vocabFileUri: String
        get() = sp.getString(KEY_VOCAB_FILE_URI, "") ?: ""
        set(v) = sp.edit().putString(KEY_VOCAB_FILE_URI, v).apply()

    /** Anzeigename der verknuepften Datei (z. B. "namen.md"). */
    var vocabFileName: String
        get() = sp.getString(KEY_VOCAB_FILE_NAME, "") ?: ""
        set(v) = sp.edit().putString(KEY_VOCAB_FILE_NAME, v).apply()

    /** Read-Timeout in Sekunden; 0 = Anbieter-Default (90, eigener Server 600). */
    var apiReadTimeoutSec: Int
        get() = sp.getInt(KEY_READ_TIMEOUT, 0)
        set(v) {
            if (v <= 0) sp.edit().remove(KEY_READ_TIMEOUT).apply()
            else sp.edit().putInt(KEY_READ_TIMEOUT, v.coerceIn(MIN_TIMEOUT_SEC, MAX_TIMEOUT_SEC)).apply()
        }

    // --- Textverbesserung (LLM) ----------------------------------------------

    /** [AccessResolver.LLM_SAME] = Transkriptions-Zugang wiederverwenden, sonst Provider-ID. */
    var llmProviderId: String
        get() = sp.getString(KEY_LLM_PROVIDER, AccessResolver.LLM_SAME) ?: AccessResolver.LLM_SAME
        set(v) = sp.edit().putString(KEY_LLM_PROVIDER, v).apply()

    var llmUrl: String
        get() = sp.getString(KEY_LLM_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_LLM_URL, v).apply()

    var llmKey: String
        get() = sp.getString(KEY_LLM_KEY, "") ?: ""
        set(v) = sp.edit().putString(KEY_LLM_KEY, v).apply()

    var llmModel: String
        get() = sp.getString(KEY_LLM_MODEL, "") ?: ""
        set(v) = sp.edit().putString(KEY_LLM_MODEL, v).apply()

    var refineMode: RefineMode
        get() = RefineMode.fromKey(sp.getString(KEY_REFINE_MODE, null))
        set(v) = sp.edit().putString(KEY_REFINE_MODE, v.key).apply()

    /**
     * Statt fester Wortliste entscheidet das Sprachmodell selbst, welche Fuellwoerter,
     * Versprecher und Wiederholungen weg koennen. Wirkt nur mit [refineMode] != OFF.
     */
    var smartFillers: Boolean
        get() = sp.getBoolean(KEY_SMART_FILLERS, false)
        set(v) = sp.edit().putBoolean(KEY_SMART_FILLERS, v).apply()

    /**
     * Das Sprachmodell gliedert laengere Diktate in Absaetze (Default, bisheriges Verhalten).
     * Aus: ein durchgehender Text ohne Zeilenumbrueche. Wirkt nur mit [refineMode] != OFF.
     */
    var refineParagraphs: Boolean
        get() = sp.getBoolean(KEY_REFINE_PARAGRAPHS, true)
        set(v) = sp.edit().putBoolean(KEY_REFINE_PARAGRAPHS, v).apply()

    // --- Nachbearbeitung -----------------------------------------------------

    var removeFillers: Boolean
        get() = sp.getBoolean(KEY_REMOVE_FILLERS, true)
        set(v) = sp.edit().putBoolean(KEY_REMOVE_FILLERS, v).apply()

    var autoCapitalize: Boolean
        get() = sp.getBoolean(KEY_AUTO_CAP, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO_CAP, v).apply()

    /** Nach jedem Diktat ein Leerzeichen anhaengen (fluessiges Weiterdiktieren). */
    var trailingSpace: Boolean
        get() = sp.getBoolean(KEY_TRAILING_SPACE, true)
        set(v) = sp.edit().putBoolean(KEY_TRAILING_SPACE, v).apply()

    /** Eigene Fuellwoerter zusaetzlich zur Sprachliste — klein geschrieben, ohne Duplikate. */
    var customFillers: Set<String>
        get() = sp.getStringSet(KEY_CUSTOM_FILLERS, null)?.toSet().orEmpty()
        set(v) = sp.edit().putStringSet(KEY_CUSTOM_FILLERS, PolishPlan.normalizeFillers(v)).apply()

    /** Woerter der eingebauten Sprachliste, die NICHT gefiltert werden sollen. */
    var disabledFillers: Set<String>
        get() = sp.getStringSet(KEY_DISABLED_FILLERS, null)?.toSet().orEmpty()
        set(v) = sp.edit().putStringSet(KEY_DISABLED_FILLERS, PolishPlan.normalizeFillers(v)).apply()

    // --- Setup-Fortschritt (reine Flags fuer das UI) --------------------------

    var welcomeSeen: Boolean
        get() = sp.getBoolean(KEY_WELCOME_SEEN, false)
        set(v) = sp.edit().putBoolean(KEY_WELCOME_SEEN, v).apply()

    var overlaySkipped: Boolean
        get() = sp.getBoolean(KEY_OVERLAY_SKIPPED, false)
        set(v) = sp.edit().putBoolean(KEY_OVERLAY_SKIPPED, v).apply()

    var a11ySkipped: Boolean
        get() = sp.getBoolean(KEY_A11Y_SKIPPED, false)
        set(v) = sp.edit().putBoolean(KEY_A11Y_SKIPPED, v).apply()

    var notifSkipped: Boolean
        get() = sp.getBoolean(KEY_NOTIF_SKIPPED, false)
        set(v) = sp.edit().putBoolean(KEY_NOTIF_SKIPPED, v).apply()

    var keyboardSkipped: Boolean
        get() = sp.getBoolean(KEY_KEYBOARD_SKIPPED, false)
        set(v) = sp.edit().putBoolean(KEY_KEYBOARD_SKIPPED, v).apply()

    /** Das Tutorial nach der Einrichtung wurde einmal gezeigt (oder uebersprungen). */
    var tutorialSeen: Boolean
        get() = sp.getBoolean(KEY_TUTORIAL_SEEN, false)
        set(v) = sp.edit().putBoolean(KEY_TUTORIAL_SEEN, v).apply()

    // --- Offline-Erkennung (whisper.cpp, WP3) --------------------------------

    /** ModelCatalog-ID (WP3 definiert den Katalog: tiny/base/small/large-v3-turbo). */
    var offlineModel: String
        get() = sp.getString(KEY_OFFLINE_MODEL, DEFAULT_OFFLINE_MODEL) ?: DEFAULT_OFFLINE_MODEL
        set(v) = sp.edit().putString(KEY_OFFLINE_MODEL, v).apply()

    /** Beam-Search (genauer, langsamer) statt Greedy. */
    var offlineAccurate: Boolean
        get() = sp.getBoolean(KEY_OFFLINE_ACCURATE, true)
        set(v) = sp.edit().putBoolean(KEY_OFFLINE_ACCURATE, v).apply()

    // --- Geteilte Audios -----------------------------------------------------

    /** Schalter "Fuellwoerter ausblenden" in der Share-Ansicht. */
    var shareHideFillers: Boolean
        get() = sp.getBoolean(KEY_SHARE_HIDE_FILLERS, true)
        set(v) = sp.edit().putBoolean(KEY_SHARE_HIDE_FILLERS, v).apply()

    // --- Schwebender Knopf ---------------------------------------------------

    /** Zuletzt gemerkte Position des schwebenden Knopfs (Bildschirm-Pixel). */
    var floatX: Int
        get() = sp.getInt(KEY_FLOAT_X, DEFAULT_FLOAT_X)
        set(v) = sp.edit().putInt(KEY_FLOAT_X, v).apply()

    var floatY: Int
        get() = sp.getInt(KEY_FLOAT_Y, DEFAULT_FLOAT_Y)
        set(v) = sp.edit().putInt(KEY_FLOAT_Y, v).apply()

    // --- Sprachauftrag (Widget -> eigener Agent) ------------------------------

    /** Default aus: wer das Feature nicht nutzt, soll es nirgends bemerken. */
    var agentEnabled: Boolean
        get() = sp.getBoolean(KEY_AGENT_ENABLED, false)
        set(v) = sp.edit().putBoolean(KEY_AGENT_ENABLED, v).apply()

    /** Base-URL der Bridge ohne Pfad, z. B. https://hermes-bridge.example.de. */
    var agentUrl: String
        get() = sp.getString(KEY_AGENT_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_AGENT_URL, v).apply()

    /** Bearer-Token der Bridge. Wie die API-Keys unverschluesselt hier — allowBackup=false gilt. */
    var agentToken: String
        get() = sp.getString(KEY_AGENT_TOKEN, "") ?: ""
        set(v) = sp.edit().putString(KEY_AGENT_TOKEN, v).apply()

    /** Eigenes Flag: [tutorialSeen] bedeutet weiterhin "Einsteiger-Tutorial gesehen". */
    var agentTutorialSeen: Boolean
        get() = sp.getBoolean(KEY_AGENT_TUTORIAL_SEEN, false)
        set(v) = sp.edit().putBoolean(KEY_AGENT_TUTORIAL_SEEN, v).apply()

    /**
     * Eingeschaltet UND brauchbar — erst dann kann das Widget ueberhaupt etwas senden.
     * Die Adresse wird geprueft, nicht nur auf "nicht leer": eine Adresse ohne Schema haette
     * das Widget sonst auf "bereit" gestellt, und der Fehler waere erst nach Aufnahme UND
     * bezahlter Transkription aufgefallen.
     */
    val agentReady: Boolean
        get() = agentEnabled && agentToken.isNotBlank() && AgentUrlCheck.isValid(agentUrl)

    // --- Aufgeloeste Zugaenge ------------------------------------------------

    fun sttAccess(): ApiAccess = AccessResolver.resolveStt(
        providerId = sttProviderId,
        baseUrl = apiBaseUrl,
        apiKey = apiKey,
        model = apiModel,
        readTimeoutSec = apiReadTimeoutSec,
    )

    fun llmAccess(): ApiAccess = AccessResolver.resolveLlm(
        stt = sttAccess(),
        providerId = llmProviderId,
        baseUrl = llmUrl,
        apiKey = llmKey,
        model = llmModel,
    )

    companion object {
        private const val PREFS_VERSION = 3
        private const val KEY_PREFS_VERSION = "prefs_version"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_ENGINE = "engine"
        private const val KEY_STT_PROVIDER = "stt_provider"
        private const val KEY_API_URL = "api_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_MODEL = "api_model"
        private const val KEY_API_PROMPT = "api_prompt"
        private const val KEY_VOCAB_FILE_URI = "vocab_file_uri"
        private const val KEY_VOCAB_FILE_NAME = "vocab_file_name"
        private const val KEY_READ_TIMEOUT = "api_read_timeout_sec"
        private const val KEY_LLM_PROVIDER = "llm_provider"
        private const val KEY_LLM_URL = "llm_url"
        private const val KEY_LLM_KEY = "llm_key"
        private const val KEY_LLM_MODEL = "llm_model"
        private const val KEY_REFINE_MODE = "refine_mode"
        private const val KEY_LLM_POLISH_LEGACY = "llm_polish"
        private const val KEY_SMART_FILLERS = "smart_fillers"
        private const val KEY_REFINE_PARAGRAPHS = "refine_paragraphs"
        private const val KEY_REMOVE_FILLERS = "remove_fillers"
        private const val KEY_AUTO_CAP = "auto_capitalize"
        private const val KEY_TRAILING_SPACE = "trailing_space"
        private const val KEY_CUSTOM_FILLERS = "custom_fillers"
        private const val KEY_DISABLED_FILLERS = "disabled_fillers"
        private const val KEY_WELCOME_SEEN = "welcome_seen"
        private const val KEY_OVERLAY_SKIPPED = "overlay_skipped"
        private const val KEY_A11Y_SKIPPED = "setup_skip_a11y"
        private const val KEY_NOTIF_SKIPPED = "setup_skip_notif"
        private const val KEY_KEYBOARD_SKIPPED = "setup_skip_keyboard"
        private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
        private const val KEY_OFFLINE_MODEL = "offline_model"
        private const val KEY_OFFLINE_ACCURATE = "offline_accurate"
        private const val KEY_SHARE_HIDE_FILLERS = "share_hide_fillers"
        private const val KEY_AGENT_ENABLED = "agent_enabled"
        private const val KEY_AGENT_URL = "agent_url"
        private const val KEY_AGENT_TOKEN = "agent_token"
        private const val KEY_AGENT_TUTORIAL_SEEN = "agent_tutorial_seen"
        private const val KEY_FLOAT_X = "float_x"
        private const val KEY_FLOAT_Y = "float_y"

        const val MIN_TIMEOUT_SEC = 30
        const val MAX_TIMEOUT_SEC = 1800

        const val DEFAULT_API_URL = "https://api.openai.com/v1"

        /**
         * Nachfolger von gpt-4o-transcribe (das 2027-02-26 abgeschaltet wird): guenstiger,
         * genauer, erwartet aber `languages[]` statt `language` (siehe ProviderCatalog).
         */
        const val DEFAULT_API_MODEL = "gpt-transcribe"

        /** Guenstiges Modell fuer die optionale Textveredelung. */
        const val DEFAULT_LLM_MODEL = "gpt-4o-mini"

        const val DEFAULT_OFFLINE_MODEL = "small"

        /** Startposition des schwebenden Knopfs, wenn noch nichts verschoben wurde. */
        const val DEFAULT_FLOAT_X = 24
        const val DEFAULT_FLOAT_Y = 320

        /** Sprachen fuer die Einstellungs-Auswahl. Erste = Default. */
        val LANGUAGES = listOf(
            "auto" to "Automatisch erkennen",
            "de" to "Deutsch",
            "en" to "Englisch",
            "es" to "Spanisch",
            "fr" to "Französisch",
            "it" to "Italienisch",
        )
    }
}
