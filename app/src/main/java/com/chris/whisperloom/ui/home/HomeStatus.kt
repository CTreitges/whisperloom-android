package com.chris.whisperloom.ui.home

import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.RefineBlock
import com.chris.whisperloom.ui.components.Tone

/** Reine Entscheidungslogik der Home-Statusanzeige (Spec §2.1) — JVM-unit-testbar. */
object HomeStatus {

    /** Hero-Button gesperrt: Pflicht-Berechtigung fehlt, Chip fuehrt in den Schritt. */
    enum class Blocked { NONE, MIC, OVERLAY }

    /** ReadinessBanner: nur der erste offene weiche Punkt in dieser Prioritaet. */
    enum class Banner { NONE, A11Y, MODEL, TEXT_MODEL, NOTIF }

    /**
     * Textverbesserung bei Offline-Erkennung (Stufe != Aus), wie die Status-Zeile sie meldet:
     * lokal, online mit lokalem Ausweg, online (Ueberspringen mit eigenem Zugang), uebersprungen,
     * oder das lokale Modell fehlt (Warnung, wie [com.chris.whisperloom.RefineDecision.localModelMissing]).
     */
    enum class OfflineRefine { LOCAL, ONLINE_LOCAL, ONLINE, SKIPPED, MISSING }

    enum class Keyboard { ACTIVE, ENABLED, OFF }

    fun blocked(micGranted: Boolean, canDrawOverlays: Boolean): Blocked = when {
        !micGranted -> Blocked.MIC
        !canDrawOverlays -> Blocked.OVERLAY
        else -> Blocked.NONE
    }

    /** @param textModelMissing offline mit KI-Stufe, aber das lokale Textmodell fehlt (Spec §4). */
    fun banner(
        a11yRunning: Boolean,
        engine: Engine?,
        modelInstalled: Boolean,
        notifNeeded: Boolean,
        notifGranted: Boolean,
        textModelMissing: Boolean = false,
    ): Banner = when {
        !a11yRunning -> Banner.A11Y
        engine == Engine.OFFLINE && !modelInstalled -> Banner.MODEL
        textModelMissing -> Banner.TEXT_MODEL
        notifNeeded && !notifGranted -> Banner.NOTIF
        else -> Banner.NONE
    }

    fun recognitionTone(engine: Engine?, modelInstalled: Boolean): Tone = when (engine) {
        Engine.ONLINE -> Tone.SUCCESS
        Engine.OFFLINE -> if (modelInstalled) Tone.SUCCESS else Tone.ERROR
        null -> Tone.ERROR
    }

    fun permissionsTone(micGranted: Boolean, canDrawOverlays: Boolean, a11yRunning: Boolean): Tone = when {
        !micGranted || !canDrawOverlays -> Tone.ERROR
        !a11yRunning -> Tone.WARNING
        else -> Tone.SUCCESS
    }

    fun keyboard(imeEnabled: Boolean, imeSelected: Boolean): Keyboard = when {
        imeEnabled && imeSelected -> Keyboard.ACTIVE
        imeEnabled -> Keyboard.ENABLED
        else -> Keyboard.OFF
    }

    /** KI-Stufe gewaehlt, aber der Zugang kann keinen Text verbessern ([RefineBlock]): warnen statt leerem Modell. */
    fun refineTone(mode: RefineMode, block: RefineBlock?): Tone =
        if (mode != RefineMode.OFF && block != null) Tone.WARNING else Tone.NEUTRAL

    /**
     * Status-Zeile bei Offline-Erkennung (Stufe != Aus). [ownOnlineReady] = eigener, vollstaendiger
     * Online-Zugang; [localReady] = gewaehltes Textmodell installiert und passt in den RAM.
     */
    fun offlineRefine(rule: OfflineRefineRule, ownOnlineReady: Boolean, localReady: Boolean): OfflineRefine = when {
        rule != OfflineRefineRule.SKIP && !localReady -> OfflineRefine.MISSING
        rule == OfflineRefineRule.LOCAL -> OfflineRefine.LOCAL
        rule == OfflineRefineRule.ONLINE_LOCAL -> if (ownOnlineReady) OfflineRefine.ONLINE_LOCAL else OfflineRefine.LOCAL
        ownOnlineReady -> OfflineRefine.ONLINE
        else -> OfflineRefine.SKIPPED
    }

    /** Nur das fehlende Textmodell warnt; Ueberspringen ist so gewaehlt (neutral). */
    fun offlineRefineTone(state: OfflineRefine): Tone = if (state == OfflineRefine.MISSING) Tone.WARNING else Tone.NEUTRAL

    /** Modelle-Zeile nur, wenn etwas installiert ist oder offline gewaehlt wurde. */
    fun showModelsRow(installedCount: Int, engine: Engine?): Boolean = installedCount > 0 || engine == Engine.OFFLINE
}
