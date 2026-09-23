package com.chris.whisperloom

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

/**
 * Liest das Vokabular samt verknuepfter .md/.txt-Datei. Die Datei bleibt ueber eine
 * persistente Leseberechtigung (Storage Access Framework) verknuepft und wird bei JEDEM
 * Diktat neu gelesen — Aenderungen an der Datei wirken ohne erneutes Verknuepfen.
 *
 * Eine verschwundene oder nicht mehr lesbare Datei bricht nie ein Diktat ab: dann gelten
 * nur die eigenen Eintraege, und die Vokabular-Ansicht zeigt den Fehler.
 */
object VocabularySource {

    private const val TAG = "VocabularySource"

    /** Dateiendungen, die als Vokabular taugen (Dateimanager melden .md oft als octet-stream). */
    private val EXTENSIONS = listOf(".md", ".markdown", ".txt")

    /** Kontext fuer den Erkenner: eigene Eintraege + Datei-Begriffe, gekappt (siehe [Vocabulary.prompt]). */
    fun prompt(context: Context, prefs: Prefs): Vocabulary.Prompt =
        Vocabulary.prompt(Vocabulary.entries(prefs.apiPrompt), fileTerms(context, prefs.vocabFileUri).orEmpty())

    /** Begriffe der verknuepften Datei; leer ohne Datei, null wenn sie nicht lesbar ist. */
    fun fileTerms(context: Context, uri: String): List<String>? {
        if (uri.isBlank()) return emptyList()
        return try {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                val bytes = input.readNBytesCompat(Vocabulary.MAX_FILE_BYTES)
                Vocabulary.parseFile(String(bytes, Charsets.UTF_8))
            }
        } catch (e: Exception) {
            // SecurityException (Berechtigung weg), FileNotFoundException (geloescht/verschoben) …
            Log.w(TAG, "Vokabular-Datei nicht lesbar: ${e.message}")
            null
        }
    }

    /** Taugt der Dateiname als Vokabular? Unbekannter Name (null) wird zugelassen. */
    fun isTextFile(name: String?): Boolean =
        name == null || EXTENSIONS.any { name.lowercase().endsWith(it) }

    /** Anzeigename der Datei (z. B. "namen.md"), sonst das letzte Pfadstueck. */
    fun displayName(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    }

    /**
     * Verknuepft eine gewaehlte Datei dauerhaft. Liefert false, wenn der Anbieter keine
     * dauerhafte Berechtigung erlaubt — dann waere die Datei nach einem Neustart verloren.
     */
    fun link(context: Context, prefs: Prefs, uri: Uri, name: String?): Boolean {
        val ok = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.isSuccess
        if (!ok) return false
        val old = prefs.vocabFileUri
        prefs.vocabFileUri = uri.toString()
        prefs.vocabFileName = name.orEmpty()
        if (old.isNotBlank() && old != uri.toString()) release(context, old)
        return true
    }

    /** Hebt die Verknuepfung auf und gibt die Berechtigung zurueck. */
    fun unlink(context: Context, prefs: Prefs) {
        val old = prefs.vocabFileUri
        prefs.vocabFileUri = ""
        prefs.vocabFileName = ""
        if (old.isNotBlank()) release(context, old)
    }

    private fun release(context: Context, uri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** InputStream.readNBytes gibt es erst ab API 33 — minSdk ist 26. */
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
