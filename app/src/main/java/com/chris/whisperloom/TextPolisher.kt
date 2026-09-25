package com.chris.whisperloom

import java.util.regex.Pattern

/**
 * Optionen fuer die Nachbearbeitung ("polish") eines rohen Whisper-Transkripts.
 * Rein datengetrieben, damit [TextPolisher] frei von Android-Abhaengigkeiten und
 * auf der JVM unit-testbar bleibt.
 */
data class PolishOptions(
    val removeFillers: Boolean = true,
    val autoCapitalize: Boolean = true,
    /** "auto" | "de" | "en" | ... — steuert die Fuellwort-Liste. */
    val language: String = "auto",
    /** Eigene Fuellwoerter des Nutzers, zusaetzlich zur Sprachliste (ganze Woerter). */
    val customFillers: Collection<String> = emptyList(),
    /** Woerter der eingebauten Sprachliste, die stehen bleiben sollen (klein geschrieben). */
    val disabledFillers: Set<String> = emptySet(),
    /**
     * Zeilenumbrueche behalten statt alles auf eine Zeile zu ziehen — fuer Text, den
     * das Sprachmodell bewusst in Absaetze oder Stichpunkte gegliedert hat.
     */
    val keepLineBreaks: Boolean = false,
)

/**
 * Entscheidet, was die Regex-Nachbearbeitung noch tun soll. Rein (ohne Android),
 * damit JVM-unit-testbar.
 */
object PolishPlan {

    /**
     * Wortgetreu: nur Whitespace und Leerzeichen vor Satzzeichen normalisieren. Fuer
     * FREMDE Sprachnachrichten — da will man hoeren, was gesagt wurde, nicht eine
     * aufgeraeumte Fassung davon.
     */
    fun verbatim(language: String) = PolishOptions(
        removeFillers = false,
        autoCapitalize = false,
        language = language,
    )

    /** Wortgetreu, aber ohne Fuellwoerter — die zweite Fassung der Share-Ansicht. */
    fun cleaned(
        language: String,
        customFillers: Collection<String> = emptyList(),
        disabledFillers: Set<String> = emptySet(),
    ) = PolishOptions(
        removeFillers = true,
        autoCapitalize = false,
        language = language,
        customFillers = customFillers,
        disabledFillers = disabledFillers,
    )

    /**
     * Wenn ein Sprachmodell selbst ueber Fuellwoerter entscheidet, darf die feste
     * Wortliste nicht nochmal daruebergehen — sonst wuerde zweimal gefiltert und die
     * Entscheidung der KI ("im Zweifel behalten") wieder ausgehebelt. Die restliche
     * Normalisierung (Whitespace, Satzzeichen, Gross-Schreibung) laeuft weiter.
     */
    fun options(
        removeFillers: Boolean,
        autoCapitalize: Boolean,
        language: String,
        refineMode: RefineMode,
        smartFillers: Boolean,
        customFillers: Collection<String> = emptyList(),
        disabledFillers: Set<String> = emptySet(),
        paragraphs: Boolean = true,
    ): PolishOptions {
        val refined = refineMode != RefineMode.OFF
        val aiDecidesFillers = refined && smartFillers
        return PolishOptions(
            removeFillers = removeFillers && !aiDecidesFillers,
            autoCapitalize = autoCapitalize,
            language = language,
            customFillers = customFillers,
            disabledFillers = disabledFillers,
            // Das Sprachmodell setzt Absaetze/Stichpunkte bewusst — nicht plattziehen. Mit
            // "Automatische Absaetze" aus werden Umbrueche, die das Modell trotzdem liefert,
            // hier zuverlaessig zu einem Fliesstext zusammengezogen.
            keepLineBreaks = refined && paragraphs,
        )
    }

    /** Klein geschrieben, getrimmt, ohne Leeres und Duplikate — so werden Fuellwoerter gespeichert. */
    fun normalizeFillers(words: Iterable<String>): Set<String> =
        words.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    /** "Ähm, halt,sozusagen" -> {"ähm", "halt", "sozusagen"} — fuer die Texteingabe im UI. */
    fun parseFillers(raw: String): Set<String> = normalizeFillers(raw.split(',', '\n', ';'))
}

/**
 * Wandelt rohe Whisper-Ausgabe in sauberen Text um: Whitespace normalisieren,
 * Fuellwoerter entfernen, Leerzeichen vor Satzzeichen fixen, Saetze gross schreiben.
 *
 * Bewusst konservativ: nur eindeutige Disfluenzen ("aehm", "um") werden entfernt,
 * keine echten Woerter — sonst zerstoert man legitime Eingaben.
 */
object TextPolisher {

    // Nur eindeutige Fuellsilben. Echte Woerter wie "like"/"halt" bleiben drin,
    // weil sie im Fliesstext meist gewollt sind (false-positive-Vermeidung).
    private val FILLERS: Map<String, List<String>> = mapOf(
        "de" to listOf("ähm", "äh", "öhm", "ähem", "hmm", "öh"),
        "en" to listOf("um", "uh", "uhm", "erm", "hmm"),
        "es" to listOf("eh", "este", "mmm"),
        "fr" to listOf("euh", "hmm"),
        "it" to listOf("ehm", "mmm"),
    )

    private val MULTI_WS = Pattern.compile("\\s+")
    private val MANY_BLANK_LINES = Pattern.compile("\\n{3,}")
    // Ein Punkt direkt vor Buchstabe oder Ziffer ist kein Satzzeichen, sondern Teil des
    // naechsten Worts (".log", ".env", ".5") — dort bleibt das Leerzeichen davor stehen.
    private val SPACE_BEFORE_PUNCT = Pattern.compile("\\s+([,;:!?…]|\\.(?![\\p{L}\\p{N}]))")
    private val COMMA_BEFORE_END = Pattern.compile(",\\s*(?=[.!?…])")

    fun polish(raw: String, options: PolishOptions = PolishOptions()): String {
        var text = raw.trim()
        if (text.isEmpty()) return ""

        text = normalizeWhitespace(text, options.keepLineBreaks)

        if (options.removeFillers) {
            val fillers = builtinFillers(options.language).filter { it !in options.disabledFillers } +
                options.customFillers
            for (filler in fillers) {
                if (filler.isBlank()) continue
                // (?<!\p{L}) ... (?!\p{L}) = ganze-Wort-Grenze, unicode-tauglich (ae, oe...).
                // Optionales folgendes Komma mitnehmen, damit keine ", ," Reste bleiben.
                val p = Pattern.compile(
                    "(?<!\\p{L})" + Pattern.quote(filler.trim()) + "(?!\\p{L}),?",
                    Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
                )
                text = p.matcher(text).replaceAll(" ")
            }
            // "gut, ähm." -> "gut, ." -> "gut." — ein Komma direkt vor dem Satzende ist nie gewollt.
            text = COMMA_BEFORE_END.matcher(text).replaceAll("")
            text = normalizeWhitespace(text, options.keepLineBreaks)
        }

        text = SPACE_BEFORE_PUNCT.matcher(text).replaceAll("$1")
        text = normalizeWhitespace(text, options.keepLineBreaks)

        if (options.autoCapitalize) {
            text = capitalizeSentences(text)
        }
        return text
    }

    /** Alles auf eine Zeile — oder je Zeile normalisieren und hoechstens eine Leerzeile lassen. */
    private fun normalizeWhitespace(text: String, keepLineBreaks: Boolean): String {
        if (!keepLineBreaks) return MULTI_WS.matcher(text).replaceAll(" ").trim()
        val lines = text.split('\n').joinToString("\n") { MULTI_WS.matcher(it).replaceAll(" ").trim() }
        return MANY_BLANK_LINES.matcher(lines).replaceAll("\n\n").trim()
    }

    // Im auto-Modus NUR sprachuebergreifend eindeutige Disfluenzen — niemals Woerter,
    // die in irgendeiner Sprache echt sind (z.B. dt. "um", span. "este"). Sonst wuerde
    // der auto-Modus legitime Eingaben loeschen.
    private val AUTO_FILLERS = listOf(
        "ähm", "äh", "öhm", "ähem", "öh", "uh", "uhm", "erm", "euh", "ehm", "hmm", "mmm",
    )

    /** Eingebaute Liste je Sprache (klein geschrieben) — fuer das Bearbeiten-Sheet im UI. */
    fun builtinFillers(language: String): List<String> {
        if (language == "auto") return AUTO_FILLERS
        return FILLERS[language] ?: emptyList()
    }

    /**
     * Erster Buchstabe + jeder Satzanfang gross. Ein Satz endet erst mit . ! ? UND folgendem
     * Leerraum (schliessende Anfuehrungszeichen/Klammern duerfen dazwischen stehen) — sonst
     * wuerde aus "config.yaml" "config.Yaml" und aus "Python 3.13 gegenueber" "3.13 Gegenueber".
     * Beginnt ein Satz mit einer Ziffer, bleibt das folgende Wort, wie es ist ("- 12 people").
     */
    private fun capitalizeSentences(text: String): String {
        val sb = StringBuilder(text.length)
        var capitalizeNext = true
        var sentenceEnd = false
        for (ch in text) {
            if (capitalizeNext && ch.isLetterOrDigit()) {
                sb.append(ch.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(ch)
            }
            when {
                ch == '.' || ch == '!' || ch == '?' -> sentenceEnd = true
                !sentenceEnd -> {}
                ch.isWhitespace() -> {
                    capitalizeNext = true
                    sentenceEnd = false
                }
                ch in SENTENCE_CLOSERS -> {}
                else -> sentenceEnd = false
            }
        }
        return sb.toString()
    }

    private const val SENTENCE_CLOSERS = "\"'“”„»«)]’"
}
