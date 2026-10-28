package com.chris.whisperloom.simple

import android.view.inputmethod.InputConnection

/** Der schmale Ausschnitt der [InputConnection], den die Simple-Tastatur braucht — in Tests ein Fake. */
interface Editor {
    fun setComposing(text: String): Boolean
    fun commit(text: String): Boolean
    fun finishComposing()
    fun deleteBefore(chars: Int)
    fun textBeforeCursor(chars: Int): CharSequence?
}

/**
 * Context7: Android Developer Reference, InputConnection — setComposingText ersetzt den gesamten vorlaeufigen
 * Text, commitText wirkt wie setComposingText + finishComposingText.
 */
class InputConnectionEditor(private val ic: InputConnection) : Editor {
    override fun setComposing(text: String): Boolean = ic.setComposingText(text, 1)
    override fun commit(text: String): Boolean = ic.commitText(text, 1)
    override fun finishComposing() { ic.finishComposingText() }
    override fun deleteBefore(chars: Int) { ic.deleteSurroundingText(chars, 0) }
    override fun textBeforeCursor(chars: Int): CharSequence? = ic.getTextBeforeCursor(chars, 0)
}

/**
 * Schreibt Echtzeit-Text ins Feld (Konzept §6.1): `partial_transcript` → vorlaeufig per Composing (jedes Partial
 * ersetzt den ganzen offenen Abschnitt, Korrekturen frueherer Woerter kommen so von selbst), `committed_transcript`
 * → fester Text per commitText.
 *
 * [mode] (Konzept §10, §6.3): [SimpleProfile.Preview.FIELD] = vorlaeufiger Text im Feld; [SimpleProfile.Preview.LINE]
 * = nur in der Vorschauzeile (TalkBack-Regel, TYPE_NULL); [SimpleProfile.Preview.OFF] = gar nicht. Ins Feld kommt
 * ausserhalb von FIELD je Abschnitt genau ein commitText. Liefert das Feld fuer Composing `false`, faellt der
 * Schreiber von selbst auf LINE zurueck (§6.5).
 *
 * Cursor bewegt ([cursorMoved]) oder Feld zu ([fieldFinished]): den offenen Text festschreiben; weitere Partials
 * und der feste Text desselben Abschnitts bringen dann nur noch den Rest, nichts doppelt.
 *
 * Der eingefuegte Text dieser Aufnahme bleibt als Zeichenkette bekannt ([insertedText]): "Loeschen = letztes Diktat
 * entfernen" loescht nur, wenn genau dieser Text noch vor dem Cursor steht (Konzept §4.2, "dieser Tastatur").
 */
class ComposingWriter(private val editor: Editor, mode: SimpleProfile.Preview) {

    var mode: SimpleProfile.Preview = mode
        private set

    val inField: Boolean get() = mode == SimpleProfile.Preview.FIELD

    /** Zuletzt gezeigter vorlaeufiger Rest (im Feld als Composing bzw. in der Vorschau). */
    var composing = ""
        private set

    /** Fest eingefuegter Text dieser Aufnahme, in Reihenfolge. */
    private val insertedText = StringBuilder()

    /** Fest eingefuegte Zeichen dieser Aufnahme. */
    val inserted: Int get() = insertedText.length

    /** Der ganze offene Abschnitt laut Anbieter (letztes Partial), unabhaengig davon, was davon schon fest ist. */
    private var lastFull = ""

    /** Anfang des offenen Abschnitts, der durch Cursor-Sprung/Feldende schon fest im Feld steht. */
    private var fixed = ""

    /** Leerzeichen vor dem Abschnitt, wenn direkt an ein Wort angefuegt wird; je offenem Stueck einmal bestimmt. */
    private var lead = ""

    /** @return Text fuer die Vorschauzeile (leer = ausblenden). */
    fun partial(text: String): String {
        lastFull = text
        val rest = remainder(text)
        if (composing.isEmpty() && rest.isNotEmpty()) lead = leadFor(rest)
        composing = rest
        if (inField && rest.isNotEmpty() && !editor.setComposing(lead + rest)) {
            // Feld kann kein Composing (§6.5): ab jetzt nur fertige Saetze, Rest in die Vorschau.
            mode = SimpleProfile.Preview.LINE
        }
        return when (mode) {
            SimpleProfile.Preview.FIELD, SimpleProfile.Preview.OFF -> ""
            SimpleProfile.Preview.LINE -> rest
        }
    }

    fun committed(text: String) {
        val rest = remainder(text)
        fixed = ""
        lastFull = ""
        if (rest.isEmpty()) {
            discard()
            return
        }
        if (composing.isEmpty()) lead = leadFor(rest)
        val chunk = "$lead$rest "
        // Ersetzt im Feld den Composing-Bereich (FIELD) bzw. fuegt an der Cursorstelle ein.
        editor.commit(chunk)
        insertedText.append(chunk)
        composing = ""
    }

    /**
     * [android.inputmethodservice.InputMethodService.onUpdateSelection]: liegt der Cursor nicht mehr im
     * Composing-Bereich ([candStart]..[candEnd], -1 = keiner), den offenen Text festschreiben.
     */
    fun cursorMoved(selStart: Int, selEnd: Int, candStart: Int, candEnd: Int) {
        if (!inField || composing.isEmpty()) return
        val inside = candStart >= 0 && selStart >= candStart && selEnd <= candEnd
        if (!inside) fix()
    }

    /** Feld geht zu ([android.inputmethodservice.InputMethodService.onFinishInput]): offenen Text festschreiben. */
    fun fieldFinished() = flush()

    /**
     * Offenen Text festschreiben, damit nichts verloren geht (Feldende, Abbruch, Fertig ohne festen Text vom
     * Anbieter): im Feld wird das Composing fest, in der Vorschau wird der Satz eingefuegt (bei OFF gibt es nichts).
     */
    fun flush() {
        if (composing.isEmpty()) return
        if (inField) fix() else committed(lastFull)
    }

    /** Composing-Text im Feld entfernen (Aufnahme verworfen). */
    fun discard() {
        if (inField && composing.isNotEmpty()) {
            editor.setComposing("")
            editor.finishComposing()
        }
        composing = ""
    }

    /**
     * "Loeschen = letztes Diktat entfernen": nur, wenn der eingefuegte Text dieser Aufnahme noch genau vor dem
     * Cursor steht — nach Feldwechsel oder Cursor-Sprung bleibt fremder Text unberuehrt.
     * @return true = entfernt.
     */
    fun removeInserted(): Boolean {
        val text = insertedText.toString()
        if (text.isEmpty()) return false
        if (editor.textBeforeCursor(text.length)?.toString() != text) return false
        editor.deleteBefore(text.length)
        insertedText.clear()
        return true
    }

    /** Was von [full] noch nicht fest im Feld steht. */
    private fun remainder(full: String): String {
        val f = fixed.trim()
        val t = full.trim()
        return if (f.isNotEmpty() && t.startsWith(f)) t.removePrefix(f).trimStart() else t
    }

    private fun fix() {
        editor.finishComposing()
        insertedText.append(lead).append(composing)
        fixed = lastFull
        composing = ""
    }

    /** Leerzeichen nur zwischen zwei Woertern: vor Satzzeichen (Rest eines festgeschriebenen Abschnitts) keins. */
    private fun leadFor(rest: String): String {
        if (rest.isEmpty() || !rest[0].isLetterOrDigit()) return ""
        val before = editor.textBeforeCursor(1)
        return if (before.isNullOrEmpty() || before[0].isWhitespace()) "" else " "
    }

    companion object {
        /** Modus je Feld (Konzept §6.3, §4.4): TalkBack und Felder ohne Composing (TYPE_NULL) erzwingen die Vorschau. */
        fun modeFor(preview: SimpleProfile.Preview, composingSupported: Boolean, talkBack: Boolean): SimpleProfile.Preview =
            when {
                preview == SimpleProfile.Preview.OFF -> SimpleProfile.Preview.OFF
                !composingSupported || talkBack -> SimpleProfile.Preview.LINE
                else -> preview
            }
    }
}
