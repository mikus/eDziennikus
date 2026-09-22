/*
 * Copyright (c) Mikolaj Olszewski 2026-9-22.
 */
package eu.mikus.edziennik.sync

import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import eu.mikus.edziennik.App
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * The first test of [SyncWorker] in this repo's history. `doWork` shipped uncovered, including the
 * try/finally added in `cc9e8b0c`.
 *
 * What this pins: `rescheduleNext` leaves exactly one pending sync job, tagged, and honours the
 * disabled-sync early return — and, the reason this task exists, that it can be told to spare one
 * job so a worker rescheduling from inside `doWork` does not cancel itself.
 *
 * What it cannot pin: the self-cancel as it actually manifests. No harness puts the worker under
 * test into WorkManager's RUNNING state, so "a running worker survives its own reschedule" stays
 * device-only evidence. Said plainly here rather than implied by the test's existence.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncWorkerRescheduleTest {

    private val app get() = RuntimeEnvironment.getApplication() as App

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        // The real App's onCreate schedules SyncWorker on a background thread, so start from a
        // known-empty state rather than racing it. Blocking on the operation's result is the
        // difference between a deterministic test and an intermittent one.
        WorkManager.getInstance(app).cancelAllWorkByTag(SyncWorker.TAG).result.get()
        app.config.sync.enabled = true
    }

    @After fun tearDown() {
        WorkManager.getInstance(app).cancelAllWorkByTag(SyncWorker.TAG)
    }

    private fun pending(): List<WorkInfo> =
        WorkManager.getInstance(app).getWorkInfosByTag(SyncWorker.TAG).get()
            .filter { !it.state.isFinished }

    @Test fun `rescheduling leaves exactly one pending sync job`() {
        SyncWorker.rescheduleNext(app)
        SyncWorker.rescheduleNext(app)

        assertEquals(1, pending().size)
    }

    @Test fun `rescheduling with sync disabled leaves none`() {
        SyncWorker.rescheduleNext(app)
        app.config.sync.enabled = false
        SyncWorker.rescheduleNext(app)

        assertTrue(pending().isEmpty())
    }

    /**
     * The point of this task. A worker rescheduling from inside its own `doWork` must not cancel
     * itself — once the await of Task 3 lands, that would kill the sync it just started.
     */
    @Test fun `rescheduling spares the job it is told to spare`() {
        SyncWorker.rescheduleNext(app)
        val existing = pending().single().id

        SyncWorker.rescheduleNext(app, exceptId = existing)

        val ids = pending().map { it.id }
        assertTrue("the spared job must survive", existing in ids)
        assertEquals("and a replacement must still be scheduled", 2, ids.size)
    }

    @Test fun `an unknown spare id cancels everything as before`() {
        SyncWorker.rescheduleNext(app)

        SyncWorker.rescheduleNext(app, exceptId = UUID.randomUUID())

        assertEquals(1, pending().size)
    }
}
