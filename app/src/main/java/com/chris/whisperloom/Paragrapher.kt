package com.chris.whisperloom

/**
 * Teilt einen langen Transkript-Text heuristisch in Absaetze — fuer geteilte
 * Sprachnachrichten, die als ein Block kaum lesbar sind. Rein (ohne Android).
 *
 * Absatzregel (UX-Spec §2.9, verbindlich): innerhalb eines Stuecks beginnt nach 3 Saetzen
 * oder sobald 350 Zeichen erreicht sind ein neuer Absatz. Dazu: nie mitten im Satz trennen,
 * Woerter nie anfassen (die Absaetze mit Leerzeichen zusammengefuegt ergeben wieder den
 * Eingabetext), Absatzwechsel bevorzugt vor Diskursmarkern ("Also", "Ausserdem", "Dann",
 * "Okay" …). Leerzeilen im Eingabetext und Stueck-Grenzen behandelt der Aufrufer
 * ([SharedAudioTranscriber.paragraphsForChunks]).
 */
object Paragrapher {

    /** Nach so vielen Saetzen beginnt ein neuer Absatz (Spec: 3). */
    private const val MAX_SENTENCES = 3

    /** Sobald ein Absatz so viele Zeichen erreicht hat, beginnt der naechste (Spec: 350). */
    private const val MAX_CHARS = 350

    /** Ein Diskursmarker darf frueher trennen — aber erst ab so vielen Saetzen/Zeichen (keine Mini-Absaetze). */
    private const val MIN_SENTENCES = 2
    private const val MIN_CHARS = 120

    /** Ein so kurzer letzter Einzelsatz ("Tschuess.") haengt sich an den vorigen Absatz. */
    private const val SHORT_TAIL_CHARS = 60

    /** Satzgrenze: Satzzeichen (optional Anfuehrungszeichen/Klammer) und dann Whitespace. */
    private val SENTENCE_END = Regex("(?<=[.!?…][\"»“”')\\]]?)\\s+")

    /**
     * Woerter, die vor einem Punkt KEIN Satzende bedeuten. Einzelbuchstaben ("z. B.") und
     * reine Zahlen ("am 3. Oktober") werden zusaetzlich programmatisch erkannt.
     */
    private val ABBREVIATIONS = setOf(
        "bzw", "usw", "ca", "evtl", "ggf", "inkl", "exkl", "zzgl", "vgl", "dr", "nr", "str",
        "prof", "vs", "etc", "mr", "mrs", "ms", "st", "tel", "max", "min",
    )

    /** Satzanfaenge, vor denen ein Absatzwechsel natuerlich wirkt (klein geschrieben). */
    private val MARKERS = listOf(
        "also", "außerdem", "ausserdem", "dann", "und dann", "okay", "ok", "zweitens", "drittens",
        "noch was", "noch etwas", "übrigens", "jedenfalls", "ach so", "ach ja", "genau", "so",
        "anyway", "then", "secondly", "another thing", "by the way", "also,",
    )

    fun split(text: String): List<String> {
        val sentences = sentences(text)
        if (sentences.isEmpty()) return emptyList()

        val paragraphs = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        var currentChars = 0

        for (s in sentences) {
            val full = current.size >= MAX_SENTENCES || currentChars >= MAX_CHARS
            val marker = current.size >= MIN_SENTENCES && currentChars >= MIN_CHARS && startsWithMarker(s)
            if (full || marker) {
                paragraphs.add(current)
                current = mutableListOf()
                currentChars = 0
            }
            current.add(s)
            currentChars += s.length + if (current.size > 1) 1 else 0
        }
        paragraphs.add(current)

        // Ein kurzer Einzelsatz am Ende ("Tschuess.") wirkt als eigener Absatz verloren —
        // bewusste Ausnahme von der 3-Saetze-Regel.
        if (paragraphs.size > 1) {
            val tail = paragraphs.last()
            if (tail.size == 1 && tail[0].length < SHORT_TAIL_CHARS) {
                paragraphs.removeAt(paragraphs.size - 1)
                paragraphs.last().addAll(tail)
            }
        }
        return paragraphs.map { it.joinToString(" ") }
    }

    /** Zerlegt in Saetze; Abkuerzungen, Einzelbuchstaben und Ordnungszahlen bleiben verbunden. */
    fun sentences(text: String): List<String> {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return emptyList()
        val pieces = SENTENCE_END.split(normalized).filter { it.isNotEmpty() }

        val out = mutableListOf<String>()
        val pending = StringBuilder()
        for (piece in pieces) {
            if (pending.isNotEmpty()) pending.append(' ')
            pending.append(piece)
            if (!endsWithAbbreviation(piece)) {
                out.add(pending.toString())
                pending.setLength(0)
            }
        }
        if (pending.isNotEmpty()) out.add(pending.toString())
        return out
    }

    /**
     * Endet [piece] mit einer Abkuerzung, einem Einzelbuchstaben oder einer Ordnungszahl samt Punkt
     * ("z. B.", "ca.", "am 3.")? Dann ist der Punkt kein Satzende. Auch [TextPolisher] nutzt das,
     * damit nach "z. B." kein Gross-Buchstabe folgt.
     */
    fun endsWithAbbreviation(piece: String): Boolean {
        if (!piece.endsWith(".")) return false
        val lastWord = piece.dropLast(1).substringAfterLast(' ').trimStart('(', '"', '„', '«')
        if (lastWord.isEmpty()) return false
        // "z.B." ohne Leerzeichen: der Teil nach dem letzten Punkt zaehlt wie ein Einzelbuchstabe.
        val tail = lastWord.substringAfterLast('.')
        if (tail.length == 1 && tail[0].isLetter()) return true
        if (lastWord.all { it.isDigit() }) return true
        return lastWord.lowercase() in ABBREVIATIONS
    }

    private fun startsWithMarker(sentence: String): Boolean {
        val s = sentence.lowercase()
        return MARKERS.any { m ->
            s.startsWith(m) && (s.length == m.length || !s[m.length].isLetter())
        }
    }
}
