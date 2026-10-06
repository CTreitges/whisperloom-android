package com.chris.whisperloom.ui.share

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chris.whisperloom.Engine
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.R
import com.chris.whisperloom.SharedAudioTranscriber
import com.chris.whisperloom.SharedTranscript
import com.chris.whisperloom.api.ApiNotConfiguredException
import com.chris.whisperloom.llm.LocalTextEngine
import com.chris.whisperloom.whisper.OfflineNotAvailableException
import com.chris.whisperloom.whisper.WhisperEngine
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Erkennung einer geteilten Datei — im Test austauschbar. */
fun interface ShareTranscriber {
    fun transcribe(
        context: Context,
        uri: Uri,
        onProgress: (Int, Int, String) -> Unit,
        isCancelled: () -> Boolean,
    ): SharedTranscript
}

/**
 * Ablauf der Share-Ansicht ohne Compose: Dateien nacheinander auf einem Hintergrund-Thread
 * erkennen, Teilergebnisse sofort in [state] spiegeln, Einzel-Retry, "Alles erneut", Abbruch.
 * Alle Zustandsaenderungen laufen ueber [post] (Main-Thread); Meldungen eines abgebrochenen
 * oder ersetzten Laufs werden anhand der Laufnummer verworfen.
 */
class ShareController(
    context: Context,
    private val uris: List<Uri>,
    private val transcriber: ShareTranscriber = ShareTranscriber { ctx, uri, onProgress, isCancelled ->
        SharedAudioTranscriber.transcribe(ctx, uri, onProgress = onProgress, isCancelled = isCancelled)
    },
    private val nameOf: (Context, Uri) -> String = SharedAudioTranscriber::displayName,
    private val executor: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "loom-share-io") },
    post: ((() -> Unit) -> Unit)? = null,
) {
    private val app: Context = context.applicationContext
    private val prefs = Prefs(app)
    private val post: (() -> Unit) -> Unit =
        post ?: Handler(Looper.getMainLooper()).let { main -> { r -> main.post(r) } }

    var state by mutableStateOf(
        ShareUiState(
            phase = if (uris.isEmpty()) SharePhase.NO_FILE else SharePhase.LOADING,
            files = uris.map { ShareFile(name = "") },
            hideFillers = prefs.shareHideFillers,
        ),
    )
        private set

    @Volatile private var cancelled = false

    /** Laufnummer: Meldungen eines alten Laufs (nach Abbruch/Neustart) werden verworfen. */
    @Volatile private var runId = 0

    @Volatile private var namesResolved = false

    /** Alle Dateien erkennen (Start, Rueckkehr aus der Einrichtung). */
    fun start() = transcribe(uris.indices.toList())

    /** Nur diese Datei erneut — die anderen Ergebnisse bleiben. Waehrend eines Laufs wirkungslos. */
    fun retryFile(index: Int) {
        if (state.phase == SharePhase.LOADING) return
        transcribe(listOf(index))
    }

    /** Alle fehlgeschlagenen Dateien erneut (FEHLER GESAMT: alle). Waehrend eines Laufs wirkungslos. */
    fun retryAll() {
        if (state.phase == SharePhase.LOADING) return
        val failed = state.files.indices.filter { state.files[it].error != null }
        transcribe(failed.ifEmpty { uris.indices.toList() })
    }

    /** Wirkt sofort auf Anzeige und Kopieren/Teilen; wird in den Prefs gemerkt. */
    fun setHideFillers(hide: Boolean) {
        prefs.shareHideFillers = hide
        state = state.copy(hideFillers = hide)
    }

    /** Angezeigter Stand als Text; Ueberschriften nur bei mehreren Dateien. */
    fun plainText(emptyText: String = ""): String =
        ShareText.plain(state.results, state.hideFillers, withHeadings = uris.size > 1, emptyText = emptyText)

    /** Laufende Arbeit abbrechen (Schliessen); Offline-Erkennung und lokale Textverbesserung werden sofort unterbrochen. */
    fun cancel() {
        cancelled = true
        runId++
        if (prefs.engine == Engine.OFFLINE) {
            WhisperEngine.abort()
            LocalTextEngine.cancel()
        }
    }

    fun dispose() {
        cancel()
        (executor as? ExecutorService)?.shutdownNow()
    }

    /**
     * Startet einen neuen Lauf und ersetzt damit einen eventuell laufenden (dessen Meldungen werden
     * verworfen) — deshalb schuetzen [retryFile]/[retryAll] gegen Aufrufe waehrend LOADING.
     */
    private fun transcribe(indices: List<Int>) {
        if (indices.isEmpty()) return
        cancelled = false
        val run = ++runId
        state = state.copy(
            phase = SharePhase.LOADING,
            files = state.files.mapIndexed { i, f -> if (i in indices) f.copy(result = null, error = null) else f },
            progress = ShareProgress(indices.first(), uris.size, 0, 1, app.getString(R.string.share_starting)),
            failure = null,
        )
        executor.execute {
            if (!namesResolved) {
                val names = uris.map { nameOf(app, it) }
                namesResolved = true
                deliver(run) { s -> s.copy(files = s.files.mapIndexed { i, f -> f.copy(name = names[i]) }) }
            }
            for (index in indices) {
                if (cancelled) return@execute
                try {
                    val t = transcriber.transcribe(
                        app,
                        uris[index],
                        onProgress = { step, total, label ->
                            deliver(run) { it.copy(progress = ShareProgress(index, uris.size, step, total, label)) }
                        },
                        isCancelled = { cancelled },
                    )
                    // Zwischenergebnis sofort zeigen — bei mehreren Dateien wartet man
                    // sonst bis zum Schluss auf den ersten Text.
                    deliver(run) { it.withFile(index) { f -> f.copy(name = t.source, result = t, error = null) } }
                } catch (e: ApiNotConfiguredException) {
                    notConfigured(run)
                    return@execute
                } catch (e: OfflineNotAvailableException) {
                    notConfigured(run)
                    return@execute
                } catch (e: Exception) {
                    if (cancelled) return@execute
                    Log.e(TAG, "Geteiltes Audio fehlgeschlagen", e)
                    val message = e.message ?: app.getString(R.string.err_unknown, e.javaClass.simpleName)
                    deliver(run) { it.withFile(index) { f -> f.copy(result = null, error = message) } }
                }
            }
            deliver(run) { s ->
                if (s.files.all { it.error != null }) {
                    s.copy(phase = SharePhase.ALL_FAILED, progress = null, failure = s.files.first().error)
                } else {
                    s.copy(phase = SharePhase.DONE, progress = null)
                }
            }
        }
    }

    private fun notConfigured(run: Int) {
        deliver(run) { it.copy(phase = SharePhase.NOT_CONFIGURED, progress = null) }
    }

    /** Zustandsaenderung auf den Main-Thread bringen — verworfen, wenn der Lauf nicht mehr aktuell ist. */
    private fun deliver(run: Int, change: (ShareUiState) -> ShareUiState) {
        post {
            if (run == runId && !cancelled) state = change(state)
        }
    }

    companion object {
        private const val TAG = "WhisperLoomShare"
    }
}
