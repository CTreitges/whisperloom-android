package com.chris.whisperloom.sync

import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Die [Prefs] als Baum im Format v1 (Desktop `docs/settings-format.md`), Keys nie. Was Android nicht selbst
 * fuehrt — der Abschnitt `desktop`, unbekannte Felder der obersten Ebene und in `android`, `stt.vocabularyFile`
 * — merkt sich [Prefs.syncCarry] vom letzten Import und gibt der Export unveraendert zurueck.
 */
internal object SyncTree {
    const val DESKTOP = "desktop"
    const val ANDROID = "android"
    const val SECRETS = "secrets"
    const val POLICY = "policy"
    const val SCHEMA_VERSION = "schemaVersion"
    private const val STT = "stt"
    private const val VOCABULARY_FILE = "vocabularyFile"

    /** Felder der obersten Ebene, die Android selbst fuehrt; alles andere (auch `desktop`) wird durchgereicht. */
    private val KNOWN_TOP = setOf(
        SCHEMA_VERSION, "language", "engine", STT, "sttFallback", "llm", "llmFallback",
        "refine", "share", "polish", "offline", "history", "serverModelsEnabled", ANDROID,
    )

    /** Felder in `android`, die Android aus den [Prefs] liest. */
    const val AGENT_ENABLED = "agentEnabled"
    const val WELCOME_SEEN = "welcomeSeen"
    private val KNOWN_ANDROID = setOf(AGENT_ENABLED, WELCOME_SEEN)

    fun of(prefs: Prefs): JSONObject = of(SyncSettings.of(prefs))

    /** Auch die Vorschau des Imports: [SyncSettings.normalized] ergibt hier genau den spaeteren Export. */
    fun of(s: SyncSettings): JSONObject {
        val tree = JSONObject()
            .put(SCHEMA_VERSION, SettingsSync.SCHEMA_VERSION)
            .put("language", s.language)
            .put("engine", s.engine ?: JSONObject.NULL)
            .put(
                STT,
                JSONObject()
                    .put("providerId", s.stt.providerId)
                    .put("baseUrl", s.stt.baseUrl)
                    .put("model", s.stt.model)
                    .put("readTimeoutSec", s.readTimeoutSec)
                    .put("vocabulary", s.vocabulary),
            )
            .put("sttFallback", access(s.sttFallback))
            .put(
                "llm",
                JSONObject()
                    .put("providerId", s.llm.providerId)
                    .put("baseUrl", s.llm.baseUrl)
                    .put("model", s.llm.model)
                    .put("stageModels", stageModels(s)),
            )
            .put("llmFallback", access(s.llmFallback))
            .put(
                "refine",
                JSONObject()
                    .put("stage", s.refineStage)
                    .put("promptEnabled", s.promptEnabled)
                    .put("polishCleanup", s.polishCleanup)
                    .put("summarizeForm", s.summarizeForm)
                    .put("paragraphsPolish", s.paragraphsPolish)
                    .put("paragraphsBeautify", s.paragraphsBeautify),
            )
            .put(
                "share",
                JSONObject()
                    .put("stage", s.shareStage)
                    .put("polishCleanup", s.sharePolishCleanup)
                    .put("summarizeForm", s.shareSummarizeForm)
                    .put("hideFillers", s.shareHideFillers),
            )
            .put(
                "polish",
                JSONObject()
                    .put("removeFillers", s.removeFillers)
                    .put("autoCapitalize", s.autoCapitalize)
                    .put("trailingSpace", s.trailingSpace)
                    // Android speichert eine Menge: sortiert, damit der Export stabil ist.
                    .put("customFillers", JSONArray(s.customFillers.sorted()))
                    .put("disabledFillers", JSONArray(s.disabledFillers.sorted())),
            )
            .put(
                "offline",
                JSONObject()
                    .put("model", s.offlineModel)
                    .put("accurate", s.offlineAccurate)
                    .put("refine", s.offlineRefine)
                    .put("localLlmModel", s.localLlmModel),
            )
            .put("history", JSONObject().put("enabled", s.historyEnabled).put("size", s.historySize))
            .put("serverModelsEnabled", s.serverModelsEnabled)
        SyncJson.merge(tree, s.carry)
        val android = tree.optJSONObject(ANDROID) ?: JSONObject().also { tree.put(ANDROID, it) }
        android.put(AGENT_ENABLED, s.agentEnabled).put(WELCOME_SEEN, s.welcomeSeen)
        return tree
    }

    private fun access(a: SyncAccess): JSONObject =
        JSONObject().put("enabled", a.enabled).put("providerId", a.providerId).put("baseUrl", a.baseUrl).put("model", a.model)

    /** Nur Stufen mit eigenem Modell; fehlende Stufe = Standard. */
    private fun stageModels(s: SyncSettings): JSONObject = JSONObject().also { o ->
        RefineMode.MODEL_STAGES.forEach { stage -> s.stageModels[stage.key]?.takeIf { it.isNotEmpty() }?.let { o.put(stage.key, it) } }
    }

    /** Der gemerkte Rest des letzten Imports; ein kaputter Eintrag zaehlt als leer. */
    fun carry(prefs: Prefs): JSONObject =
        prefs.syncCarry.takeIf { it.isNotEmpty() }?.let {
            try {
                JSONObject(it)
            } catch (_: JSONException) {
                null
            }
        } ?: JSONObject()

    /** Was aus dem gemischten Baum [tree] durchgereicht werden muss (siehe Klassenkommentar). */
    fun carryOf(tree: JSONObject): JSONObject {
        val carry = JSONObject()
        for (key in tree.keys()) {
            val value = tree.get(key)
            when (key) {
                ANDROID -> (value as? JSONObject)?.let { section ->
                    val rest = JSONObject()
                    section.keys().forEach { if (it !in KNOWN_ANDROID) rest.put(it, section.get(it)) }
                    if (rest.length() > 0) carry.put(ANDROID, rest)
                }
                STT -> (value as? JSONObject)?.opt(VOCABULARY_FILE)?.let { carry.put(STT, JSONObject().put(VOCABULARY_FILE, it)) }
                in KNOWN_TOP, SECRETS, POLICY -> Unit
                else -> carry.put(key, value)
            }
        }
        return carry
    }
}
