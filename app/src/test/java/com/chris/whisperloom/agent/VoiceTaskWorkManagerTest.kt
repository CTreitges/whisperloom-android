package com.chris.whisperloom.agent

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Der ECHTE WorkManager (work-testing), ohne Naht zwischen [VoiceTaskWork] und WorkManager.
 * Noetig, weil Dienst- und Trampolin-Test das Einreihen ersetzen: dort sieht man nur, was
 * uebergeben wird, nicht, was WorkManager daraus macht — und das echte `phaseImpl` laeuft nie.
 *
 * Nicht auf linux-aarch64: dort hat Robolectric kein SQLite, WorkManager braucht aber seine
 * Room-Datenbank. Auf der CI (ubuntu x86_64) und unter Windows laeuft er.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceTaskWorkManagerTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val wm: WorkManager get() = WorkManager.getInstance(app)
    private val echteFactory = VoiceTaskWorker.pipelineFactory
    private var gestartet = false
    private var pool: ExecutorService? = null

    @Before fun aufbauen() {
        assumeFalse("Robolectric hat auf linux-aarch64 kein SQLite", System.getProperty("os.arch") == "aarch64")
        app.getSharedPreferences("whisperloom", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences(VoiceTaskStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        VoiceTaskStore(app).clear()
    }

    @After fun abbauen() {
        VoiceTaskWorker.pipelineFactory = echteFactory
        pool?.shutdownNow()
        if (gestartet) WorkManagerTestInitHelper.closeWorkDatabase()
    }

    /** Alles auf dem Test-Thread: ein Job ohne Bedingung laeuft sofort beim Einreihen durch. */
    private fun synchron() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        gestartet = true
    }

    /** Worker auf EINEM eigenen Thread — ein Lauf kann mitten in der Erkennung stehen, waehrend getippt wird. */
    private fun mitWorkerThread() {
        val p = Executors.newSingleThreadExecutor().also { pool = it }
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration.Builder().setExecutor(p).build(),
            WorkManagerTestInitHelper.ExecutorsMode.PRESERVE_EXECUTORS,
        )
        gestartet = true
    }

    private fun infos(): List<WorkInfo> = wm.getWorkInfosForUniqueWork(VoiceTaskWork.UNIQUE_NAME).get()

    /** Wartet auf etwas, das auf anderen Threads passiert (WorkManager arbeitet asynchron). */
    private fun warteBis(was: String, bedingung: () -> Boolean) {
        val ende = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!bedingung()) {
            assertTrue("Zeitueberschreitung: $was", System.nanoTime() < ende)
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    @Test fun derAutomatischeWegWartetAufNetz() {
        synchron()

        VoiceTaskWork.enqueue(app)

        val job = infos().single()
        assertEquals(WorkInfo.State.ENQUEUED, job.state)
        assertEquals(NetworkType.CONNECTED, job.constraints.requiredNetworkType)
        assertEquals(JobPhase.WAITING, VoiceTaskWork.phase(app))
    }

    @Test fun einTippLoestDenWartendenJobAb() {
        // Genau #10: mit KEEP blieb der Job, der auf Netz oder Backoff wartete, liegen — und der
        // Tipp tat nichts.
        synchron()
        VoiceTaskWork.enqueue(app)
        val alt = infos().single().id

        VoiceTaskWork.sendNow(app)

        val neu = infos().single()
        assertNotEquals("Der Tipp muss den wartenden Job abloesen", alt, neu.id)
        assertEquals(NetworkType.NOT_REQUIRED, neu.constraints.requiredNetworkType)
        assertNull("REPLACE loescht den alten Job — er steht nicht als CANCELLED daneben", wm.getWorkInfoById(alt).get())
        // Ohne Bedingung laeuft er sofort; ohne Auftrag im Store ist er gleich fertig.
        assertEquals(WorkInfo.State.SUCCEEDED, neu.state)
        assertEquals(JobPhase.NONE, VoiceTaskWork.phase(app))
    }

    @Test fun einTippLoestEinenLaufendenJobAbUndDerAlteSendetNichts() {
        mitWorkerThread()
        VoiceTaskStore(app).begin(FloatArray(800) { 0.3f }, 4000, "2026-09-21T20:00:00Z")
        val erkennt = CountDownLatch(1)
        val weiter = CountDownLatch(1)
        val zustaendig = CopyOnWriteArrayList<() -> Boolean>()
        val gesendet = CopyOnWriteArrayList<String>()
        val transkribiert = AtomicInteger()
        VoiceTaskWorker.pipelineFactory = { _, s, aktuell ->
            zustaendig += aktuell
            VoiceTaskPipeline(
                samples = { s.loadSamples() },
                transcribe = {
                    transkribiert.incrementAndGet()
                    erkennt.countDown()
                    weiter.await(10, TimeUnit.SECONDS)
                    "Kauf Milch"
                },
                send = { gesendet += it },
                stillCurrent = aktuell,
            )
        }

        VoiceTaskWork.sendNow(app)
        warteBis("der erste Lauf erkennt") { erkennt.count == 0L }
        assertEquals(JobPhase.RUNNING, VoiceTaskWork.phase(app))
        val ersterLauf = zustaendig.single()

        VoiceTaskWork.sendNow(app)
        warteBis("REPLACE stoppt den laufenden Worker") { !ersterLauf() }
        weiter.countDown()
        warteBis("der Nachfolger ist fertig") { infos().singleOrNull()?.state == WorkInfo.State.SUCCEEDED }

        assertEquals("Genau ein Versand — der abgeloeste Lauf schickt nichts hinterher", listOf("Kauf Milch"), gesendet)
        assertEquals("Der Nachfolger nimmt den bezahlten Text des abgeloesten Laufs", 1, transkribiert.get())
        assertEquals(2, zustaendig.size)
        assertFalse(VoiceTaskStore(app).hasWork)
        assertEquals(JobPhase.NONE, VoiceTaskWork.phase(app))
    }
}
