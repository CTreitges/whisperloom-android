package com.chris.whisperloom.sync

import android.content.Context
import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.PolishCleanup
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import com.chris.whisperloom.SummarizeForm
import com.chris.whisperloom.api.AccessResolver
import com.chris.whisperloom.api.ProviderCatalog
import com.chris.whisperloom.history.History
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException

/** Ein Zugang im Format v1 (`stt`, `sttFallback`, `llm`, `llmFallback`). */
internal data class SyncAccess(val enabled: Boolean, val providerId: String, val baseUrl: String, val model: String)

/**
 * Gelesene und gepruefte Einstellungen eines Imports (schon mit den aktuellen gemischt), bereit fuer [writeTo].
 * Pruefregeln und Meldungen wie `SyncValidator` im Desktop-Repo.
 */
internal data class SyncSettings(
    val language: String,
    val engine: String?,
    val stt: SyncAccess,
    val readTimeoutSec: Int,
    val vocabulary: String,
    val sttFallback: SyncAccess,
    val llm: SyncAccess,
    val stageModels: Map<String, String>,
    val llmFallback: SyncAccess,
    val refineStage: String,
    val promptEnabled: Boolean,
    val polishCleanup: String,
    val summarizeForm: String,
    val paragraphsPolish: Boolean,
    val paragraphsBeautify: Boolean,
    val shareStage: String,
    val sharePolishCleanup: String,
    val shareSummarizeForm: String,
    val shareHideFillers: Boolean,
    val removeFillers: Boolean,
    val autoCapitalize: Boolean,
    val trailingSpace: Boolean,
    val customFillers: List<String>,
    val disabledFillers: List<String>,
    val offlineModel: String,
    val offlineAccurate: Boolean,
    val offlineRefine: String,
    val localLlmModel: String,
    val historyEnabled: Boolean,
    val historySize: Int,
    val serverModelsEnabled: Boolean,
    val agentEnabled: Boolean,
    val welcomeSeen: Boolean,
    val carry: JSONObject,
) {

    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        if (language != "auto" && !(language.length in 2..3 && language.all { it in 'a'..'z' })) {
            errors += "language: \"auto\" oder ISO-Code (z. B. \"de\")"
        }
        if (engine != null && Engine.fromKey(engine) == null) errors += "engine: \"online\" oder \"offline\""

        provider(errors, "stt.providerId", stt.providerId, stt = true)
        url(errors, "stt.baseUrl", stt.baseUrl)
        if (readTimeoutSec != 0 && readTimeoutSec !in Prefs.MIN_TIMEOUT_SEC..Prefs.MAX_TIMEOUT_SEC) {
            errors += "stt.readTimeoutSec: 0 oder 30–1800"
        }
        provider(errors, "sttFallback.providerId", sttFallback.providerId, stt = true)
        url(errors, "sttFallback.baseUrl", sttFallback.baseUrl)

        if (llm.providerId != AccessResolver.LLM_SAME) provider(errors, "llm.providerId", llm.providerId, stt = false)
        url(errors, "llm.baseUrl", llm.baseUrl)
        val modelStages = RefineMode.MODEL_STAGES.map { it.key }
        stageModels.keys.filter { it !in modelStages }.forEach {
            errors += "llm.stageModels.$it: Stufe unbekannt (${modelStages.joinToString(", ")})"
        }
        provider(errors, "llmFallback.providerId", llmFallback.providerId, stt = false)
        url(errors, "llmFallback.baseUrl", llmFallback.baseUrl)

        oneOf(errors, "refine.stage", refineStage, RefineMode.entries.map { it.key } + LEGACY_PARAGRAPHS)
        oneOf(errors, "refine.polishCleanup", polishCleanup, PolishCleanup.entries.map { it.key })
        oneOf(errors, "refine.summarizeForm", summarizeForm, SummarizeForm.entries.map { it.key })
        oneOf(errors, "share.stage", shareStage, RefineMode.SETTINGS.map { it.key })
        oneOf(errors, "share.polishCleanup", sharePolishCleanup, PolishCleanup.entries.map { it.key })
        oneOf(errors, "share.summarizeForm", shareSummarizeForm, SummarizeForm.entries.map { it.key })
        oneOf(errors, "offline.refine", offlineRefine, OfflineRefineRule.entries.map { it.key })
        if (historySize !in Prefs.HISTORY_SIZES) errors += "history.size: eine von ${Prefs.HISTORY_SIZES.joinToString(", ")}"
        return errors
    }

    /**
     * Schreibt alles in die vorhandenen [Prefs]-Schluessel. Keys bleiben, ausser denen in [droppedKeys]
     * ([SyncAccessRules.droppedKeys]: sie gingen sonst an ein neues Ziel). Den Verlauf schaltet und
     * kuerzt [History] (Aus loescht, Verkleinern kuerzt — wie die Schalter in den Einstellungen).
     */
    fun writeTo(context: Context, prefs: Prefs, droppedKeys: List<String>) {
        // Zuerst: Zu keinem Zeitpunkt darf ein neues Ziel neben dem alten Key stehen.
        SyncAccessRules.clearKeys(prefs, droppedKeys)
        prefs.language = language
        // null = "noch nicht gewaehlt": steht in jedem Desktop-Export, der die Erkennung nie umgestellt hat. Es
        // setzt eine getroffene Wahl nicht zurueck — sonst stuende auf dem Telefon wieder die Einrichtung an.
        Engine.fromKey(engine)?.let { prefs.engine = it }
        prefs.sttProviderId = stt.providerId
        prefs.apiBaseUrl = stt.baseUrl
        prefs.apiModel = stt.model
        prefs.apiReadTimeoutSec = readTimeoutSec
        prefs.apiPrompt = vocabulary
        prefs.sttFallbackEnabled = sttFallback.enabled
        prefs.sttFallbackProviderId = sttFallback.providerId
        prefs.sttFallbackUrl = sttFallback.baseUrl
        prefs.sttFallbackModel = sttFallback.model

        prefs.llmProviderId = llm.providerId
        prefs.llmUrl = llm.baseUrl
        prefs.llmModel = llm.model
        RefineMode.MODEL_STAGES.forEach { prefs.setLlmModelFor(it, stageModels[it.key].orEmpty()) }
        prefs.llmFallbackEnabled = llmFallback.enabled
        prefs.llmFallbackProviderId = llmFallback.providerId
        prefs.llmFallbackUrl = llmFallback.baseUrl
        prefs.llmFallbackModel = llmFallback.model

        // "Lesbar" ist auf Android Glaetten mit der Bereinigung "Lesbar" (RefineMode.READABLE), "paragraphs" Glaetten.
        val stage = when (refineStage) {
            LEGACY_PARAGRAPHS, RefineMode.READABLE.key -> RefineMode.POLISH
            else -> RefineMode.fromKey(refineStage)
        }
        val cleanup = if (refineStage == RefineMode.READABLE.key) PolishCleanup.READABLE else PolishCleanup.fromKey(polishCleanup)
        prefs.promptLevelEnabled = promptEnabled
        prefs.refineMode = stage
        prefs.setPolishCleanupFor(RefineWay.DICTATION, cleanup)
        prefs.setSummarizeFormFor(RefineWay.DICTATION, SummarizeForm.fromKey(summarizeForm))
        prefs.setParagraphsFor(RefineMode.POLISH, paragraphsPolish)
        prefs.setParagraphsFor(RefineMode.BEAUTIFY, paragraphsBeautify)
        prefs.shareRefineMode = RefineMode.fromKey(shareStage)
        prefs.setPolishCleanupFor(RefineWay.SHARE, PolishCleanup.fromKey(sharePolishCleanup))
        prefs.setSummarizeFormFor(RefineWay.SHARE, SummarizeForm.fromKey(shareSummarizeForm))
        prefs.shareHideFillers = shareHideFillers

        prefs.removeFillers = removeFillers
        prefs.autoCapitalize = autoCapitalize
        prefs.trailingSpace = trailingSpace
        prefs.customFillers = customFillers.toSet()
        prefs.disabledFillers = disabledFillers.toSet()

        prefs.offlineModel = offlineModel
        prefs.offlineAccurate = offlineAccurate
        prefs.offlineRefine = OfflineRefineRule.fromKey(offlineRefine)
        prefs.localLlmModel = localLlmModel
        prefs.serverModelsEnabled = serverModelsEnabled
        prefs.proWidgetsEnabled = agentEnabled
        prefs.welcomeSeen = welcomeSeen
        prefs.syncCarry = if (carry.length() == 0) "" else carry.toString()

        if (historySize != prefs.historySize) History.setSize(context, historySize)
        if (historyEnabled != prefs.historyEnabled) History.setEnabled(context, historyEnabled)
    }

    companion object {
        private const val LEGACY_PARAGRAPHS = "paragraphs"

        /** Liest den gemischten Baum; Typfehler landen als "Falscher Typ bei <pfad>" in [errors], dann null. */
        fun read(tree: JSONObject, errors: MutableCollection<String>): SyncSettings? {
            val r = Reader(tree)
            listOf(SyncTree.DESKTOP, SyncTree.ANDROID).forEach { r.section(it) }
            val settings = SyncSettings(
                language = r.str("language"),
                engine = r.strOrNull("engine"),
                stt = r.access("stt", switch = false),
                readTimeoutSec = r.int("stt.readTimeoutSec"),
                vocabulary = r.str("stt.vocabulary"),
                sttFallback = r.access("sttFallback"),
                llm = r.access("llm", switch = false),
                stageModels = r.strMap("llm.stageModels"),
                llmFallback = r.access("llmFallback"),
                refineStage = r.str("refine.stage"),
                promptEnabled = r.bool("refine.promptEnabled"),
                polishCleanup = r.str("refine.polishCleanup"),
                summarizeForm = r.str("refine.summarizeForm"),
                paragraphsPolish = r.bool("refine.paragraphsPolish"),
                paragraphsBeautify = r.bool("refine.paragraphsBeautify"),
                shareStage = r.str("share.stage"),
                sharePolishCleanup = r.str("share.polishCleanup"),
                shareSummarizeForm = r.str("share.summarizeForm"),
                shareHideFillers = r.bool("share.hideFillers"),
                removeFillers = r.bool("polish.removeFillers"),
                autoCapitalize = r.bool("polish.autoCapitalize"),
                trailingSpace = r.bool("polish.trailingSpace"),
                customFillers = r.strList("polish.customFillers"),
                disabledFillers = r.strList("polish.disabledFillers"),
                offlineModel = r.str("offline.model"),
                offlineAccurate = r.bool("offline.accurate"),
                offlineRefine = r.str("offline.refine"),
                localLlmModel = r.str("offline.localLlmModel"),
                historyEnabled = r.bool("history.enabled"),
                historySize = r.int("history.size"),
                serverModelsEnabled = r.bool("serverModelsEnabled"),
                agentEnabled = r.bool("android.${SyncTree.AGENT_ENABLED}"),
                welcomeSeen = r.bool("android.${SyncTree.WELCOME_SEEN}"),
                carry = SyncTree.carryOf(tree),
            )
            if (tree.optJSONObject("stt")?.has("vocabularyFile") == true) r.str("stt.vocabularyFile")
            errors += r.errors
            return settings.takeIf { r.errors.isEmpty() }
        }

        private fun provider(errors: MutableList<String>, path: String, id: String, stt: Boolean) {
            val p = ProviderCatalog.find(id)
            if (p == null || (if (stt) !p.hasStt else !p.hasLlm)) {
                val allowed = (if (stt) ProviderCatalog.sttProviders else ProviderCatalog.llmProviders).map { it.id }
                errors += "$path: Anbieter unbekannt (${allowed.joinToString(", ")})"
            }
        }

        private fun url(errors: MutableList<String>, path: String, url: String) {
            // Gekuerzt und Schema ohne Gross-/Kleinschreibung wie zur Laufzeit (ServerUrlCheck) und auf dem Desktop
            // (System.Uri) — sonst liesse sich der eigene Export mit "https://x/v1 " nicht wieder importieren.
            if (url.isBlank()) return
            val ok = try {
                val u = URI(url.trim())
                u.isAbsolute && u.scheme.lowercase() in setOf("http", "https") && !u.host.isNullOrEmpty()
            } catch (_: URISyntaxException) {
                false
            }
            if (!ok) errors += "$path: leer oder http(s)-URL"
        }

        private fun oneOf(errors: MutableList<String>, path: String, value: String, allowed: List<String>) {
            if (value !in allowed) errors += "$path: eines von ${allowed.joinToString(", ")}"
        }
    }
}

/** Liest Werte nach Pfad ("stt.model") und sammelt Typfehler; liefert dann einen Platzhalter. */
private class Reader(private val root: JSONObject) {
    val errors = linkedSetOf<String>()

    fun str(path: String): String = get(path) as? String ?: "".also { typeError(path) }

    fun strOrNull(path: String): String? = when (val v = get(path)) {
        JSONObject.NULL -> null
        is String -> v
        else -> null.also { typeError(path) }
    }

    fun bool(path: String): Boolean = get(path) as? Boolean ?: false.also { typeError(path) }

    fun int(path: String): Int = when (val v = get(path)) {
        is Int -> v
        is Long -> if (v in Int.MIN_VALUE..Int.MAX_VALUE) v.toInt() else 0.also { typeError(path) }
        else -> 0.also { typeError(path) }
    }

    fun strList(path: String): List<String> {
        val a = get(path) as? JSONArray ?: return emptyList<String>().also { typeError(path) }
        return (0 until a.length()).map { a.get(it) as? String ?: return emptyList<String>().also { typeError(path) } }
    }

    fun strMap(path: String): Map<String, String> {
        val o = get(path) as? JSONObject ?: return emptyMap<String, String>().also { typeError(path) }
        return o.keys().asSequence().associateWith { k -> o.get(k) as? String ?: "".also { typeError("$path.$k") } }
    }

    /** [switch] = der Zugang hat einen Schalter `enabled` (die Fallbacks); sonst gilt er als an. */
    fun access(section: String, switch: Boolean = true): SyncAccess = SyncAccess(
        enabled = if (switch) bool("$section.enabled") else true,
        providerId = str("$section.providerId"),
        baseUrl = str("$section.baseUrl"),
        model = str("$section.model"),
    )

    /** Abschnitt, der fehlen darf, aber sonst ein Objekt sein muss. */
    fun section(name: String) {
        if (root.has(name) && root.get(name) !is JSONObject) typeError(name)
    }

    /** Wert am Pfad; ist ein Abschnitt unterwegs kein Objekt, zaehlt das als Typfehler des Abschnitts. */
    private fun get(path: String): Any? {
        var node: Any? = root
        var at = ""
        for (part in path.split('.')) {
            val obj = node as? JSONObject
            if (obj == null) {
                typeError(at)
                return MISSING
            }
            at = if (at.isEmpty()) part else "$at.$part"
            node = obj.opt(part)
        }
        return node
    }

    private fun typeError(path: String) {
        // Ein kaputter Abschnitt meldet sich einmal, nicht mit jedem Feld darunter.
        if (errors.none { path.startsWith(it.removePrefix(PREFIX) + ".") }) errors += PREFIX + path
    }

    private companion object {
        const val PREFIX = "Falscher Typ bei "
        val MISSING = Any()
    }
}
