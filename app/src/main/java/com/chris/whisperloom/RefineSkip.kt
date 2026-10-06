package com.chris.whisperloom

import com.chris.whisperloom.llm.LocalTextEngine
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Ausweg waehrend der Textverbesserung (Tastatur, schwebender Knopf): ein Tipp ruft [skip], und
 * [TranscriptionEngine.transcribe] kehrt sofort mit dem Text ohne KI zurueck — statt bis zum Ende
 * einer langsamen lokalen Rechnung oder eines Timeouts zu warten. Die laufende Arbeit wird
 * verworfen: eine Online-Anfrage laeuft im Hintergrund aus, das lokale Modell bricht ab
 * (cancelProcess, nie close mitten in der Rechnung).
 */
class RefineSkip {

    private val skipped = CompletableFuture<Unit>()

    val isSkipped: Boolean get() = skipped.isDone

    /** Aus jedem Thread; mehrfach harmlos. */
    fun skip() {
        if (skipped.complete(Unit)) LocalTextEngine.cancel()
    }

    /**
     * Fuehrt [work] auf einem eigenen Thread aus und wartet auf sein Ergebnis oder [skip], was zuerst
     * kommt. null = uebersprungen. Fehler von [work] kommen durch, solange nicht uebersprungen wurde.
     */
    internal fun <T : Any> race(work: () -> T): T? {
        val result = CompletableFuture.supplyAsync({ work() }, POOL)
        try {
            CompletableFuture.anyOf(result, skipped).get()
        } catch (e: ExecutionException) {
            if (!isSkipped) throw e.cause ?: e
        }
        return if (isSkipped) null else result.get()
    }

    private companion object {
        /** Ungebunden: eine verworfene Online-Anfrage darf die naechste Verbesserung nicht aufhalten. */
        val POOL: ExecutorService = Executors.newCachedThreadPool { r ->
            Thread(r, "loom-refine").apply { isDaemon = true }
        }
    }
}
