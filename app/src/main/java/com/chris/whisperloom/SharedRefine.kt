package com.chris.whisperloom

import android.util.Log
import com.chris.whisperloom.api.TextRefiner

/**
 * KI-Stufe fuer geteilte Sprachnachrichten ([Prefs.shareRefineMode]) — getrennt von der Stufe
 * fuers Diktat und ab Werk aus. Ohne Audio und (ueber [run]s `refine`) ohne Netz testbar.
 */
object SharedRefine {

    /** @property paragraphs KI-Fassung; null = keine (Stufe aus oder gescheitert, Grund in [skipped]). */
    data class Result(val mode: RefineMode, val paragraphs: List<String>?, val skipped: String?)

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
        refine: (raw: String, mode: RefineMode) -> String = { raw, mode ->
            TextRefiner(prefs.llmAccess()).refine(raw, language, mode, prefs.smartFillers)
        },
    ): Result {
        val mode = prefs.effective(prefs.shareRefineMode)
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
