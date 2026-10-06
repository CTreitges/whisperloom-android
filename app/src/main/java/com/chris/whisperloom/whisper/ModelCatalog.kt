package com.chris.whisperloom.whisper

/**
 * Ein herunterladbares Offline-Modell: whisper.cpp-Modelle zum Erkennen ([ModelCatalog]) und
 * Textmodelle fuer die lokale Textverbesserung ([TextModelCatalog]). Download, Pruefung und Loeschen
 * laufen fuer beide gleich ([ModelDownloader], [ModelStore], [ModelDownloadService]); die kennen
 * nur die ID — sie ist deshalb ueber beide Kataloge eindeutig ([findOfflineModel]).
 *
 * Schlaegt nach einer Aenderung upstream die Integritaetspruefung fehl, ist das Absicht: lieber
 * ein sauberer Download-Fehler als ein korruptes Modell.
 */
data class OfflineModel(
    /** Stabile ID (Prefs.offlineModel bzw. Prefs.localLlmModel): "small", "gemma4_e2b" … */
    val id: String,
    /** Anzeigename ("Small", "Gemma 4 E2B"), auch in der Download-Notification. */
    val label: String,
    /** Name der Datei im Modellordner. */
    val fileName: String,
    /** Download-Adresse; HttpURLConnection folgt der Weiterleitung zum CDN, Range wird unterstuetzt. */
    val url: String,
    /** Exakte Dateigroesse — ein Download gilt nur bei Gleichheit als vollstaendig. */
    val bytes: Long,
    /** SHA-256 der Datei, hex, klein geschrieben. */
    val sha256: String,
    /** Grober RAM-Bedarf beim Rechnen (whisper: Modell + KV-Cache + Compute-Puffer). */
    val approxRamBytes: Long,
    /** Mindest-Geraete-RAM (0 = keine Einschraenkung); [OfflineSupport.fitsDevice] rechnet 10 % Toleranz ein. */
    val minDeviceRamBytes: Long,
    val recommended: Boolean = false,
    /**
     * Platz, den das Modell nach dem Download zusaetzlich belegt — Textmodelle: XNNPACK-Cache beim
     * ersten Laden ([ModelStore.cacheDir]). Die Platzpruefung vor dem Download rechnet ihn mit.
     */
    val extraDiskBytes: Long = 0,
)

/** Ein Modell aus beiden Katalogen (whisper und Text) — fuer Download-Dienst und [ModelStore]. */
fun findOfflineModel(id: String?): OfflineModel? = ModelCatalog.find(id) ?: TextModelCatalog.find(id)

/**
 * Die vier whisper.cpp-Modelle (GGML, quantisiert) von huggingface.co/ggerganov/whisper.cpp in
 * Anzeige-Reihenfolge (UX-Spec E4). Kein Modell liegt im APK.
 *
 * Bytes und SHA-256 stammen aus der Hugging-Face-API (research/whisper-cpp.md §5, live geprueft
 * 2026-09-06); das Katalog-Repo ist seit 2024-10-29 unveraendert.
 */
object ModelCatalog {

    /** 302 -> CDN; HttpURLConnection folgt (gleiches Schema https). Range-Requests werden unterstuetzt. */
    const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

    private const val MB = 1_000_000L
    private const val GIB = 1024L * 1024L * 1024L

    val TINY = OfflineModel(
        id = "tiny", label = "Tiny", fileName = "ggml-tiny-q5_1.bin", url = BASE_URL + "ggml-tiny-q5_1.bin",
        bytes = 32_152_673L,
        sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
        approxRamBytes = 250 * MB, minDeviceRamBytes = 0,
    )
    val BASE = OfflineModel(
        id = "base", label = "Base", fileName = "ggml-base-q5_1.bin", url = BASE_URL + "ggml-base-q5_1.bin",
        bytes = 59_707_625L,
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
        approxRamBytes = 355 * MB, minDeviceRamBytes = 0,
    )
    val SMALL = OfflineModel(
        id = "small", label = "Small", fileName = "ggml-small-q5_1.bin", url = BASE_URL + "ggml-small-q5_1.bin",
        bytes = 190_085_487L,
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        approxRamBytes = 430 * MB, minDeviceRamBytes = 0,
        recommended = true,
    )
    val LARGE_V3_TURBO = OfflineModel(
        id = "large-v3-turbo", label = "Large v3 Turbo", fileName = "ggml-large-v3-turbo-q5_0.bin",
        url = BASE_URL + "ggml-large-v3-turbo-q5_0.bin",
        bytes = 574_041_195L,
        sha256 = "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
        approxRamBytes = 1_000 * MB, minDeviceRamBytes = 6 * GIB,
    )

    val models: List<OfflineModel> = listOf(TINY, BASE, SMALL, LARGE_V3_TURBO)

    /** Standard-Download und Fallback fuer unbekannte IDs (= Prefs.DEFAULT_OFFLINE_MODEL). */
    val DEFAULT: OfflineModel = SMALL

    fun find(id: String?): OfflineModel? = models.firstOrNull { it.id == id }

    fun byId(id: String?): OfflineModel = find(id) ?: DEFAULT
}
