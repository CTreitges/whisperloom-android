package com.chris.whisperloom.ui.share

import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.SharedTranscript

/** Globale Zustaende der Share-Ansicht (UX-Spec §2.9). */
enum class SharePhase { NO_FILE, NOT_CONFIGURED, LOADING, DONE, ALL_FAILED }

/** Eine geteilte Datei: erst nur der Name, spaeter Ergebnis oder Fehlergrund. */
data class ShareFile(
    val name: String,
    val result: SharedTranscript? = null,
    val error: String? = null,
)

/**
 * Fortschritt der laufenden Datei. [chunk] ist der 0-basierte Schritt aus
 * [com.chris.whisperloom.SharedAudioTranscriber] und ist nach dem letzten Stueck == [chunks].
 */
data class ShareProgress(
    val fileIndex: Int,
    val files: Int,
    val chunk: Int,
    val chunks: Int,
    val label: String,
) {
    /** Anteil 0..1 fuer den determinierten Balken. */
    val fraction: Float get() = fraction(fileIndex, files, chunk, chunks)

    /** 1-basierte Stuecknummer fuer die Anzeige, nie groesser als [chunks]. */
    val displayChunk: Int get() = (chunk + 1).coerceIn(1, maxOf(chunks, 1))

    /** Nur ein Stueck in nur einer Datei: dann reicht der Statustext allein (Spec §2.9). */
    val isSingle: Boolean get() = files <= 1 && chunks <= 1

    companion object {
        /** (fileIndex + chunk/chunks) / files, auf 0..1 begrenzt; ohne Dateien 0. */
        fun fraction(fileIndex: Int, files: Int, chunk: Int, chunks: Int): Float {
            if (files <= 0) return 0f
            val within = if (chunks <= 0) 0f else (chunk.toFloat() / chunks).coerceIn(0f, 1f)
            return ((fileIndex + within) / files).coerceIn(0f, 1f)
        }
    }
}

/** Alles, was die Share-Ansicht zeichnet — unveraenderlich, Aenderungen ueber copy(). */
data class ShareUiState(
    val phase: SharePhase,
    val files: List<ShareFile> = emptyList(),
    val progress: ShareProgress? = null,
    /** Schalter "Fuellwoerter ausblenden" — Spiegel von Prefs.shareHideFillers. */
    val hideFillers: Boolean = true,
    /** FEHLER GESAMT: Grund der ersten Datei. */
    val failure: String? = null,
) {
    val results: List<SharedTranscript> get() = files.mapNotNull { it.result }
    val hasErrors: Boolean get() = files.any { it.error != null }
    val totalDurationMs: Long get() = results.sumOf { it.durationMs }

    /** Nur Ergebnisse ohne KI-Fassung haben etwas, das "Fuellwoerter ausblenden" umschalten koennte. */
    val hasUnrefined: Boolean get() = results.any { it.paragraphsRefined == null }

    /** Die fuer geteilte Audios eingestellte KI-Stufe dieses Laufs; OFF = keine. */
    val refineMode: RefineMode get() = results.firstOrNull { it.refineMode != RefineMode.OFF }?.refineMode ?: RefineMode.OFF

    /** Grund, falls die Textverbesserung bei einer Datei gescheitert ist. */
    val refineSkipped: String? get() = results.firstNotNullOfOrNull { it.refineSkipped }

    fun withFile(index: Int, change: (ShareFile) -> ShareFile): ShareUiState =
        copy(files = files.mapIndexed { i, f -> if (i == index) change(f) else f })
}
