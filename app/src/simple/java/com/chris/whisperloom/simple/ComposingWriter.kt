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
 * [inField] = false (TalkBack-Regel §6.3, Profil "nur Vorschau", Feld ohne Composing / TYPE_NULL): vorlaeufiger
 * Text geht nur in die Vorschauzeile der Tastatur; ins Feld kommt je Abschnitt genau ein commitText.
 *
 * Cursor bewegt ([cursorMoved]) oder Feld zu ([fieldFinished]): den offenen Text festschreiben; das naechste
 * `committed_transcript` desselben Abschnitts bringt dann nur noch den Rest, nicht den Anfang doppelt.
 */
class ComposingWriter(private val editor: Editor, val inField: Boolean) {

    /** Zuletzt gezeigter vorlaeufiger Text (im Feld als Composing bzw. nur in der Vorschau). */
    var composing = ""
        private set

    /** Fest eingefuegte Zeichen dieser Aufnahme — fuer "Loeschen = letztes Diktat entfernen". */
    var inserted = 0
        private set

    /** Vom Cursor-Sprung/Feldende festgeschriebener vorlaeufiger Text, vom naechsten festen Text abzuziehen. */
    private var fixed: String? = null

    /** Leerzeichen vor dem Abschnitt, wenn direkt an ein Wort angefuegt wird; je offenem Abschnitt einmal bestimmt. */
    private var lead = ""

    /** @return Text fuer die Vorschauzeile (leer = ausblenden). */
    fun partial(text: String): String {
        if (composing.isEmpty()) lead = leadFor()
        composing = text
        if (inField && text.isNotEmpty()) editor.setComposing(lead + text)
        return if (inField) "" else text
    }

    fun committed(text: String) {
        val fixedPart = fixed
        fixed = null
        var out = text.trim()
        if (fixedPart != null) {
            // Der Anfang steht schon fest im Feld — nur den Rest einfuegen.
            out = out.removePrefix(fixedPart.trim()).trimStart()
            if (out.isEmpty()) { composing = ""; return }
            lead = leadFor()
        } else if (composing.isEmpty()) {
            lead = leadFor()
        }
        if (out.isEmpty()) {
            discard()
            return
        }
        val chunk = "$lead$out "
        // Ersetzt im Feld den Composing-Bereich (inField) bzw. fuegt an der Cursorstelle ein (Vorschau-Modus).
        editor.commit(chunk)
        inserted += chunk.length
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
    fun fieldFinished() {
        if (composing.isEmpty()) return
        if (inField) {
            fix()
        } else {
            // Nur in der Vorschau: als fertigen Satz einfuegen, sonst waere er weg.
            committed(composing)
        }
    }

    /** Composing-Text im Feld entfernen (Aufnahme verworfen). */
    fun discard() {
        if (inField && composing.isNotEmpty()) {
            editor.setComposing("")
            editor.finishComposing()
        }
        composing = ""
    }

    private fun fix() {
        editor.finishComposing()
        inserted += (lead + composing).length
        fixed = composing
        composing = ""
    }

    private fun leadFor(): String {
        val before = editor.textBeforeCursor(1)
        return if (before.isNullOrEmpty() || before[0].isWhitespace()) "" else " "
    }

    companion object {
        /** Vorlaeufiger Text im Feld nur, wenn Profil, Feld und TalkBack es zulassen (Konzept §6.3, §4.4). */
        fun inField(preview: SimpleProfile.Preview, composingSupported: Boolean, talkBack: Boolean): Boolean =
            preview == SimpleProfile.Preview.FIELD && composingSupported && !talkBack
    }
}
