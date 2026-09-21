package com.chris.whisperloom.ui.state

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.agent.AgentUrlCheck
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

    // Textverbesserung
    var llmProviderId: String by pref({ prefs.llmProviderId }) { prefs.llmProviderId = it }
    var llmUrl: String by pref({ prefs.llmUrl }) { prefs.llmUrl = it }
    var llmKey: String by pref({ prefs.llmKey }) { prefs.llmKey = it }
    var llmModel: String by pref({ prefs.llmModel }) { prefs.llmModel = it }
    var refineMode: RefineMode by pref({ prefs.refineMode }) { prefs.refineMode = it }
    var smartFillers: Boolean by pref({ prefs.smartFillers }) { prefs.smartFillers = it }

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

    // Sprachauftrag
    var agentEnabled: Boolean by pref({ prefs.agentEnabled }) { prefs.agentEnabled = it }
    var agentUrl: String by pref({ prefs.agentUrl }) { prefs.agentUrl = it }
    var agentToken: String by pref({ prefs.agentToken }) { prefs.agentToken = it }
    var agentTutorialSeen: Boolean by pref({ prefs.agentTutorialSeen }) { prefs.agentTutorialSeen = it }

    /** Wie [Prefs.agentReady], aber ueber die Spiegel — damit Compose Aenderungen sieht. */
    val agentReady: Boolean
        get() = agentEnabled && agentToken.isNotBlank() && AgentUrlCheck.isValid(agentUrl)

    /** Der Nutzer hat einen eigenen LLM-Zugang gewaehlt (sonst gilt der Erkennungs-Zugang). */
    val llmUseOwn: Boolean get() = llmProviderId != AccessResolver.LLM_SAME

    /** Aufgeloester Transkriptions-Zugang — liest die Spiegel-Felder, damit Compose Aenderungen sieht. */
    fun sttAccess(): ApiAccess = AccessResolver.resolveStt(
        providerId = sttProviderId,
        baseUrl = apiBaseUrl,
        apiKey = apiKey,
        model = apiModel,
        readTimeoutSec = prefs.apiReadTimeoutSec,
    )

    fun llmAccess(): ApiAccess = AccessResolver.resolveLlm(
        stt = sttAccess(),
        providerId = llmProviderId,
        baseUrl = llmUrl,
        apiKey = llmKey,
        model = llmModel,
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

        /** Nur schreiben, wenn sich wirklich etwas geaendert hat — sonst zeichnet Compose umsonst. */
        fun reload() {
            val aktuell = read()
            if (state.value != aktuell) state.value = aktuell
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            write(value)
            state.value = value
        }
    }
}
