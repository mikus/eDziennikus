/*
 * Copyright (c) Mikolaj Olszewski 2026-9-21.
 */
package eu.mikus.edziennik.data.api

import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.events.ApiTaskAllFinishedEvent
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.events.ApiTaskFinishedEvent
import eu.mikus.edziennik.data.api.events.requests.ServiceCloseRequest
import eu.mikus.edziennik.data.api.task.ErrorReportTask
import eu.mikus.edziennik.data.api.task.IApiTask
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
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
 * Pins the invariant that every route ending a sync posts a terminal event. The whole UI - the
 * toolbar subtitle, `SyncStatus.isRefreshing` behind nine `PullToRefreshBox` screens, two
 * `setCancelable(false)` wait dialogs and `LoginSyncScreen`, which has no cancel, skip or timeout -
 * learns a sync ended from `ApiTaskAllFinishedEvent` and nothing else.
 *
 * Three couplings keep these tests honest, and all three are silent if broken:
 *
 * 1. **[HangingTask] hangs only because `runTask()`'s `when (task)` has no `else` arm.** It is a
 *    statement over three concrete types and `IApiTask` is `abstract`, not `sealed`, so a fourth
 *    subtype matches nothing: the task is marked started and no callback can ever fire. Adding an
 *    `else ->` arm to that `when` silently turns every hang here into a completion.
 * 2. **The service is driven by DIRECT calls, never `EventBus.post`.** All three `@Subscribe`
 *    methods are `ThreadMode.ASYNC`, so a posted event crosses a pool thread, and EventBus's
 *    `throwSubscriberException` defaults to false - a swallowed exception would read as "nothing
 *    was posted", which is exactly the assertion.
 * 3. **This class boots the real [App]**, unlike the repo's six other Robolectric classes, which
 *    all pin `application = Application::class`. It has to: `ApiService.app` is
 *    `applicationContext as App`, dereferenced through the `notification` lazy in `onCreate`, so a
 *    stock `Application` throws `ClassCastException`. Do not "fix" this to match the others.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApiServiceTerminalEventTest {

    /** A fourth [IApiTask] subtype: matches no arm of `runTask()`'s `when`, so it never calls back. */
    private class HangingTask : IApiTask(0) {
        var cancelCount = 0
        override fun prepare(app: App) { taskName = HANGING }
        override fun cancel() { cancelCount++ }
    }

    /**
     * Counts deliveries. A sticky read cannot count, and POSTING mode is the only thread mode that
     * delivers synchronously on the test thread under Robolectric's paused looper.
     *
     * **Must not be `private`.** EventBus invokes subscribers reflectively, so a private nested
     * class fails at run time with `IllegalAccessException` wrapped in
     * `IllegalStateException: Unexpected exception` - which reads as a broken service, not a broken
     * probe. Verified: making it private turns this test red for the wrong reason.
     */
    class Probe {
        var count = 0
        @Subscribe fun onAllFinished(event: ApiTaskAllFinishedEvent) { count++ }
    }

    private lateinit var controller: ServiceController<ApiService>

    @Before fun setUp() {
        EventBus.getDefault().removeAllStickyEvents()
        // lastEventTime is a companion var stamped at class load and shared across every test in
        // the JVM fork. Resetting it keeps the 30 s freeze check at the top of runTask() from
        // spuriously clearing a task, and keeps this fixture independent of how long the suite has
        // been running. Nothing else in ApiService's companion needs resetting here. (Deliberately
        // names no symbol a later commit deletes: a comment naming a gated token makes that commit's
        // absence gate unsatisfiable, which has bitten this project five times.)
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

    /** Fixture self-check: proves the hang is a hang, not a silent completion. `prepare()` runs at
     *  `runTask():188`, before the `when`, so a non-null taskName proves the task really reached the
     *  dispatch point. Do NOT assert on a sticky `ApiTaskStartedEvent` - it is posted with `post`,
     *  not `postSticky`. */
    private fun startHanging(): HangingTask {
        val task = HangingTask()
        controller.get().onApiTask(task)
        assertEquals("the task never reached runTask()", HANGING, task.taskName)
        assertNull("the fixture stopped hanging - did runTask()'s when gain an else arm?", allFinished())
        return task
    }

    @Test fun `a destroyed service ends the sync`() {
        startHanging()
        controller.get().onDestroy()
        assertNotNull("a platform-killed service must report the sync ended", allFinished())
    }

    @Test fun `the close route does not also post from onDestroy`() {
        val probe = Probe()
        EventBus.getDefault().register(probe)
        try {
            startHanging()
            controller.get().onServiceCloseRequest(ServiceCloseRequest())
            controller.get().onDestroy()
            assertEquals("exactly one terminal event for one ending", 1, probe.count)
        } finally {
            EventBus.getDefault().unregister(probe)
        }
    }

    @Test fun `the close request ends the sync`() {
        val task = startHanging()
        controller.get().onServiceCloseRequest(ServiceCloseRequest())
        assertNotNull(allFinished())
        // Pins spec D-3's reorder: allCompleted() runs first and the cancel below it still reaches
        // the task. Fails at 0 if anyone drops or short-circuits taskRunning?.cancel() while
        // shuffling that block - which nothing else in this file would notice.
        assertEquals("the cancel must still run after allCompleted()", 1, task.cancelCount)
    }

    @Test fun `a normally completed task ends the sync`() {
        controller.get().onApiTask(ErrorReportTask())
        assertNotNull("the queue drained", allFinished())
        assertNotNull(
            "a task actually completed",
            EventBus.getDefault().getStickyEvent(ApiTaskFinishedEvent::class.java),
        )
        assertNull(
            "and it completed rather than erroring",
            EventBus.getDefault().getStickyEvent(ApiTaskErrorEvent::class.java),
        )
    }

    private companion object { const val HANGING = "hanging" }
}
