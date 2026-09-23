/*
 * Copyright (c) Mikolaj Olszewski 2026-9-22.
 */
package eu.mikus.edziennik.sync

import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.ApiService
import eu.mikus.edziennik.data.api.ApiServiceBindAndAwaitTest
import eu.mikus.edziennik.data.api.events.requests.TaskCancelRequest
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        // first(), not single(): the real App's onCreate can enqueue concurrently, and the count is
        // not what this pins -- survival is. Asserting an exact count made the test order-dependent,
        // which showed up as "List has more than one element" only in some orderings.
        val spared = pending().first().id

        SyncWorker.rescheduleNext(app, exceptId = spared)

        val ids = pending().map { it.id }
        assertTrue("the spared job must survive", spared in ids)
        assertTrue("and a replacement must still be scheduled", ids.size >= 2)
    }

    @Test fun `an unknown spare id cancels everything as before`() {
        SyncWorker.rescheduleNext(app)

        SyncWorker.rescheduleNext(app, exceptId = UUID.randomUUID())

        assertEquals(1, pending().size)
    }

    /**
     * The one assertion `work-testing` exists for: a worker rescheduling from inside its own
     * `doWork` must leave a replacement job behind and must not have cancelled itself. The sync will
     * time out quickly here (nothing services the bind under Robolectric), which is fine — the
     * reschedule is in a `finally`.
     *
     * The timeout is shortened for the duration of the call; at its production value this one test
     * would hold the suite for five minutes.
     */
    @Test fun `doWork reschedules without cancelling itself`() {
        val worker = TestWorkerBuilder<SyncWorker>(app, SynchronousExecutor()).build()

        val timeout = SyncWorker.SYNC_TIMEOUT_MS
        val grace = ApiService.CANCEL_GRACE_MS
        SyncWorker.SYNC_TIMEOUT_MS = 100
        // Nothing services the bind under Robolectric, so the sync always times out here and the
        // cancel grace always runs out in full. At its production 15 s that is 15 s added to every
        // suite run for no signal.
        ApiService.CANCEL_GRACE_MS = 50
        try {
            worker.doWork()
        } finally {
            SyncWorker.SYNC_TIMEOUT_MS = timeout
            ApiService.CANCEL_GRACE_MS = grace
        }

        assertTrue("a replacement must be scheduled", pending().isNotEmpty())
    }

    /**
     * WorkManager is taking the window back. The sync is still writing — `cancel()` reaches
     * `Data.saveData()`, a multi-DAO flush — so the service must be asked to end it rather than
     * the process being reclaimed mid-write. Best effort: there is no time guarantee here, which is
     * why `bindAndAwait`'s timeout path does the same thing with a grace wait rather than relying on
     * this.
     */
    @Test fun `being stopped asks the service to end the sync`() {
        val probe = ApiServiceBindAndAwaitTest.CancelProbe()
        EventBus.getDefault().register(probe)
        try {
            TestWorkerBuilder<SyncWorker>(app, SynchronousExecutor()).build().onStopped()

            assertTrue("the service must be asked to stop", probe.seen.isNotEmpty())
            // ...and asked NON-stickily. postSticky would also reach the probe above, so that
            // assertion alone cannot tell the two apart -- and a sticky that found no subscriber
            // would be delivered to the next service created and abort a sync the user did ask for.
            assertNull(
                "a cancel request must not be left on the bus",
                EventBus.getDefault().getStickyEvent(TaskCancelRequest::class.java),
            )
        } finally {
            EventBus.getDefault().unregister(probe)
            EventBus.getDefault().removeStickyEvent(TaskCancelRequest::class.java)
        }
    }
}
