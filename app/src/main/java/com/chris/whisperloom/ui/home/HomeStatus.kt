package com.chris.whisperloom.ui.home

import com.chris.whisperloom.Engine
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.RefineBlock
import com.chris.whisperloom.ui.components.Tone

/** Reine Entscheidungslogik der Home-Statusanzeige (Spec §2.1) — JVM-unit-testbar. */
object HomeStatus {

    /** Hero-Button gesperrt: Pflicht-Berechtigung fehlt, Chip fuehrt in den Schritt. */
    enum class Blocked { NONE, MIC, OVERLAY }

    /** ReadinessBanner: nur der erste offene weiche Punkt in dieser Prioritaet. */
    enum class Banner { NONE, A11Y, MODEL, NOTIF }

    enum class Keyboard { ACTIVE, ENABLED, OFF }

    fun blocked(micGranted: Boolean, canDrawOverlays: Boolean): Blocked = when {
        !micGranted -> Blocked.MIC
        !canDrawOverlays -> Blocked.OVERLAY
        else -> Blocked.NONE
    }

    fun banner(
        a11yRunning: Boolean,
        engine: Engine?,
        modelInstalled: Boolean,
        notifNeeded: Boolean,
        notifGranted: Boolean,
    ): Banner = when {
        !a11yRunning -> Banner.A11Y
        engine == Engine.OFFLINE && !modelInstalled -> Banner.MODEL
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

    /** Modelle-Zeile nur, wenn etwas installiert ist oder offline gewaehlt wurde. */
    fun showModelsRow(installedCount: Int, engine: Engine?): Boolean = installedCount > 0 || engine == Engine.OFFLINE
}
