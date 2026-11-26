package com.chris.whisperloom.a11y

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.chris.whisperloom.history.HistoryPolicy
import com.chris.whisperloom.overlay.FloatingMicService

/**
 * Bedienungshilfe-Dienst, der erkannten Text an der Cursor-Position in das gerade
 * fokussierte, editierbare Feld einfuegt — OHNE selbst Tastatur zu sein. Dadurch kann
 * der schwebende Mikro-Button diktieren, waehrend Gboard aktiv bleibt.
 *
 * Bewusst clipboard-frei (ACTION_SET_TEXT), da Zwischenablage-Schreibzugriff auf
 * Android 10+ fuer Hintergrund-Prozesse eingeschraenkt ist.
 */
class TextInserterAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    /**
     * Fokus auf ein editierbares Feld an den schwebenden Knopf melden (UX-Spec §6.22). Gelesen werden
     * nur Fenster-ID, Knoten-Kennung und isEditable — nie der Inhalt.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_FOCUSED) return
        fieldKeyOf(event.windowId, event.source)?.let(FloatingMicService::fieldFocused)
    }

    override fun onInterrupt() { /* nicht benoetigt */ }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Fuegt [text] an der Cursor-Position des Fokusfelds ein. */
    fun insert(text: String): InsertResult = insertInto(focusedField(), text)

    /** Das fokussierte, editierbare Feld; null ohne Fenster oder Fokus. */
    private fun focusedField(): AccessibilityNodeInfo? =
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }

    companion object {
        @Volatile
        private var instance: TextInserterAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        /** Kennung eines editierbaren Felds; null fuer alles andere. */
        internal fun fieldKeyOf(windowId: Int, node: AccessibilityNodeInfo?): FieldKey? =
            node?.takeIf { it.isEditable }?.let { FieldKey(windowId, it.hashCode()) }

        /** Versucht den Text einzufuegen; [InsertResult.FAILED], wenn Dienst aus oder kein Fokusfeld. */
        fun tryInsert(text: String): InsertResult = instance?.insert(text) ?: InsertResult.FAILED

        /** Fuegt [text] an der Cursor-Position von [node] ein — nie in ein Passwortfeld (UX-Spec §6.22). */
        internal fun insertInto(node: AccessibilityNodeInfo?, text: String): InsertResult {
            if (text.isEmpty()) return InsertResult.INSERTED
            if (node == null) return InsertResult.FAILED
            if (isPassword(node)) return InsertResult.BLOCKED

            val old = currentText(node)
            val result = TextInsertion.compute(old, node.textSelectionStart, node.textSelectionEnd, text)

            val setArgs = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, result.text,
                )
            }
            if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)) return InsertResult.FAILED

            val cursor = result.cursor
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
            return InsertResult.INSERTED
        }

        /**
         * Aktueller Feldinhalt — leer, wenn das Feld nur seinen Platzhalter zeigt.
         *
         * WICHTIG: `AccessibilityNodeInfo.getText()` liefert bei einem LEEREN Feld den
         * Hint-Text (TextView.getTextForAccessibility faellt auf den Hint zurueck). Ohne
         * diese Pruefung landet der Platzhalter als "vorhandener Text" im Feld und das
         * Diktat wird daran angehaengt — genau die Symptome "Nachricht ..." (WhatsApp)
         * oder "Google ..." (Suchleiste) vor dem eigentlichen Text.
         */
        private fun currentText(node: AccessibilityNodeInfo): String {
            val text = node.text?.toString() ?: return ""
            // isShowingHintText ist der offizielle Weg (API 26+); der Hint-Vergleich faengt
            // Felder ab, die das Flag nicht setzen, aber trotzdem den Hint als Text melden.
            if (node.isShowingHintText || text == node.hintText?.toString()) return ""
            return text
        }

        /**
         * Ist das fokussierte Feld ein Passwortfeld? Dann kommt das Diktat nicht in den Verlauf.
         * Gelesen wird nur diese Eigenschaft, kein Inhalt. false = Dienst aus oder kein Fokusfeld.
         */
        fun focusedIsPassword(): Boolean = instance?.focusedField()?.let(::isPassword) ?: false

        /** Passwortfeld laut Knoten oder Eingabetyp; ein Inkognito-Flag kennt die Bedienungshilfe nicht. */
        internal fun isPassword(node: AccessibilityNodeInfo): Boolean =
            node.isPassword || HistoryPolicy.isPrivateField(node.inputType, imeOptions = 0)
    }
}
