package com.chris.whisperloom.a11y

/**
 * Kennung eines fokussierten Eingabefelds ohne Inhalt: Fenster-ID und Knoten-Kennung
 * (`AccessibilityNodeInfo.hashCode()` setzt sich aus View-ID, virtueller ID und Fenster-ID
 * zusammen). Reicht, um "dasselbe Feld" von "ein anderes Feld" zu unterscheiden (UX-Spec §6.22).
 */
data class FieldKey(val windowId: Int, val nodeId: Int)
