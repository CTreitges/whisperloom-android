package com.chris.whisperloom

/**
 * Vokabular fuer die Erkennung: Namen, Fachbegriffe, gewuenschte Schreibweisen. Rein (ohne
 * Android), damit JVM-unit-testbar.
 *
 * Gespeichert wird die Liste in [Prefs.apiPrompt], ein Eintrag pro Zeile. Ein Freitext aus
 * frueheren Versionen bleibt dadurch ein einzelner Eintrag und kommt unveraendert beim
 * Anbieter an — keine Migration noetig.
 *
 * Dazu kann eine .md/.txt-Datei verknuepft sein ([VocabularySource]); ihre Begriffe werden bei
 * jedem Diktat neu gelesen und hinter die eigenen Eintraege gehaengt.
 */
object Vocabulary {

    /**
     * Obergrenze fuer den mitgeschickten Kontext. Whisper beachtet nur die LETZTEN 224 Token
     * (OpenAI-Doku zu `prompt`, whisper.cpp kappt initial_prompt genauso von vorn); 800 Zeichen
     * sind grob diese Menge. Weil vorn gekappt wird, stehen die eigenen Begriffe am ENDE.
     */
    const val MAX_PROMPT_CHARS = 800

    /** Groesste Datei, die gelesen wird — ein Vokabular hat nie Megabytes. */
    const val MAX_FILE_BYTES = 256 * 1024

    private const val BOM = "\uFEFF"

    private val SEPARATORS = Regex("[,;\\n]")

    private val HEADING = Regex("^#{1,6}\\s")

    /** Markdown-Zeilenanfaenge: Zitat, Aufzaehlung (auch Checkbox), Nummerierung. */
    private val LINE_MARKER = Regex("^(>\\s*|[-*+]\\s+(\\[[ xX]]\\s+)?|\\d+[.)]\\s+)")

    /** Hervorhebungen und Code-Zeichen am Rand eines Begriffs (**Name**, `Begriff`). */
    private val EMPHASIS = charArrayOf('*', '_', '`', '"')

    /** Gespeicherte Eintraege (eine Zeile = ein Eintrag). */
    fun entries(stored: String): List<String> =
        stored.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    fun serialize(entries: List<String>): String = entries.joinToString("\n")

    /**
     * Haengt Eingaben an: "Anna, Bernd; Carla" wird zu drei Eintraegen. Was es (ohne Ruecksicht
     * auf Gross-/Kleinschreibung) schon gibt, kommt nicht doppelt.
     */
    fun add(entries: List<String>, input: String): List<String> =
        dedupe(entries + input.split(SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() })

    fun remove(entries: List<String>, entry: String): List<String> = entries.filter { it != entry }

    /**
     * Begriffe aus einer .md/.txt-Datei: pro Zeile, zusaetzlich an Komma/Semikolon getrennt.
     * Markdown-Zeichen am Zeilenanfang, Hervorhebungen, Codeblock-Zaeune und Leerzeilen fallen weg.
     */
    fun parseFile(text: String): List<String> {
        val out = mutableListOf<String>()
        var inCodeBlock = false
        for (rawLine in text.removePrefix(BOM).lines()) {
            val line = rawLine.trim()
            if (line.startsWith("```") || line.startsWith("~~~")) {
                inCodeBlock = !inCodeBlock
                continue
            }
            // Ueberschriften gliedern die Datei ("# Namen", "## Technik") — sie sind keine Begriffe.
            // Tabellenzeilen ("| a | b |") ebenso wenig.
            if (inCodeBlock || line.isEmpty() || line.startsWith("---") || line.startsWith("|") ||
                HEADING.containsMatchIn(line)
            ) continue
            val content = LINE_MARKER.replace(line, "")
            for (part in content.split(',', ';')) {
                val term = part.trim().trim(*EMPHASIS).trim()
                // Ohne Buchstabe/Ziffer ist es kein Begriff: "-" allein, leere Checkbox "[ ]", "***".
                if (term.any { it.isLetterOrDigit() }) out += term
            }
        }
        return dedupe(out)
    }

    /**
     * Ergebnis fuer den Erkenner: [text] geht als `prompt` bzw. initial_prompt raus;
     * [used] von [total] Begriffen passten unter [MAX_PROMPT_CHARS]; [cut] = ein einzelner
     * Begriff war selbst zu lang und wurde gekuerzt.
     */
    data class Prompt(val text: String, val used: Int, val total: Int, val cut: Boolean = false) {
        val truncated: Boolean get() = used < total || cut
    }

    /**
     * Datei-Begriffe vorn, eigene Eintraege am Ende (Whisper verwirft von vorn); Duplikate
     * fallen weg, dabei gewinnt der eigene Eintrag. Wird es zu lang, fallen zuerst die vorderen
     * Datei-Begriffe weg — gekuerzt wird nur ganzheitlich, nie mitten in einem Begriff.
     */
    fun prompt(own: List<String>, file: List<String> = emptyList(), maxChars: Int = MAX_PROMPT_CHARS): Prompt {
        val ownTerms = dedupe(own)
        val ownKeys = ownTerms.map { it.lowercase() }.toHashSet()
        val ordered = dedupe(file).filter { it.lowercase() !in ownKeys } + ownTerms
        if (ordered.isEmpty()) return Prompt("", 0, 0)

        val last = ordered.last()
        if (last.length > maxChars) {
            // Ein einzelner langer Eintrag (z. B. Freitext aus v3.4): lieber gekuerzt als gar nicht.
            val head = last.take(maxChars).substringBeforeLast(' ').ifEmpty { last.take(maxChars) }
            return Prompt(head, 1, ordered.size, cut = true)
        }
        // Von hinten auffuellen, solange es passt.
        val kept = ArrayDeque<String>()
        var length = 0
        for (term in ordered.asReversed()) {
            val extra = if (kept.isEmpty()) term.length else term.length + 2
            if (length + extra > maxChars) break
            kept.addFirst(term)
            length += extra
        }
        return Prompt(kept.joinToString(", "), kept.size, ordered.size)
    }

    private fun dedupe(terms: List<String>): List<String> {
        val seen = HashSet<String>()
        return terms.filter { seen.add(it.lowercase()) }
    }
}
