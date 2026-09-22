/*
 * Copyright (c) Mikolaj Olszewski 2026-9-22.
 */
package eu.mikus.edziennik.data.api.task

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.ERROR_SERVICE_START_REFUSED
import eu.mikus.edziennik.data.api.events.ApiTaskErrorEvent
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Pins that a refused foreground-service start is reported rather than fatal.
 *
 * Android 12+ refuses `startForegroundService` from a background process. Measured on an API 35
 * emulator 2026-09-22 by forcing the app's own scheduled job while the process was cached: the
 * platform answered `Background started FGS: Disallowed ... code:DENIED` and the throw escaped
 * `SyncWorker.doWork`, which killed the hourly reschedule chain outright.
 *
 * Both halves of the handling matter and each has its own test. Rethrowing kills the process, and
 * `CustomActivityOnCrash` cannot show its dialog from the background, so the user would see nothing
 * at all. Swallowing is no better: `syncFeature` calls `markRefreshing()` five lines before the
 * enqueue and `SyncStatus` clears `isRefreshing` only on `AllFinished` or `Error`, so a silent catch
 * would leave a spinner nothing can stop -- the defect class Phase 41 exists to close.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IApiTaskEnqueueTest {

    /** Stands in for a backgrounded process: the platform's refusal is an
     *  `IllegalStateException` subclass, which is what the production catch names. */
    private class RefusingContext(base: Context) : ContextWrapper(base) {
        override fun startForegroundService(service: Intent): ComponentName? =
            throw IllegalStateException(
                "startForegroundService() not allowed due to mAllowStartForeground false"
            )
    }

    private class FakeTask : IApiTask(0) {
        override fun prepare(app: App) {}
        override fun cancel() {}
    }

    private val bus get() = EventBus.getDefault()

    @Before fun setUp() = bus.removeAllStickyEvents()
    @After fun tearDown() = bus.removeAllStickyEvents()

    @Test fun `a refused service start is reported rather than thrown`() {
        FakeTask().enqueue(RefusingContext(RuntimeEnvironment.getApplication()))

        val event = bus.getStickyEvent(ApiTaskErrorEvent::class.java)
        assertNotNull("the refusal must reach the UI, not kill the process", event)
        assertEquals(ERROR_SERVICE_START_REFUSED, event.error.errorCode)
    }

    /**
     * `ApiService.onApiTask` is a sticky subscriber, so a task left on the bus after a failed start
     * would fire against whichever service starts next -- a sync the user never asked for, at a
     * moment nothing is expecting one.
     */
    @Test fun `a refused service start does not leave the task on the bus`() {
        FakeTask().enqueue(RefusingContext(RuntimeEnvironment.getApplication()))

        assertNull(bus.getStickyEvent(FakeTask::class.java))
    }

    /** The success path is unchanged: the task is posted and nothing is reported. */
    @Test fun `an accepted service start still posts the task`() {
        FakeTask().enqueue(RuntimeEnvironment.getApplication())

        assertNotNull(bus.getStickyEvent(FakeTask::class.java))
        assertNull(bus.getStickyEvent(ApiTaskErrorEvent::class.java))
    }
}
