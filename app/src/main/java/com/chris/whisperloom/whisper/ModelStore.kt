package com.chris.whisperloom.whisper

import android.content.Context
import java.io.File

/**
 * Modell-Dateien auf dem Geraet (Default: filesDir/models), whisper- und Textmodelle nebeneinander.
 * Eine Datei gilt nur als installiert, wenn sie exakt die Katalog-Groesse hat und keine Teildatei
 * (.part) mehr daneben liegt — so laedt die Engine nie einen halben Download. Der Downloader
 * schreibt nach `<datei>.part` und benennt erst nach erfolgreicher Pruefsumme um.
 * Alles unter models/ ist vom Backup ausgeschlossen (backup_rules.xml, data_extraction_rules.xml).
 */
class ModelStore(val dir: File) {

    constructor(context: Context) : this(File(context.filesDir, DIR_NAME))

    fun file(model: OfflineModel): File = File(dir, model.fileName)

    /** Teildatei eines laufenden oder abgebrochenen Downloads (Range-Resume setzt hier an). */
    fun partFile(model: OfflineModel): File = File(dir, model.fileName + PART_SUFFIX)

    /** Cache-Ordner eines Textmodells (LiteRT-LM `cacheDir`, XNNPACK-Cache), je Modell getrennt. */
    fun cacheDir(model: OfflineModel): File = File(dir, "$CACHE_DIR_NAME/${model.id}")

    fun isInstalled(model: OfflineModel): Boolean {
        val f = file(model)
        return f.isFile && f.length() == model.bytes && !partFile(model).exists()
    }

    /** Fuer Prefs.offlineModel und Prefs.localLlmModel (IDs gelten katalogweit); unbekannte sind nie installiert. */
    fun isInstalled(modelId: String?): Boolean = findOfflineModel(modelId)?.let { isInstalled(it) } ?: false

    /** Installierte Modelle eines Katalogs; ohne Angabe die whisper-Modelle. */
    fun installed(models: List<OfflineModel> = ModelCatalog.models): List<OfflineModel> = models.filter { isInstalled(it) }

    /** Loescht Modell, Teildatei und Cache. true, wenn danach nichts mehr da ist. */
    fun delete(model: OfflineModel): Boolean {
        file(model).delete()
        partFile(model).delete()
        cacheDir(model).deleteRecursively()
        return !file(model).exists() && !partFile(model).exists() && !cacheDir(model).exists()
    }

    /** Groesse des Cache-Ordners eines Textmodells in Bytes; 0, solange es nie geladen wurde. */
    fun cacheBytes(model: OfflineModel): Long = cacheDir(model).walk().filter { it.isFile }.sumOf { it.length() }

    /** Belegter Platz (Modelle, Teildateien, Caches der Textmodelle) in Bytes. */
    fun usedBytes(): Long = dir.walk().filter { it.isFile }.sumOf { it.length() }

    /** Freier Platz im Dateisystem des Modellordners (auch wenn der Ordner noch nicht existiert). */
    fun freeBytes(): Long = generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() }?.usableSpace ?: 0L

    fun ensureDir(): Boolean = dir.isDirectory || dir.mkdirs()

    companion object {
        const val DIR_NAME = "models"
        const val PART_SUFFIX = ".part"
        const val CACHE_DIR_NAME = "llm-cache"
    }
}
