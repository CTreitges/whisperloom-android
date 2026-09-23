package com.chris.whisperloom

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
}
