package com.chris.whisperloom.ui.state

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.PolishCleanup
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.ProFeature
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import com.chris.whisperloom.Refinement
import com.chris.whisperloom.SummarizeForm
import com.chris.whisperloom.api.AccessResolver
import com.chris.whisperloom.api.ApiAccess
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Compose-Spiegel der [Prefs]: jede Zuweisung schreibt SOFORT in die SharedPreferences
 * (UX-Spec §1.3 — kein Speichern-Button) und stoesst ueber den Snapshot-State die
 * Neuzeichnung aller Screens an, die den Wert lesen. Eine Instanz pro Activity.
 */
class PrefsState(val prefs: Prefs) {

    // Muss VOR den Feldern stehen: Kotlin initialisiert in Deklarationsreihenfolge, und
    // jedes pref(...) traegt sich hier schon waehrend des Konstruktors ein.
    private val fields = mutableListOf<PrefField<*>>()

    var language: String by pref({ prefs.language }) { prefs.language = it }
    var engine: Engine? by pref({ prefs.engine }) { prefs.engine = it }

    // Transkriptions-Zugang (roh; "" = Anbieter-Default, siehe AccessResolver)
    var sttProviderId: String by pref({ prefs.sttProviderId }) { prefs.sttProviderId = it }
    var apiBaseUrl: String by pref({ prefs.apiBaseUrl }) { prefs.apiBaseUrl = it }
    var apiKey: String by pref({ prefs.apiKey }) { prefs.apiKey = it }
    var apiModel: String by pref({ prefs.apiModel }) { prefs.apiModel = it }
    var apiPrompt: String by pref({ prefs.apiPrompt }) { prefs.apiPrompt = it }
    var vocabFileUri: String by pref({ prefs.vocabFileUri }) { prefs.vocabFileUri = it }
    var vocabFileName: String by pref({ prefs.vocabFileName }) { prefs.vocabFileName = it }

    // Textverbesserung
    var llmProviderId: String by pref({ prefs.llmProviderId }) { prefs.llmProviderId = it }
    var llmUrl: String by pref({ prefs.llmUrl }) { prefs.llmUrl = it }
    var llmKey: String by pref({ prefs.llmKey }) { prefs.llmKey = it }
    var llmModel: String by pref({ prefs.llmModel }) { prefs.llmModel = it }
    var refineMode: RefineMode by pref({ prefs.refineMode }) { prefs.refineMode = it }
    var promptLevelEnabled: Boolean by pref({ prefs.promptLevelEnabled }) { prefs.promptLevelEnabled = it }
    var shareRefineMode: RefineMode by pref({ prefs.shareRefineMode }) { prefs.shareRefineMode = it }

    // Stufen-Einstellungen je Weg (Bereinigung, Form) und Absaetze des Diktats je Stufe
    private val polishCleanups: Map<RefineWay, PrefField<PolishCleanup>> = RefineWay.entries.associateWith { way ->
        PrefField({ prefs.polishCleanupFor(way) }) { prefs.setPolishCleanupFor(way, it) }.also { fields += it }
    }
    private val summarizeForms: Map<RefineWay, PrefField<SummarizeForm>> = RefineWay.entries.associateWith { way ->
        PrefField({ prefs.summarizeFormFor(way) }) { prefs.setSummarizeFormFor(way, it) }.also { fields += it }
    }
    private val paragraphs: Map<RefineMode, PrefField<Boolean>> = RefineMode.PARAGRAPH_STAGES.associateWith { stage ->
        PrefField({ prefs.paragraphsFor(stage) }) { prefs.setParagraphsFor(stage, it) }.also { fields += it }
    }

    // Modell je Stufe (RefineMode.MODEL_STAGES; "" = Standard)
    private val stageModels: Map<RefineMode, PrefField<String>> = RefineMode.MODEL_STAGES.associateWith { stage ->
        PrefField({ prefs.llmModelFor(stage) }) { prefs.setLlmModelFor(stage, it) }.also { fields += it }
    }

    // Regeln ohne KI
    var removeFillers: Boolean by pref({ prefs.removeFillers }) { prefs.removeFillers = it }
    var autoCapitalize: Boolean by pref({ prefs.autoCapitalize }) { prefs.autoCapitalize = it }
    var trailingSpace: Boolean by pref({ prefs.trailingSpace }) { prefs.trailingSpace = it }
    var customFillers: Set<String> by pref({ prefs.customFillers }) { prefs.customFillers = it }
    var disabledFillers: Set<String> by pref({ prefs.disabledFillers }) { prefs.disabledFillers = it }

    // Assistent-Flags
    var welcomeSeen: Boolean by pref({ prefs.welcomeSeen }) { prefs.welcomeSeen = it }
    var overlaySkipped: Boolean by pref({ prefs.overlaySkipped }) { prefs.overlaySkipped = it }
    var a11ySkipped: Boolean by pref({ prefs.a11ySkipped }) { prefs.a11ySkipped = it }
    var notifSkipped: Boolean by pref({ prefs.notifSkipped }) { prefs.notifSkipped = it }
    var keyboardSkipped: Boolean by pref({ prefs.keyboardSkipped }) { prefs.keyboardSkipped = it }
    var tutorialSeen: Boolean by pref({ prefs.tutorialSeen }) { prefs.tutorialSeen = it }

    // Offline
    var offlineModel: String by pref({ prefs.offlineModel }) { prefs.offlineModel = it }
    var offlineRefine: OfflineRefineRule by pref({ prefs.offlineRefine }) { prefs.offlineRefine = it }
    var localLlmModel: String by pref({ prefs.localLlmModel }) { prefs.localLlmModel = it }

    // Pro-Funktionen ("Erweitert"); Server und Token stehen je Widget im Profil, nicht hier.
    var proWidgetsEnabled: Boolean by pref({ prefs.proWidgetsEnabled }) { prefs.proWidgetsEnabled = it }
    var serverModelsEnabled: Boolean by pref({ prefs.serverModelsEnabled }) { prefs.serverModelsEnabled = it }
    var agentTutorialSeen: Boolean by pref({ prefs.agentTutorialSeen }) { prefs.agentTutorialSeen = it }

    // Verlauf: nur lesen. Ausschalten loescht, Verkleinern kuerzt — beides ueber
    // com.chris.whisperloom.history.History (setEnabled, setSize); der Horcher zieht die Spiegel nach.
    val historyEnabled: Boolean by pref({ prefs.historyEnabled }) {}
    val historySize: Int by pref({ prefs.historySize }) {}

    /** Wie [Prefs.isEnabled], aber ueber die Spiegel — damit Compose Aenderungen sieht. */
    fun isEnabled(feature: ProFeature): Boolean = when (feature) {
        ProFeature.WIDGETS -> proWidgetsEnabled
        ProFeature.PROMPT -> promptLevelEnabled
        ProFeature.SERVER_MODELS -> serverModelsEnabled
    }

    /** Schalter in "Erweitert": schreibt sofort durch, wie jede Zuweisung hier. */
    fun setEnabled(feature: ProFeature, on: Boolean) {
        when (feature) {
            ProFeature.WIDGETS -> proWidgetsEnabled = on
            ProFeature.PROMPT -> promptLevelEnabled = on
            ProFeature.SERVER_MODELS -> serverModelsEnabled = on
        }
    }

    /** Wie [Prefs.refineModeFor], ueber die Spiegel. */
    fun refineModeFor(way: RefineWay): RefineMode = when (way) {
        RefineWay.DICTATION -> refineMode
        RefineWay.SHARE -> shareRefineMode
    }

    /** Waehlt die Stufe des Wegs ([refineMode] bzw. [shareRefineMode]). */
    fun setRefineModeFor(way: RefineWay, stage: RefineMode) {
        when (way) {
            RefineWay.DICTATION -> refineMode = stage
            RefineWay.SHARE -> shareRefineMode = stage
        }
    }

    /** Wie [Prefs.polishCleanupFor], ueber die Spiegel. */
    fun polishCleanupFor(way: RefineWay): PolishCleanup = polishCleanups.getValue(way).value

    /** Schreibt sofort durch, wie jede Zuweisung hier. */
    fun setPolishCleanupFor(way: RefineWay, cleanup: PolishCleanup) {
        polishCleanups.getValue(way).value = cleanup
    }

    /** Wie [Prefs.summarizeFormFor], ueber die Spiegel. */
    fun summarizeFormFor(way: RefineWay): SummarizeForm = summarizeForms.getValue(way).value

    fun setSummarizeFormFor(way: RefineWay, form: SummarizeForm) {
        summarizeForms.getValue(way).value = form
    }

    /** Wie [Prefs.paragraphsFor], ueber die Spiegel: Stufen ohne Schalter gliedern immer. */
    fun paragraphsFor(stage: RefineMode): Boolean = paragraphsField(stage)?.value ?: true

    /** @throws IllegalArgumentException fuer eine Stufe ohne Schalter "Absaetze" (wie [Prefs.setParagraphsFor]). */
    fun setParagraphsFor(stage: RefineMode, on: Boolean) {
        requireNotNull(paragraphsField(stage)) { "Stufe ${stage.name} hat keinen Schalter Absaetze" }.value = on
    }

    /** "Lesbar" ist Glaetten — es teilt dessen Schalter. */
    private fun paragraphsField(stage: RefineMode): PrefField<Boolean>? = stage.modelStage?.let { paragraphs[it] }

    /** Wie [Prefs.refinementFor], ueber die Spiegel. */
    fun refinementFor(way: RefineWay): Refinement {
        val stage = refineModeFor(way)
        return Refinement.of(way, stage, polishCleanupFor(way), paragraphsFor(stage), summarizeFormFor(way))
    }

    /** Der Nutzer hat einen eigenen LLM-Zugang gewaehlt (sonst gilt der Erkennungs-Zugang). */
    val llmUseOwn: Boolean get() = llmProviderId != AccessResolver.LLM_SAME

    /** Wie [Prefs.llmModelFor], ueber die Spiegel: eigenes Modell der Stufe, "" = Standard. */
    fun llmModelFor(mode: RefineMode?): String = mode?.modelStage?.let { stageModels.getValue(it).value }.orEmpty()

    /** Schreibt sofort durch, wie jede Zuweisung hier; "" = zurueck auf Standard. */
    fun setLlmModelFor(mode: RefineMode, model: String) {
        stageModels.getValue(requireNotNull(mode.modelStage) { "Stufe aus hat kein Modell" }).value = model
    }

    /** Wie [Prefs.clearLlmModels] (Anbieterwechsel) — die Spiegel ziehen sofort nach. */
    fun clearLlmModels() {
        prefs.clearLlmModels()
        fields.forEach { it.reload() }
    }

    /** Aufgeloester Transkriptions-Zugang — liest die Spiegel-Felder, damit Compose Aenderungen sieht. */
    fun sttAccess(): ApiAccess = AccessResolver.resolveStt(
        providerId = sttProviderId,
        baseUrl = apiBaseUrl,
        apiKey = apiKey,
        model = apiModel,
        readTimeoutSec = prefs.apiReadTimeoutSec,
        serverModels = prefs.modelCache,
    )

    /** Wie [Prefs.llmAccess]: mit [mode] gilt das Modell dieser Stufe. */
    fun llmAccess(mode: RefineMode? = null): ApiAccess = resolveLlm(llmModelFor(mode), mode)

    /**
     * Der Zugang, den die Stufe von [mode] ohne eigenes Modell haette: das Modell des Zugangs, sonst
     * die Empfehlung des Anbieters fuer die Stufe — fuer Labels wie "Standard · <Modell>".
     */
    fun standardLlmAccess(mode: RefineMode): ApiAccess = resolveLlm(stageModel = "", mode)

    private fun resolveLlm(stageModel: String, mode: RefineMode?): ApiAccess = AccessResolver.resolveLlm(
        stt = sttAccess(),
        providerId = llmProviderId,
        baseUrl = llmUrl,
        apiKey = llmKey,
        model = llmModel,
        serverModels = prefs.modelCache,
        sttOffline = engine == Engine.OFFLINE,
        stageModel = stageModel,
        stage = mode,
    )

    /** Position des schwebenden Knopfs auf den Default (E3 "Position zuruecksetzen"). */
    fun resetBubblePosition() {
        prefs.floatX = Prefs.DEFAULT_FLOAT_X
        prefs.floatY = Prefs.DEFAULT_FLOAT_Y
    }

    /**
     * Holt alle Spiegel frisch aus den [Prefs]. Noetig, weil die Diktat-Tastatur als eigener
     * Dienst direkt schreibt (Schnellzugriff auf die Textverbesserung): ohne das zeigte der
     * Einstellungs-Screen danach weiter den alten Wert.
     *
     * Als Feld gehalten, nicht als Lambda an Ort und Stelle — SharedPreferences haelt
     * Horcher nur schwach.
     */
    private val onExternalChange = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        fields.forEach { it.reload() }
    }

    init {
        prefs.observe(onExternalChange)
    }

    /** Fuer Tests und kurzlebige Instanzen; eine Activity-lange Instanz braucht es nicht. */
    fun dispose() {
        prefs.unobserve(onExternalChange)
    }

    private fun <T> pref(read: () -> T, write: (T) -> Unit): ReadWriteProperty<Any?, T> =
        PrefField(read, write).also { fields += it }

    private class PrefField<T>(
        private val read: () -> T,
        private val write: (T) -> Unit,
    ) : ReadWriteProperty<Any?, T> {
        private val state = mutableStateOf(read())

        var value: T
            get() = state.value
            set(v) {
                write(v)
                state.value = v
            }

        /** Nur schreiben, wenn sich wirklich etwas geaendert hat — sonst zeichnet Compose umsonst. */
        fun reload() {
            val aktuell = read()
            if (state.value != aktuell) state.value = aktuell
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this.value = value
        }
    }
}
