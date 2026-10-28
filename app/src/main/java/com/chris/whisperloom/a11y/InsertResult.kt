package com.chris.whisperloom.a11y

/** Ergebnis des Einfuegens per Bedienungshilfe ([TextInserterAccessibilityService.tryInsert]). */
enum class InsertResult {
    /** Text steht im Feld. */
    INSERTED,

    /** Dienst aus, kein Fokusfeld oder das Feld lehnt ab — der Knopf nimmt die Zwischenablage. */
    FAILED,

    /** Passwortfeld: bewusst nicht eingefuegt, auch nicht in die Zwischenablage (UX-Spec §6.21). */
    BLOCKED,
}
