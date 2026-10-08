package com.chris.whisperloom

/**
 * Woher der Text kommt. Je Weg gibt es eine eigene Stufe und eigene Stufen-Einstellungen
 * (Bereinigung beim Glaetten, Form beim Zusammenfassen); das Modell je Stufe
 * ([Prefs.llmModelFor]) gilt fuer beide Wege gemeinsam.
 */
enum class RefineWay(val key: String) {
    /** Eigenes Diktat: Tastatur, schwebender Knopf, Widget ([TranscriptionEngine]). */
    DICTATION("dictation"),

    /** Geteilte Sprachnachricht ([SharedRefine]). */
    SHARE("share");

    companion object {
        fun fromKey(key: String?): RefineWay? = entries.firstOrNull { it.key == key }
    }
}

/** Bereinigung beim Glaetten — genau die drei Prompt-Varianten der Stufe ([Refinement.of]). */
enum class PolishCleanup(val key: String) {
    /** Nur Zeichensetzung: reines Korrektorat, kein Wort faellt weg (Standard). */
    PLAIN("plain"),

    /** Ohne Fuellwoerter: die KI laesst Fuellwoerter, Stotterer und Doppler weg, sonst wie [PLAIN]. */
    CLEAN("clean"),

    /** Lesbar: behutsames Lektorat ([RefineMode.READABLE]). */
    READABLE("readable");

    companion object {
        /** Ungespeichert und Unbekanntes gilt als [PLAIN]. */
        fun fromKey(key: String?): PolishCleanup = entries.firstOrNull { it.key == key } ?: PLAIN
    }
}

/** Form beim Zusammenfassen. */
enum class SummarizeForm(val key: String) {
    /** Liste ab drei Aufgaben, Terminen oder Fragen, sonst Fliesstext in kurzen Absaetzen (Standard). */
    AUTO("auto"),

    /** Wenige Saetze als ein Absatz, ohne Liste. */
    PROSE("prose");

    companion object {
        /** Ungespeichert und Unbekanntes gilt als [AUTO]. */
        fun fromKey(key: String?): SummarizeForm = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/**
 * Was ein Auftrag wirklich an das Sprachmodell gibt: die Prompt-Variante ([mode]), ob die KI ueber
 * Fuellwoerter entscheidet ([smartFillers], nur Glaetten · Ohne Fuellwoerter) und ob Absaetze
 * erwuenscht sind ([paragraphs]; bei Zusammenfassen die Form).
 */
data class Refinement(
    val mode: RefineMode,
    val smartFillers: Boolean = false,
    val paragraphs: Boolean = true,
) {
    companion object {
        val OFF = Refinement(RefineMode.OFF)

        /**
         * Die wirksame Verarbeitung der gespeicherten Stufe [stage] auf dem Weg [way]. Die Bereinigung
         * zaehlt nur beim Glaetten, die Form nur beim Zusammenfassen. Absaetze waehlt das Diktat je
         * Stufe ([paragraphs]); Sprachnachrichten sind immer gegliedert, am Stueck lesen sie sich schlecht.
         * "Prompt" gliedert selbst — dort ist die Gliederung der Zweck.
         */
        fun of(
            way: RefineWay,
            stage: RefineMode,
            cleanup: PolishCleanup,
            paragraphs: Boolean,
            form: SummarizeForm,
        ): Refinement {
            val p = way == RefineWay.SHARE || paragraphs
            return when (stage) {
                RefineMode.OFF -> OFF
                RefineMode.POLISH, RefineMode.READABLE -> when (cleanup) {
                    PolishCleanup.PLAIN -> Refinement(RefineMode.POLISH, paragraphs = p)
                    PolishCleanup.CLEAN -> Refinement(RefineMode.POLISH, smartFillers = true, paragraphs = p)
                    PolishCleanup.READABLE -> Refinement(RefineMode.READABLE, paragraphs = p)
                }
                RefineMode.BEAUTIFY -> Refinement(RefineMode.BEAUTIFY, paragraphs = p)
                RefineMode.SUMMARIZE -> Refinement(RefineMode.SUMMARIZE, paragraphs = form == SummarizeForm.AUTO)
                RefineMode.PROMPT -> Refinement(RefineMode.PROMPT)
            }
        }
    }
}
