package com.chris.whisperloom.a11y

/**
 * Was die Bedienungshilfe dem schwebenden Knopf meldet (UX-Spec §6.22) — nur Metadaten, nie Inhalt:
 * das fokussierte, editierbare Feld ([field], null = keins), ob es ein Passwortfeld ist (Knoten oder
 * Eingabetyp) und ob ein Fenster vom Typ Eingabemethode offen ist ([keyboardOpen]).
 */
data class FieldFocus(val field: FieldKey?, val password: Boolean, val keyboardOpen: Boolean)
