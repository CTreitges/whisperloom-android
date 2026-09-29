package com.chris.whisperloom.agent

import android.annotation.SuppressLint
import androidx.work.BackoffPolicy
import androidx.work.NetworkType
import androidx.work.WorkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Das Einreihen ohne WorkManager-Instanz (die laeuft auf linux-aarch64 unter Robolectric nicht):
 * die Phase aus den Job-Zustaenden und der gebaute Request.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@SuppressLint("RestrictedApi") // workSpec ist fuer Tests die einzige Sicht auf den gebauten Request.
class VoiceTaskWorkTest {

    private fun phase(vararg states: WorkInfo.State) = VoiceTaskWork.phaseOf(states.toList())

    @Test fun ohneJobIstNichtsDa() {
        assertEquals(JobPhase.NONE, phase())
    }

    @Test fun beendeteJobsZaehlenNicht() {
        assertEquals(JobPhase.NONE, phase(WorkInfo.State.SUCCEEDED, WorkInfo.State.FAILED, WorkInfo.State.CANCELLED))
    }

    @Test fun eingereihtOderBlockiertHeisstWarten() {
        assertEquals(JobPhase.WAITING, phase(WorkInfo.State.ENQUEUED))
        assertEquals(JobPhase.WAITING, phase(WorkInfo.State.BLOCKED))
    }

    @Test fun nachReplaceStehtDerAlteJobAlsAbgebrochenDaneben() {
        assertEquals(JobPhase.WAITING, phase(WorkInfo.State.CANCELLED, WorkInfo.State.ENQUEUED))
        assertEquals(JobPhase.RUNNING, phase(WorkInfo.State.CANCELLED, WorkInfo.State.RUNNING))
    }

    @Test fun einLaufenderJobGewinnt() {
        assertEquals(JobPhase.RUNNING, phase(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING))
    }

    @Test fun derAutomatischeWegWartetAufNetz() {
        val spec = VoiceTaskWork.request(manual = false).workSpec
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(VoiceTaskWork.BACKOFF_SECONDS * 1000, spec.backoffDelayDuration)
        assertFalse("Kein Expedited — der GreedyScheduler startet den Tipp-Request ohnehin sofort", spec.expedited)
        assertEquals(0, spec.initialDelay)
    }

    @Test fun derTippWartetAufNichts() {
        // Eine haengende Netz-Bedingung (VPN, unvalidiertes WLAN) war eine der Ursachen fuer #10.
        val spec = VoiceTaskWork.request(manual = true).workSpec
        assertEquals(NetworkType.NOT_REQUIRED, spec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(VoiceTaskWork.BACKOFF_SECONDS * 1000, spec.backoffDelayDuration)
        assertFalse(spec.expedited)
        assertEquals(0, spec.initialDelay)
    }
}
