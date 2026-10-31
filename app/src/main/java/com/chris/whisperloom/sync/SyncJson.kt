package com.chris.whisperloom.sync

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/** Datei im Sync-Format ist ungueltig. Die Meldung nennt Pfade, nie Werte (die Datei kann Keys enthalten). */
class SyncFormatException(message: String) : Exception(message)

/**
 * JSON-Werkzeuge fuer das Format v1 (Desktop `docs/settings-format.md`): lesen mit Kommentaren und Komma am
 * Ende, Objekte feldweise mischen, Blaetter fuer die Vorschau, Ausgabe ohne `\/`.
 */
internal object SyncJson {

    /** @throws SyncFormatException kein JSON oder kein Objekt */
    fun parseObject(text: String): JSONObject {
        val value = try {
            val tokener = JSONTokener(stripCommentsAndTrailingCommas(text))
            tokener.nextValue().also {
                if (tokener.nextClean() != '\u0000') throw JSONException("Text nach dem Ende")
                checkTree(it, 0)
            }
        } catch (_: JSONException) {
            // Nicht die Meldung von org.json: sie enthaelt den ganzen Text, also womoeglich Keys.
            throw SyncFormatException("Kein gültiges JSON.")
        } catch (_: StackOverflowError) {
            // org.json liest rekursiv: "[[[[…" ueber viele tausend Ebenen.
            throw SyncFormatException("Kein gültiges JSON.")
        }
        return value as? JSONObject ?: throw SyncFormatException("Die Datei muss ein JSON-Objekt sein.")
    }

    /** Tiefe wie System.Text.Json auf dem Desktop (MaxDepth 64). */
    private const val MAX_DEPTH = 64

    /**
     * org.json auf Android macht aus `[1,,2]` ein Java-null im Array, an dem spaeter jedes `get` scheitert —
     * also schon hier ablehnen, ebenso zu tiefe Verschachtelung (rekursives Kopieren und Schreiben).
     */
    private fun checkTree(value: Any?, depth: Int) {
        if (depth > MAX_DEPTH) throw JSONException("zu tief")
        when (value) {
            is JSONObject -> value.keys().forEach { checkTree(value.opt(it), depth + 1) }
            is JSONArray -> for (i in 0 until value.length()) {
                checkTree(value.opt(i) ?: throw JSONException("Lücke in Liste"), depth + 1)
            }
        }
    }

    /**
     * Entfernt `//`- und `/* */`-Kommentare und Kommas vor `}`/`]` ausserhalb von Strings. org.json auf Android
     * kennt Kommentare, macht aber aus `[1,]` ein `[1, null]` und lehnt `{"a": 1,}` ab.
     */
    fun stripCommentsAndTrailingCommas(text: String): String {
        val out = StringBuilder(text.length)
        var pendingComma = -1 // Position eines Kommas in out, das vielleicht am Ende steht
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> {
                    pendingComma = -1
                    val end = stringEnd(text, i)
                    out.append(text, i, end)
                    i = end
                    continue
                }
                c == '/' && text.startsWith("//", i) -> {
                    i = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    continue
                }
                c == '/' && text.startsWith("/*", i) -> {
                    i = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                    out.append(' ')
                    continue
                }
                c == ',' -> pendingComma = out.length
                c == '}' || c == ']' -> if (pendingComma >= 0) out.setCharAt(pendingComma, ' ')
                !c.isWhitespace() -> pendingComma = -1
            }
            if (c == '}' || c == ']') pendingComma = -1
            out.append(c)
            i++
        }
        return out.toString()
    }

    /** Index hinter dem schliessenden `"` des Strings ab [start] (oder Textende). */
    private fun stringEnd(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                else -> i++
            }
        }
        return text.length
    }

    /** Felder von [incoming] ersetzen die in [target]; Objekte feldweise, Listen und Werte ganz. */
    fun merge(target: JSONObject, incoming: JSONObject) {
        for (key in incoming.keys()) {
            val value = incoming.get(key)
            val current = target.opt(key)
            if (value is JSONObject && current is JSONObject) merge(current, value) else target.put(key, copy(value))
        }
    }

    fun copy(value: Any?): Any? = when (value) {
        is JSONObject -> JSONObject().also { c -> value.keys().forEach { c.put(it, copy(value.get(it))) } }
        is JSONArray -> JSONArray().also { c -> for (i in 0 until value.length()) c.put(copy(value.get(i))) }
        else -> value
    }

    /** Blaetter als Pfad → Text (Listen sind ein Blatt), fuer den Vergleich vorher/nachher. */
    fun leaves(obj: JSONObject, prefix: String = "", into: MutableMap<String, String> = sortedMapOf()): Map<String, String> {
        for (key in obj.keys()) {
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            val value = obj.get(key)
            // Leere Objekte sind kein Blatt: {} → {"polish": "x"} ist genau eine Aenderung.
            if (value is JSONObject) leaves(value, path, into) else into[path] = write(value)
        }
        return into
    }

    /** Eingerueckte Ausgabe mit zwei Leerzeichen, Reihenfolge wie eingefuegt. */
    fun write(value: Any?, indent: String = ""): String = when (value) {
        is JSONObject -> if (value.length() == 0) "{}" else {
            val inner = "$indent  "
            value.keys().asSequence().joinToString(",\n", "{\n", "\n$indent}") { "$inner${quote(it)}: ${write(value.get(it), inner)}" }
        }
        is JSONArray -> if (value.length() == 0) "[]" else {
            val inner = "$indent  "
            (0 until value.length()).joinToString(",\n", "[\n", "\n$indent]") { inner + write(value.get(it), inner) }
        }
        is String -> quote(value)
        null, JSONObject.NULL -> "null"
        else -> value.toString()
    }

    private fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c)
            }
        }
        append('"')
    }
}
