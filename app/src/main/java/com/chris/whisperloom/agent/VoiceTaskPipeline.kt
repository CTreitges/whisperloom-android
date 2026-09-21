package com.chris.whisperloom.agent

import com.chris.whisperloom.api.isRetryable

/** Wie ein Auftrag ausgegangen ist. [text] wird mitgereicht, damit ein Wiederholungsversuch nicht erneut transkribieren muss. */
sealed class TaskOutcome {
    data class Sent(val text: String) : TaskOutcome()

    /** Voruebergehendes Problem (Netz, 5xx, 429) — WorkManager darf es spaeter erneut versuchen. */
    data class Retry(val reason: String, val text: String?) : TaskOutcome()

    /** Endgueltig: falsches Token, kaputte Adresse, nichts erkannt. Erneutes Senden hilft nur nach einer Aenderung. */
    data class Failed(val reason: String, val text: String?) : TaskOutcome()
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
 */
class VoiceTaskPipeline(
    private val samples: () -> FloatArray,
    private val transcribe: (FloatArray) -> String,
    private val send: (String) -> Unit,
) {

    fun run(cachedText: String? = null): TaskOutcome {
        val text = cachedText ?: try {
            transcribe(samples())
        } catch (e: Exception) {
            return outcome(e, null)
        }
        if (text.isBlank()) return TaskOutcome.Failed(MSG_EMPTY, null)

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
