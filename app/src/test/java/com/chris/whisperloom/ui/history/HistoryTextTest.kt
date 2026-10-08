package com.chris.whisperloom.ui.history

import com.chris.whisperloom.history.HistoryEntry
import com.chris.whisperloom.history.HistorySource
import com.chris.whisperloom.history.HistoryVersion
import com.chris.whisperloom.history.Processing
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine Helfer der Verlauf-Bildschirme: Tagesgruppen, Datum, Uhrzeit, Woerter, Absaetze, Stufen. */
class HistoryTextTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun millis(date: String, time: String, zone: ZoneId = berlin): Long =
        LocalDate.parse(date).atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli()

    private fun entry(createdAt: Long, processing: Processing = Processing.SUMMARIZE, versions: Map<Processing, HistoryVersion> = emptyMap()) =
        HistoryEntry("%013d-0000000a".format(createdAt), createdAt, HistorySource.KEYBOARD, "de", 41_000, "roh", processing, versions)

    @Test fun gruppiertNachKalendertagInDerZoneUndBehaeltDieReihenfolge() {
        val spaet = entry(millis("2026-10-08", "23:30"))
        val frueh = entry(millis("2026-10-08", "00:10"))
        val gestern = entry(millis("2026-10-07", "23:59"))

        val groups = HistoryText.groups(listOf(spaet, frueh, gestern), berlin)

        assertEquals(listOf(LocalDate.parse("2026-10-08"), LocalDate.parse("2026-10-07")), groups.map { it.day })
        assertEquals(listOf(spaet, frueh), groups[0].entries)
        // Dieselben Zeitpunkte in UTC: 23:30 Berlin ist dort noch derselbe Tag, 00:10 Berlin schon der Vortag.
        assertEquals(listOf(1, 2), HistoryText.groups(listOf(spaet, frueh, gestern), ZoneOffset.UTC).map { it.entries.size })
    }

    @Test fun heuteGesternSonstDatumMitJahrNurAusEinemAnderenJahr() {
        val heute = LocalDate.parse("2026-10-08")
        assertEquals(HistoryText.DayKind.TODAY, HistoryText.kind(heute, heute))
        assertEquals(HistoryText.DayKind.YESTERDAY, HistoryText.kind(heute.minusDays(1), heute))
        assertEquals(HistoryText.DayKind.DATE, HistoryText.kind(heute.minusDays(2), heute))
        assertEquals("Di., 6. Okt.", HistoryText.date(LocalDate.parse("2026-10-06"), heute))
        assertEquals("Mi., 31. Dez. 2025", HistoryText.date(LocalDate.parse("2025-12-31"), heute))
        // Gestern ueber den Jahreswechsel
        assertEquals(HistoryText.DayKind.YESTERDAY, HistoryText.kind(LocalDate.parse("2025-12-31"), LocalDate.parse("2026-01-01")))
    }

    @Test fun uhrzeitIn24Stunden() {
        assertEquals("09:05", HistoryText.time(millis("2026-10-08", "09:05"), berlin))
        assertEquals("21:40", HistoryText.time(millis("2026-10-08", "21:40"), berlin))
    }

    @Test fun woerterZaehlenOhneLeerraum() {
        assertEquals(0, HistoryText.words("   \n "))
        assertEquals(5, HistoryText.words("  ähm also ich\nkomme  später "))
    }

    @Test fun absaetzeNurAnLeerzeilenListenBleibenZusammen() {
        assertEquals(
            listOf("Erster Absatz.", "- Brot\n- Milch", "Ende."),
            HistoryText.paragraphs("Erster Absatz.\n\n- Brot\n- Milch\n \n\n Ende.\n"),
        )
        assertEquals(emptyList<String>(), HistoryText.paragraphs("  \n\n "))
    }

    @Test fun ohneKiNurWennEineStufeGewaehltWar() {
        val ohne = HistoryVersion("t", 0L, model = null, failed = "Kein Netz")
        val mit = HistoryVersion("t", 0L, model = "Claude")
        assertTrue(HistoryText.failed(Processing.SUMMARIZE, ohne))
        assertTrue("auch ohne Grund (Tipp auf „ohne KI“)", HistoryText.failed(Processing.POLISH_PLAIN, ohne.copy(failed = null)))
        assertFalse(HistoryText.failed(Processing.SUMMARIZE, mit))
        assertFalse("Aus ist nie gescheitert", HistoryText.failed(Processing.OFF, ohne))
        assertFalse("Bearbeitet ist keine Stufe", HistoryText.failed(Processing.EDITED, ohne))
        assertFalse(HistoryText.failed(Processing.SUMMARIZE, null))
    }

    @Test fun andereStufeWieInDenEinstellungenOhneAusPromptNurMitPro() {
        val ohnePro = listOf(Processing.POLISH_PLAIN, Processing.POLISH_CLEAN, Processing.POLISH_READABLE, Processing.BEAUTIFY, Processing.SUMMARIZE)
        assertEquals(ohnePro, HistoryText.stages(prompt = false))
        assertEquals(ohnePro + Processing.PROMPT, HistoryText.stages(prompt = true))
    }

    @Test fun oeffnetAufDerDamalsErzeugtenFassungSonstAufDemUrsprung() {
        val v = HistoryVersion("Kurz.", 0L, "Claude")
        assertEquals(Processing.SUMMARIZE, HistoryText.initial(entry(1L, Processing.SUMMARIZE, mapOf(Processing.POLISH_PLAIN to v, Processing.SUMMARIZE to v))))
        assertNull(HistoryText.initial(entry(1L, Processing.SUMMARIZE, mapOf(Processing.POLISH_PLAIN to v))))
    }
}
