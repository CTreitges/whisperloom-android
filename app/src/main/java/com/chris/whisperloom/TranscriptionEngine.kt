package com.chris.whisperloom

import android.content.Context
import android.provider.OpenableColumns
import android.util.Log
import com.chris.whisperloom.api.ApiNotConfiguredException
import com.chris.whisperloom.api.TextRefiner
import com.chris.whisperloom.api.WavUpload
import com.chris.whisperloom.whisper.OfflineBackend
import com.chris.whisperloom.whisper.OfflineNotAvailableException
import com.chris.whisperloom.whisper.OfflineStatus
import java.io.File
import java.io.RandomAccessFile

/**
 * Einziger Weg vom Audio zum fertigen Text. Reihenfolge:
 *
 *  1. Stille am Anfang/Ende wegschneiden (kleinerer Upload)
 *  2. Erkennung ueber das gewaehlte Backend (Anbieter-API oder offline)
 *  3. optional: Sprachmodell bearbeitet den Text (Modus aus den Einstellungen)
 *  4. Nachbearbeitung (Fuellwoerter, Gross-Schreibung, Whitespace)
 *
 * Blockierend — immer aus einem Hintergrund-Thread aufrufen.
 */
object TranscriptionEngine {

    /** Ob ueberhaupt diktiert werden kann (Zugang vollstaendig bzw. Offline-Modell da). */
    fun isConfigured(context: Context): Boolean =
        isConfigured(context.applicationContext, Prefs(context.applicationContext))

    private fun isConfigured(app: Context, prefs: Prefs): Boolean = when (prefs.engine) {
        Engine.ONLINE -> prefs.sttAccess().let {
            SetupState.sttComplete(it.baseUrl, it.apiKey, it.provider.needsKey)
        }
        Engine.OFFLINE -> OfflineStatus.isModelAvailable(app)
        null -> false // noch keine Engine gewaehlt
    }

    /**
     * @throws ApiNotConfiguredException wenn keine Engine gewaehlt oder der Zugang unvollstaendig ist.
     * @throws OfflineNotAvailableException wenn offline gewaehlt ist, aber kein Modell da.
     */
    internal fun requireConfigured(app: Context, prefs: Prefs) {
        if (isConfigured(app, prefs)) return
        throw when (prefs.engine) {
            Engine.OFFLINE -> OfflineNotAvailableException()
            Engine.ONLINE, null -> ApiNotConfiguredException()
        }
    }

    /**
     * @param prompt Vokabular fuer den Erkenner — die Aufrufer mit Context geben
     *   [VocabularySource.prompt] mit, damit die verknuepfte Datei bei JEDEM Diktat frisch gelesen wird.
     */
    internal fun backend(
        prefs: Prefs,
        prompt: String = Vocabulary.prompt(Vocabulary.entries(prefs.apiPrompt)).text,
    ): TranscriptionBackend = when (prefs.engine) {
        Engine.ONLINE -> OnlineBackend(prefs.sttAccess(), prompt)
        Engine.OFFLINE -> OfflineBackend(prefs.offlineModel, prefs.offlineAccurate, prompt)
        null -> throw ApiNotConfiguredException()
    }

    /** Bei "auto" die vom Erkenner gemeldete Sprache nehmen — sonst bleibt "auto". */
    internal fun effectiveLanguage(configured: String, detected: String?): String =
        if (configured == "auto" && !detected.isNullOrBlank()) detected else configured

    /**
     * @param onRefineSkipped wird gerufen, wenn die Textverbesserung (Schritt 3) scheitert —
     *   der erkannte Text kommt dann unveraendert durch die Nachbearbeitung; die Meldung
     *   (z. B. "API-Fehler 401 …") kann der Aufrufer als Hinweis zeigen.
     * @throws ApiNotConfiguredException wenn keine Engine gewaehlt oder der Zugang unvollstaendig ist.
     * @throws OfflineNotAvailableException wenn offline gewaehlt ist, aber kein Modell da.
     * @throws com.chris.whisperloom.api.ApiNetworkException bei Netzproblemen (Erkennung).
     * @throws com.chris.whisperloom.api.ApiHttpException bei Fehlerstatus der API (Erkennung).
     */
    fun transcribe(context: Context, samples: FloatArray, onRefineSkipped: (String) -> Unit = {}): String {
        val app = context.applicationContext
        val prefs = Prefs(app)
        requireConfigured(app, prefs)

        val trimmed = AudioUtils.trimSilence(samples)
        val result = backend(prefs, VocabularySource.prompt(app, prefs).text)
            .transcribe(WavUpload.fromSamples(trimmed), prefs.language)
        val raw = result.text
        if (raw.isBlank()) return ""

        val language = effectiveLanguage(prefs.language, result.detectedLanguage)
        val mode = prefs.refineMode
        val refined = if (mode != RefineMode.OFF) {
            refineOrRaw(raw, language, mode, prefs, onRefineSkipped)
        } else {
            raw
        }

        val options = PolishPlan.options(
            removeFillers = prefs.removeFillers,
            autoCapitalize = prefs.autoCapitalize,
            language = language,
            refineMode = mode,
            smartFillers = prefs.smartFillers,
            customFillers = prefs.customFillers,
            disabledFillers = prefs.disabledFillers,
            paragraphs = prefs.refineParagraphs,
        )
        return TextPolisher.polish(refined, options)
    }

    /**
     * Die Veredelung darf ein bereits erkanntes (und ggf. bezahltes) Diktat nie verschlucken:
     * scheitert das Sprachmodell (falsches Modell, 401/429, eigener Server aus, Base-URL leer,
     * unbrauchbare Antwort), kommt der Rohtext durch — nur mit Hinweis statt Fehler.
     */
    private fun refineOrRaw(raw: String, language: String, mode: RefineMode, prefs: Prefs, onSkipped: (String) -> Unit): String =
        try {
            TextRefiner(prefs.llmAccess()).refine(raw, language, mode, prefs.smartFillers, prefs.refineParagraphs)
        } catch (e: Exception) {
            Log.w(TAG, "Textverbesserung uebersprungen: ${e.message}", e)
            onSkipped(e.message ?: e.javaClass.simpleName)
            raw
        }

    private const val TAG = "TranscriptionEngine"
}

/**
 * Ergebnis einer geteilten Audiodatei — in zwei Fassungen, zwischen denen die
 * Share-Ansicht umschaltet: wortgetreu und ohne Fuellwoerter.
 */
data class SharedTranscript(
    /** Anzeigename der Quelle (Dateiname), fuer die Ueberschrift in der Ergebnis-Ansicht. */
    val source: String,
    val verbatimText: String,
    val cleanedText: String,
    val paragraphsVerbatim: List<String>,
    val paragraphsCleaned: List<String>,
    val durationMs: Long,
    /** Womit erkannt wurde ("OpenAI", "Offline · Small") — fuer den Hinweis-Chip. */
    val backendLabel: String,
    /** In wie viele Stuecke (AudioChunks) die Datei zerlegt wurde — jede Grenze ist ein Absatz. */
    val chunkCount: Int = 1,
)

/**
 * Transkribiert Audiodateien, die aus einer anderen App geteilt wurden
 * (WhatsApp-Sprachnachricht, Aufnahme-App, Dateimanager …).
 *
 * Bewusst getrennt von [TranscriptionEngine]: geteiltes Audio wird WORTGETREU
 * ausgegeben — keine KI-Glaettung; die Fuellwort-freie Fassung ist ein Umschalter in
 * der Ansicht, kein Ersatz. Bei einer fremden Sprachnachricht will man wissen, was
 * gesagt wurde, nicht eine geglaettete Fassung.
 */
object SharedAudioTranscriber {

    /** Hoechstlaenge eines Stuecks. 5 Min = 9,6 MB WAV — deutlich unter dem 25-MB-Limit. */
    private const val MAX_CHUNK_FRAMES = AudioConvert.TARGET_RATE * 300

    /** So weit vor der Grenze wird nach einer Sprechpause zum Schneiden gesucht. */
    private const val CUT_SEARCH_FRAMES = AudioConvert.TARGET_RATE * 20

    /**
     * @param onProgress (Schritt, Gesamtschritte, Beschriftung) — Gesamtschritte ist erst
     *   nach dem Entpacken bekannt und kann sich einmal erhoehen.
     * @throws ApiNotConfiguredException wenn der Anbieter-Zugang unvollstaendig ist.
     * @throws OfflineNotAvailableException wenn offline gewaehlt ist, aber kein Modell da.
     * @throws UnsupportedAudioException wenn die Datei nicht decodiert werden kann.
     */
    fun transcribe(
        context: Context,
        uri: android.net.Uri,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): SharedTranscript {
        val app = context.applicationContext
        val prefs = Prefs(app)
        TranscriptionEngine.requireConfigured(app, prefs)
        val backend = TranscriptionEngine.backend(prefs, VocabularySource.prompt(app, prefs).text)

        val name = displayName(app, uri)
        val temp = File.createTempFile("shared-", ".pcm", app.cacheDir)
        try {
            onProgress(0, 1, app.getString(R.string.share_decoding))
            val decoded = AudioDecoder.decodeToPcm(app, uri, temp, isCancelled = isCancelled)
            if (decoded.frameCount == 0) {
                throw UnsupportedAudioException(app.getString(R.string.share_empty))
            }

            val chunks = AudioChunks.plan(
                totalFrames = decoded.frameCount,
                maxFrames = MAX_CHUNK_FRAMES,
                profile = decoded.profile,
                framesPerEntry = decoded.framesPerProfileEntry,
                searchFrames = CUT_SEARCH_FRAMES,
            )

            // Je Stueck ein Rohtext — die Stueck-Grenzen werden spaeter zu Absatzgrenzen.
            val parts = mutableListOf<String>()
            var detected: String? = null
            for ((i, chunk) in chunks.withIndex()) {
                if (isCancelled()) throw UnsupportedAudioException("Abgebrochen")
                onProgress(i, chunks.size, app.getString(R.string.share_sending))
                val part = backend.transcribe(upload(decoded.pcmFile, chunk), prefs.language)
                parts.add(part.text)
                if (detected == null) detected = part.detectedLanguage
            }
            onProgress(chunks.size, chunks.size, app.getString(R.string.share_sending))

            val language = TranscriptionEngine.effectiveLanguage(prefs.language, detected)
            // keepLineBreaks: Leerzeilen, die der Erkenner liefert, bleiben Absatzgrenzen (Regel 1).
            val verbatimOptions = PolishPlan.verbatim(language).copy(keepLineBreaks = true)
            val cleanedOptions = PolishPlan.cleaned(language, prefs.customFillers, prefs.disabledFillers)
                .copy(keepLineBreaks = true)
            val paragraphsVerbatim = paragraphsForChunks(parts.map { TextPolisher.polish(it, verbatimOptions) })
            val paragraphsCleaned = paragraphsForChunks(parts.map { TextPolisher.polish(it, cleanedOptions) })
            return SharedTranscript(
                source = name,
                verbatimText = paragraphsVerbatim.joinToString("\n\n"),
                cleanedText = paragraphsCleaned.joinToString("\n\n"),
                paragraphsVerbatim = paragraphsVerbatim,
                paragraphsCleaned = paragraphsCleaned,
                durationMs = decoded.durationMs,
                backendLabel = backend.label,
                chunkCount = chunks.size,
            )
        } finally {
            temp.delete()
        }
    }

    /** Leerzeile im Text = vorhandene Absatzgrenze (Regel 1). */
    private val BLANK_LINE = Regex("\\n\\s*\\n")

    /**
     * Absatzregel der Share-Ansicht (UX-Spec §2.9), rein und testbar:
     * (1) vorhandene Leerzeilen uebernehmen, (2) jede Stueck-Grenze ist ein Absatz,
     * (3) innerhalb eines Stuecks teilt [Paragrapher] (3 Saetze / 350 Zeichen).
     * Leere Stuecke (z. B. nur Stille oder nur Fuellwoerter) fallen weg.
     */
    fun paragraphsForChunks(chunks: List<String>): List<String> =
        chunks.flatMap { chunk -> chunk.split(BLANK_LINE).flatMap { block -> Paragrapher.split(block) } }

    /** Streamt genau ein Stueck aus der entpackten PCM-Datei in die Verbindung. */
    private fun upload(pcmFile: File, chunk: AudioChunks.Chunk) = WavUpload(
        pcmByteCount = chunk.frameCount * 2,
    ) { os ->
        RandomAccessFile(pcmFile, "r").use { raf ->
            raf.seek(chunk.startFrame * 2L)
            val buf = ByteArray(64 * 1024)
            var left = chunk.frameCount * 2
            while (left > 0) {
                val n = raf.read(buf, 0, minOf(buf.size, left))
                if (n <= 0) break
                os.write(buf, 0, n)
                left -= n
            }
        }
    }

    /** Dateiname der geteilten Quelle, sonst ein neutraler Ersatz. */
    internal fun displayName(context: Context, uri: android.net.Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) {
                        val n = c.getString(idx)
                        if (!n.isNullOrBlank()) return n
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.share_unknown_source)
    }
}
