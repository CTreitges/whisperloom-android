package com.chris.whisperloom

import android.content.Context
import android.util.Log

/**
 * KI-Stufe fuer geteilte Sprachnachrichten ([Prefs.shareStage]: Stufe und "Lesbarer glaetten") —
 * getrennt von denen fuers Diktat und ab Werk aus. Ohne Audio und (ueber [run]s `refine`) ohne Netz testbar.
 */
object SharedRefine {

    /**
     * @property paragraphs KI-Fassung; null = keine (Stufe aus oder gescheitert, Grund in [skipped]).
     * @property localFallback online gescheitert, das lokale Textmodell ist eingesprungen — kein Fehler, nur zur Info.
     */
    data class Result(
        val mode: RefineMode,
        val paragraphs: List<String>?,
        val skipped: String?,
        val localFallback: Boolean = false,
    )

    /**
     * Der Weg im Betrieb: [run] mit der Route aus [RefinePlan] — online (Netz vorher geprueft),
     * lokal oder online mit lokaler Ausweichloesung. Geht es nach der Regel gar nicht (kein Netz,
     * kein Textmodell), kommt die Fassung ohne KI mit Hinweis, ohne Anfrage. "Ueberspringen" ohne
     * eigenen Zugang gilt wie "Aus": so gewollt, kein Hinweis.
     *
     * @param onStart vor der ersten Rechnung; `local` = das lokale Textmodell rechnet (Fortschrittszeile).
     */
    fun run(
        context: Context,
        prefs: Prefs,
        parts: List<String>,
        language: String,
        isCancelled: () -> Boolean = { false },
        onStart: (local: Boolean) -> Unit = {},
    ): Result {
        val mode = prefs.shareStage
        if (mode == RefineMode.OFF) return Result(mode, null, null)
        val plan = RefinePlan.of(context, prefs, mode)
        val route = plan.route
        if (route is RefineRoute.Raw) {
            val hint = route.hint ?: return Result(RefineMode.OFF, null, null)
            return Result(mode, null, RefinePlan.message(hint))
        }
        // Der einzige Hinweis von plan.refine: online gescheitert, lokal verbessert.
        var localFallback = false
        val result = run(prefs, parts, language, isCancelled, { onStart(route == RefineRoute.Local) }) { raw, stage ->
            plan.refine(raw, language, stage, prefs.smartFillers, paragraphs = true, onNote = { localFallback = true }, cancelled = isCancelled)
        }
        return if (result.paragraphs != null) result.copy(localFallback = localFallback) else result
    }

    /**
     * Jedes Stueck (hoechstens 5 Minuten, siehe [SharedAudioTranscriber]) geht EINZELN an das
     * Sprachmodell. Am Stueck waere eine lange Nachricht an der Laengengrenze des Modells
     * abgeschnitten worden (max_completion_tokens bei Reasoning-Modellen, das Kontextfenster bei
     * Ollama) — ein Stueck passt immer. Die meisten Sprachnachrichten sind ohnehin ein Stueck;
     * bei laengeren fasst "Zusammenfassen" je Stueck zusammen.
     *
     * Scheitert ein Stueck, gilt die ganze Datei als nicht verbessert: halb KI, halb Rohtext
     * liest sich schlechter als die Fassung ohne KI.
     *
     * @param onStart vor der ersten Anfrage — fuer die Fortschrittszeile; bei "Aus" nie.
     * @throws UnsupportedAudioException bei Abbruch ([isCancelled]) — der einzige Fehler, der durchgeht.
     */
    fun run(
        prefs: Prefs,
        parts: List<String>,
        language: String,
        isCancelled: () -> Boolean = { false },
        onStart: () -> Unit = {},
        refine: (raw: String, mode: RefineMode) -> String,
    ): Result {
        val mode = prefs.shareStage
        if (mode == RefineMode.OFF) return Result(mode, null, null)
        onStart()
        return try {
            Result(mode, paragraphs(parts, options(prefs, language, mode), isCancelled) { refine(it, mode) }, null)
        } catch (e: UnsupportedAudioException) {
            throw e
        } catch (e: Exception) {
            // Wie beim Diktat: die KI darf eine erkannte Nachricht nie verschlucken — dann bleibt es
            // bei der Fassung ohne KI, mit Hinweis statt Fehler. Das gilt ausdruecklich auch fuer
            // ApiNotConfiguredException: die betrifft hier den LLM-Zugang, nicht die Erkennung, und
            // darf die Share-Ansicht nicht auf "Kein Zugang eingerichtet" stellen.
            Log.w(TAG, "Textverbesserung uebersprungen: ${e.message}", e)
            Result(mode, null, e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Nachbearbeitung der KI-Fassung — dieselben Regeln wie beim Diktat, nur die Absaetze sind
     * immer an: eine Sprachnachricht am Stueck liest sich schlecht, und der Schalter
     * "Automatische Absaetze" gilt fuer geteilte Audios nicht.
     */
    internal fun options(prefs: Prefs, language: String, mode: RefineMode): PolishOptions = PolishPlan.options(
        removeFillers = prefs.removeFillers,
        autoCapitalize = prefs.autoCapitalize,
        language = language,
        refineMode = mode,
        smartFillers = prefs.smartFillers,
        customFillers = prefs.customFillers,
        disabledFillers = prefs.disabledFillers,
        paragraphs = true,
    )

    /**
     * Rein: je nicht leerem Stueck ein [refine]-Aufruf, danach [TextPolisher] mit [options]. Die
     * Absaetze setzt das Modell (Leerzeilen trennen sie); einfache Umbrueche (Stichpunkte) bleiben.
     * Nichts erkannt = leere Liste, ohne Anfrage. Fehler von [refine] werden durchgereicht.
     */
    internal fun paragraphs(
        parts: List<String>,
        options: PolishOptions,
        isCancelled: () -> Boolean = { false },
        refine: (String) -> String,
    ): List<String> =
        parts.map { it.trim() }.filter { it.isNotEmpty() }.flatMap { part ->
            if (isCancelled()) throw UnsupportedAudioException("Abgebrochen")
            TextPolisher.polish(refine(part), options)
                .split(BLANK_LINE)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

    private val BLANK_LINE = Regex("\\n\\s*\\n")
    private const val TAG = "SharedRefine"
}
