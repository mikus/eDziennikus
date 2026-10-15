/*
 * Copyright (c) Mikolaj Olszewski 2026-10-15.
 */
package eu.mikus.edziennik.data.api

import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.ApiTaskAllFinishedEvent
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.interfaces.EdziennikCallback
import eu.mikus.edziennik.data.api.models.ApiError
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Characterizes the asymmetry between `ApiService.onError`'s two arms, which is the whole reason
 * phase 70's fix lives in [eu.mikus.edziennik.data.api.edziennik.librus.Librus] rather than here.
 *
 * The critical arm cancels, clears and runs the next task. The non-critical arm bumps a notification
 * counter and **returns** - it does not cancel, does not clear, and does not call `runTask()`, which
 * is the only caller of `checkIfTaskFrozen()`. So an `ApiError` marked non-critical by a caller that
 * does not itself resume whatever it was doing leaves the service with `taskIsRunning` still true and
 * nothing scheduled to change that: no terminal event, a foreground service that never stops, and a
 * `bindAndAwait` that burns its whole timeout. `ApiError.isCritical` defaults to true and no
 * production caller in the current tree sets it false - the last was removed in `3defe2d3`.
 *
 * Driven by DIRECT calls, never `EventBus.post`: all three `@Subscribe` methods are `ThreadMode.ASYNC`
 * and `throwSubscriberException` defaults to false, so a posted event would swallow a failure and read
 * as "nothing happened", which is exactly the assertion.
 *
 * Boots the real [App], as [ApiServiceTerminalEventTest] does: `ApiService.app` is
 * `applicationContext as App`, dereferenced through the `notification` lazy in `onCreate`.
 *
 * The critical test drains the queue into a real `SzkolnyTask`, so it does real DB work - inert on the
 * empty test database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApiServiceErrorBranchTest {

    /**
     * Reports one error of its own choosing and returns.
     *
     * `request` is a bare `Any`, so `onApiTask`'s `when (task.request)` falls to its `else` arm and
     * the task is queued without touching a DAO. `prepare` is overridden away from the real one for
     * the same reason. `run` is overridable only because [EdziennikTask.run] is `open`; it is the
     * only seam that does not reach for reflection to hand the service's private `taskCallback` an
     * `ApiError` the test controls.
     */
    private class ErroringTask(private val error: ApiError) : EdziennikTask(0, Any()) {
        var cancelCount = 0
        override fun prepare(app: App) { taskName = "erroring" }
        override fun cancel() { cancelCount++ }
        override fun run(app: App, taskCallback: EdziennikCallback) { taskCallback.onError(error) }
    }

    private lateinit var controller: ServiceController<ApiService>

    @Before fun setUp() {
        EventBus.getDefault().removeAllStickyEvents()
        // A companion var stamped at class load and shared across the whole JVM fork; resetting it
        // keeps runTask()'s 30 s inactivity check from clearing a task out from under the fixture.
        ApiService.lastEventTime = System.currentTimeMillis()
        controller = Robolectric.buildService(ApiService::class.java).create()
    }

    @After fun tearDown() {
        // Robolectric's stopSelf() does not dispatch onDestroy, so a service that reached
        // allCompleted() would otherwise stay registered on the process-wide bus.
        controller.destroy()
        EventBus.getDefault().removeAllStickyEvents()
    }

    private fun allFinished() = EventBus.getDefault().getStickyEvent(ApiTaskAllFinishedEvent::class.java)
    private fun lastError() = EventBus.getDefault().getStickyEvent(ApiTaskErrorEvent::class.java)

    @Test fun `a critical error cancels the task and ends the sync`() {
        val task = ErroringTask(ApiError("T", ERROR_REQUEST_FAILURE))
        controller.get().onApiTask(task)

        assertEquals("the critical arm must reach the task", 1, task.cancelCount)
        assertNotNull("and it must be reported", lastError())
        assertNotNull("and the queue must drain to a terminal event", allFinished())
    }

    @Test fun `a non-critical error neither cancels the task nor ends the sync`() {
        val task = ErroringTask(ApiError("T", ERROR_REQUEST_FAILURE).setCritical(false))
        controller.get().onApiTask(task)

        assertEquals("the non-critical arm must NOT cancel", 0, task.cancelCount)
        assertNotNull("but it must still be reported, exactly like a critical one", lastError())
        assertNull(
            "ApiService does not resume anything by itself - whoever marks an error non-critical " +
                    "must resume its own work, or the sync hangs here",
            allFinished(),
        )
    }
}
