package com.chris.whisperloom.llm

import com.chris.whisperloom.api.RefineRejectedException
import com.chris.whisperloom.api.TextRefiner
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.util.concurrent.CancellationException

/**
 * Ein geladenes lokales Textmodell. Interface, weil die native LiteRT-LM-Bibliothek nur auf
 * arm64-Geraeten laeuft — JVM- und Robolectric-Tests setzen ueber [LocalTextEngine.factory] einen Fake ein.
 */
interface LocalTextModel {

    /**
     * Blockierend. Nie zwei zugleich — dafuer sorgt [LocalTextEngine].
     *
     * @param cancelled Abbruch-Frage DIESES Auftrags: [cancel] bricht nur ab, wenn sie true sagt ([CancelSlot]).
     * @throws CancellationException wenn der Auftrag schon vor der Rechnung abgebrochen ist.
     */
    fun generate(system: String, user: String, cancelled: () -> Boolean): String

    /**
     * Laufende [generate] abbrechen (aus einem anderen Thread), wenn ihr Auftrag abgebrochen ist;
     * sonst und ohne laufende Generierung wirkungslos.
     */
    fun cancel()

    /** Speicher freigeben. Nie waehrend [generate]: LiteRT-LM stuerzt dann nativ ab (SIGSEGV, Issue #3771). */
    fun close()

    companion object {
        /**
         * KV-Cache fuer Eingabe und Ausgabe zusammen: System-Prompt + Diktat + bearbeiteter Text.
         * Zugleich die einzige Grenze der Antwort — nur an ihr ist ein Abbruch erkennbar ([truncated]).
         */
        const val MAX_NUM_TOKENS = 4096

        /**
         * Zeichen je Token, bewusst knapp: gemessen (Gemma 4 E2B, deutsch) 3,8–4,2 fuer System-Prompt
         * und Diktat, 4,5 fuer die Antwort — die Schaetzung liegt also eher zu hoch.
         */
        private const val CHARS_PER_TOKEN = 3.5

        /**
         * Passen System-Prompt, Diktat und eine etwa gleich lange Antwort in [MAX_NUM_TOKENS]? Sonst
         * schnitte LiteRT-LM die Antwort an der Grenze still ab (Review c3) — dann gar nicht erst rechnen.
         */
        fun fits(system: String, user: String): Boolean =
            (system.length + 2 * user.length) / CHARS_PER_TOKEN <= MAX_NUM_TOKENS

        /**
         * Volle KV-Tabelle nach der Rechnung = Antwort abgeschnitten. LiteRT-LM meldet das nicht, es
         * hoert einfach auf; die Conversation steht dann genau auf [MAX_NUM_TOKENS] (Smoketest 0.16.1).
         */
        fun truncated(tokenCount: Int): Boolean = tokenCount >= MAX_NUM_TOKENS
    }
}

/**
 * Gemma 4 ueber LiteRT-LM auf der CPU (die GPU ist auf Pixel G1–G3, Exynos und Mali fehlerhaft).
 * Der Konstruktor laedt das Modell (`initialize`): beim ersten Mal baut LiteRT-LM den XNNPACK-Cache
 * in [cacheDir] auf (E2B 788 MB, gut 6 s; danach rund 1 s) — nur aus einem Hintergrund-Thread.
 * Je Auftrag eine eigene Conversation: LiteRT-LM erlaubt nur eine Sitzung je Engine.
 *
 * Context7: LiteRT-LM v0.16.1 (/google-ai-edge/litert-lm, docs/api/kotlin/getting_started.md);
 * Signaturen (maxOutputToken, cancelProcess, getTokenCount) per javap am AAR 0.16.1 geprueft; Abbruch
 * und KV-Grenze im JVM-Smoketest: cancelProcess vor sendMessage verpufft, waehrend der Rechnung wirft
 * sendMessage "CANCELLED"; an der Grenze kommt die Antwort ohne Fehler abgeschnitten zurueck.
 */
class LiteRtTextModel(modelPath: String, cacheDir: String) : LocalTextModel {

    private val engine = Engine(
        EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            maxNumTokens = LocalTextModel.MAX_NUM_TOKENS,
            cacheDir = cacheDir,
        ),
    )

    /** Laufende Conversation fuer [cancel]; haelt Abbruch und Schliessen auseinander. */
    private val slot = CancelSlot<Conversation> { it.cancelProcess() }

    init {
        try {
            engine.initialize()
        } catch (e: Exception) {
            runCatching { engine.close() }
            throw e
        }
    }

    override fun generate(system: String, user: String, cancelled: () -> Boolean): String {
        val config = ConversationConfig(
            systemInstruction = Contents.of(system),
            // Niedrige Temperatur: korrigieren, nicht dichten. Thinking bleibt aus (Gemma-4-Standard).
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2),
            // Keine eigene Grenze unter der KV-Tabelle: an maxOutputToken schnitte LiteRT-LM die
            // Antwort genauso still ab, aber unerkennbar (Smoketest). Die Zeit begrenzt der Waechter.
            maxOutputToken = LocalTextModel.MAX_NUM_TOKENS,
        )
        val conv = engine.createConversation(config)
        try {
            slot.enter(conv, cancelled)
            val out = conv.sendMessage(user).toString()
            if (LocalTextModel.truncated(conv.getTokenCount())) throw RefineRejectedException(TextRefiner.MSG_TRUNCATED)
            return out
        } finally {
            slot.leave { conv.close() }
        }
    }

    override fun cancel() = slot.cancel()

    override fun close() = engine.close()
}

/**
 * Bindet den Abbruch an genau eine Rechnung (Review c4). [enter] traegt sie samt Abbruch-Frage
 * ihres Auftrags ein und prueft im selben Schritt, ob der Auftrag schon abgebrochen ist — sonst
 * ginge ein Tipp "ohne KI" verloren, der kurz vor dem Eintragen kam. [cancel] bricht nur ab, wenn
 * die Frage der EINGETRAGENEN Rechnung true sagt: nie die des naechsten Auftrags.
 *
 * @param abort bricht die eingetragene Rechnung ab (LiteRT-LM: cancelProcess).
 */
class CancelSlot<T : Any>(private val abort: (T) -> Unit) {

    private var running: T? = null
    private var cancelled: () -> Boolean = { false }

    /** @throws CancellationException wenn der Auftrag schon abgebrochen ist — dann wird nichts eingetragen. */
    @Synchronized
    fun enter(run: T, cancelled: () -> Boolean) {
        if (cancelled()) throw CancellationException("Textverbesserung abgebrochen")
        running = run
        this.cancelled = cancelled
    }

    /** Austragen; [then] (Schliessen) laeuft im selben Schritt, also nie neben [cancel]. */
    @Synchronized
    fun leave(then: () -> Unit = {}) {
        running = null
        cancelled = { false }
        then()
    }

    @Synchronized
    fun cancel() {
        val run = running ?: return
        if (cancelled()) abort(run)
    }
}
