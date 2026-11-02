package com.chris.whisperloom.sync

import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
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

    fun of(prefs: Prefs): JSONObject {
        val tree = JSONObject()
            .put(SCHEMA_VERSION, SettingsSync.SCHEMA_VERSION)
            .put("language", prefs.language)
            .put("engine", prefs.engine?.key ?: JSONObject.NULL)
            .put(
                STT,
                JSONObject()
                    .put("providerId", prefs.sttProviderId)
                    .put("baseUrl", prefs.apiBaseUrl)
                    .put("model", prefs.apiModel)
                    .put("readTimeoutSec", prefs.apiReadTimeoutSec)
                    .put("vocabulary", prefs.apiPrompt),
            )
            .put("sttFallback", access(prefs.sttFallbackEnabled, prefs.sttFallbackProviderId, prefs.sttFallbackUrl, prefs.sttFallbackModel))
            .put(
                "llm",
                JSONObject()
                    .put("providerId", prefs.llmProviderId)
                    .put("baseUrl", prefs.llmUrl)
                    .put("model", prefs.llmModel)
                    .put("stageModels", stageModels(prefs)),
            )
            .put("llmFallback", access(prefs.llmFallbackEnabled, prefs.llmFallbackProviderId, prefs.llmFallbackUrl, prefs.llmFallbackModel))
            .put(
                "refine",
                JSONObject()
                    .put("stage", prefs.refineMode.key)
                    .put("promptEnabled", prefs.promptLevelEnabled)
                    .put("polishCleanup", prefs.polishCleanupFor(RefineWay.DICTATION).key)
                    .put("summarizeForm", prefs.summarizeFormFor(RefineWay.DICTATION).key)
                    .put("paragraphsPolish", prefs.paragraphsFor(RefineMode.POLISH))
                    .put("paragraphsBeautify", prefs.paragraphsFor(RefineMode.BEAUTIFY)),
            )
            .put(
                "share",
                JSONObject()
                    .put("stage", prefs.shareRefineMode.key)
                    .put("polishCleanup", prefs.polishCleanupFor(RefineWay.SHARE).key)
                    .put("summarizeForm", prefs.summarizeFormFor(RefineWay.SHARE).key)
                    .put("hideFillers", prefs.shareHideFillers),
            )
            .put(
                "polish",
                JSONObject()
                    .put("removeFillers", prefs.removeFillers)
                    .put("autoCapitalize", prefs.autoCapitalize)
                    .put("trailingSpace", prefs.trailingSpace)
                    // Android speichert eine Menge: sortiert, damit der Export stabil ist.
                    .put("customFillers", JSONArray(prefs.customFillers.sorted()))
                    .put("disabledFillers", JSONArray(prefs.disabledFillers.sorted())),
            )
            .put(
                "offline",
                JSONObject()
                    .put("model", prefs.offlineModel)
                    .put("accurate", prefs.offlineAccurate)
                    .put("refine", prefs.offlineRefine.key)
                    .put("localLlmModel", prefs.localLlmModel),
            )
            .put("history", JSONObject().put("enabled", prefs.historyEnabled).put("size", prefs.historySize))
            .put("serverModelsEnabled", prefs.serverModelsEnabled)
        SyncJson.merge(tree, carry(prefs))
        val android = tree.optJSONObject(ANDROID) ?: JSONObject().also { tree.put(ANDROID, it) }
        android.put(AGENT_ENABLED, prefs.proWidgetsEnabled).put(WELCOME_SEEN, prefs.welcomeSeen)
        return tree
    }

    private fun access(enabled: Boolean, providerId: String, baseUrl: String, model: String): JSONObject =
        JSONObject().put("enabled", enabled).put("providerId", providerId).put("baseUrl", baseUrl).put("model", model)

    /** Nur Stufen mit eigenem Modell; fehlende Stufe = Standard. */
    private fun stageModels(prefs: Prefs): JSONObject = JSONObject().also { o ->
        RefineMode.MODEL_STAGES.forEach { stage -> prefs.llmModelFor(stage).takeIf { it.isNotEmpty() }?.let { o.put(stage.key, it) } }
    }

    /** Der gemerkte Rest des letzten Imports; ein kaputter Eintrag zaehlt als leer. */
    private fun carry(prefs: Prefs): JSONObject =
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
