/*
 * Copyright (c) Mikolaj Olszewski 2026-9-22.
 */
package eu.mikus.edziennik.data.api

import android.app.NotificationManager
import android.content.Context
import eu.mikus.edziennik.App
import eu.mikus.edziennik.data.api.task.IApiTask
import org.greenrobot.eventbus.EventBus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Pins that a service which was bound but never started posts no notification.
 *
 * `EdziennikNotification.post()` is a plain `NotificationManager.notify`, independent of
 * `startForeground`, and nothing in the tree ever cancels the sync notification — today it vanishes
 * only because `startForeground` ties it to the service lifecycle. A worker that binds without
 * starting would therefore strand an ongoing notification for good, carrying a Close button that
 * routes to a background `startService` the platform refuses.
 *
 * The foreground path is unaffected: `startForeground` is what displays the notification there, and
 * the `post()` calls only update it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApiServiceNotificationGateTest {

    private class HangingTask : IApiTask(0) {
        override fun prepare(app: App) { taskName = "hanging" }
        override fun cancel() {}
    }

    private val app get() = RuntimeEnvironment.getApplication() as App
    private val notifications
        get() = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

    private lateinit var controller: ServiceController<ApiService>

    @Before fun setUp() {
        EventBus.getDefault().removeAllStickyEvents()
        ApiService.lastEventTime = System.currentTimeMillis()
        controller = Robolectric.buildService(ApiService::class.java).create()
    }

    @After fun tearDown() {
        controller.destroy()
        EventBus.getDefault().removeAllStickyEvents()
    }

    @Test fun `a service that was never started posts no notification`() {
        controller.get().onApiTask(HangingTask())

        assertEquals(0, notifications.allNotifications.size)
    }

    /**
     * Asserts on CONTENT, not count. Robolectric's `startForeground` posts the notification itself
     * under the same id, so a count assertion passes even with `post()` stubbed to `return` — it
     * would be decorative. Only `setCurrentTask(...).post()` puts the running task's name on it.
     */
    @Test fun `a started service posts the running task's notification`() {
        controller.startCommand(0, 0)
        controller.get().onApiTask(HangingTask())

        val posted = notifications.getNotification(app.notificationChannelsManager.sync.id)
        assertEquals("hanging", posted?.extras?.getString(android.app.Notification.EXTRA_TITLE))
    }
}
