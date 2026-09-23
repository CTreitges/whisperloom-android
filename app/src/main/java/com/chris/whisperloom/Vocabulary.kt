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
     * Obergrenze fuer den mitgeschickten Kontext. Whisper beachtet nur die letzten 224 Token
     * (OpenAI-Doku zu `prompt`, whisper.cpp kappt initial_prompt genauso); 800 Zeichen deutscher
     * Text sind knapp darunter. Was darueber hinausgeht, wuerde der Erkenner ohnehin verwerfen.
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
            if (inCodeBlock || line.isEmpty() || line.startsWith("---") || HEADING.containsMatchIn(line)) continue
            val content = LINE_MARKER.replace(line, "")
            for (part in content.split(',', ';')) {
                val term = part.trim().trim(*EMPHASIS).trim()
                if (term.isNotEmpty()) out += term
            }
        }
        return dedupe(out)
    }

    /**
     * Ergebnis fuer den Erkenner: [text] geht als `prompt` bzw. initial_prompt raus;
     * [used] von [total] Begriffen passten unter [MAX_PROMPT_CHARS].
     */
    data class Prompt(val text: String, val used: Int, val total: Int) {
        val truncated: Boolean get() = used < total
    }

    /**
     * Eigene Eintraege zuerst, dann die Datei-Begriffe; Duplikate fallen weg. Es wird nur
     * ganzheitlich gekuerzt — nie mitten in einem Begriff.
     */
    fun prompt(own: List<String>, file: List<String> = emptyList(), maxChars: Int = MAX_PROMPT_CHARS): Prompt {
        val all = dedupe(own + file)
        val sb = StringBuilder()
        var used = 0
        for (term in all) {
            if (sb.isEmpty() && term.length > maxChars) {
                // Ein einzelner langer Eintrag (z. B. Freitext aus v3.4): lieber gekuerzt als gar nicht.
                sb.append(term.take(maxChars).substringBeforeLast(' ').ifEmpty { term.take(maxChars) })
                used++
                break
            }
            val extra = if (sb.isEmpty()) term.length else term.length + 2
            if (sb.length + extra > maxChars) break
            if (sb.isNotEmpty()) sb.append(", ")
            sb.append(term)
            used++
        }
        return Prompt(sb.toString(), used, all.size)
    }

    private fun dedupe(terms: List<String>): List<String> {
        val seen = HashSet<String>()
        return terms.filter { seen.add(it.lowercase()) }
    }
}
