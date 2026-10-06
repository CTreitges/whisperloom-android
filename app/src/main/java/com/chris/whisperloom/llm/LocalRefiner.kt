package com.chris.whisperloom.llm

import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.api.TextRefiner

/**
 * Textverbesserung mit dem lokalen Textmodell ([LocalTextEngine]) — derselbe Auftrag und dieselbe
 * Nacharbeit wie online ([TextRefiner.messages], [TextRefiner.finish]), nur ohne Netz: der Text
 * verlaesst das Geraet nicht. Ohne den Wiederholversuch "ohne temperature" der Online-Variante —
 * den Sampler setzt die App hier selbst.
 *
 * @param cancelled Abbruch des Auftrags (Tipp "ohne KI"), siehe [LocalTextEngine.generate].
 */
class LocalRefiner(private val modelId: String, private val cancelled: () -> Boolean = { false }) {

    /**
     * Wie [TextRefiner.refine]: bei leerer Eingabe oder [RefineMode.OFF] der Originaltext, ohne zu rechnen.
     *
     * @throws com.chris.whisperloom.api.RefineRejectedException wenn das Modell geantwortet hat,
     *   statt den Text zu bearbeiten (Ausgabe weit laenger als das Diktat).
     * @throws Exception wenn das Modell fehlt oder die Rechnung scheitert (Init, nativer Fehler, Abbruch).
     */
    fun refine(raw: String, language: String, mode: RefineMode, smartFillers: Boolean, paragraphs: Boolean = true): String {
        if (raw.isBlank() || mode == RefineMode.OFF) return raw
        val (system, user) = TextRefiner.messages(raw, language, mode, smartFillers, paragraphs)
        return TextRefiner.finish(raw, mode, LocalTextEngine.generate(modelId, system, user, cancelled))
    }
}
