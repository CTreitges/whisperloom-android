package com.chris.whisperloom.history

import com.chris.whisperloom.PolishCleanup
import com.chris.whisperloom.Prefs
import com.chris.whisperloom.RefineMode
import com.chris.whisperloom.RefineWay
import com.chris.whisperloom.Refinement
import org.json.JSONArray
import org.json.JSONObject

/** Woher ein Eintrag kommt. Sprachnachrichten und das Pro-Widget zeichnen nicht auf (E9). */
enum class HistorySource(val key: String) {
    KEYBOARD("keyboard"),
    BUBBLE("bubble");

    companion object {
        /** Unbekanntes gilt als Tastatur. */
        fun fromKey(key: String?): HistorySource = entries.firstOrNull { it.key == key } ?: KEYBOARD
    }
}

/**
 * Eine Verarbeitung im Verlauf: die Stufe, beim Glaetten mit Bereinigung — oder der bearbeitete
 * Ursprung ([EDITED]). Je Verarbeitung hat ein Eintrag hoechstens eine Fassung. Gespeichert ueber
 * [key], nie ueber den Namen.
 */
enum class Processing(val key: String, val stage: RefineMode?, val cleanup: PolishCleanup? = null) {
    OFF("off", RefineMode.OFF),
    POLISH_PLAIN("polish", RefineMode.POLISH, PolishCleanup.PLAIN),
    POLISH_CLEAN("polish_clean", RefineMode.POLISH, PolishCleanup.CLEAN),
    POLISH_READABLE("polish_readable", RefineMode.POLISH, PolishCleanup.READABLE),
    BEAUTIFY("beautify", RefineMode.BEAUTIFY),
    SUMMARIZE("summarize", RefineMode.SUMMARIZE),
    PROMPT("prompt", RefineMode.PROMPT),

    /** Der Ursprung, im Bearbeiten-Fenster geaendert (E8). Keine Stufe, wird nie neu verarbeitet. */
    EDITED("edited", null);

    /**
     * Die Verarbeitung mit den aktuellen Diktat-Einstellungen (Absaetze, Form) — die Grundlage
     * fuers Neu-Verarbeiten. null fuer [EDITED].
     */
    fun refinement(prefs: Prefs): Refinement? {
        val stage = stage ?: return null
        val way = RefineWay.DICTATION
        return Refinement.of(way, stage, cleanup ?: PolishCleanup.PLAIN, prefs.paragraphsFor(stage), prefs.summarizeFormFor(way))
    }

    companion object {
        /** Die Verarbeitung eines Auftrags: "Lesbar" und "Ohne Fuellwoerter" sind Glaetten mit Bereinigung. */
        fun of(refinement: Refinement): Processing = when (refinement.mode) {
            RefineMode.OFF -> OFF
            RefineMode.POLISH -> if (refinement.smartFillers) POLISH_CLEAN else POLISH_PLAIN
            RefineMode.READABLE -> POLISH_READABLE
            RefineMode.BEAUTIFY -> BEAUTIFY
            RefineMode.SUMMARIZE -> SUMMARIZE
            RefineMode.PROMPT -> PROMPT
        }

        fun fromKey(key: String?): Processing? = entries.firstOrNull { it.key == key }
    }
}

/** Eine Fassung eines Eintrags. */
data class HistoryVersion(
    val text: String,
    /** Wann sie entstand bzw. zuletzt bearbeitet wurde. */
    val createdAt: Long,
    /** Womit verbessert wurde, als Anzeige; null = ohne KI. */
    val model: String? = null,
    /** Warum ohne KI, obwohl eine Stufe gewaehlt war; null = nicht gescheitert. */
    val failed: String? = null,
    /** Im Bearbeiten-Fenster geaendert. */
    val edited: Boolean = false,
) {
    /** Ohne Text: ein `Log.d("$version")` darf nie ein Diktat zeigen. */
    override fun toString(): String =
        "HistoryVersion(text=${text.length} Zeichen, createdAt=$createdAt, model=$model, failed=${failed != null}, edited=$edited)"
}

/**
 * Ein Diktat im Verlauf: der Rohtext der Erkennung (der "Ursprung", nie ueberschrieben) und die
 * Fassungen je [Processing]. [processing] ist die damalige Verarbeitung, ihre Fassung "was damals
 * herauskam". [versions] behaelt die Reihenfolge des Entstehens; Ersetzen bleibt an der Stelle.
 */
data class HistoryEntry(
    /** "<createdAt, 13 Ziffern>-<8 Hex-Zeichen>" — sortiert wie die Zeit, ohne ':' (Back-Stack). */
    val id: String,
    val createdAt: Long,
    val source: HistorySource,
    /** Wirksame Sprache des Diktats; der Prompt beim Neu-Verarbeiten haengt davon ab. */
    val language: String,
    val durationMs: Long,
    val raw: String,
    val processing: Processing,
    val versions: Map<Processing, HistoryVersion>,
) {
    /** Ohne Text, wie [HistoryVersion.toString]. */
    override fun toString(): String =
        "HistoryEntry(id=$id, source=$source, language=$language, durationMs=$durationMs, " +
            "raw=${raw.length} Zeichen, processing=$processing, versions=${versions.keys.map { it.key }})"

    fun toJson(): JSONObject = JSONObject()
        .put(K_FORMAT, FORMAT)
        .put(K_CREATED, createdAt)
        .put(K_SOURCE, source.key)
        .put(K_LANGUAGE, language)
        .put(K_DURATION, durationMs)
        .put(K_RAW, raw)
        .put(K_PROCESSING, processing.key)
        .put(K_VERSIONS, JSONArray().apply { versions.forEach { (p, v) -> put(v.toJson(p)) } })

    companion object {
        private const val FORMAT = 1

        /**
         * Tolerant gelesen: fehlt ein Feld, gilt der Standard, Unbekanntes wird ignoriert, eine
         * Fassung mit unbekannter Verarbeitung faellt weg. Ohne Rohtext unbrauchbar (null).
         */
        fun fromJson(id: String, o: JSONObject): HistoryEntry? {
            val raw = text(o, K_RAW)
            if (raw.isBlank()) return null
            val versions = LinkedHashMap<Processing, HistoryVersion>()
            val arr = o.optJSONArray(K_VERSIONS) ?: JSONArray()
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                val p = Processing.fromKey(text(v, K_PROCESSING)) ?: continue
                versions[p] = HistoryVersion(
                    text = text(v, K_TEXT),
                    createdAt = v.optLong(K_CREATED, 0L),
                    model = text(v, K_MODEL).ifEmpty { null },
                    failed = text(v, K_FAILED).ifEmpty { null },
                    edited = v.optBoolean(K_EDITED, false),
                )
            }
            return HistoryEntry(
                id = id,
                createdAt = o.optLong(K_CREATED, id.substringBefore('-').toLongOrNull() ?: 0L),
                source = HistorySource.fromKey(text(o, K_SOURCE)),
                language = text(o, K_LANGUAGE).ifEmpty { "auto" },
                durationMs = o.optLong(K_DURATION, 0L),
                raw = raw,
                processing = Processing.fromKey(text(o, K_PROCESSING)) ?: Processing.OFF,
                versions = versions,
            )
        }

        private fun HistoryVersion.toJson(p: Processing): JSONObject = JSONObject()
            .put(K_PROCESSING, p.key)
            .put(K_TEXT, text)
            .put(K_CREATED, createdAt)
            .putOpt(K_MODEL, model)
            .putOpt(K_FAILED, failed)
            .put(K_EDITED, edited)

        /** optString macht aus JSON-null den Text "null" — hier wird daraus "". */
        private fun text(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key, "")

        private const val K_FORMAT = "v"
        private const val K_CREATED = "createdAt"
        private const val K_SOURCE = "source"
        private const val K_LANGUAGE = "language"
        private const val K_DURATION = "durationMs"
        private const val K_RAW = "raw"
        private const val K_PROCESSING = "processing"
        private const val K_VERSIONS = "versions"
        private const val K_TEXT = "text"
        private const val K_MODEL = "model"
        private const val K_FAILED = "failed"
        private const val K_EDITED = "edited"
    }
}
