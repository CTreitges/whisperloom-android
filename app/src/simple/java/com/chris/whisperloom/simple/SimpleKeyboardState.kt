package com.chris.whisperloom.simple

/**
 * Zustandsmaschine der Ein-Knopf-Tastatur (Konzept §4.2), rein und JVM-testbar.
 *
 * Tippen = Start (gesperrt), Tippen = Fertig; daneben Loeschen. Doppeltreffer-Sperre: Tipps innerhalb von
 * [lockMs] nach einem Zustandswechsel zaehlen nicht (Zittern, doppelter Schalterdruck). Loeschen: waehrend des
 * Verbindens = Abbrechen, waehrend der Aufnahme = verwerfen, danach = letztes Diktat entfernen (je Profil erst mit
 * zweitem Tipp). Keine Pause-Funktion, kein automatisches Ende — das regelt der Dienst (Sicherheits-Stopp).
 */
class SimpleKeyboardState(
    private val lockMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    enum class Phase { READY, CONNECTING, RECORDING, INSERTING, INSERTED, ERROR }

    enum class RecordAction { NONE, START, FINISH }

    enum class DeleteAction { NONE, CANCEL_CONNECT, DISCARD_RECORDING, DISCARD_ERROR, ASK_CONFIRM, REMOVE_LAST }

    var phase = Phase.READY
        private set

    /** Zeichen des letzten eingefuegten Diktats dieser Tastatur; 0 = nichts zu entfernen. */
    var lastInserted = 0

    private var changedAt = Long.MIN_VALUE / 2
    private var confirmAskedAt = Long.MIN_VALUE / 2

    /** Loeschen wartet auf den bestaetigenden zweiten Tipp. */
    val confirmPending: Boolean get() = clock() - confirmAskedAt < CONFIRM_MS

    private fun locked(): Boolean = clock() - changedAt < lockMs

    private fun move(to: Phase) {
        phase = to
        changedAt = clock()
        confirmAskedAt = Long.MIN_VALUE / 2
    }

    fun tapRecord(): RecordAction {
        if (locked()) return RecordAction.NONE
        return when (phase) {
            Phase.READY, Phase.INSERTED, Phase.ERROR -> { move(Phase.CONNECTING); RecordAction.START }
            Phase.RECORDING -> { move(Phase.INSERTING); RecordAction.FINISH }
            Phase.CONNECTING, Phase.INSERTING -> RecordAction.NONE
        }
    }

    /** Fertig ohne Tipp (Sicherheits-Stopp, "Fertig per Stille"): wie der zweite Tipp, ohne Sperre. */
    fun finish() {
        if (phase == Phase.RECORDING) move(Phase.INSERTING)
    }

    /** Verbindung steht, Mikrofon laeuft. false = inzwischen abgebrochen. */
    fun connected(): Boolean {
        if (phase != Phase.CONNECTING) return false
        move(Phase.RECORDING)
        return true
    }

    /** Text ist im Feld ([chars] Zeichen, auch 0 nach Feldende). */
    fun inserted(chars: Int) {
        lastInserted = chars
        move(Phase.INSERTED)
    }

    fun failed() = move(Phase.ERROR)

    /** Zurueck zu Bereit (Quittung abgelaufen, Fehler verworfen, Aufnahme abgebrochen). */
    fun ready() = move(Phase.READY)

    fun tapDelete(confirm: Boolean): DeleteAction {
        if (locked()) return DeleteAction.NONE
        return when (phase) {
            Phase.CONNECTING -> { move(Phase.READY); DeleteAction.CANCEL_CONNECT }
            Phase.RECORDING -> { move(Phase.READY); DeleteAction.DISCARD_RECORDING }
            Phase.INSERTING -> DeleteAction.NONE
            Phase.ERROR -> { move(Phase.READY); DeleteAction.DISCARD_ERROR }
            Phase.READY, Phase.INSERTED -> when {
                lastInserted == 0 -> DeleteAction.NONE
                !confirm -> removeLast()
                !confirmPending -> { confirmAskedAt = clock(); DeleteAction.ASK_CONFIRM }
                // Der bestaetigende Tipp unterliegt derselben Sperre wie jeder andere: ein Doppeltreffer loescht nicht.
                clock() - confirmAskedAt < lockMs -> DeleteAction.NONE
                else -> removeLast()
            }
        }
    }

    private fun removeLast(): DeleteAction {
        move(Phase.READY)
        return DeleteAction.REMOVE_LAST
    }

    companion object {
        /** So lange wartet "Wirklich loeschen?" auf den zweiten Tipp. */
        const val CONFIRM_MS = 4_000L
    }
}
