package com.chris.whisperloom.history

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.util.AtomicFile
import com.chris.whisperloom.Dictation
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.Refined
import com.chris.whisperloom.TranscriptionEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.random.Random

/**
 * Der Verlauf: eine JSON-Datei je Eintrag in `noBackupFilesDir/history/` — dort nie in einem
 * Cloud-Backup oder Geraeteumzug, egal was die Backup-Regeln sagen. Ein Diktat schreibt nur seine
 * eigene kleine Datei und loescht die aeltesten am Dateinamen, ohne den Rest zu lesen (Plan §6.5).
 *
 * Prozessweit: Tastatur, schwebender Knopf und App laufen in einem Prozess und teilen sich die
 * Sperre dieses Objekts. Jeder Dateizugriff laeuft darunter — auch das Lesen, denn
 * [AtomicFile.openRead] raeumt die `.new`-Datei eines laufenden Schreibvorgangs weg. Alle Methoden
 * blockieren: nur vom IO-Thread aufrufen. Nie Text loggen.
 */
object History {

    private const val TAG = "History"
    private const val DIR = "history"
    private const val SUFFIX = ".json"

    /** Dateiname = Id + [SUFFIX]; alles andere im Ordner ist kein Eintrag. */
    private val ID = Regex("""\d{13}-[0-9a-f]{8}""")

    /** Naht fuer Tests: Kuerzen und Reihenfolge haengen an der Zeit, im Test kommen mehrere Diktate je Millisekunde. */
    @VisibleForTesting
    internal var clock: () -> Long = System::currentTimeMillis

    private val _changes = MutableStateFlow(0L)

    /** Steigt nach jeder Aenderung — die Liste laedt dann neu (Compose: collectAsStateWithLifecycle). */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    fun dir(context: Context): File = File(context.applicationContext.noBackupFilesDir, DIR)

    /** Hat [s] die Form einer Eintrags-Id? Fuer Back-Stack-Schluessel, die von aussen kommen. */
    fun isId(s: String): Boolean = ID.matches(s)

    /**
     * Ein Diktat aufzeichnen — VOR dem Einfuegen, damit auch ein Text, der in kein Feld mehr kommt,
     * erhalten bleibt. Leere Erkennungen, Verlauf aus: nichts. Ein Fehler (Speicher voll, IO) bricht
     * nie das Diktat ab: still false. Ob das Feld privat ist, prueft der Aufrufer ([HistoryPolicy]).
     *
     * @return true = der Text liegt jetzt im Verlauf
     */
    fun record(context: Context, source: HistorySource, dictation: Dictation): Boolean {
        if (dictation.raw.isBlank()) return false
        return try {
            val app = context.applicationContext
            val now = clock()
            val result = dictation.result
            val processing = Processing.of(result.refinement)
            val version = HistoryVersion(result.text, now, result.model, result.skipped)
            val saved = synchronized(this) {
                // Unter der Sperre gelesen: sonst schriebe ein Diktat noch nach dem Ausschalten.
                val limit = limit(Prefs(app))
                if (limit <= 0) return false
                val dir = dir(app)
                val entry = HistoryEntry(
                    newId(dir, now), now, source, dictation.language, dictation.durationMs,
                    dictation.raw, processing, mapOf(processing to version),
                )
                write(dir, entry)
                trimLocked(dir, limit)
                // Bei zurueckgestellter Uhr ist die neue Id die aelteste — das Kuerzen kann sie gleich
                // wieder geloescht haben (wie bei restore).
                File(dir, entry.id + SUFFIX).exists()
            }
            changed()
            saved
        } catch (e: Exception) {
            Log.w(TAG, "Diktat nicht im Verlauf gespeichert (${e.javaClass.simpleName})")
            false
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "Diktat nicht im Verlauf gespeichert (${e.javaClass.simpleName})")
            false
        }
    }

    /** null = nicht (mehr) da oder unlesbar. */
    fun get(context: Context, id: String): HistoryEntry? = synchronized(this) { read(dir(context), id) }

    /** Alle Eintraege, neueste zuerst; kaputte Dateien werden uebersprungen. */
    fun list(context: Context): List<HistoryEntry> = synchronized(this) {
        val dir = dir(context)
        sweep(dir)
        ids(dir).mapNotNull { read(dir, it) }
    }

    /** Anzahl der Eintraege, ohne sie zu lesen (Hub-Zeile "37 von 50"). */
    fun count(context: Context): Int = synchronized(this) { ids(dir(context)).size }

    /**
     * Fassung [processing] setzen oder ersetzen; der Rohtext bleibt. Ersetzt behaelt die Fassung
     * ihren Platz. Liest frisch unter der Sperre: ein inzwischen geloeschter Eintrag wird nicht
     * wiederbelebt.
     *
     * @return der geaenderte Eintrag; null = nicht mehr da
     */
    fun setVersion(context: Context, id: String, processing: Processing, version: HistoryVersion): HistoryEntry? =
        update(context, id) { it.copy(versions = it.versions + (processing to version)) }

    /**
     * Bearbeiten-Fenster: [text] ersetzt die Fassung und markiert sie "bearbeitet"; Modell und
     * Fehlergrund bleiben. Wer den Ursprung bearbeitet, gibt [Processing.EDITED] — der Rohtext bleibt (E8).
     *
     * @return der geaenderte Eintrag; null = nicht mehr da
     */
    fun edit(context: Context, id: String, processing: Processing, text: String): HistoryEntry? {
        val now = clock()
        return update(context, id) { entry ->
            val old = entry.versions[processing]
            val version = old?.copy(text = text, createdAt = now, edited = true) ?: HistoryVersion(text, now, edited = true)
            entry.copy(versions = entry.versions + (processing to version))
        }
    }

    /** Lesen, aendern, schreiben in einem Zug unter der Sperre; ein geloeschter Eintrag bleibt geloescht. */
    private fun update(context: Context, id: String, change: (HistoryEntry) -> HistoryEntry): HistoryEntry? {
        val updated = synchronized(this) {
            val dir = dir(context)
            val entry = read(dir, id) ?: return null
            change(entry).also { write(dir, it) }
        }
        changed()
        return updated
    }

    /**
     * Neu verarbeiten: aus dem gespeicherten Rohtext mit den aktuellen Diktat-Einstellungen (Modell
     * je Stufe, Zugang, Offline-Regel; ohne neue Erkennung). Das Netz gilt nicht als bewiesen. Nur
     * eine Fassung mit KI wird gespeichert (bei "Aus" die Regeln-Fassung); ohne KI bleibt der
     * Eintrag unveraendert, der Grund steht in [Refined.skipped]. Die Rechnung laeuft ausserhalb der
     * Sperre — sie dauert Sekunden bis Minuten.
     *
     * @param cancelled Abbruch: die lokale Rechnung bricht ab, nichts wird gespeichert.
     * @return das Ergebnis; null = der Eintrag ist nicht (mehr) da
     * @throws IllegalArgumentException fuer [Processing.EDITED] — das ist keine Stufe.
     */
    fun reprocess(
        context: Context,
        id: String,
        processing: Processing,
        cancelled: () -> Boolean = { false },
    ): Refined? {
        val app = context.applicationContext
        val refinement = requireNotNull(processing.refinement(Prefs(app))) { "Bearbeitet ist keine Stufe" }
        val entry = get(app, id) ?: return null
        val result = TranscriptionEngine.refine(app, entry.raw, entry.language, refinement, networkProven = false, cancelled = cancelled)
        if (cancelled() || (processing != Processing.OFF && !result.refined)) return result
        val version = HistoryVersion(result.text, clock(), result.model)
        return setVersion(app, id, processing, version)?.let { result }
    }

    /** Eintrag loeschen. @return der geloeschte Eintrag fuer "Rueckgaengig" ([restore]); null = war nicht da */
    fun delete(context: Context, id: String): HistoryEntry? {
        val gone = synchronized(this) {
            val dir = dir(context)
            val entry = read(dir, id) ?: return null
            AtomicFile(File(dir, id + SUFFIX)).delete()
            entry
        }
        changed()
        return gone
    }

    /**
     * "Rueckgaengig" nach [delete]: unter seiner alten Id zurueck, gekuerzt wie beim Einfuegen.
     * Bei ausgeschaltetem Verlauf nichts. @return true = wieder da
     */
    fun restore(context: Context, entry: HistoryEntry): Boolean {
        if (!ID.matches(entry.id)) return false
        val restored = synchronized(this) {
            val limit = limit(Prefs(context.applicationContext))
            if (limit <= 0) return false
            val dir = dir(context)
            write(dir, entry)
            trimLocked(dir, limit)
            File(dir, entry.id + SUFFIX).exists()
        }
        changed()
        return restored
    }

    /** Alles loeschen, samt Resten abgebrochener Schreibvorgaenge. */
    fun clear(context: Context) {
        synchronized(this) { dir(context).deleteRecursively() }
        changed()
    }

    /** Wie viele Eintraege die Groesse [size] loeschen wuerde — fuer die Rueckfrage beim Verkleinern. */
    fun excess(context: Context, size: Int): Int = (count(context) - size).coerceAtLeast(0)

    /** Verlauf an/aus (E7). Aus loescht alles; die Rueckfrage stellt die UI. */
    fun setEnabled(context: Context, on: Boolean) {
        synchronized(this) {
            Prefs(context.applicationContext).historyEnabled = on
            if (!on) dir(context).deleteRecursively()
        }
        changed()
    }

    /**
     * Groesse setzen (eine aus [Prefs.HISTORY_SIZES]) und sofort kuerzen.
     *
     * @return Anzahl der geloeschten Eintraege
     * @throws IllegalArgumentException fuer eine nicht waehlbare Groesse
     */
    fun setSize(context: Context, size: Int): Int {
        val removed = synchronized(this) {
            Prefs(context.applicationContext).historySize = size
            trimLocked(dir(context), size)
        }
        changed()
        return removed
    }

    // --- Dateien, alles unter der Sperre --------------------------------------------------------

    /** 0 = nichts schreiben. */
    private fun limit(prefs: Prefs): Int = if (prefs.historyEnabled) prefs.historySize else 0

    /** Ids der Eintraege, neueste zuerst — die Id beginnt mit der Zeit. */
    private fun ids(dir: File): List<String> =
        dir.list().orEmpty()
            .filter { it.endsWith(SUFFIX) }
            .map { it.removeSuffix(SUFFIX) }
            .filter { ID.matches(it) }
            .sortedDescending()

    private fun newId(dir: File, now: Long): String {
        while (true) {
            val id = String.format(Locale.ROOT, "%013d-%08x", now, Random.nextInt())
            if (!File(dir, id + SUFFIX).exists()) return id
        }
    }

    private fun read(dir: File, id: String): HistoryEntry? {
        if (!ID.matches(id)) return null
        val file = File(dir, id + SUFFIX)
        if (!file.exists()) return null
        return try {
            HistoryEntry.fromJson(id, JSONObject(String(AtomicFile(file).readFully(), Charsets.UTF_8)))
        } catch (e: Exception) {
            Log.w(TAG, "Verlaufseintrag unlesbar, uebersprungen (${e.javaClass.simpleName})")
            null
        }
    }

    private fun write(dir: File, entry: HistoryEntry) {
        dir.mkdirs()
        val file = AtomicFile(File(dir, entry.id + SUFFIX))
        val out = file.startWrite()
        try {
            out.write(entry.toJson().toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }

    /** Auf die neuesten [limit] kuerzen. @return Anzahl der geloeschten Eintraege */
    private fun trimLocked(dir: File, limit: Int): Int {
        sweep(dir)
        val old = ids(dir).drop(limit.coerceAtLeast(0))
        old.forEach { AtomicFile(File(dir, it + SUFFIX)).delete() }
        return old.size
    }

    /**
     * Reste abgebrochener Schreibvorgaenge (`.new` ohne fertige Datei, alte `.bak`) enthalten Text —
     * weg damit. Unter der Sperre schreibt gerade niemand, jeder Rest ist verwaist.
     */
    private fun sweep(dir: File) {
        dir.listFiles { f -> f.name.endsWith(".new") || f.name.endsWith(".bak") }?.forEach { it.delete() }
    }

    private fun changed() = _changes.update { it + 1 }
}
