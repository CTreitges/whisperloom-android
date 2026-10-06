package com.chris.whisperloom.llm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig

/**
 * Ein geladenes lokales Textmodell. Interface, weil die native LiteRT-LM-Bibliothek nur auf
 * arm64-Geraeten laeuft — JVM- und Robolectric-Tests setzen ueber [LocalTextEngine.factory] einen Fake ein.
 */
interface LocalTextModel {

    /** Blockierend. Nie zwei zugleich — dafuer sorgt [LocalTextEngine]. */
    fun generate(system: String, user: String): String

    /** Laufende [generate] abbrechen (aus einem anderen Thread); ohne laufende Generierung wirkungslos. */
    fun cancel()

    /** Speicher freigeben. Nie waehrend [generate]: LiteRT-LM stuerzt dann nativ ab (SIGSEGV, Issue #3771). */
    fun close()
}

/**
 * Gemma 4 ueber LiteRT-LM auf der CPU (die GPU ist auf Pixel G1–G3, Exynos und Mali fehlerhaft).
 * Der Konstruktor laedt das Modell (`initialize`): beim ersten Mal baut LiteRT-LM den XNNPACK-Cache
 * in [cacheDir] auf (E2B 788 MB, gut 6 s; danach rund 1 s) — nur aus einem Hintergrund-Thread.
 * Je Auftrag eine eigene Conversation: LiteRT-LM erlaubt nur eine Sitzung je Engine.
 *
 * Context7: LiteRT-LM v0.16.1 (/google-ai-edge/litert-lm, docs/api/kotlin/getting_started.md);
 * Signaturen (maxOutputToken, cancelProcess) per javap am AAR 0.16.1 geprueft.
 */
class LiteRtTextModel(modelPath: String, cacheDir: String) : LocalTextModel {

    private val engine = Engine(
        EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            maxNumTokens = MAX_NUM_TOKENS,
            cacheDir = cacheDir,
        ),
    )

    /** Laufende Conversation fuer [cancel]; [guard] haelt Abbruch und Schliessen auseinander. */
    private var conversation: Conversation? = null
    private val guard = Any()

    init {
        try {
            engine.initialize()
        } catch (e: Exception) {
            runCatching { engine.close() }
            throw e
        }
    }

    override fun generate(system: String, user: String): String {
        val config = ConversationConfig(
            systemInstruction = Contents.of(system),
            // Niedrige Temperatur: korrigieren, nicht dichten. Thinking bleibt aus (Gemma-4-Standard).
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2),
            maxOutputToken = MAX_OUTPUT_TOKENS,
        )
        val conv = engine.createConversation(config)
        synchronized(guard) { conversation = conv }
        try {
            return conv.sendMessage(user).toString()
        } finally {
            synchronized(guard) {
                conversation = null
                conv.close()
            }
        }
    }

    override fun cancel() {
        synchronized(guard) { conversation?.cancelProcess() }
    }

    override fun close() = engine.close()

    private companion object {
        /** KV-Cache fuer Eingabe und Ausgabe zusammen: System-Prompt + Diktat + bearbeiteter Text. */
        const val MAX_NUM_TOKENS = 4096

        /** Obergrenze der Antwort; ein laengeres Ergebnis waere ohnehin unplausibel ([com.chris.whisperloom.api.TextRefiner.cleanText]). */
        const val MAX_OUTPUT_TOKENS = 2048
    }
}
