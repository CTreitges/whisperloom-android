package com.chris.whisperloom.sync

import android.content.Context
import com.chris.whisperloom.Prefs
import org.json.JSONObject

/** Ergebnis der Import-Vorschau. Uebernehmen ([SettingsSync.apply]) nur, wenn [canApply]. */
class ImportPreview internal constructor(
    /** Meldungen mit Pfad ("history.size: eine von …"), nie mit Werten aus der Datei. */
    val errors: List<String>,
    /** Hinweise, die das Uebernehmen nicht verhindern (secrets/policy ignoriert, Verlauf wird geloescht). */
    val warnings: List<String>,
    /** Geaenderte Pfade, sortiert; ohne den Abschnitt `desktop`. */
    val changes: List<String>,
    internal val settings: SyncSettings?,
) {
    val canApply: Boolean get() = settings != null && errors.isEmpty()
}

/**
 * Export und Import der Einstellungen im Format v1 von WhisperLoom Desktop (`docs/settings-format.md` in
 * `CTreitges/whisperloom-desktop`): Stufen, Modell je Stufe, Kontext-Woerter, Fuellwoerter, Anbieter — nie Keys.
 *
 * Import wie auf dem Desktop: die Felder der Datei ersetzen die aktuellen, fehlende bleiben (Objekte feldweise,
 * Listen ganz); geprueft wird streng, bei einem Fehler wird nichts uebernommen. `secrets` und `policy` (Provisioning)
 * gelten nur auf dem Desktop und werden mit Hinweis ignoriert.
 */
object SettingsSync {
    const val SCHEMA_VERSION = 1

    /** Groesste Datei, die der Import liest (eine echte Datei hat wenige KB). */
    const val MAX_FILE_BYTES = 1_000_000

    fun export(prefs: Prefs): String = SyncJson.write(SyncTree.of(prefs)) + "\n"

    fun preview(prefs: Prefs, json: String): ImportPreview {
        val incoming = try {
            SyncJson.parseObject(json).also { migrate(it) }
        } catch (e: SyncFormatException) {
            return ImportPreview(listOf(e.message.orEmpty()), emptyList(), emptyList(), null)
        }
        provisioningError(incoming)?.let { return ImportPreview(listOf(it), emptyList(), emptyList(), null) }
        val warnings = mutableListOf<String>()
        if (incoming.has(SyncTree.SECRETS) || incoming.has(SyncTree.POLICY)) {
            warnings += "Keys und Richtlinie (secrets, policy) gelten nur für WhisperLoom Desktop und werden hier ignoriert."
        }
        listOf(SyncTree.SECRETS, SyncTree.POLICY, SyncTree.SCHEMA_VERSION).forEach { incoming.remove(it) }

        val before = SyncTree.of(prefs)
        val merged = SyncJson.copy(before) as JSONObject
        SyncJson.merge(merged, incoming)
        val errors = mutableListOf<String>()
        val settings = SyncSettings.read(merged, errors)
        settings?.let { errors += it.validate() }
        if (settings == null || errors.isNotEmpty()) return ImportPreview(errors, warnings, emptyList(), null)

        if (!settings.historyEnabled && prefs.historyEnabled) {
            warnings += "Der Verlauf wird ausgeschaltet, gespeicherte Diktate werden gelöscht."
        } else if (settings.historyEnabled && settings.historySize < prefs.historySize) {
            warnings += "Der Verlauf wird auf ${settings.historySize} Einträge verkleinert, ältere werden gelöscht."
        }
        return ImportPreview(emptyList(), warnings, changes(before, merged), settings)
    }

    /** @throws IllegalStateException fuer eine Vorschau mit Fehlern — dann bleibt alles, wie es ist. */
    fun apply(context: Context, prefs: Prefs, preview: ImportPreview) {
        val settings = checkNotNull(preview.settings.takeIf { preview.canApply }) { "Import mit Fehlern" }
        settings.writeTo(context, prefs)
    }

    /**
     * Hebt die Datei auf [SCHEMA_VERSION] (wie `SyncMigration` auf dem Desktop).
     * @throws SyncFormatException ungueltige oder neuere Version
     */
    private fun migrate(root: JSONObject) {
        val version = when (val v = root.opt(SyncTree.SCHEMA_VERSION)) {
            null, JSONObject.NULL -> 0
            is Int -> v.takeIf { it >= 0 }
            else -> null
        } ?: throw SyncFormatException("schemaVersion muss eine ganze Zahl ≥ 0 sein.")
        if (version > SCHEMA_VERSION) {
            throw SyncFormatException(
                "Die Datei hat Schema $version, diese Version kennt nur bis $SCHEMA_VERSION. Bitte WhisperLoom aktualisieren.",
            )
        }
        if (version == 0) {
            // v0 → v1: fruehere Stufe "Absaetze" ist Glaetten. Die Fuellwoerter normalisiert Prefs beim Schreiben.
            listOf("refine", "share").forEach { section ->
                root.optJSONObject(section)?.let { if (it.opt("stage") == "paragraphs") it.put("stage", "polish") }
            }
        }
    }

    /**
     * Android uebernimmt `secrets`/`policy` nicht, prueft ihre Form aber wie `SyncDocument` auf dem Desktop: eine
     * Datei ist auf beiden Seiten gueltig oder auf beiden ungueltig. Meldungen ohne Werte (Keys!).
     */
    private fun provisioningError(root: JSONObject): String? {
        root.opt(SyncTree.SECRETS)?.let { secrets ->
            if (secrets !is JSONObject) return "secrets muss ein Objekt sein (Anbieter → Key)."
            for (name in secrets.keys()) {
                val key = secrets.get(name) as? String
                if (key.isNullOrBlank()) return "secrets.$name muss ein nicht leerer Text sein."
                if (key.trim().any { it.isWhitespace() }) return "secrets.$name enthält Leerraum."
            }
        }
        root.opt(SyncTree.POLICY)?.let { if (it !is JSONObject) return "policy muss ein Objekt sein." }
        return null
    }

    private fun changes(before: JSONObject, after: JSONObject): List<String> {
        val a = SyncJson.leaves(before)
        val b = SyncJson.leaves(after)
        return (a.keys + b.keys).filter { !it.startsWith(SyncTree.DESKTOP + ".") && a[it] != b[it] }.sorted()
    }
}
