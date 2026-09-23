/*
 * Copyright (c) Mikolaj Olszewski 2026-9-22.
 */
package eu.mikus.edziennik.data.api

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.edziennik.EdziennikTask
import eu.mikus.edziennik.data.api.events.ApiTaskAllFinishedEvent
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import eu.mikus.edziennik.data.api.events.requests.TaskCancelRequest
import eu.mikus.edziennik.data.api.models.ApiError
import eu.mikus.edziennik.data.api.task.IApiTask
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.concurrent.thread

/**
 * Pins the bind-and-await protocol, which has four wrong defaults.
 *
 * Robolectric will not run `ApiService`'s real lifecycle in response to `bindService`, so "the
 * service was created and actually synced" stays device-only evidence. What IS pinned here is
 * everything that decides whether the protocol is correct.
 *
 * **Do not assert through `shadowOf(app)`.** Verified against shadows-framework-4.14.1:
 * `unbindService` removes the connection from `boundServiceConnections`, so it is empty by the time
 * a test reads it; and `bindService` appends to the *same* list `getNextStartedService()` pops from,
 * so bind-versus-start — the fact this whole fix rests on — is not observable there at all. Hence
 * the recording [ProbeContext], in the style of `IApiTaskEnqueueTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApiServiceBindAndAwaitTest {

    /** Records which of bind/start was used, and can refuse the bind. */
    class ProbeContext(base: Context, private val allowBind: Boolean = true) : ContextWrapper(base) {
        var bindCount = 0
        var unbindCount = 0
        var startCount = 0
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            bindCount++
            return allowBind
        }
        override fun unbindService(conn: ServiceConnection) { unbindCount++ }
        override fun startService(service: Intent?): ComponentName? { startCount++; return null }
        override fun startForegroundService(service: Intent?): ComponentName? { startCount++; return null }
    }

    /** Must be a named, public class: EventBus invokes subscribers reflectively, and an anonymous
     *  `object` compiles to a package-private class that fails with IllegalAccessException wrapped
     *  in `IllegalStateException: Unexpected exception` - which reads as a broken service. */
    class CancelProbe {
        val seen = mutableListOf<TaskCancelRequest>()
        @Subscribe fun onCancel(request: TaskCancelRequest) { seen += request }
    }

    private val app get() = RuntimeEnvironment.getApplication() as App
    private lateinit var ctx: ProbeContext

    @Before fun setUp() {
        EventBus.getDefault().removeAllStickyEvents()
        ctx = ProbeContext(app)
    }
    @After fun tearDown() = EventBus.getDefault().removeAllStickyEvents()

    private fun await(timeoutMs: Long, graceMs: Long = 50, task: IApiTask = EdziennikTask.sync()) =
        ApiService.bindAndAwait(ctx, task, timeoutMs, graceMs)

    /** The premise of the entire fix: bind, never start. A start is what the platform refuses. */
    @Test fun `it binds and never starts a service`() {
        await(timeoutMs = 150)

        assertTrue("must bind", ctx.bindCount == 1)
        assertTrue("must NOT start a service", ctx.startCount == 0)
    }

    @Test fun `it unbinds even when the sync never ends`() {
        await(timeoutMs = 150)

        assertTrue(ctx.unbindCount == 1)
    }

    /** Android requires the connection to be released even when bindService returns false. */
    @Test fun `it unbinds even when the bind was refused`() {
        val refusing = ProbeContext(app, allowBind = false)

        val finished = ApiService.bindAndAwait(refusing, EdziennikTask.sync(), 150, 50)

        assertFalse(finished)
        assertTrue("a refused bind must still be released", refusing.unbindCount == 1)
    }

    @Test fun `the await releases when the sync reports it is over`() {
        // Started before the call, as the error test below does: bindAndAwait blocks this thread, so
        // anything posted after it could never run.
        thread { Thread.sleep(40); EventBus.getDefault().postSticky(ApiTaskAllFinishedEvent()) }

        val finished = ApiService.bindAndAwait(ctx, EdziennikTask.sync(), 5_000, 50)

        assertTrue(finished)
    }

    @Test fun `an error ends a task, not the sync, so the await keeps waiting`() {
        thread { Thread.sleep(40); EventBus.getDefault().postSticky(ApiTaskErrorEvent(ApiError("t", 1))) }

        val finished = await(timeoutMs = 400)

        assertFalse("releasing here would skip the tail task", finished)
    }

    @Test fun `a stale terminal event from a previous run cannot satisfy the await`() {
        EventBus.getDefault().postSticky(ApiTaskAllFinishedEvent())

        val finished = await(timeoutMs = 300)

        assertFalse("the previous run's sticky must not count", finished)
    }

    @Test fun `it clears the stale terminal sticky before posting the task`() {
        EventBus.getDefault().postSticky(ApiTaskAllFinishedEvent())

        await(timeoutMs = 150)

        assertNull(EventBus.getDefault().getStickyEvent(ApiTaskAllFinishedEvent::class.java))
    }

    /** A task left on the sticky bus fires against whichever service starts next. */
    @Test fun `it leaves no task on the bus when the sync never ends`() {
        await(timeoutMs = 150)

        assertNull(EventBus.getDefault().getStickyEvent(EdziennikTask::class.java))
    }

    /** On timeout the abandoned task is still writing; Data.saveData() is a multi-DAO flush. */
    @Test fun `a timeout asks the service to end the sync`() {
        val probe = CancelProbe()
        EventBus.getDefault().register(probe)
        try {
            await(timeoutMs = 150)
            assertTrue("a timed-out sync must be told to stop", probe.seen.isNotEmpty())
        } finally {
            EventBus.getDefault().unregister(probe)
        }
    }

    /** ...and must not leave that request on the bus to abort the NEXT sync. */
    @Test fun `it leaves no cancel request on the bus`() {
        await(timeoutMs = 150)

        assertNull(EventBus.getDefault().getStickyEvent(TaskCancelRequest::class.java))
    }

    @Test fun `the await gives up rather than hanging`() {
        val started = System.currentTimeMillis()

        assertFalse(await(timeoutMs = 250))

        assertTrue(System.currentTimeMillis() - started < 5_000)
    }
}
