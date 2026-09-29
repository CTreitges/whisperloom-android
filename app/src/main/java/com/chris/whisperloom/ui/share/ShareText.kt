package com.chris.whisperloom.ui.share

import com.chris.whisperloom.Formats
import com.chris.whisperloom.SharedTranscript

/** Reiner Text fuer Kopieren/Teilen — ohne Android, JVM-testbar. */
object ShareText {

    /** Die angezeigte Fassung: die KI-Fassung, wenn es eine gibt — sonst ohne Fuellwoerter oder wortgetreu. */
    fun paragraphs(t: SharedTranscript, hideFillers: Boolean): List<String> =
        t.paragraphsRefined ?: if (hideFillers) t.paragraphsCleaned else t.paragraphsVerbatim

    /** Ueberschrift eines Abschnitts: "— Quelle · Dauer —". */
    fun heading(t: SharedTranscript): String = "— ${t.source} · ${Formats.duration(t.durationMs)} —"

    /**
     * Absaetze durch Leerzeilen getrennt; bei [withHeadings] (mehrere Dateien) je Datei eine
     * Ueberschrift plus Leerzeile. Leere Ergebnisse werden zu [emptyText].
     */
    fun plain(
        results: List<SharedTranscript>,
        hideFillers: Boolean,
        withHeadings: Boolean,
        emptyText: String = "",
    ): String = results.joinToString("\n\n") { t ->
        val body = paragraphs(t, hideFillers).joinToString("\n\n").ifBlank { emptyText }
        if (withHeadings) listOf(heading(t), body).filter { it.isNotBlank() }.joinToString("\n\n") else body
    }.trim()
}
