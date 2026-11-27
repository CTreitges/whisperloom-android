package com.chris.whisperloom.a11y

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
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
        report()
    }

    /**
     * Fokus und Tastatur an den schwebenden Knopf melden (UX-Spec §6.22): er erscheint nur am
     * editierbaren Feld mit offener Bildschirmtastatur. Gelesen werden nur Metadaten — Fenstertyp,
     * Knoten-Kennung, isEditable, isPassword, Eingabetyp —, nie der Inhalt.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            -> report()
        }
    }

    private fun report() = FloatingMicService.focusChanged(currentFocusState())

    /** Momentaufnahme ueber alle Fenster (flagRetrieveInteractiveWindows). */
    private fun currentFocusState(): FieldFocus =
        focusOf(windows.map { it.type }, findFocus(AccessibilityNodeInfo.FOCUS_INPUT))

    override fun onInterrupt() { /* nicht benoetigt */ }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            FloatingMicService.focusChanged(null) // Bedienungshilfe aus: kein Knopf mehr
        }
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

        /** Aktueller Fokus fuer den gerade gestarteten Knopf; null = Bedienungshilfe aus. */
        fun currentFocus(): FieldFocus? = instance?.currentFocusState()

        /**
         * Meldung aus den Fenstertypen und dem Eingabefokus: Tastatur offen = ein Fenster vom Typ
         * [AccessibilityWindowInfo.TYPE_INPUT_METHOD]; Passwort nach Knoten oder Eingabetyp.
         */
        internal fun focusOf(windowTypes: List<Int>, focused: AccessibilityNodeInfo?): FieldFocus {
            val field = focused?.let { fieldKeyOf(it.windowId, it) }
            return FieldFocus(
                field = field,
                password = focused != null && field != null && isPassword(focused),
                keyboardOpen = AccessibilityWindowInfo.TYPE_INPUT_METHOD in windowTypes,
            )
        }

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
