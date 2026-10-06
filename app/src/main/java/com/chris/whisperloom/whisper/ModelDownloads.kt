package com.chris.whisperloom.whisper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Zustand eines Modell-Downloads, wie ihn die UI zeigt (E4: nicht geladen / laedt / Fehler / fertig). */
sealed class DownloadState {

    /** Kein Download aktiv. Ob das Modell installiert ist, weiss [ModelStore]. */
    data object Idle : DownloadState()

    data class Running(val bytes: Long, val total: Long, val bytesPerSec: Long) : DownloadState() {
        val percent: Int get() = percentOf(bytes, total)
    }

    /** [retryable] = "Erneut" setzt an der Teildatei an; sonst laedt es von vorn. */
    data class Failed(val message: String, val retryable: Boolean) : DownloadState()

    /** Vollstaendig und verifiziert. Bleibt bis [ModelDownloads.clear] oder Prozessende stehen. */
    data object Done : DownloadState()
}

/** Ganzzahliger Fortschritt 0..100; 0, wenn die Gesamtgroesse unbekannt ist. */
fun percentOf(bytes: Long, total: Long): Int =
    if (total > 0) (bytes * 100 / total).toInt().coerceIn(0, 100) else 0

/**
 * Prozessweite Sicht auf Modell-Downloads: der [ModelDownloadService] schreibt, die UI liest
 * [states] (Compose: `collectAsStateWithLifecycle`). Schluessel = Modell-ID, katalogweit eindeutig
 * ([findOfflineModel]: whisper- und Textmodelle).
 */
object ModelDownloads {

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    fun stateOf(modelId: String): DownloadState = states.value[modelId] ?: DownloadState.Idle

    val isAnyRunning: Boolean get() = states.value.values.any { it is DownloadState.Running }

    /** Zustand setzen; [DownloadState.Idle] entfernt den Eintrag. */
    internal fun update(modelId: String, state: DownloadState) {
        _states.update { if (state is DownloadState.Idle) it - modelId else it + (modelId to state) }
    }

    /** Fehler-/Fertig-Eintrag verwerfen (z. B. nach Loeschen des Modells oder Quittieren in der UI). */
    fun clear(modelId: String) = update(modelId, DownloadState.Idle)
}
