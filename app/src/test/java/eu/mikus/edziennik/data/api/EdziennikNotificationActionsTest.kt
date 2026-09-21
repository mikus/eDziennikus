/*
 * Copyright (c) Mikolaj Olszewski 2026-9-21.
 */
package eu.mikus.edziennik.data.api

import eu.mikus.edziennik.App
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins that each of the sync notification's actions delivers the request its label promises.
 *
 * `PendingIntent` matching is (package, factory kind, requestCode, `Intent.filterEquals`) and
 * **extras are excluded**, so two `getBroadcast(app, 0, …)` calls on explicit `SzkolnyReceiver`
 * intents are one record. Only the cancel site passed `FLAG_UPDATE_CURRENT`, so it stamped its
 * extras on the shared token at every task start and the Close button then re-attached that same
 * token - for the life of the install, since nothing ever calls `PendingIntent.cancel()`.
 *
 * Boots the real [App] for the same reason [ApiServiceTerminalEventTest] does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EdziennikNotificationActionsTest {

    private fun taskOf(n: EdziennikNotification): String? {
        val action = n.notification.actions?.lastOrNull() ?: return null
        return shadowOf(action.actionIntent).savedIntent.extras?.getString("task")
    }

    @Test fun `the button labelled Close delivers a close request after a task has run`() {
        val app = RuntimeEnvironment.getApplication() as App
        val n = EdziennikNotification(app)

        // onCreate's order.
        n.setIdle().setCloseAction()
        assertEquals("ServiceCloseRequest", taskOf(n))

        // runTask() stamps the cancel extras on the shared request code.
        n.setCurrentTask(7, "syncing").post()
        assertEquals("TaskCancelRequest", taskOf(n))

        // An idle or errored sync then re-attaches the Close label.
        n.setIdle().setCloseAction()
        assertEquals("the Close button must not deliver a cancel", "ServiceCloseRequest", taskOf(n))
    }
}
