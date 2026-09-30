package com.chris.whisperloom

import com.chris.whisperloom.api.ApiAccess

/**
 * Vollstaendigkeit des Transkriptions-Zugangs — rein (ohne Android). Ob die App insgesamt
 * "eingerichtet" ist (Engine, URL gueltig, Modell, Mikrofon, Overlay), entscheidet allein
 * `ui.nav.SetupRouter.isSetUp`; hier liegt nur der Baustein, den Router und
 * [TranscriptionEngine.isConfigured] gemeinsam nutzen.
 */
object SetupState {

    /** Transkriptions-Zugang vollstaendig: Base-URL da und Key da (sofern der Anbieter einen will). */
    fun sttComplete(baseUrl: String, apiKey: String, needsKey: Boolean): Boolean =
        baseUrl.isNotBlank() && (!needsKey || apiKey.isNotBlank())

    /**
     * Textverbesserungs-Zugang brauchbar: wie [sttComplete] und zusaetzlich ein Modell. Katalog-
     * Anbieter haben immer eins; Ollama lokal und der eigene Server haben keinen Default — ohne
     * Modell ginge jedes Diktat mit `"model":""` raus und kaeme nur als Rohtext zurueck.
     */
    fun llmComplete(baseUrl: String, apiKey: String, needsKey: Boolean, model: String): Boolean =
        sttComplete(baseUrl, apiKey, needsKey) && model.isNotBlank()

    /**
     * Kann eine KI-Stufe mit diesem Zugang etwas ausrichten? Wie [llmComplete], dazu muss der
     * Anbieter Chat koennen: ElevenLabs "wie Erkennung" hat keinen, auch wenn von frueher noch ein
     * Modell eingetragen ist.
     */
    fun llmReady(access: ApiAccess): Boolean =
        access.refineBlock == null && llmComplete(access.baseUrl, access.apiKey, access.provider.needsKey, access.model)
}
