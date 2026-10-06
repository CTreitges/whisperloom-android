package com.chris.whisperloom.ui.nav

import com.chris.whisperloom.Engine
import com.chris.whisperloom.OfflineRefineRule
import com.chris.whisperloom.SetupState
import com.chris.whisperloom.api.ServerUrlCheck
import com.chris.whisperloom.ui.state.PrefsState

/** Zustand eines Assistenten-Schritts (Status-Chip, UX-Spec §2.2). */
enum class StepState { DONE, OPEN, OPTIONAL, SKIPPED }

/** Wahl in Schritt 2b: lokales Textmodell oder Textverbesserung ohne Netz ueberspringen. */
enum class TextChoice { LOCAL, SKIP }

/** Womit die App startet (Router, UX-Spec §1.2). */
sealed class Start {
    data object Home : Start()
    data object Welcome : Start()
    data class Step(val step: Int) : Start()
}

/**
 * Alle Fakten, die Router und Assistent brauchen — rein (ohne Android), damit die
 * Startlogik JVM-unit-testbar ist. Gebaut aus [PrefsState] + [SystemStatus] (siehe [from]).
 */
data class SetupFacts(
    val engine: Engine? = null,
    val sttComplete: Boolean = false,
    val urlValid: Boolean = true,
    val modelInstalled: Boolean = false,
    val micGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val overlaySkipped: Boolean = false,
    val a11yRunning: Boolean = false,
    val a11ySkipped: Boolean = false,
    val notifNeeded: Boolean = false,
    val notifGranted: Boolean = true,
    val notifSkipped: Boolean = false,
    val imeEnabled: Boolean = false,
    val keyboardSkipped: Boolean = false,
    val welcomeSeen: Boolean = false,
) {
    companion object {
        fun from(prefs: PrefsState, status: SystemStatus): SetupFacts {
            val access = prefs.sttAccess()
            return SetupFacts(
                engine = prefs.engine,
                sttComplete = SetupState.sttComplete(access.baseUrl, access.apiKey, access.provider.needsKey),
                urlValid = ServerUrlCheck.check(access.baseUrl, access.provider)?.severity != ServerUrlCheck.Severity.ERROR,
                modelInstalled = prefs.offlineModel in status.installedModels,
                micGranted = status.micGranted,
                overlayGranted = status.canDrawOverlays,
                overlaySkipped = prefs.overlaySkipped,
                a11yRunning = status.a11yRunning,
                a11ySkipped = prefs.a11ySkipped,
                notifNeeded = status.notifNeeded,
                notifGranted = status.notifGranted,
                notifSkipped = prefs.notifSkipped,
                imeEnabled = status.imeEnabled,
                keyboardSkipped = prefs.keyboardSkipped,
                welcomeSeen = prefs.welcomeSeen,
            )
        }
    }
}

/**
 * Startlogik und Schritt-Reihenfolge des Assistenten (UX-Spec §1.2, §2.2) — rein.
 *
 *  recognitionReady = online && URL gueltig && (Key da, sofern noetig) || offline && Modell da
 *  overlayOk        = Overlay erlaubt || (bewusst "nur Tastatur" && Tastatur aktiviert)
 *  isSetUp          = recognitionReady && micGranted && overlayOk
 */
object SetupRouter {

    const val STEP_ENGINE = 1
    const val STEP_ACCESS = 2
    const val STEP_MIC = 3
    const val STEP_OVERLAY = 4
    const val STEP_A11Y = 5
    const val STEP_NOTIF = 6
    const val STEP_KEYBOARD = 7

    fun recognitionReady(f: SetupFacts): Boolean = when (f.engine) {
        Engine.ONLINE -> f.sttComplete && f.urlValid
        Engine.OFFLINE -> f.modelInstalled
        null -> false
    }

    fun overlayOk(f: SetupFacts): Boolean = f.overlayGranted || (f.overlaySkipped && f.imeEnabled)

    fun isSetUp(f: SetupFacts): Boolean = recognitionReady(f) && f.micGranted && overlayOk(f)

    /** Sichtbare Schritte in Reihenfolge; Benachrichtigungen nur, wenn das System sie kennt (API >= 33). */
    fun visibleSteps(f: SetupFacts): List<Int> =
        if (f.notifNeeded) (STEP_ENGINE..STEP_KEYBOARD).toList()
        else listOf(STEP_ENGINE, STEP_ACCESS, STEP_MIC, STEP_OVERLAY, STEP_A11Y, STEP_KEYBOARD)

    /** Pflicht = ohne Erledigung nicht ueberspringbar; Tastatur wird Pflicht im "nur Tastatur"-Pfad. */
    fun isMandatory(step: Int, f: SetupFacts): Boolean = when (step) {
        STEP_ENGINE, STEP_ACCESS, STEP_MIC, STEP_OVERLAY -> true
        STEP_KEYBOARD -> f.overlaySkipped
        else -> false
    }

    fun stepState(step: Int, f: SetupFacts): StepState = when (step) {
        STEP_ENGINE -> done(f.engine != null)
        STEP_ACCESS -> done(recognitionReady(f))
        STEP_MIC -> done(f.micGranted)
        STEP_OVERLAY -> when {
            f.overlayGranted -> StepState.DONE
            f.overlaySkipped -> StepState.SKIPPED
            else -> StepState.OPEN
        }
        STEP_A11Y -> optional(f.a11yRunning, f.a11ySkipped)
        STEP_NOTIF -> optional(f.notifGranted, f.notifSkipped)
        STEP_KEYBOARD -> when {
            f.imeEnabled -> StepState.DONE
            isMandatory(step, f) -> StepState.OPEN
            f.keyboardSkipped -> StepState.SKIPPED
            else -> StepState.OPTIONAL
        }
        else -> StepState.OPTIONAL
    }

    /** Erster Schritt, der weder erledigt noch uebersprungen ist (Fallback: erster Schritt). */
    fun firstOpenStep(f: SetupFacts): Int {
        val steps = visibleSteps(f)
        return steps.firstOrNull { stepState(it, f) == StepState.OPEN || stepState(it, f) == StepState.OPTIONAL }
            ?: steps.first()
    }

    fun start(f: SetupFacts): Start = when {
        isSetUp(f) -> Start.Home
        !f.welcomeSeen -> Start.Welcome
        else -> Start.Step(firstOpenStep(f))
    }

    /** Naechster sichtbarer Schritt; null = Assistent fertig (W9). */
    fun next(step: Int, f: SetupFacts): Int? = visibleSteps(f).firstOrNull { it > step }

    /** Vorheriger sichtbarer Schritt; null = erster Schritt. */
    fun previous(step: Int, f: SetupFacts): Int? = visibleSteps(f).lastOrNull { it < step }

    /** 1-basierte Position fuer "Schritt x von y". */
    fun position(step: Int, f: SetupFacts): Int = visibleSteps(f).indexOf(step) + 1

    fun doneCount(f: SetupFacts): Int =
        visibleSteps(f).count { stepState(it, f) == StepState.DONE || stepState(it, f) == StepState.SKIPPED }

    /**
     * Schritt 2b, Textverbesserung ohne Netz (Spec §4): "Ueberspringen" = Regel skip; "Lokales
     * Textmodell" = das Modell ist bereit oder laedt (Weiter schon waehrend des Downloads). null =
     * noch offen, Weiter gesperrt. Weich: [isSetUp] haengt nicht davon ab.
     */
    fun textChoice(rule: OfflineRefineRule, localReady: Boolean, localLoading: Boolean): TextChoice? = when {
        rule == OfflineRefineRule.SKIP -> TextChoice.SKIP
        localReady || localLoading -> TextChoice.LOCAL
        else -> null
    }

    private fun done(isDone: Boolean) = if (isDone) StepState.DONE else StepState.OPEN

    private fun optional(isDone: Boolean, skipped: Boolean) = when {
        isDone -> StepState.DONE
        skipped -> StepState.SKIPPED
        else -> StepState.OPTIONAL
    }
}
