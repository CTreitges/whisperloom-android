package com.chris.whisperloom.agent

import com.chris.whisperloom.api.isRetryable

/** Wie ein Auftrag ausgegangen ist. [text] wird mitgereicht, damit ein Wiederholungsversuch nicht erneut transkribieren muss. */
sealed class TaskOutcome {
    data class Sent(val text: String) : TaskOutcome()

    /** Voruebergehendes Problem (Netz, 5xx, 429) — WorkManager darf es spaeter erneut versuchen. */
    data class Retry(val reason: String, val text: String?) : TaskOutcome()

    /** Endgueltig: falsches Token, kaputte Adresse, nichts erkannt. Erneutes Senden hilft nur nach einer Aenderung. */
    data class Failed(val reason: String, val text: String?) : TaskOutcome()

    /**
     * Nicht gesendet, weil der Lauf nicht mehr zustaendig ist (abgeloest, verworfen, gestoppt).
     * [text] ist bezahlt — der Aufrufer darf ihn cachen, wenn der Auftrag noch derselbe ist.
     */
    data class Superseded(val text: String) : TaskOutcome()
}

/**
 * Der Weg vom aufgenommenen Ton zum abgeschickten Auftrag — bewusst ohne Android, damit er
 * ohne Geraet und ohne WorkManager pruefbar ist (auf diesem Entwicklungsrechner laeuft
 * WorkManager gar nicht, siehe PR-Text).
 *
 * Zwei Schritte, beide koennen scheitern:
 *  1. Transkribieren (teuer, kostet ggf. Geld) — das Ergebnis wird deshalb vom Aufrufer
 *     gespeichert und beim naechsten Versuch als [cachedText] wieder hereingereicht.
 *  2. An die Bridge schicken.
 *
 * [stillCurrent] wird direkt vor dem Senden gefragt, also nach der (langen) Transkription:
 * ein abgeloester, verworfener oder gestoppter Lauf darf nichts mehr an die Bridge schicken.
 * Die Bridge claimt die request_id VOR ihrer Arbeit — ein Zombie-Versand wuerde sonst den
 * echten Auftrag als Duplikat verdraengen.
 */
class VoiceTaskPipeline(
    private val samples: () -> FloatArray,
    private val transcribe: (FloatArray) -> String,
    private val send: (String) -> Unit,
    private val stillCurrent: () -> Boolean = { true },
) {

    /**
     * @param recognized wird gerufen, sobald die Erkennung vorbei ist — mit Text, leer oder
     *   gescheitert, in jedem Fall VOR dem Senden. Nicht, wenn [cachedText] sie erspart.
     */
    fun run(cachedText: String? = null, recognized: () -> Unit = {}): TaskOutcome {
        val text = cachedText ?: try {
            transcribe(samples())
        } catch (e: Exception) {
            return outcome(e, null)
        } finally {
            recognized()
        }
        if (text.isBlank()) return TaskOutcome.Failed(MSG_EMPTY, null)
        if (!stillCurrent()) return TaskOutcome.Superseded(text)

        return try {
            send(text)
            TaskOutcome.Sent(text)
        } catch (e: Exception) {
            outcome(e, text)
        }
    }

    private fun outcome(e: Exception, text: String?): TaskOutcome {
        val reason = e.message ?: e.javaClass.simpleName
        return if (e.isRetryable()) TaskOutcome.Retry(reason, text) else TaskOutcome.Failed(reason, text)
    }

    companion object {
        const val MSG_EMPTY = "Nichts verstanden — nichts gesendet"
    }
}
