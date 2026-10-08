package com.chris.whisperloom.ui.nav

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.chris.whisperloom.AppNav
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import com.chris.whisperloom.history.Processing
import com.chris.whisperloom.history.History as HistoryStore
import com.chris.whisperloom.ui.tutorial.TutorialKind

/** Die Screens der MainActivity (UX-Spec §1.1). [key] ist stabil je Screen-Typ (Uebergangs-Animation). */
sealed class Screen(val key: String) {
    data object Home : Screen("home")

    /** [step] 0 = Willkommen (W1), 1..7 = Schritte, 8 = Fertig (W9). */
    data class Setup(val step: Int) : Screen("setup") {
        companion object {
            const val WELCOME = 0
            const val DONE = 8
        }
    }

    data object SettingsHub : Screen("settings")

    /** "Textverbesserung" (3.9.0): Stufen fuer Diktat und Sprachnachrichten. Bis 3.8.6 "text" und "text-page", siehe [decode]. */
    data object Refine : Screen("refine")

    /**
     * Seite einer Stufe auf einem Weg (3.9.0): Aus, Glaetten, Verschoenern, Zusammenfassen und — nur
     * beim Diktat — Prompt. Eigener Schluessel je Seite ("stage:polish:dictation"), sonst fehlte beim
     * Wechsel von einer Stufen-Seite zur anderen die Uebergangsanimation.
     */
    data class Stage(val stage: RefineMode, val way: RefineWay) : Screen("stage:${stage.key}:${way.key}") {
        companion object {
            /** Die Seite zu [stage] und [way], wenn es sie gibt; sonst null. */
            fun of(stage: RefineMode, way: RefineWay): Stage? =
                Stage(stage, way).takeIf { stage in RefineMode.settings(promptEnabled = way == RefineWay.DICTATION) }
        }
    }

    /** "Woerterbuch & Regeln" (3.9.0): Vokabular und die festen Regeln ohne KI. */
    data object Dictionary : Screen("dictionary")

    /** "Spracherkennung". */
    data object Recognition : Screen("recognition")

    /** "KI-Zugang" (3.9.0, vorher Text › Online-Zugang & Modelle): Zugang und Modell je Stufe. */
    data object LlmAccess : Screen("llm-access")
    data object ButtonKeyboard : Screen("button")

    /** "Offline-Modelle": Erkennungs- und Textmodelle, dazu die Regel bei Offline-Erkennung (3.9.0). */
    data object Models : Screen("models")

    /** "Erweitert": Pro-/Entwickler-Funktionen freischalten (bis 3.7.0 "agent", siehe [decode]). */
    data object Advanced : Screen("advanced")

    /**
     * Widget-Menue. [tab] = gewaehlter Tab (null: der Screen waehlt; ein Tab-Wechsel ersetzt den
     * Eintrag), [edit] = Profil-Id, deren Editor sich einmalig oeffnet (Widget-Tipp ohne Server).
     * Die Id enthaelt kein ":".
     */
    data class Widgets(val tab: WidgetTab? = null, val edit: String? = null) : Screen("widgets")

    /** Verlauf (3.9.0): die Liste der Diktate, aus Home und dem Hub. */
    data object History : Screen("history")

    /**
     * Ein Eintrag des Verlaufs. [show] = sichtbare Fassung: ein Processing-Schluessel, [ORIGIN] fuer
     * den Ursprung oder null fuer die damals erzeugte. Ein Chip-Wechsel ersetzt den Eintrag im
     * Back-Stack (wie ein Tab-Wechsel bei [Widgets]). Die Id enthaelt kein ":".
     */
    data class HistoryDetail(val id: String, val show: String? = null) : Screen("history-entry") {
        companion object {
            const val ORIGIN = "raw"
        }
    }

    /** Bearbeiten-Fenster einer Fassung; [processing] = EDITED ist der bearbeitete Ursprung. */
    data class HistoryEdit(val id: String, val processing: Processing) : Screen("history-edit")

    /** Verlauf-Einstellungen (ueber ⋮ in der Liste). */
    data object HistorySettings : Screen("history-settings")

    /** Patchnotes (P): "?" neben der Versionsnummer in Home, Hilfe und Ueber-Sheet. */
    data object Patchnotes : Screen("patchnotes")

    /** [section] 1..8 = initial geoeffneter Hilfe-Abschnitt (6 = Widgets & Pro Widgets). */
    data class Help(val section: Int = 1) : Screen("help")

    /**
     * Tutorial (T); [startPage] = zuerst gezeigte Seite des Heftes [kind], [PAGE_SHARE] = Sprachnachrichten abtippen.
     * [startBubbleAfter]: W9 "Knopf starten & los" — der schwebende Knopf startet erst beim Beenden des
     * Tutorials (sonst schwebt er darueber).
     */
    data class Tutorial(
        val startPage: Int = 0,
        val startBubbleAfter: Boolean = false,
        val kind: TutorialKind = TutorialKind.BASICS,
    ) : Screen("tutorial") {
        companion object {
            const val PAGE_SHARE = 2
        }
    }

    fun encode(): String = when (this) {
        is Setup -> "$key:$step"
        is Help -> "$key:$section"
        is Widgets -> "$key:${tab?.key.orEmpty()}:${edit.orEmpty()}"
        is Tutorial -> "$key:$startPage:${if (startBubbleAfter) 1 else 0}:${kind.key}"
        is HistoryDetail -> "$key:$id:${show.orEmpty()}"
        is HistoryEdit -> "$key:$id:${processing.key}"
        else -> key
    }

    companion object {
        fun decode(s: String): Screen {
            val parts = s.split(':')
            val arg = parts.getOrNull(1)?.toIntOrNull()
            return when (parts[0]) {
                "setup" -> Setup(arg ?: Setup.WELCOME)
                "settings" -> SettingsHub
                "refine" -> Refine
                // Unbekannte Stufe oder unbekannter Weg: die Textverbesserung, auf der sie stehen. Nicht
                // ueber RefineMode.fromKey: das liest einen unbekannten Schluessel als "Aus", und "Aus"
                // hat eine eigene Seite.
                "stage" -> RefineMode.entries.firstOrNull { it.key == parts.getOrNull(1) }
                    ?.let { stage -> RefineWay.fromKey(parts.getOrNull(2))?.let { Stage.of(stage, it) } } ?: Refine
                "dictionary" -> Dictionary
                "recognition" -> Recognition
                "llm-access" -> LlmAccess
                // Bis 3.8.6 der Text-Hub und seine Unterseiten: jede landet auf ihrer neuen Seite,
                // Diktat, Sprachnachrichten und eine unbekannte Unterseite auf der Textverbesserung.
                "text" -> Refine
                "text-page" -> when (parts.getOrNull(1)) {
                    "access" -> LlmAccess
                    "offline" -> Models
                    "rules" -> Dictionary
                    else -> Refine
                }
                "button" -> ButtonKeyboard
                "models" -> Models
                // "agent" = Name bis 3.7.0, steht noch in gespeicherten Back-Stacks.
                "advanced", "agent" -> Advanced
                "widgets" -> Widgets(WidgetTab.fromKey(parts.getOrNull(1)), parts.getOrNull(2)?.ifEmpty { null })
                "help" -> Help(arg ?: 1)
                "history" -> History
                // Eine kaputte Id fuehrt zur Liste; eine unbekannte merkt erst der Eintrag selbst (dann ebenfalls zur Liste).
                "history-entry" -> historyId(parts.getOrNull(1))?.let { id ->
                    HistoryDetail(id, parts.getOrNull(2)?.takeIf { it == HistoryDetail.ORIGIN || Processing.fromKey(it) != null })
                } ?: History
                "history-edit" -> historyId(parts.getOrNull(1))?.let { id ->
                    Processing.fromKey(parts.getOrNull(2))?.let { HistoryEdit(id, it) } ?: HistoryDetail(id)
                } ?: History
                "history-settings" -> HistorySettings
                "patchnotes" -> Patchnotes
                "tutorial" -> Tutorial(
                    arg ?: 0,
                    startBubbleAfter = parts.getOrNull(2) == "1",
                    kind = TutorialKind.fromKey(parts.getOrNull(3)),
                )
                else -> Home
            }
        }

        private fun historyId(s: String?): String? = s?.takeIf { HistoryStore.isId(it) }
    }
}

/** Tabs im Widget-Menue. Gespeichert wird [key], nie der Enum-Name. */
enum class WidgetTab(val key: String) {
    NORMAL("normal"),
    PRO("pro"),
    ;

    companion object {
        fun fromKey(key: String?): WidgetTab? = entries.firstOrNull { it.key == key }
    }
}

/** Einfacher Back-Stack ohne Navigation-Lib (Spec §0.2); ueberlebt Rotation/Prozess-Tod per Saver. */
class NavState(initial: List<Screen>) {

    private val stack = mutableStateListOf<Screen>().also { it.addAll(initial) }

    val current: Screen get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop() {
        if (canPop) stack.removeAt(stack.lastIndex)
    }

    fun replaceTop(screen: Screen) {
        stack[stack.lastIndex] = screen
    }

    fun replaceAll(vararg screens: Screen) {
        stack.clear()
        stack.addAll(screens)
    }

    fun snapshot(): List<Screen> = stack.toList()

    companion object {
        val Saver = listSaver<NavState, String>(
            save = { it.snapshot().map(Screen::encode) },
            restore = { NavState(it.map(Screen::decode)) },
        )
    }
}

@Composable
fun rememberNavState(initial: () -> List<Screen>): NavState =
    rememberSaveable(saver = NavState.Saver) { NavState(initial()) }

/**
 * Deep-Link-Wunsch aus dem Start-Intent (AppNav) — `route` home|settings|refine|llm-access|models|setup|advanced|widgets,
 * optional `step` 1..7 (setup) bzw. [profileId] (widgets: dessen Editor oeffnen).
 */
data class RouteRequest(val route: String, val step: Int? = null, val profileId: String? = null) {
    companion object {
        /** Alias in der Manifest-Datei: Ziel von method.xml (settingsActivity) und alten Intents. */
        const val SETTINGS_ALIAS = "com.chris.whisperloom.SettingsActivity"

        /**
         * Deep-Link fuer onCreate: nur beim echten Erststart. Nach Rotation/Prozess-Tod liefert
         * getIntent() denselben Deep-Link noch einmal — der per rememberSaveable wiederhergestellte
         * Back-Stack (Spec §0.2) darf dann nicht durch replaceAll() ueberschrieben werden.
         */
        fun initial(intent: Intent?, savedInstanceState: Bundle?): RouteRequest? =
            if (savedInstanceState == null) from(intent) else null

        fun from(intent: Intent?): RouteRequest? {
            if (intent == null) return null
            val route = intent.getStringExtra(AppNav.EXTRA_ROUTE)
                ?: (if (intent.component?.className == SETTINGS_ALIAS) AppNav.ROUTE_SETTINGS else null)
                ?: return null
            val step = intent.getIntExtra(AppNav.EXTRA_STEP, -1)
                .takeIf { it in SetupRouter.STEP_ENGINE..SetupRouter.STEP_KEYBOARD }
            // Die Activity ist exportiert: nur eine Id, die das Back-Stack-Format nicht bricht.
            val profileId = intent.getStringExtra(AppNav.EXTRA_PROFILE)
                ?.takeIf { route == AppNav.ROUTE_WIDGETS && it.isNotBlank() && ':' !in it }
            return RouteRequest(route, step, profileId)
        }
    }
}
