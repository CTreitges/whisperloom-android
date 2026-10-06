package com.chris.whisperloom.whisper

/**
 * Lokale Textmodelle fuer die Textverbesserung bei Offline-Erkennung: Gemma 4 (Apache 2.0) als
 * LiteRT-LM-Datei von huggingface.co/litert-community. Nur die universellen .litertlm-Dateien —
 * die App rechnet auf der CPU (GPU ist auf Pixel G1–G3, Exynos und Mali fehlerhaft).
 *
 * Die URLs zeigen auf eine feste Revision: auf `main` werden die Dateien ersetzt, ein fester SHA-256
 * schluege dann bei jedem Upload fehl. Bytes und SHA-256 per HEAD gegen genau diese Revision
 * bestaetigt (x-linked-size/-etag, 2026-10-06). Kein Token noetig (nicht gated).
 */
object TextModelCatalog {

    private const val MB = 1_000_000L
    private const val GIB = 1024L * 1024L * 1024L

    // approxRamBytes: Googles Peak-RAM auf der CPU (E2B 1.733 MB, E4B 3.283 MB).
    // extraDiskBytes: XNNPACK-Cache, den LiteRT-LM beim ersten Laden anlegt (E2B gemessen 788 MB, E4B geschaetzt).
    val GEMMA4_E2B = OfflineModel(
        id = "gemma4_e2b", label = "Gemma 4 E2B", fileName = "gemma-4-E2B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/" +
            "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1/gemma-4-E2B-it.litertlm",
        bytes = 2_588_147_712L,
        sha256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c",
        approxRamBytes = 1_800 * MB, minDeviceRamBytes = 6 * GIB,
        recommended = true,
        extraDiskBytes = 800 * MB,
    )
    val GEMMA4_E4B = OfflineModel(
        id = "gemma4_e4b", label = "Gemma 4 E4B", fileName = "gemma-4-E4B-it.litertlm",
        url = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/" +
            "2eee7ac325f20eb8c9ac1d0e972f7c84663062da/gemma-4-E4B-it.litertlm",
        bytes = 3_659_530_240L,
        sha256 = "0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0",
        approxRamBytes = 3_300 * MB, minDeviceRamBytes = 8 * GIB,
        extraDiskBytes = 1_100 * MB,
    )

    val models: List<OfflineModel> = listOf(GEMMA4_E2B, GEMMA4_E4B)

    /** Standard und Fallback fuer unbekannte IDs (= Prefs.DEFAULT_LOCAL_LLM_MODEL). */
    val DEFAULT: OfflineModel = GEMMA4_E2B

    fun find(id: String?): OfflineModel? = models.firstOrNull { it.id == id }

    fun byId(id: String?): OfflineModel = find(id) ?: DEFAULT

    /** Passt wenigstens ein Textmodell in den RAM? Sonst wirkt die Regel wie "Ueberspringen" (OfflineRefineRule.effective). */
    fun anyFits(totalRamBytes: Long): Boolean = models.any { OfflineSupport.fitsDevice(totalRamBytes, it) }
}
