/*
 * Copyright (c) Mikolaj Olszewski 2026-9-21.
 */
package eu.mikus.edziennik.data.api

import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.events.ApiTaskAllFinishedEvent
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.events.ApiTaskFinishedEvent
import eu.mikus.edziennik.data.api.events.requests.ServiceCloseRequest
import eu.mikus.edziennik.data.api.events.requests.TaskCancelRequest
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
 * 3. **This class boots the real [App]**, as does [EdziennikNotificationActionsTest]; the repo's
 *    six other Robolectric classes all pin `application = Application::class`. It has to:
 *    `ApiService.app` is
 *    `applicationContext as App`, dereferenced through the `notification` lazy in `onCreate`, so a
 *    stock `Application` throws `ClassCastException`. Do not "fix" this to match the others.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApiServiceTerminalEventTest {

    /** A fourth [IApiTask] subtype: matches no arm of `runTask()`'s `when`, so it never calls back. */
    private class HangingTask : IApiTask(0) {
        var cancelCount = 0
        var throwOnCancel = false
        override fun prepare(app: App) { taskName = HANGING }
        override fun cancel() {
            cancelCount++
            if (throwOnCancel) throw IllegalStateException("simulated saveData() failure")
        }
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
     *  `runTask():187`, before the `when`, so a non-null taskName proves the task really reached the
     *  dispatch point. Do NOT assert on a sticky `ApiTaskStartedEvent` - it is posted with `post`,
     *  not `postSticky`. */
    private fun startHanging(): HangingTask {
        val task = HangingTask()
        controller.get().onApiTask(task)
        assertEquals("the task never reached runTask()", HANGING, task.taskName)
        assertNull("the fixture stopped hanging - did runTask()'s when gain an else arm?", allFinished())
        return task
    }

    /**
     * Reproduces the defect: a service the platform stops told nobody. Red at `d8db7649`.
     *
     * Also the only guard on the fix's line placement. `onDestroy` sets `serviceClosed` itself, so
     * a post written BELOW that assignment can never fire - this test is red for that too, not just
     * for a missing post.
     */
    @Test fun `a destroyed service ends the sync`() {
        startHanging()
        controller.get().onDestroy()
        assertNotNull("a platform-killed service must report the sync ended", allFinished())
    }

    /**
     * Deliberately not named "no second terminal event": it covers the `stopSelf()` -> `onDestroy`
     * path only. It CANNOT see the late-callback route, where a cancelled task's HTTP call returns
     * after the service stopped and posts again - [HangingTask] never calls back. That route is
     * known, accepted and harmless across all five subscribers; it is simply not tested here.
     */
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

    /**
     * The positive control, and the only test here that does real DB work: draining the queue makes
     * `runTask()` synthesise a real `SzkolnyTask`, which sweeps nine DAOs and posts notifications.
     * Inert on the empty test database, and it terminates.
     *
     * The two extra assertions are load-bearing. `allCompleted()` is reached by the error path too,
     * so a bare "a terminal event exists" would pass whether or not the task actually completed.
     */
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

    @Test fun `one cancel tap ends the sync`() {
        startHanging()
        controller.get().onTaskCancelRequest(TaskCancelRequest(1))
        assertNotNull("one tap is an instruction, not a question", allFinished())
    }

    @Test fun `a cancel reaches the running task`() {
        val task = startHanging()
        controller.get().onTaskCancelRequest(TaskCancelRequest(1))
        // Fails at 0 if clearTask() is ever ordered before taskRunning?.cancel(): clearTask() nulls
        // taskRunning, so the cancel would silently never reach the task while the service stopped.
        assertEquals(1, task.cancelCount)
    }

    /**
     * Cancelling aborts the remaining profiles, but must still run the queue's tail task.
     *
     * `SzkolnyTask` is the only caller of `setAllNotEmpty()`, which clears `profiles.empty` - and
     * ~14 endpoints stamp Metadata's seen/notified columns from that flag. A cancel that skipped the
     * tail would leave it set, so the NEXT sync would write every row as already-seen and
     * already-notified: no notifications, no unread badges, silently.
     *
     * Observed through the sticky `ApiTaskFinishedEvent`, which only a task that actually completed
     * posts. [HangingTask] never calls back, so this event can only have come from the tail.
     *
     * Replacing `runTask()` with `allCompleted()` in the handler's `finally` fails this.
     */
    @Test fun `a cancel still runs the queue tail`() {
        startHanging()
        controller.get().onTaskCancelRequest(TaskCancelRequest(1))
        assertNotNull(
            "the tail task must still run, or profiles.empty is never cleared",
            EventBus.getDefault().getStickyEvent(ApiTaskFinishedEvent::class.java),
        )
        assertNotNull("and the sync must still end", allFinished())
    }

    /**
     * Pins the reason both terminal handlers use try/finally rather than a plain statement order.
     *
     * `IApiTask.cancel()` reaches `Data.saveData()`, a multi-DAO flush. Both handlers are ASYNC
     * subscribers, and EventBus's `throwSubscriberException` defaults to false - so in production a
     * throw in that flush is swallowed silently. Without the `finally`, `allCompleted()` would be
     * skipped, nothing would be posted, and `onDestroy`'s guard would suppress the retry too,
     * because the handler has already set `serviceClosed`. The sync would hang exactly as it did
     * before this phase.
     *
     * Replacing the `finally` block with a plain call after the `try` fails this test.
     */
    @Test fun `a throwing cancel still ends the sync`() {
        val task = startHanging()
        task.throwOnCancel = true
        // Driven directly, so the throw reaches us rather than EventBus. Production swallows it.
        runCatching { controller.get().onTaskCancelRequest(TaskCancelRequest(1)) }
        assertEquals("the cancel must have been attempted", 1, task.cancelCount)
        assertNotNull("the flush threw, but the sync must still be reported ended", allFinished())
    }

    private companion object { const val HANGING = "hanging" }
}
