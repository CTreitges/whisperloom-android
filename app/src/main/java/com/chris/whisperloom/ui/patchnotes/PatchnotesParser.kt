package com.chris.whisperloom.ui.patchnotes

import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Kategorien eines Releases; die Reihenfolge hier ist die Anzeige-Reihenfolge. TECH wird nie angezeigt. */
enum class Category { ADDED, CHANGED, FIXED, SECURITY, REMOVED, KNOWN, OTHER, TECH }

/** Ein Punkt: fetter Anfang ([lead]), Rest ([body]) und eingerueckte Unterpunkte. Texte = Inline-Markdown. */
data class Entry(val lead: String?, val body: String?, val children: List<String>)

/** Eine `###`-Kategorie; [title] ist der Originaltitel (Anzeige bei [Category.OTHER]). */
data class Section(val category: Category, val title: String, val entries: List<Entry>)

/** Ein Punkt aus den Fastlane-Highlights; [kicker] = einzelnes Wort vor dem Doppelpunkt („Neu“, „Behoben“). */
data class Highlight(val kicker: String?, val text: String)

data class HighlightFile(val version: String, val tagline: String?, val items: List<Highlight>)

data class Release(
    val version: String,
    val date: LocalDate?,
    val intro: String?,
    /** Datei-Reihenfolge. */
    val sections: List<Section>,
    val highlights: List<Highlight> = emptyList(),
    val tagline: String? = null,
) {
    /** Ohne Technik, in Anzeige-Reihenfolge (sortedBy ist stabil: mehrere OTHER bleiben in Datei-Reihenfolge). */
    val visibleSections get() = sections.filter { it.category != Category.TECH }.sortedBy { it.category }
    val changeCount get() = visibleSections.sumOf { it.entries.size }
    val major get() = version.substringBefore('.').toInt()
}

/**
 * Liest CHANGELOG.md (Keep a Changelog, deutsch) und die Fastlane-Highlights (`<versionCode>.txt`).
 * Reines Kotlin ohne Android, damit es auf der JVM gegen die echten Dateien testbar ist.
 */
object PatchnotesParser {

    private val REF_DEFINITION = Regex("""^\[[^\]]+]:\s""")
    private val VERSION_HEAD = Regex("""^##\s+\[?([^\]\s]+)]?(?:\s+[—–-]\s+(\d{4}-\d{2}-\d{2}))?""")
    private val VERSION = Regex("""^\d+(\.\d+){1,3}$""")
    private val BULLET = Regex("""^(\s*)[-*]\s+(.+)""")
    private val LEAD = Regex("""^\*\*(.+?)\*\*(.*)$""")
    private val HIGHLIGHT_VERSION = Regex("""(\d+(?:\.\d+){1,2})""")
    private val KICKER = Regex("""^([A-ZÄÖÜ]\p{L}{1,11}):\s+(.+)""")
    private val TAGLINE_SEPARATORS = listOf(" – ", " — ", " - ")
    private val HIGHLIGHT_MARKERS = listOf("• ", "- ", "* ")

    private val CATEGORIES = mapOf(
        "hinzugefügt" to Category.ADDED,
        "geändert" to Category.CHANGED,
        "behoben" to Category.FIXED,
        "sicherheit" to Category.SECURITY,
        "entfernt" to Category.REMOVED,
        "bekannte punkte" to Category.KNOWN,
        "technik" to Category.TECH,
    )

    // Veraenderliche Bausteine waehrend des Lesens; am Ende werden daraus die unveraenderlichen Typen.
    private class EntryBuilder(val lead: String?, var body: String?) {
        val children = mutableListOf<String>()
        fun build() = Entry(lead, body, children.toList())
    }

    private class SectionBuilder(val category: Category, val title: String) {
        val entries = mutableListOf<EntryBuilder>()
        fun build() = Section(category, title, entries.map { it.build() })
    }

    private class ReleaseBuilder(val version: String, val date: LocalDate?) {
        var intro: String? = null
        val sections = mutableListOf<SectionBuilder>()
        var section: SectionBuilder? = null
        fun build() = Release(version, date, intro, sections.map { it.build() })
    }

    fun parseChangelog(md: String): List<Release> {
        val releases = mutableListOf<ReleaseBuilder>()
        var release: ReleaseBuilder? = null // null: Dateikopf oder uebersprungene Version ([Unreleased])
        var inCode = false
        for (line in md.replace("\r", "").lines()) {
            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
                continue
            }
            if (inCode || REF_DEFINITION.containsMatchIn(line) || line.startsWith("# ")) continue
            val head = VERSION_HEAD.find(line)
            if (head != null) {
                val version = head.groupValues[1]
                release = if (VERSION.matches(version)) {
                    ReleaseBuilder(version, parseDate(head.groupValues[2])).also { releases += it }
                } else {
                    null
                }
                continue
            }
            val current = release ?: continue
            when {
                line.startsWith("### ") -> current.section = sectionFor(current, line.removePrefix("### ").trim())
                line.isBlank() -> Unit
                else -> {
                    val bullet = BULLET.find(line)
                    if (bullet != null) {
                        addBullet(current, bullet.groupValues[1].length, bullet.groupValues[2].trim())
                    } else {
                        addText(current, line.trim())
                    }
                }
            }
        }
        return releases.map { it.build() }
    }

    private fun parseDate(s: String): LocalDate? =
        if (s.isEmpty()) null else try { LocalDate.parse(s) } catch (_: DateTimeParseException) { null }

    /** Doppelte Kategorie in einer Version: weiter in die vorhandene (OTHER nur bei gleichem Titel). */
    private fun sectionFor(release: ReleaseBuilder, title: String): SectionBuilder {
        val category = CATEGORIES[title.lowercase()] ?: Category.OTHER
        return release.sections.firstOrNull { it.category == category && (category != Category.OTHER || it.title == title) }
            ?: SectionBuilder(category, title).also { release.sections += it }
    }

    private fun addBullet(release: ReleaseBuilder, indent: Int, text: String) {
        val section = release.section
        if (section == null) {
            // Aufzaehlung vor dem ersten ###: Teil des Intros (kommt in der echten Datei nicht vor).
            addText(release, text)
            return
        }
        val last = section.entries.lastOrNull()
        if (indent >= 2 && last != null) {
            last.children += text // tiefere Ebenen werden auf diese eine abgeflacht
        } else {
            section.entries += if (indent >= 2) EntryBuilder(null, text) else splitLead(text)
        }
    }

    /** Fortsetzungszeile: Intro, sonst ans letzte Element (Kind oder Eintrag), sonst neuer Eintrag ohne Lead. */
    private fun addText(release: ReleaseBuilder, text: String) {
        val section = release.section
        if (section == null) {
            release.intro = join(release.intro, text)
            return
        }
        val last = section.entries.lastOrNull()
        when {
            last == null -> section.entries += EntryBuilder(null, text)
            last.children.isNotEmpty() -> last.children[last.children.lastIndex] = join(last.children.last(), text)
            else -> last.body = join(last.body, text)
        }
    }

    private fun join(a: String?, b: String) = if (a.isNullOrEmpty()) b else "$a $b"

    /** Lead-Split `**L**R` (nur oberste Ebene, Spec §3): die erste passende Regel gilt. */
    private fun splitLead(text: String): EntryBuilder {
        val m = LEAD.find(text) ?: return EntryBuilder(null, text)
        val lead = m.groupValues[1]
        val rest = m.groupValues[2]
        return when {
            rest.isBlank() -> EntryBuilder(lead, null)
            lead.last() in ".:!?" -> EntryBuilder(lead.trimEnd('.', ':'), rest.trim())
            rest.startsWith(" — ") || rest.startsWith(" – ") ->
                EntryBuilder(lead, rest.substring(3).trim().replaceFirstChar { it.uppercaseChar() })
            rest.startsWith(" (") -> EntryBuilder(lead, rest.trim())
            // Der Satz laeuft nach dem Fettdruck weiter („ mit sieben Schritten …“): kein Lead, ** bleibt inline.
            else -> EntryBuilder(null, text)
        }
    }

    /**
     * Fastlane-Highlights: Zeile 1 traegt die Version (sonst null) und nach dem ersten Gedankenstrich
     * die Tagline; danach Punkte mit `•`/`-`/`*`, andere Zeilen haengen am vorigen Punkt.
     */
    fun parseHighlights(txt: String): HighlightFile? {
        val lines = txt.replace("\r", "").lines()
        val first = lines.firstOrNull() ?: return null
        val version = HIGHLIGHT_VERSION.find(first)?.value ?: return null
        val sep = TAGLINE_SEPARATORS.mapNotNull { s -> first.indexOf(s).takeIf { it >= 0 }?.let { it to s } }.minByOrNull { it.first }
        val tagline = sep?.let { (i, s) -> first.substring(i + s.length).trim() }
            ?.takeIf { it.isNotEmpty() }
            ?.replaceFirstChar { it.uppercaseChar() }

        val items = mutableListOf<String>()
        for (raw in lines.drop(1)) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val marker = HIGHLIGHT_MARKERS.firstOrNull { line.startsWith(it) }
            when {
                marker != null -> items += line.removePrefix(marker).trim()
                items.isEmpty() -> items += line
                else -> items[items.lastIndex] = join(items.last(), line)
            }
        }
        return HighlightFile(version, tagline, items.map(::highlight))
    }

    private fun highlight(text: String): Highlight {
        val m = KICKER.find(text) ?: return Highlight(null, text)
        return Highlight(m.groupValues[1], m.groupValues[2])
    }

    /**
     * Highlights an die Releases haengen. [files] aufsteigend nach versionCode: bei derselben Version gewinnt
     * die spaetere Datei; Highlights fuer eine unbekannte Version fallen weg.
     */
    fun merge(releases: List<Release>, files: List<HighlightFile>): List<Release> {
        val byVersion = files.associateBy { it.version } // associateBy: der letzte Eintrag gewinnt
        return releases.map { r ->
            val f = byVersion[r.version] ?: return@map r
            r.copy(highlights = f.items, tagline = f.tagline)
        }
    }

    /** Einzeiler fuer die Versionsliste: Tagline, Intro, erstes Highlight, erster Eintrag (als Klartext). */
    fun teaser(r: Release): String? {
        val firstHighlight = r.highlights.firstOrNull()?.let { h -> if (h.kicker != null) "${h.kicker}: ${h.text}" else h.text }
        val firstEntry = r.sections.firstOrNull { it.category != Category.TECH && it.entries.isNotEmpty() }
            ?.entries?.first()?.let { it.lead ?: it.body }
        return (r.tagline ?: r.intro ?: firstHighlight ?: firstEntry)?.let(::plainText)
    }
}
