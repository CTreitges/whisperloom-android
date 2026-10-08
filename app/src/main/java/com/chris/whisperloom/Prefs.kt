package com.chris.whisperloom

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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
 * Duenner SharedPreferences-Wrapper fuer die App-Einstellungen.
 *
 * Die Zugangs-Getter liefern die ROHEN gespeicherten Werte ("" = nicht gesetzt); die
 * Anbieter-Defaults zieht erst [sttAccess]/[llmAccess] ueber [AccessResolver].
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Zuletzt geladene Modell-Listen (eigene Datei) — liefern die Flags fuer Server-Modelle. */
    val modelCache = ModelCache(context)

    init {
        PrefsMigration.run(sp)
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

    /** Erkennungssprache: "auto" oder ISO-Code ("de", "en", "es", "fr", "it"). Default: Deutsch. */
    var language: String
        get() = sp.getString(KEY_LANGUAGE, "de") ?: "de"
        set(v) = sp.edit { putString(KEY_LANGUAGE, v) }

    /** null = noch nicht gewaehlt (Setup zeigen). */
    var engine: Engine?
        get() = Engine.fromKey(sp.getString(KEY_ENGINE, null))
        set(v) {
            if (v == null) sp.edit { remove(KEY_ENGINE) }
            else sp.edit { putString(KEY_ENGINE, v.key) }
        }

    // --- Transkriptions-API --------------------------------------------------

    /** Provider-ID aus [ProviderCatalog]; Nutzer von vor v3 haben keine -> OpenAI. */
    var sttProviderId: String
        get() = sp.getString(KEY_STT_PROVIDER, ProviderCatalog.OPENAI_ID) ?: ProviderCatalog.OPENAI_ID
        set(v) = sp.edit { putString(KEY_STT_PROVIDER, v) }

    val sttProvider: Provider get() = ProviderCatalog.byId(sttProviderId)

    var apiBaseUrl: String
        get() = sp.getString(KEY_API_URL, "") ?: ""
        set(v) = sp.edit { putString(KEY_API_URL, v) }

    var apiKey: String
        get() = sp.getString(KEY_API_KEY, "") ?: ""
        set(v) = sp.edit { putString(KEY_API_KEY, v) }

    var apiModel: String
        get() = sp.getString(KEY_API_MODEL, "") ?: ""
        set(v) = sp.edit { putString(KEY_API_MODEL, v) }

    /**
     * Vokabular fuer die Erkennung (Eigennamen, Fachbegriffe, gewuenschte Schreibweisen), ein
     * Eintrag pro Zeile ([Vocabulary]). Geht zusammen mit [vocabFileUri] als `prompt` an die API
     * (offline: initial_prompt) — kostet nichts extra.
     */
    var apiPrompt: String
        get() = sp.getString(KEY_API_PROMPT, "") ?: ""
        set(v) = sp.edit { putString(KEY_API_PROMPT, v) }

    /** content://-URI der verknuepften .md/.txt-Datei ("" = keine), siehe [VocabularySource]. */
    var vocabFileUri: String
        get() = sp.getString(KEY_VOCAB_FILE_URI, "") ?: ""
        set(v) = sp.edit { putString(KEY_VOCAB_FILE_URI, v) }

    /** Anzeigename der verknuepften Datei (z. B. "namen.md"). */
    var vocabFileName: String
        get() = sp.getString(KEY_VOCAB_FILE_NAME, "") ?: ""
        set(v) = sp.edit { putString(KEY_VOCAB_FILE_NAME, v) }

    /** Read-Timeout in Sekunden; 0 = Anbieter-Default (90, eigener Server 600). */
    var apiReadTimeoutSec: Int
        get() = sp.getInt(KEY_READ_TIMEOUT, 0)
        set(v) {
            if (v <= 0) sp.edit { remove(KEY_READ_TIMEOUT) }
            else sp.edit { putInt(KEY_READ_TIMEOUT, v.coerceIn(MIN_TIMEOUT_SEC, MAX_TIMEOUT_SEC)) }
        }

    // --- Textverbesserung (LLM) ----------------------------------------------

    /** [AccessResolver.LLM_SAME] = Transkriptions-Zugang wiederverwenden, sonst Provider-ID. */
    var llmProviderId: String
        get() = sp.getString(KEY_LLM_PROVIDER, AccessResolver.LLM_SAME) ?: AccessResolver.LLM_SAME
        set(v) = sp.edit { putString(KEY_LLM_PROVIDER, v) }

    var llmUrl: String
        get() = sp.getString(KEY_LLM_URL, "") ?: ""
        set(v) = sp.edit { putString(KEY_LLM_URL, v) }

    var llmKey: String
        get() = sp.getString(KEY_LLM_KEY, "") ?: ""
        set(v) = sp.edit { putString(KEY_LLM_KEY, v) }

    /** Modell des Online-Zugangs = Standard fuer alle Stufen ohne eigenes ([llmModelFor]); "" = Empfehlung je Stufe. */
    var llmModel: String
        get() = sp.getString(KEY_LLM_MODEL, "") ?: ""
        set(v) = sp.edit { putString(KEY_LLM_MODEL, v) }

    /**
     * Eigenes Modell der Stufe von [mode] (siehe [RefineMode.modelStage]), roh: "" = Standard.
     * Gilt fuer Diktat und Sprachnachrichten gemeinsam, gleich mit welcher Bereinigung oder Form.
     * Schluessel `llm_model_polish`, `…_beautify`, `…_summarize`, `…_prompt`.
     */
    fun llmModelFor(mode: RefineMode?): String {
        val stage = mode?.modelStage ?: return ""
        return sp.getString(KEY_LLM_MODEL_PREFIX + stage.key, "") ?: ""
    }

    /** @throws IllegalArgumentException fuer [RefineMode.OFF] — ohne Stufe gibt es kein Modell. */
    fun setLlmModelFor(mode: RefineMode, model: String) {
        val stage = requireNotNull(mode.modelStage) { "Stufe aus hat kein Modell" }
        sp.edit { putString(KEY_LLM_MODEL_PREFIX + stage.key, model) }
    }

    /**
     * Anbieterwechsel: das Modell des Zugangs und alle Stufen-Modelle zurueck auf Standard — sie
     * gehoeren zum alten Anbieter (sonst ginge z. B. claude-sonnet-5 an OpenAI: 404).
     */
    fun clearLlmModels() = sp.edit {
        remove(KEY_LLM_MODEL)
        RefineMode.MODEL_STAGES.forEach { remove(KEY_LLM_MODEL_PREFIX + it.key) }
    }

    /**
     * Gespeicherte Stufe. "Prompt" gilt nur, solange sie eingeschaltet ist — sonst waere sie
     * unsichtbar und trotzdem aktiv; dann gilt "Glaetten", die naechste unauffaellige Stufe. Ebenso
     * die fruehere Stufe "Absaetze" (Glaetten mit Absaetzen).
     */
    var refineMode: RefineMode
        get() {
            val key = sp.getString(KEY_REFINE_MODE, null)
            val mode = if (key == LEGACY_PARAGRAPHS) RefineMode.POLISH else RefineMode.fromKey(key)
            return if (mode == RefineMode.PROMPT && !promptLevelEnabled) RefineMode.POLISH else mode
        }
        set(v) = sp.edit { putString(KEY_REFINE_MODE, v.key) }

    /** Pro-Funktion ([ProFeature.PROMPT]): Stufe "Prompt" in Tastatur und Einstellungen anbieten. */
    var promptLevelEnabled: Boolean
        get() = isEnabled(ProFeature.PROMPT)
        set(v) = setEnabled(ProFeature.PROMPT, v)

    // --- Stufen-Einstellungen je Weg (3.9.0); das Modell je Stufe gilt fuer beide Wege -------

    /** Die gespeicherte Stufe des Wegs: [refineMode] bzw. [shareRefineMode]. */
    fun refineModeFor(way: RefineWay): RefineMode = when (way) {
        RefineWay.DICTATION -> refineMode
        RefineWay.SHARE -> shareRefineMode
    }

    /**
     * Bereinigung beim Glaetten, je Weg: "Nur Zeichensetzung" (ab Werk), "Ohne Fuellwoerter"
     * (die KI entscheidet) oder "Lesbar". Gespeichert und angezeigt bleibt die Stufe "Glaetten".
     */
    fun polishCleanupFor(way: RefineWay): PolishCleanup = PolishCleanup.fromKey(sp.getString(cleanupKey(way), null))

    fun setPolishCleanupFor(way: RefineWay, cleanup: PolishCleanup) = sp.edit { putString(cleanupKey(way), cleanup.key) }

    /** Form beim Zusammenfassen, je Weg: automatisch (Liste ab drei Punkten, ab Werk) oder Fliesstext. */
    fun summarizeFormFor(way: RefineWay): SummarizeForm = SummarizeForm.fromKey(sp.getString(formKey(way), null))

    fun setSummarizeFormFor(way: RefineWay, form: SummarizeForm) = sp.edit { putString(formKey(way), form.key) }

    /**
     * Absaetze beim Diktat fuer Glaetten und Verschoenern ([RefineMode.PARAGRAPH_STAGES], ab Werk an).
     * Aus: ein durchgehender Text. Andere Stufen haben keinen Schalter (true); Sprachnachrichten
     * sind immer gegliedert ([Refinement.of]).
     */
    fun paragraphsFor(stage: RefineMode): Boolean = paragraphsKey(stage)?.let { sp.getBoolean(it, true) } ?: true

    /** @throws IllegalArgumentException fuer eine Stufe ohne Schalter "Absaetze". */
    fun setParagraphsFor(stage: RefineMode, on: Boolean) {
        val key = requireNotNull(paragraphsKey(stage)) { "Stufe ${stage.name} hat keinen Schalter Absaetze" }
        sp.edit { putBoolean(key, on) }
    }

    /** Was ein Auftrag des Wegs wirklich an das Sprachmodell gibt: Stufe mit ihren Einstellungen. */
    fun refinementFor(way: RefineWay): Refinement {
        val stage = refineModeFor(way)
        return Refinement.of(way, stage, polishCleanupFor(way), paragraphsFor(stage), summarizeFormFor(way))
    }

    private fun cleanupKey(way: RefineWay) = if (way == RefineWay.DICTATION) KEY_POLISH_CLEANUP else KEY_SHARE_POLISH_CLEANUP

    private fun formKey(way: RefineWay) = if (way == RefineWay.DICTATION) KEY_SUMMARIZE_FORM else KEY_SHARE_SUMMARIZE_FORM

    private fun paragraphsKey(stage: RefineMode): String? = when (stage) {
        RefineMode.POLISH, RefineMode.READABLE -> KEY_PARAGRAPHS_POLISH
        RefineMode.BEAUTIFY -> KEY_PARAGRAPHS_BEAUTIFY
        else -> null
    }

    // --- Nachbearbeitung -----------------------------------------------------

    var removeFillers: Boolean
        get() = sp.getBoolean(KEY_REMOVE_FILLERS, true)
        set(v) = sp.edit { putBoolean(KEY_REMOVE_FILLERS, v) }

    var autoCapitalize: Boolean
        get() = sp.getBoolean(KEY_AUTO_CAP, true)
        set(v) = sp.edit { putBoolean(KEY_AUTO_CAP, v) }

    /** Nach jedem Diktat ein Leerzeichen anhaengen (fluessiges Weiterdiktieren). */
    var trailingSpace: Boolean
        get() = sp.getBoolean(KEY_TRAILING_SPACE, true)
        set(v) = sp.edit { putBoolean(KEY_TRAILING_SPACE, v) }

    /** Eigene Fuellwoerter zusaetzlich zur Sprachliste — klein geschrieben, ohne Duplikate. */
    var customFillers: Set<String>
        get() = sp.getStringSet(KEY_CUSTOM_FILLERS, null)?.toSet().orEmpty()
        set(v) = sp.edit { putStringSet(KEY_CUSTOM_FILLERS, PolishPlan.normalizeFillers(v)) }

    /** Woerter der eingebauten Sprachliste, die NICHT gefiltert werden sollen. */
    var disabledFillers: Set<String>
        get() = sp.getStringSet(KEY_DISABLED_FILLERS, null)?.toSet().orEmpty()
        set(v) = sp.edit { putStringSet(KEY_DISABLED_FILLERS, PolishPlan.normalizeFillers(v)) }

    // --- Setup-Fortschritt (reine Flags fuer das UI) --------------------------

    var welcomeSeen: Boolean
        get() = sp.getBoolean(KEY_WELCOME_SEEN, false)
        set(v) = sp.edit { putBoolean(KEY_WELCOME_SEEN, v) }

    var overlaySkipped: Boolean
        get() = sp.getBoolean(KEY_OVERLAY_SKIPPED, false)
        set(v) = sp.edit { putBoolean(KEY_OVERLAY_SKIPPED, v) }

    var a11ySkipped: Boolean
        get() = sp.getBoolean(KEY_A11Y_SKIPPED, false)
        set(v) = sp.edit { putBoolean(KEY_A11Y_SKIPPED, v) }

    var notifSkipped: Boolean
        get() = sp.getBoolean(KEY_NOTIF_SKIPPED, false)
        set(v) = sp.edit { putBoolean(KEY_NOTIF_SKIPPED, v) }

    var keyboardSkipped: Boolean
        get() = sp.getBoolean(KEY_KEYBOARD_SKIPPED, false)
        set(v) = sp.edit { putBoolean(KEY_KEYBOARD_SKIPPED, v) }

    /** Das Tutorial nach der Einrichtung wurde einmal gezeigt (oder uebersprungen). */
    var tutorialSeen: Boolean
        get() = sp.getBoolean(KEY_TUTORIAL_SEEN, false)
        set(v) = sp.edit { putBoolean(KEY_TUTORIAL_SEEN, v) }

    // --- Offline-Erkennung (whisper.cpp, WP3) --------------------------------

    /** ModelCatalog-ID (WP3 definiert den Katalog: tiny/base/small/large-v3-turbo). */
    var offlineModel: String
        get() = sp.getString(KEY_OFFLINE_MODEL, DEFAULT_OFFLINE_MODEL) ?: DEFAULT_OFFLINE_MODEL
        set(v) = sp.edit { putString(KEY_OFFLINE_MODEL, v) }

    /** Beam-Search (genauer, langsamer) statt Greedy. */
    var offlineAccurate: Boolean
        get() = sp.getBoolean(KEY_OFFLINE_ACCURATE, true)
        set(v) = sp.edit { putBoolean(KEY_OFFLINE_ACCURATE, v) }

    // --- Lokales Textmodell (LiteRT-LM) --------------------------------------

    /**
     * Textverbesserung bei Offline-Erkennung (lokal / online, ohne Netz lokal / ueberspringen).
     * Ungespeichert gilt [OfflineRefineRule.LOCAL] — auch fuer Bestandsnutzer, ohne Migration.
     */
    var offlineRefine: OfflineRefineRule
        get() = OfflineRefineRule.fromKey(sp.getString(KEY_OFFLINE_REFINE, null))
        set(v) = sp.edit { putString(KEY_OFFLINE_REFINE, v.key) }

    /** TextModelCatalog-ID des lokalen Textmodells (gemma4_e2b/gemma4_e4b). */
    var localLlmModel: String
        get() = sp.getString(KEY_LOCAL_LLM_MODEL, DEFAULT_LOCAL_LLM_MODEL) ?: DEFAULT_LOCAL_LLM_MODEL
        set(v) = sp.edit { putString(KEY_LOCAL_LLM_MODEL, v) }

    // --- Geteilte Audios -----------------------------------------------------

    /** Schalter "Fuellwoerter ausblenden" in der Share-Ansicht. */
    var shareHideFillers: Boolean
        get() = sp.getBoolean(KEY_SHARE_HIDE_FILLERS, true)
        set(v) = sp.edit { putBoolean(KEY_SHARE_HIDE_FILLERS, v) }

    /**
     * KI-Stufe fuer geteilte Sprachnachrichten — unabhaengig von [refineMode] und ab Werk aus
     * (dann bleibt es wortgetreu). Nur die Stufen aus [RefineMode.SETTINGS]; "Prompt" ergibt
     * fuer eine fremde Nachricht keinen Sinn und gilt wie alles Unbekannte als aus.
     */
    var shareRefineMode: RefineMode
        get() = RefineMode.fromKey(sp.getString(KEY_SHARE_REFINE_MODE, null))
            .takeIf { it in RefineMode.SETTINGS } ?: RefineMode.OFF
        set(v) = sp.edit { putString(KEY_SHARE_REFINE_MODE, v.key) }

    // --- Schwebender Knopf ---------------------------------------------------

    /** Zuletzt gemerkte Position des schwebenden Knopfs (Bildschirm-Pixel). */
    var floatX: Int
        get() = sp.getInt(KEY_FLOAT_X, DEFAULT_FLOAT_X)
        set(v) = sp.edit { putInt(KEY_FLOAT_X, v) }

    var floatY: Int
        get() = sp.getInt(KEY_FLOAT_Y, DEFAULT_FLOAT_Y)
        set(v) = sp.edit { putInt(KEY_FLOAT_Y, v) }

    // --- Pro-Funktionen ("Erweitert") ----------------------------------------

    /** Default aus: wer eine Pro-Funktion nicht nutzt, soll sie nirgends bemerken. */
    fun isEnabled(feature: ProFeature): Boolean = sp.getBoolean(keyOf(feature), false)

    fun setEnabled(feature: ProFeature, on: Boolean) = sp.edit { putBoolean(keyOf(feature), on) }

    /**
     * Pro Widgets (Sprach-Command-Widgets mit eigenem Server). Server und Token stehen seit 3.7.1
     * im jeweiligen Widget-Profil ([com.chris.whisperloom.agent.WidgetProfile.serverReady]).
     */
    var proWidgetsEnabled: Boolean
        get() = isEnabled(ProFeature.WIDGETS)
        set(v) = setEnabled(ProFeature.WIDGETS, v)

    /**
     * Modelle vom Server: Modellwahl mit der aktuellen Liste des Anbieters (Liste in [modelCache]).
     * Aus = Empfehlungen aus dem Katalog wie bisher.
     */
    var serverModelsEnabled: Boolean
        get() = isEnabled(ProFeature.SERVER_MODELS)
        set(v) = setEnabled(ProFeature.SERVER_MODELS, v)

    /** Eigenes Flag: [tutorialSeen] bedeutet weiterhin "Einsteiger-Tutorial gesehen". */
    var agentTutorialSeen: Boolean
        get() = sp.getBoolean(KEY_AGENT_TUTORIAL_SEEN, false)
        set(v) = sp.edit { putBoolean(KEY_AGENT_TUTORIAL_SEEN, v) }

    private fun keyOf(feature: ProFeature): String = when (feature) {
        ProFeature.WIDGETS -> KEY_PRO_WIDGETS
        ProFeature.PROMPT -> KEY_PROMPT_LEVEL
        ProFeature.SERVER_MODELS -> KEY_SERVER_MODELS
    }

    // --- Aufgeloeste Zugaenge ------------------------------------------------

    fun sttAccess(): ApiAccess = AccessResolver.resolveStt(
        providerId = sttProviderId,
        baseUrl = apiBaseUrl,
        apiKey = apiKey,
        model = apiModel,
        readTimeoutSec = apiReadTimeoutSec,
        serverModels = modelCache,
    )

    /**
     * Zugang fuer die Textverbesserung. Mit [mode] (der wirksamen Stufe des Auftrags) gilt das Modell
     * dieser Stufe; ohne — Bereitschaft, Tastatur, "Zugang pruefen" — das des Zugangs.
     */
    fun llmAccess(mode: RefineMode? = null): ApiAccess = AccessResolver.resolveLlm(
        stt = sttAccess(),
        providerId = llmProviderId,
        baseUrl = llmUrl,
        apiKey = llmKey,
        model = llmModel,
        serverModels = modelCache,
        sttOffline = engine == Engine.OFFLINE,
        stageModel = llmModelFor(mode),
        stage = mode,
    )

    companion object {
        /** Stand der Einstellungen; [PrefsMigration] hebt aeltere an. */
        internal const val PREFS_VERSION = 6

        internal const val KEY_PREFS_VERSION = "prefs_version"
        private const val KEY_LANGUAGE = "language"
        internal const val KEY_ENGINE = "engine"
        internal const val KEY_STT_PROVIDER = "stt_provider"
        internal const val KEY_API_URL = "api_url"
        internal const val KEY_API_KEY = "api_key"
        private const val KEY_API_MODEL = "api_model"
        private const val KEY_API_PROMPT = "api_prompt"
        private const val KEY_VOCAB_FILE_URI = "vocab_file_uri"
        private const val KEY_VOCAB_FILE_NAME = "vocab_file_name"
        private const val KEY_READ_TIMEOUT = "api_read_timeout_sec"
        internal const val KEY_LLM_PROVIDER = "llm_provider"
        private const val KEY_LLM_URL = "llm_url"
        private const val KEY_LLM_KEY = "llm_key"
        internal const val KEY_LLM_MODEL = "llm_model"

        /** + [RefineMode.key] der Stufe: llm_model_polish, llm_model_beautify, llm_model_summarize, llm_model_prompt. */
        private const val KEY_LLM_MODEL_PREFIX = "llm_model_"
        internal const val KEY_REFINE_MODE = "refine_mode"

        /** Fruehere Stufe "Absaetze" in [KEY_REFINE_MODE] — gilt als Glaetten. */
        private const val LEGACY_PARAGRAPHS = "paragraphs"
        internal const val KEY_POLISH_CLEANUP = "polish_cleanup"
        internal const val KEY_SHARE_POLISH_CLEANUP = "share_polish_cleanup"
        internal const val KEY_PARAGRAPHS_POLISH = "paragraphs_polish"
        internal const val KEY_PARAGRAPHS_BEAUTIFY = "paragraphs_beautify"
        internal const val KEY_SUMMARIZE_FORM = "summarize_form"
        internal const val KEY_SHARE_SUMMARIZE_FORM = "share_summarize_form"
        private const val KEY_PROMPT_LEVEL = "refine_prompt_enabled"
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
        private const val KEY_OFFLINE_REFINE = "offline_refine"
        private const val KEY_LOCAL_LLM_MODEL = "local_llm_model"
        private const val KEY_SHARE_HIDE_FILLERS = "share_hide_fillers"
        private const val KEY_SHARE_REFINE_MODE = "share_refine_mode"
        /** Schluessel aus der Zeit des "Sprachauftrags" — bleibt, damit nichts migriert werden muss. */
        private const val KEY_PRO_WIDGETS = "agent_enabled"
        private const val KEY_AGENT_TUTORIAL_SEEN = "agent_tutorial_seen"
        private const val KEY_SERVER_MODELS = "pro_server_models"
        private const val KEY_FLOAT_X = "float_x"
        private const val KEY_FLOAT_Y = "float_y"

        /** Datei der Einstellungen (`shared_prefs/whisperloom.xml`). */
        const val FILE = "whisperloom"

        /**
         * Bis 3.7.0 ein Server fuer alle Widgets. Nur noch fuer
         * [com.chris.whisperloom.agent.WidgetProfileStore.migrateLegacyServer], die ihn in die Profile kopiert.
         */
        const val LEGACY_KEY_AGENT_URL = "agent_url"
        const val LEGACY_KEY_AGENT_TOKEN = "agent_token"

        const val MIN_TIMEOUT_SEC = 30
        const val MAX_TIMEOUT_SEC = 1800

        const val DEFAULT_API_URL = "https://api.openai.com/v1"

        /**
         * Nachfolger von gpt-4o-transcribe (das 2027-02-26 abgeschaltet wird): guenstiger,
         * genauer, erwartet aber `languages[]` statt `language` (siehe ProviderCatalog).
         */
        const val DEFAULT_API_MODEL = "gpt-transcribe"

        /** Empfehlung zum Glaetten bei OpenAI (erstes Katalog-Modell, Stand 2026-10-08). */
        const val DEFAULT_LLM_MODEL = "gpt-6-luna"

        const val DEFAULT_OFFLINE_MODEL = "small"

        /** Gemma 4 E2B: laeuft ab 6 GB RAM (E4B erst ab 8 GB). */
        const val DEFAULT_LOCAL_LLM_MODEL = "gemma4_e2b"

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
