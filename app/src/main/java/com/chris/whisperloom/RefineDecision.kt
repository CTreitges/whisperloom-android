package com.chris.whisperloom

/**
 * Regel "Textverbesserung bei Offline-Erkennung" ([Prefs.offlineRefine]). Online erkannter Text
 * wird davon unberuehrt wie bisher online verbessert.
 */
enum class OfflineRefineRule(val key: String) {
    /** Standard: offline erkannter Text wird lokal verbessert, nie online. */
    LOCAL("local"),

    /** Mit eigenem Online-Zugang und Netz online; ohne Netz, ohne eigenen Zugang oder bei Online-Fehler lokal. */
    ONLINE_LOCAL("online_local"),

    /** Mit eigenem Online-Zugang und Netz online; sonst Text ohne KI (nur die Regeln ohne KI). */
    SKIP("skip");

    companion object {
        /**
         * Ungespeichert und Unbekanntes gilt als [LOCAL] — auch fuer Bestandsnutzer, die schon
         * offline mit einer KI-Stufe erkennen; es wird nichts migriert (sie sehen den Hinweis).
         */
        fun fromKey(key: String?): OfflineRefineRule = entries.firstOrNull { it.key == key } ?: LOCAL
    }
}

/** Wohin die Textverbesserung eines Diktats geht ([RefineDecision.route]). */
sealed interface RefineRoute {

    /** Online wie bisher. [fallbackLocal]: scheitert die Anfrage, rechnet das (bereite) lokale Modell. */
    data class Online(val fallbackLocal: Boolean) : RefineRoute

    /** Lokales Textmodell. */
    data object Local : RefineRoute

    /** Text ohne KI. [hint] = Hinweis an den Nutzer; null = so gewollt (Stufe aus oder Ueberspringen). */
    data class Raw(val hint: RefineHint?) : RefineRoute
}

/** Warum ein Diktat ohne KI kommt, obwohl eine Stufe an ist. */
enum class RefineHint {
    /** Kein Netz fuer die Online-Textverbesserung — es ging keine Anfrage raus (refine_no_net_skipped). */
    NO_NET,

    /** Das gewaehlte lokale Textmodell fehlt oder passt nicht in den RAM (refine_local_missing). */
    LOCAL_MISSING,
}

/**
 * Reine Entscheidungslogik der Textverbesserung (Entscheidungstabelle der Spezifikation) — ohne
 * Android, JVM-unit-testbar. Die Aufrufer sammeln die Eingaben (Prefs, Zugang, Netz, Modell).
 */
object RefineDecision {

    /**
     * @param stageActive die Stufe des Auftrags (Diktat bzw. geteiltes Audio) ist nicht "Aus"
     * @param ownOnlineReady eigener Text-Zugang (nicht "wie Erkennung") und vollstaendig ([SetupState.llmReady])
     * @param network Netz fuer den Text-Zugang da ([com.chris.whisperloom.api.NetworkCheck])
     * @param localReady gewaehltes Textmodell installiert (exakte Groesse, keine Teildatei) und passt in den RAM
     */
    fun route(
        engine: Engine?,
        rule: OfflineRefineRule,
        stageActive: Boolean,
        ownOnlineReady: Boolean,
        network: Boolean,
        localReady: Boolean,
    ): RefineRoute {
        if (!stageActive) return RefineRoute.Raw(null)
        // Online-Erkennung: wie bisher online; faellt das Netz nach der Erkennung weg, sofort ohne KI.
        if (engine != Engine.OFFLINE) {
            return if (network) RefineRoute.Online(fallbackLocal = false) else RefineRoute.Raw(RefineHint.NO_NET)
        }
        val online = ownOnlineReady && network
        return when (rule) {
            OfflineRefineRule.LOCAL -> local(localReady)
            OfflineRefineRule.ONLINE_LOCAL -> if (online) RefineRoute.Online(fallbackLocal = localReady) else local(localReady)
            OfflineRefineRule.SKIP -> when {
                online -> RefineRoute.Online(fallbackLocal = false)
                ownOnlineReady -> RefineRoute.Raw(RefineHint.NO_NET)
                else -> RefineRoute.Raw(null)
            }
        }
    }

    private fun local(ready: Boolean): RefineRoute =
        if (ready) RefineRoute.Local else RefineRoute.Raw(RefineHint.LOCAL_MISSING)

    /**
     * Koennen die KI-Stufen gerade etwas ausrichten (Stufenleiste der Tastatur)? Online erkannt
     * entscheidet der Zugang, offline die Regel: lokal mit bereitem Modell, online mit eigenem Zugang.
     * Das Netz zaehlt hier nicht — es wechselt, und ohne Netz greift ohnehin [route].
     *
     * @param onlineReady [SetupState.llmReady] des Text-Zugangs; offline zaehlt "wie Erkennung" dabei nie.
     */
    fun stagesReady(engine: Engine?, rule: OfflineRefineRule, onlineReady: Boolean, localReady: Boolean): Boolean {
        if (engine != Engine.OFFLINE) return onlineReady
        return when (rule) {
            OfflineRefineRule.LOCAL -> localReady
            OfflineRefineRule.ONLINE_LOCAL -> onlineReady || localReady
            OfflineRefineRule.SKIP -> onlineReady
        }
    }

    /**
     * "Lokales Textmodell fehlt": steuert Pflichtkarte, Home-Warnung und Banner. Offline-Erkennung,
     * eine KI-Stufe an (Diktat oder geteilte Audios), die Regel will lokal rechnen, das Modell ist nicht bereit.
     */
    fun localModelMissing(
        engine: Engine?,
        dictationMode: RefineMode,
        shareMode: RefineMode,
        rule: OfflineRefineRule,
        localReady: Boolean,
    ): Boolean =
        engine == Engine.OFFLINE &&
            (dictationMode != RefineMode.OFF || shareMode != RefineMode.OFF) &&
            rule != OfflineRefineRule.SKIP &&
            !localReady
}
