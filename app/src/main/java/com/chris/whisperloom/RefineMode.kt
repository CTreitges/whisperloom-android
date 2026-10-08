package com.chris.whisperloom

/**
 * Was das Sprachmodell nach der Erkennung mit dem Text tun soll.
 * [READABLE] ist nirgends waehlbar: es ist [POLISH] mit der Bereinigung "Lesbar" ([Refinement.of]) —
 * je eine fuers Diktat und fuer geteilte Sprachnachrichten.
 * [PROMPT] nur, wenn in den erweiterten Optionen eingeschaltet ([Prefs.promptLevelEnabled]).
 * Die fruehere Stufe "Absaetze" (Schluessel `paragraphs`, nie waehlbar) liest [Prefs.refineMode] als [POLISH].
 */
enum class RefineMode(val key: String) {
    OFF("off"),
    POLISH("polish"),
    BEAUTIFY("beautify"),
    SUMMARIZE("summarize"),

    /** Glaetten plus behutsames Lektorat: Satzbau lesbar, Wortwahl und Ton bleiben. Nie gespeichert. */
    READABLE("readable"),

    /** Formt das Diktat zu einem Prompt fuer einen KI-Assistenten (ChatGPT, Claude, Gemini). */
    PROMPT("prompt");

    /**
     * Unter welcher Stufe das Textmodell eingestellt ist ([Prefs.llmModelFor]): die Bereinigung
     * "Lesbar" rechnet mit dem Glaetten-Modell. null = aus, kein Modell.
     */
    val modelStage: RefineMode?
        get() = when (this) {
            OFF -> null
            POLISH, READABLE -> POLISH
            BEAUTIFY, SUMMARIZE, PROMPT -> this
        }

    companion object {
        /** Reihenfolge im Einstellungs-Dropdown. */
        val SETTINGS = listOf(OFF, POLISH, BEAUTIFY, SUMMARIZE)

        /** Stufen mit eigenem Textmodell ([modelStage]); gelten fuer Diktat und Sprachnachrichten gemeinsam. */
        val MODEL_STAGES = listOf(POLISH, BEAUTIFY, SUMMARIZE, PROMPT)

        /** Stufen mit eigenem Schalter "Absaetze" beim Diktat ([Prefs.paragraphsFor]). */
        val PARAGRAPH_STAGES = listOf(POLISH, BEAUTIFY)

        /** Die waehlbaren Stufen — "Prompt" nur fuer die, die sie eingeschaltet haben. */
        fun settings(promptEnabled: Boolean): List<RefineMode> = if (promptEnabled) SETTINGS + PROMPT else SETTINGS

        fun fromKey(key: String?): RefineMode = entries.firstOrNull { it.key == key } ?: OFF
    }
}
