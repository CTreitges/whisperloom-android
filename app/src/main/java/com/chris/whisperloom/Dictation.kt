package com.chris.whisperloom

/**
 * Ergebnis von KI und Regeln ([TranscriptionEngine.refine]) — fuer ein Diktat wie fuer das
 * Neu-Verarbeiten aus dem Verlauf.
 */
data class Refined(
    /** Die gewaehlte Verarbeitung: Stufe samt Bereinigung, Absaetzen bzw. Form. */
    val refinement: Refinement,
    /** Fertiger Text nach den Regeln — mit KI-Fassung oder, ohne KI, aus dem Rohtext. */
    val text: String,
    /** Womit verbessert wurde, als Anzeige ("Claude Sonnet 5.5", "Gemma 4 E2B"); null = ohne KI. */
    val model: String? = null,
    /** Warum ohne KI, obwohl eine Stufe gewaehlt war ("API-Fehler 401 …"); null = nicht gescheitert. */
    val skipped: String? = null,
    /** Hinweis, der den Text nicht betrifft: online gescheitert, lokal verbessert. */
    val note: String? = null,
) {
    /** Mit KI bearbeitet. Ohne: Stufe aus, nicht moeglich, gescheitert oder "ohne KI" getippt. */
    val refined: Boolean get() = model != null

    /** Ohne Text: ein `Log.d("$result")` darf nie ein Diktat zeigen. */
    override fun toString(): String =
        "Refined(refinement=$refinement, text=${text.length} Zeichen, model=$model, skipped=$skipped, note=$note)"
}

/** Ein Diktat ([TranscriptionEngine.transcribe]): Rohtext der Erkennung und was daraus wurde. */
data class Dictation(
    /** Rohtext der Erkennung, vor KI und Regeln; leer = nichts erkannt. */
    val raw: String,
    /** Wirksame Sprache (bei "auto" die erkannte). */
    val language: String,
    /** Laenge der Aufnahme. */
    val durationMs: Long,
    val result: Refined,
) {
    /** Was eingefuegt wird. */
    val text: String get() = result.text

    /** Ohne Text, wie [Refined.toString]. */
    override fun toString(): String =
        "Dictation(raw=${raw.length} Zeichen, language=$language, durationMs=$durationMs, result=$result)"
}
