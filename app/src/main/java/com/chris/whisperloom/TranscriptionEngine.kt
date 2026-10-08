package com.chris.whisperloom

import android.content.Context
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.chris.whisperloom.api.ApiNotConfiguredException
import com.chris.whisperloom.api.WavUpload
import com.chris.whisperloom.llm.LocalTextEngine
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
 *  3. optional: Sprachmodell bearbeitet den Text (Modus aus den Einstellungen) — online oder mit
 *     dem lokalen Textmodell, wie [RefinePlan] es nach Regel, Netz und Modell entscheidet
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

    /**
     * Naht fuer Tests: Offline-Erkennung ohne libwhisperloom.so simulieren (Fake-Backend). Die
     * Pruefung "eingerichtet" und alles nach der Erkennung laufen trotzdem wie im Betrieb.
     */
    @VisibleForTesting
    internal var backendFactory: (Prefs, String) -> TranscriptionBackend = { prefs, prompt -> backend(prefs, prompt) }

    /**
     * Aufnahmestart (Tastatur, schwebender Knopf, Widget): wird das Diktat gleich lokal verbessert,
     * laedt das Textmodell schon jetzt im Hintergrund — parallel zu Aufnahme und Erkennung statt
     * danach (Init Sekunden bis gut 30 s). Kehrt sofort zurueck.
     */
    fun warmUp(context: Context) {
        val app = context.applicationContext
        val prefs = Prefs(app)
        if (prefs.engine != Engine.OFFLINE) return
        if (RefinePlan.of(app, prefs, prefs.refinementFor(RefineWay.DICTATION).mode).route == RefineRoute.Local) {
            LocalTextEngine.warmUp(prefs.localLlmModel)
        }
    }

    /** Bei "auto" die vom Erkenner gemeldete Sprache nehmen — sonst bleibt "auto". */
    internal fun effectiveLanguage(configured: String, detected: String?): String =
        if (configured == "auto" && !detected.isNullOrBlank()) detected else configured

    /**
     * @param skip Ausweg waehrend der Textverbesserung: [RefineSkip.skip] liefert sofort den Text
     *   ohne KI (ohne Hinweis — so gewollt). Ohne bleibt die Verbesserung auf dem aufrufenden Thread.
     * @param cancelled der Auftrag ist hinfaellig (Widget: abgeloest oder verworfen) — die lokale
     *   Rechnung bricht dann ab, statt das Textmodell fuer den naechsten Auftrag zu blockieren.
     * @param onRefineStart die Textverbesserung beginnt (online oder lokal) — ab jetzt hilft [skip].
     *   Nicht, wenn ohne KI weitergeht (Stufe aus, kein Netz, kein Textmodell, Ueberspringen).
     * @return Rohtext, Sprache, Dauer und das Ergebnis von KI und Regeln ([refine]); scheitert die
     *   Textverbesserung, kommt der erkannte Text mit Hinweis ([Refined.skipped]) durch. Leerer
     *   Rohtext = nichts erkannt, dann auch kein Text.
     * @throws ApiNotConfiguredException wenn keine Engine gewaehlt oder der Zugang unvollstaendig ist.
     * @throws OfflineNotAvailableException wenn offline gewaehlt ist, aber kein Modell da.
     * @throws com.chris.whisperloom.api.ApiNetworkException bei Netzproblemen (Erkennung).
     * @throws com.chris.whisperloom.api.ApiHttpException bei Fehlerstatus der API (Erkennung).
     */
    fun transcribe(
        context: Context,
        samples: FloatArray,
        skip: RefineSkip? = null,
        cancelled: () -> Boolean = { false },
        onRefineStart: () -> Unit = {},
    ): Dictation {
        val app = context.applicationContext
        val prefs = Prefs(app)
        requireConfigured(app, prefs)

        val durationMs = samples.size * 1000L / AudioRecorder.SAMPLE_RATE
        val trimmed = AudioUtils.trimSilence(samples)
        val result = backendFactory(prefs, VocabularySource.prompt(app, prefs).text)
            .transcribe(WavUpload.fromSamples(trimmed), prefs.language)
        val raw = result.text
        val language = effectiveLanguage(prefs.language, result.detectedLanguage)
        // Diktat, Knopf und Widget: die Stufe und ihre Einstellungen fuers Diktat, nie die der Sprachnachrichten.
        val refinement = prefs.refinementFor(RefineWay.DICTATION)
        if (raw.isBlank()) return Dictation("", language, durationMs, Refined(refinement, ""))

        // Die Online-Erkennung hat das Netz gerade bewiesen; offline erkannt zaehlt nur ein validiertes.
        val refined = refine(app, raw, language, refinement, prefs.engine != Engine.OFFLINE, skip, cancelled, onRefineStart)
        return Dictation(raw, language, durationMs, refined)
    }

    /**
     * Schritt 3 und 4 der Pipeline: KI (falls moeglich) und Regeln — der gemeinsame Weg von Diktat
     * und Verlauf (Neu-Verarbeiten aus dem gespeicherten Rohtext, ohne neue Erkennung). Es gelten
     * die aktuellen Einstellungen: Zugang, Modell der Stufe, Offline-Regel, Fuellwoerter.
     *
     * Die Veredelung darf einen bereits erkannten (und ggf. bezahlten) Text nie verschlucken:
     * scheitert das Sprachmodell (falsches Modell, 401/429, eigener Server aus, Base-URL leer,
     * unbrauchbare oder leere Antwort, lokales Modell), kommt der Rohtext durch — nur mit Hinweis statt Fehler.
     * Ohne Netz geht gar keine Anfrage raus ([RefinePlan]): sofort ohne KI statt im Timeout zu haengen.
     *
     * Blockierend — immer aus einem Hintergrund-Thread aufrufen.
     *
     * @param networkProven eine Anfrage hat das Netz gerade getragen (Online-Erkennung): dann reicht
     *   ein aktives Netz. Der Verlauf hat keinen Nachweis (false) — sonst hinge die Anfrage in einem
     *   WLAN mit Anmeldeseite im Connect-Timeout.
     * @param skip wie bei [transcribe]; [onStart] wie dort `onRefineStart`.
     */
    fun refine(
        context: Context,
        raw: String,
        language: String,
        refinement: Refinement,
        networkProven: Boolean,
        skip: RefineSkip? = null,
        cancelled: () -> Boolean = { false },
        onStart: () -> Unit = {},
    ): Refined {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val plan = RefinePlan.of(app, prefs, refinement.mode, networkProven)
        var skipped: String? = null
        var note: String? = null
        val ai = when (val route = plan.route) {
            is RefineRoute.Raw -> {
                skipped = route.hint?.let(RefinePlan::message)
                null
            }
            else -> {
                onStart()
                val work = {
                    plan.refine(raw, language, refinement, { note = it }) { skip?.isSkipped == true || cancelled() }
                }
                try {
                    if (skip == null) work() else skip.race(work)
                } catch (e: Exception) {
                    Log.w(TAG, "Textverbesserung uebersprungen: ${e.message}", e)
                    skipped = e.message ?: e.javaClass.simpleName
                    null
                }
            }
        }

        val options = PolishPlan.options(
            removeFillers = prefs.removeFillers,
            autoCapitalize = prefs.autoCapitalize,
            language = language,
            // Ohne KI (aus, gescheitert, uebersprungen) = Rohtext, den keine KI bearbeitet hat: volle
            // Regeln, sonst blieben bei "Prompt" die "ähm"s stehen und Umbrueche ungeglaettet.
            refineMode = if (ai == null) RefineMode.OFF else refinement.mode,
            customFillers = prefs.customFillers,
            disabledFillers = prefs.disabledFillers,
            paragraphs = refinement.paragraphs,
        )
        val text = TextPolisher.polish(ai ?: raw, options)
        return Refined(refinement, text, model = ai?.let { plan.modelLabel() }, skipped = skipped, note = note)
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
    /** KI-Fassung nach [refineMode]; null = keine (Stufe aus oder Textverbesserung gescheitert). */
    val paragraphsRefined: List<String>? = null,
    /** Die wirksame Stufe fuer geteilte Audios ([Refinement.mode] des Wegs [RefineWay.SHARE]). */
    val refineMode: RefineMode = RefineMode.OFF,
    /** Warum die KI-Fassung trotz Stufe fehlt ("API-Fehler 401 …"); null = nicht gescheitert. */
    val refineSkipped: String? = null,
    /** Online gescheitert, das lokale Textmodell hat verbessert (nur Hinweis, kein Fehler). */
    val refineLocalFallback: Boolean = false,
)

/**
 * Transkribiert Audiodateien, die aus einer anderen App geteilt wurden
 * (WhatsApp-Sprachnachricht, Aufnahme-App, Dateimanager …).
 *
 * Getrennt von [TranscriptionEngine]: geteiltes Audio wird ab Werk WORTGETREU ausgegeben —
 * bei einer fremden Sprachnachricht will man wissen, was gesagt wurde. Die Fuellwort-freie
 * Fassung ist ein Umschalter in der Ansicht. Eine KI-Stufe gibt es nur, wenn sie in den
 * Einstellungen fuer geteilte Audios eingeschaltet ist ([SharedRefine]).
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

            val refined = SharedRefine.run(app, prefs, parts, language, isCancelled, onStart = { local ->
                onProgress(chunks.size, chunks.size, app.getString(if (local) R.string.share_refining_local else R.string.share_refining))
            })
            return SharedTranscript(
                source = name,
                verbatimText = paragraphsVerbatim.joinToString("\n\n"),
                cleanedText = paragraphsCleaned.joinToString("\n\n"),
                paragraphsVerbatim = paragraphsVerbatim,
                paragraphsCleaned = paragraphsCleaned,
                durationMs = decoded.durationMs,
                backendLabel = backend.label,
                chunkCount = chunks.size,
                paragraphsRefined = refined.paragraphs,
                refineMode = refined.mode,
                refineSkipped = refined.skipped,
                refineLocalFallback = refined.localFallback,
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
