package com.chris.whisperloom.ui.history

import com.chris.whisperloom.history.HistoryEntry
import com.chris.whisperloom.history.HistoryVersion
import com.chris.whisperloom.history.Processing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Reine Helfer der Verlauf-Bildschirme (ohne Android, JVM-testbar): Tage, Uhrzeit, Woerter, Absaetze. */
object HistoryText {

    /** Eine Tagesgruppe der Liste: [day] = Kalendertag in der Zone des Geraets. */
    data class DayGroup(val day: LocalDate, val entries: List<HistoryEntry>)

    /** Wie die Ueberschrift einer Tagesgruppe heisst. */
    enum class DayKind { TODAY, YESTERDAY, DATE }

    private val DATE = DateTimeFormatter.ofPattern("EEE, d. MMM", Locale.GERMAN)
    private val DATE_YEAR = DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", Locale.GERMAN)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMAN)
    private val BLANK_LINE = Regex("\\n\\s*\\n")
    private val WHITESPACE = Regex("\\s+")

    fun day(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    /** Eintraege (neueste zuerst) nach Kalendertag, Reihenfolge bleibt. */
    fun groups(entries: List<HistoryEntry>, zone: ZoneId): List<DayGroup> =
        entries.groupBy { day(it.createdAt, zone) }.map { (day, list) -> DayGroup(day, list) }

    fun kind(day: LocalDate, today: LocalDate): DayKind = when (day) {
        today -> DayKind.TODAY
        today.minusDays(1) -> DayKind.YESTERDAY
        else -> DayKind.DATE
    }

    /** "Mo., 6. Okt." — aus einem anderen Jahr mit Jahr. */
    fun date(day: LocalDate, today: LocalDate): String = (if (day.year == today.year) DATE else DATE_YEAR).format(day)

    /** "14:32" */
    fun time(millis: Long, zone: ZoneId): String = TIME.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun words(text: String): Int = text.trim().split(WHITESPACE).count { it.isNotEmpty() }

    /** Absaetze an Leerzeilen; einfache Zeilenumbrueche (Listen) bleiben im Absatz. */
    fun paragraphs(text: String): List<String> = text.split(BLANK_LINE).map { it.trim() }.filter { it.isNotEmpty() }

    /** Eine Stufe war gewaehlt, aber die Fassung ist ohne KI entstanden (kein Netz, Fehler). */
    fun failed(processing: Processing, version: HistoryVersion?): Boolean =
        version != null && version.model == null && processing != Processing.OFF && processing != Processing.EDITED

    /** Die Verarbeitungen, die "Andere Stufe …" anbietet — wie in den Einstellungen, ohne "Aus", Prompt nur mit Pro. */
    fun stages(prompt: Boolean): List<Processing> =
        Processing.entries.filter { it != Processing.OFF && it != Processing.EDITED && (prompt || it != Processing.PROMPT) }

    /** Was der Eintrag beim Oeffnen zeigt: die damals erzeugte Fassung, ohne sie den Ursprung (null). */
    fun initial(entry: HistoryEntry): Processing? = entry.processing.takeIf { it in entry.versions }
}
