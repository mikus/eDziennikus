/*
 * Copyright (c) Mikolaj Olszewski 2026-9-23.
 */
package eu.mikus.edziennik.sync

import androidx.work.WorkInfo
import androidx.work.impl.model.WorkSpec
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

/**
 * The scheduling decision, pulled out of [WorkerUtils.scheduleNext] so it can be read.
 *
 * What made the original untestable was `inline` + `AsyncTask.execute` + the static
 * `WorkManager.getInstance` — not `@RestrictTo`, which Robolectric reaches fine. Only the first
 * three are removed here; the query itself stays device-evidence (see the class KDoc on
 * [SyncWorkerRescheduleTest]).
 */
class WorkerUtilsDecisionTest {

    private fun spec(state: WorkInfo.State, dueAtMs: Long) =
        WorkSpec("id-$dueAtMs-$state", SyncWorker::class.java.name).apply {
            this.state = state
            periodStartTime = dueAtMs
            initialDelay = 0L
        }

    // ---- decideReschedule -------------------------------------------------

    /** Nothing scheduled at all: schedule at the interval, not promptly. D-4. */
    @Test
    fun `no work at all schedules at the interval`() {
        assertEquals(
            RescheduleDecision.AtInterval,
            WorkerUtils.decideReschedule(unfinishedCount = 0, overdueCount = 0, rescheduleIfFailedFound = true),
        )
    }

    /** The defect this phase exists for: the only job is late, so run it soon. */
    @Test
    fun `the only job being overdue schedules promptly`() {
        assertEquals(
            RescheduleDecision.Promptly,
            WorkerUtils.decideReschedule(unfinishedCount = 1, overdueCount = 1, rescheduleIfFailedFound = true),
        )
    }

    /**
     * One healthy job: leave it alone. This is the case that covers a RUNNING sync once the caller
     * counts unfinished work — rescheduling here is what cancelled syncs in flight.
     */
    @Test
    fun `a healthy job is left alone`() {
        assertEquals(
            null,
            WorkerUtils.decideReschedule(unfinishedCount = 1, overdueCount = 0, rescheduleIfFailedFound = true),
        )
    }

    /** One healthy and one overdue: the healthy one still covers us. Catches `reschedule = overdue > 0`. */
    @Test
    fun `an overdue job alongside a healthy one is left alone`() {
        assertEquals(
            null,
            WorkerUtils.decideReschedule(unfinishedCount = 2, overdueCount = 1, rescheduleIfFailedFound = true),
        )
    }

    /** App.onCreate's path: never act on overdue work, only on its total absence. */
    @Test
    fun `without failed-work handling an overdue job is still left alone`() {
        assertEquals(
            null,
            WorkerUtils.decideReschedule(unfinishedCount = 1, overdueCount = 1, rescheduleIfFailedFound = false),
        )
    }

    /** ...but an empty schedule is still refilled, at the interval. */
    @Test
    fun `without failed-work handling an empty schedule is refilled at the interval`() {
        assertEquals(
            RescheduleDecision.AtInterval,
            WorkerUtils.decideReschedule(unfinishedCount = 0, overdueCount = 0, rescheduleIfFailedFound = false),
        )
    }

    // ---- overdueWork ------------------------------------------------------

    private val now = 1_700_000_000_000L
    private val grace = WorkerUtils.RESCHEDULE_GRACE_MS

    @Test
    fun `a job past due by more than the grace is overdue`() {
        val late = spec(WorkInfo.State.ENQUEUED, now - grace - 1)
        assertEquals(listOf(late), WorkerUtils.overdueWork(listOf(late), now, grace))
    }

    @Test
    fun `a job still inside the grace is not overdue`() {
        val recent = spec(WorkInfo.State.ENQUEUED, now - grace + 1)
        assertEquals(emptyList<WorkSpec>(), WorkerUtils.overdueWork(listOf(recent), now, grace))
    }

    /**
     * The defect in one assertion: a sync that is RUNNING is not a sync that failed to start,
     * however long ago it was due. Dropping the state check makes a long-running sync look failed —
     * and the caller then cancels it.
     */
    @Test
    fun `a running job is never overdue however old`() {
        val running = spec(WorkInfo.State.RUNNING, now - 10 * grace)
        assertEquals(emptyList<WorkSpec>(), WorkerUtils.overdueWork(listOf(running), now, grace))
    }

    /** The App Manager threshold is the looser of the two, so it must select a subset. D-1(a). */
    @Test
    fun `the app manager grace is looser than the reschedule grace`() {
        val late = spec(WorkInfo.State.ENQUEUED, now - grace - 1)
        assertEquals(listOf(late), WorkerUtils.overdueWork(listOf(late), now, grace))
        assertEquals(emptyList<WorkSpec>(), WorkerUtils.overdueWork(listOf(late), now, WorkerUtils.APP_MANAGER_GRACE_MS))
    }
}
