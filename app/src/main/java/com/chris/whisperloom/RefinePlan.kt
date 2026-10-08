package com.chris.whisperloom

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.chris.whisperloom.api.AndroidNetworkCheck
import com.chris.whisperloom.api.ApiAccess
import com.chris.whisperloom.api.Http
import com.chris.whisperloom.api.NetworkCheck
import com.chris.whisperloom.api.RefineRejectedException
import com.chris.whisperloom.api.TextRefiner
import com.chris.whisperloom.llm.LocalRefiner
import com.chris.whisperloom.llm.LocalTextEngine
import com.chris.whisperloom.whisper.OfflineSupport
import com.chris.whisperloom.whisper.TextModelCatalog

/**
 * Die Textverbesserung eines Auftrags, wie [RefineDecision] sie entscheidet, samt Ausfuehrung —
 * der gemeinsame Weg von Diktat ([TranscriptionEngine]) und geteilten Audios ([SharedRefine]).
 * [of] sammelt die Eingaben: Regel, eigener Online-Zugang, Netz und lokales Textmodell. Das Netz
 * wird VOR jeder Online-Anfrage geprueft: ohne Netz geht keine raus, statt dass das Diktat im
 * Connect-Timeout haengt. Nach einer Online-Erkennung reicht ein aktives Netz — sie hat es gerade
 * bewiesen; VALIDATED verlangen die Offline-Erkennung und das Neu-Verarbeiten aus dem Verlauf.
 */
internal class RefinePlan(
    val route: RefineRoute,
    /** Nach einer abgelehnten temperature die Kopie ohne: weitere Stuecke (geteilte Audios) fragen gleich richtig. */
    @Volatile private var access: ApiAccess,
    private val localModelId: String,
    /** Merkt ein Modell, das temperature abgelehnt hat — dauerhaft je Anbieter, Adresse und Modell (ModelCache). */
    private val rememberNoTemperature: (ApiAccess) -> Unit = {},
) {

    /** Einmal online gescheitert: weitere Stuecke (geteilte Audios) gleich lokal statt wieder in den Timeout. */
    @Volatile private var onlineFailed = false

    /**
     * Bearbeiteter Text auf der [route]. [RefineRoute.Raw] rechnet nie — der Aufrufer fragt die Route
     * vorher. Fehler werden geworfen wie bei [TextRefiner]; der Aufrufer faellt auf den Rohtext zurueck.
     *
     * @param onNote Hinweis, der den Text nicht betrifft: online gescheitert, lokal verbessert
     *   ([MSG_ONLINE_FAILED_LOCAL]).
     * @param cancelled Abbruch des Auftrags (Tipp "ohne KI"): keine lokale Ausweichrechnung mehr.
     * @throws RefineRejectedException wenn das Modell geantwortet hat, statt den Text zu bearbeiten.
     * @throws RuntimeException mit [MSG_LOCAL_FAILED], wenn die lokale Rechnung scheitert.
     */
    fun refine(
        raw: String,
        language: String,
        refinement: Refinement,
        onNote: (String) -> Unit = {},
        cancelled: () -> Boolean = { false },
    ): String = when (route) {
        is RefineRoute.Raw -> raw
        RefineRoute.Local -> local(raw, language, refinement, cancelled)
        is RefineRoute.Online -> if (onlineFailed) {
            local(raw, language, refinement, cancelled)
        } else {
            try {
                TextRefiner(access, connectTimeoutMs(route), ::temperatureRejected)
                    .refine(raw, language, refinement.mode, refinement.smartFillers, refinement.paragraphs)
            } catch (e: Exception) {
                if (!route.fallbackLocal || cancelled()) throw e
                Log.w(TAG, "Online gescheitert, verbessere lokal: ${e.message}", e)
                onlineFailed = true
                local(raw, language, refinement, cancelled).also { onNote(MSG_ONLINE_FAILED_LOCAL) }
            }
        }
    }

    /**
     * Womit [refine] gerechnet hat, als Anzeige: das Modell des Online-Zugangs oder — auf der Route
     * [RefineRoute.Local] bzw. nach einem Online-Fehler — das lokale Textmodell. null auf [RefineRoute.Raw].
     */
    fun modelLabel(): String? = when {
        route is RefineRoute.Raw -> null
        route == RefineRoute.Local || onlineFailed -> TextModelCatalog.byId(localModelId).label
        // Wie ui.components.modelLabel: Katalog-Label, sonst die freie ID.
        else -> access.modelOption?.label ?: access.model
    }

    private fun temperatureRejected(withoutTemperature: ApiAccess) {
        rememberNoTemperature(withoutTemperature)
        access = withoutTemperature
    }

    /** Init, nativer Fehler, Speicher: als [MSG_LOCAL_FAILED]. Eine unplausible Antwort behaelt ihre eigene Meldung. */
    private fun local(
        raw: String,
        language: String,
        refinement: Refinement,
        cancelled: () -> Boolean,
    ): String = try {
        LocalRefiner(localModelId, cancelled).refine(raw, language, refinement.mode, refinement.smartFillers, refinement.paragraphs)
    } catch (e: RefineRejectedException) {
        throw e
    } catch (e: Exception) {
        throw RuntimeException(MSG_LOCAL_FAILED, e)
    } catch (e: OutOfMemoryError) {
        throw RuntimeException(MSG_LOCAL_FAILED, e)
    }

    companion object {
        private const val TAG = "RefinePlan"

        /** Kein Netz: es ging keine Anfrage raus (refine_no_net_skipped). */
        const val MSG_NO_NET = "Kein Netz für den Online-Zugang"

        /** Regel "lokal", aber das Textmodell fehlt oder passt nicht in den RAM (refine_local_missing). */
        const val MSG_LOCAL_MISSING = "Kein Textmodell geladen — unter „Offline-Modelle“ laden oder „Überspringen“ wählen"

        /** Die lokale Rechnung ist gescheitert: Init, Speicher, nativer Fehler (refine_local_failed). */
        const val MSG_LOCAL_FAILED = "Lokales Textmodell fehlgeschlagen"

        /** Kein Ausfall, nur zur Info: der Text ist verbessert, nur eben lokal (refine_online_failed_local). */
        const val MSG_ONLINE_FAILED_LOCAL = "Online-Textverbesserung fehlgeschlagen — lokal verbessert"

        /** Connect-Timeout, wenn das lokale Modell einspringen kann: lieber frueh lokal als 15 s warten. */
        const val FALLBACK_CONNECT_TIMEOUT_MS = 8_000

        /** Naht fuer Tests: Netz faken. Produktion: aktives Netz + NET_CAPABILITY_VALIDATED (bzw. die Netze unter einem VPN). */
        @VisibleForTesting
        internal var networkCheck: (Context) -> NetworkCheck = { AndroidNetworkCheck(it) }

        /**
         * Sammelt die Eingaben der Entscheidungstabelle. Das Netz wird nur gefragt, wenn es zaehlt
         * (Online-Erkennung oder eigener Zugang), das lokale Modell nur bei Offline-Erkennung.
         *
         * @param mode die wirksame Stufe des Auftrags ([Refinement.mode] aus [Prefs.refinementFor]) —
         *   sie bestimmt auch das Textmodell ([Prefs.llmModelFor], fuer beide Wege dasselbe).
         * @param networkProven eine Anfrage hat das Netz gerade getragen ([NetworkCheck.availableFor]):
         *   ab Werk nach einer Online-Erkennung. Das Neu-Verarbeiten aus dem Verlauf hat keinen Nachweis.
         */
        fun of(
            context: Context,
            prefs: Prefs,
            mode: RefineMode,
            networkProven: Boolean = prefs.engine != Engine.OFFLINE,
        ): RefinePlan {
            val access = prefs.llmAccess(mode)
            val engine = prefs.engine
            val stageActive = mode != RefineMode.OFF
            // Bei Offline-Erkennung zaehlt "wie Erkennung" nie (RefineBlock.OFFLINE): bereit ist dann
            // nur ein eigener, vollstaendiger Zugang.
            val ownOnlineReady = SetupState.llmReady(access)
            val network = stageActive && (engine != Engine.OFFLINE || ownOnlineReady) &&
                networkCheck(context).availableFor(access.baseUrl, proven = networkProven)
            val localModelId = prefs.localLlmModel
            val localReady = stageActive && engine == Engine.OFFLINE && LocalTextEngine.isReady(context, localModelId)
            // Passt kein Textmodell ins Geraet, wirkt jede Regel wie "Ueberspringen".
            val rule = prefs.offlineRefine.effective(OfflineSupport.textModelFits(context))
            val route = RefineDecision.route(engine, rule, stageActive, ownOnlineReady, network, localReady)
            return RefinePlan(route, access, localModelId, prefs.modelCache::rememberNoTemperature)
        }

        fun message(hint: RefineHint): String = when (hint) {
            RefineHint.NO_NET -> MSG_NO_NET
            RefineHint.LOCAL_MISSING -> MSG_LOCAL_MISSING
        }

        internal fun connectTimeoutMs(route: RefineRoute.Online): Int =
            if (route.fallbackLocal) FALLBACK_CONNECT_TIMEOUT_MS else Http.CONNECT_TIMEOUT_MS
    }
}
