package com.chris.whisperloom.api

import com.chris.whisperloom.RefineMode

/**
 * Baut die Anweisung fuer die Textverbesserung. Rein (ohne Android/Netz), damit
 * JVM-unit-testbar — die Anweisung entscheidet ueber die Textqualitaet und darf
 * nicht unbemerkt verrutschen.
 */
object RefinePrompt {

    /**
     * @param mode Was das Modell tun soll (nicht [RefineMode.OFF]).
     * @param german Anweisung auf Deutsch (bei deutscher Diktatsprache) statt Englisch.
     * @param smartFillers Wenn true, entscheidet das Modell selbst, welche Fuellwoerter,
     *   Versprecher und Wiederholungen weg koennen — statt einer festen Wortliste.
     *   Bei einer Zusammenfassung gegenstandslos.
     * @param paragraphs Schalter "Automatische Absaetze": false = ein durchgehender Text ohne
     *   Zeilenumbrueche. Fuer [RefineMode.PARAGRAPHS] gegenstandslos (Absaetze sind dort der Zweck).
     */
    fun build(mode: RefineMode, german: Boolean, smartFillers: Boolean, paragraphs: Boolean = true): String {
        require(mode != RefineMode.OFF) { "RefineMode.OFF hat keine Anweisung" }
        val withParagraphs = paragraphs || mode == RefineMode.PARAGRAPHS
        val sb = StringBuilder()
        sb.append(if (german) taskDe(mode, withParagraphs) else taskEn(mode, withParagraphs))
        if (!withParagraphs) sb.append(if (german) NO_PARAGRAPHS_DE else NO_PARAGRAPHS_EN)
        if (smartFillers && mode != RefineMode.SUMMARIZE) {
            sb.append(if (german) FILLERS_DE else FILLERS_EN)
        }
        sb.append(if (german) replyDe(mode) else replyEn(mode))
        return sb.toString()
    }

    private fun taskDe(mode: RefineMode, paragraphs: Boolean): String = when (mode) {
        RefineMode.POLISH ->
            "Du korrigierst diktierten Text. Setze Zeichensetzung, Gross- und " +
                "Kleinschreibung" + (if (paragraphs) " sowie Absaetze" else "") + " richtig. " +
                "Aendere den Inhalt nicht, uebersetze nicht, ergaenze nichts und kommentiere nicht."
        RefineMode.BEAUTIFY ->
            "Du ueberarbeitest diktierten Text, damit er verstaendlicher wird: formuliere " +
                "holprige Stellen klarer, ziehe zerstueckelte Saetze zusammen und setze " +
                (if (paragraphs) "Zeichensetzung und Absaetze" else "die Zeichensetzung") + " richtig. " +
                "Bewahre Inhalt und Absicht, behalte die Ich-Perspektive bei, erfinde keine " +
                "neuen Fakten, uebersetze nicht und kommentiere nicht."
        RefineMode.SUMMARIZE ->
            "Du fasst diktierten Text kurz zusammen: die Kernaussagen " +
                (if (paragraphs) "in wenigen Absaetzen oder Stichpunkten" else "in wenigen Saetzen") +
                ", in der Sprache des Textes. Erfinde keine neuen Fakten, " +
                "uebersetze nicht und kommentiere nicht."
        RefineMode.PARAGRAPHS ->
            "Du gliederst diktierten Text in Absaetze. Lass den Wortlaut sonst unveraendert, " +
                "fasse nichts zusammen, aendere den Inhalt nicht, uebersetze nicht, ergaenze " +
                "nichts und kommentiere nicht."
        RefineMode.OFF -> ""
    }

    private fun taskEn(mode: RefineMode, paragraphs: Boolean): String = when (mode) {
        RefineMode.POLISH ->
            "You clean up dictated text. Fix punctuation" +
                (if (paragraphs) ", capitalisation and paragraphs" else " and capitalisation") + ". " +
                "Do not change the meaning, do not translate, do not add anything and do not comment."
        RefineMode.BEAUTIFY ->
            "You rewrite dictated text so it reads more clearly: smooth out clumsy " +
                "phrasing, merge fragmented sentences and fix " +
                (if (paragraphs) "punctuation and paragraphs" else "punctuation") + ". " +
                "Preserve the content and intent, keep the first-person perspective, do not " +
                "invent new facts, do not translate and do not comment."
        RefineMode.SUMMARIZE ->
            "You summarise dictated text briefly: the key points " +
                (if (paragraphs) "in a few paragraphs or bullet points" else "in a few sentences") +
                ", in the language of the text. Do not invent new facts, do not " +
                "translate and do not comment."
        RefineMode.PARAGRAPHS ->
            "You split dictated text into paragraphs. Leave the wording unchanged " +
                "otherwise, do not summarise, do not change the meaning, do not translate, " +
                "do not add anything and do not comment."
        RefineMode.OFF -> ""
    }

    private fun replyDe(mode: RefineMode): String = when (mode) {
        RefineMode.SUMMARIZE -> " Antworte ausschliesslich mit der Zusammenfassung."
        else -> " Antworte ausschliesslich mit dem bearbeiteten Text."
    }

    private fun replyEn(mode: RefineMode): String = when (mode) {
        RefineMode.SUMMARIZE -> " Reply only with the summary."
        else -> " Reply only with the edited text."
    }

    private const val NO_PARAGRAPHS_DE =
        " Setze keine Absaetze und keine Zeilenumbrueche: gib alles als einen einzigen " +
            "fortlaufenden Absatz zurueck."

    private const val NO_PARAGRAPHS_EN =
        " Do not add paragraphs or line breaks: return everything as one single continuous paragraph."

    private const val FILLERS_DE =
        " Entferne ausserdem Fuellwoerter, Versprecher, Stotterer und unbeabsichtigte " +
            "Wiederholungen, wenn sie erkennbar nicht gemeint waren. Im Zweifel behalte das Wort."

    private const val FILLERS_EN =
        " Also remove filler words, false starts, stutters and unintended repetitions " +
            "where they were clearly not meant. When in doubt, keep it."
}
