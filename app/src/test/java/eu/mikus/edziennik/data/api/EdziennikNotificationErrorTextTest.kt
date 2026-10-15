/*
 * Copyright (c) Mikolaj Olszewski 2026-10-15.
 */
package eu.mikus.edziennik.data.api

import android.app.Notification
import eu.mikus.edziennik.App
import eu.mikus.edziennik.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Pins that a non-critical error leaves a trace in the sync notification, and that it leaves the
 * rest of the notification alone.
 *
 * `addError()` used to be `errorCount++` and nothing else, and `post()` re-posts the builder
 * unchanged - so the first caller of ApiService's non-critical arm would have reported nothing at
 * all in the shade. The count was only ever rendered by `setCriticalError()` and `setCurrentTask()`,
 * i.e. from the NEXT task onwards - on a single-profile sync, the tail `SzkolnyTask`, under that
 * task's title rather than the failing one's. During the failing task itself it never appeared.
 *
 * Text and style only. Not the title and not the progress bar, unlike `setCriticalError()` - during
 * a non-critical error the sync is still running, the title is the current endpoint's progress text
 * and the bar is live.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EdziennikNotificationErrorTextTest {

    private val app get() = RuntimeEnvironment.getApplication() as App

    private fun textOf(n: EdziennikNotification): CharSequence? =
        n.notification.extras.getCharSequence(Notification.EXTRA_TEXT)

    private fun titleOf(n: EdziennikNotification): CharSequence? =
        n.notification.extras.getCharSequence(Notification.EXTRA_TITLE)

    private fun normalErrors(n: Int) = app.resources.getQuantityString(R.plurals.normal_errors_format, n, n)
    private fun criticalErrors(n: Int) = app.resources.getQuantityString(R.plurals.critical_errors_format, n, n)

    /**
     * Fixture self-check. `setCriticalError()` has always written the text, so if this reads null the
     * probe is wrong - EXTRA_TEXT is not where this NotificationCompat build puts it - and every
     * other assertion in this class would be vacuous.
     */
    @Test fun `the probe can see the text a critical error writes`() {
        val n = EdziennikNotification(app)
        n.setCriticalError()
        assertNotNull("EXTRA_TEXT must be where the content text lands", textOf(n))
        assertEquals(criticalErrors(1), textOf(n).toString())
    }

    @Test fun `a non-critical error writes its count into the notification text`() {
        val n = EdziennikNotification(app)
        n.setCurrentTask(7, "syncing")
        assertNull("no errors yet, so no text", textOf(n))

        n.addError()
        assertEquals(normalErrors(1), textOf(n).toString())

        val one = textOf(n).toString()
        n.addError()
        assertEquals(normalErrors(2), textOf(n).toString())
        assertNotEquals("the count must actually change, not just re-render", one, textOf(n).toString())
    }

    @Test fun `a non-critical error leaves the title and the progress bar alone`() {
        val n = EdziennikNotification(app)
        n.setCurrentTask(7, "syncing")
        n.setProgressText("reading grades")
        n.setProgress(42f)

        n.addError()

        assertEquals("the title is the running endpoint's, not an error title", "reading grades", titleOf(n).toString())
        assertEquals(
            "the bar must stay live - the sync is still running",
            42,
            n.notification.extras.getInt(Notification.EXTRA_PROGRESS),
        )
        assertEquals(normalErrors(1), textOf(n).toString())

        // The sync is still running: later progress updates must not wipe the count, and the
        // expanded shade must carry it too - EXTRA_TEXT alone would still pass with setStyle gone.
        n.setProgressText("next endpoint")
        n.setProgress(60f)

        assertEquals("the title keeps tracking the endpoint", "next endpoint", titleOf(n).toString())
        assertEquals(60, n.notification.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals("the count survives later progress updates", normalErrors(1), textOf(n).toString())
        assertEquals(
            "and the expanded shade carries it too",
            normalErrors(1),
            n.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString(),
        )
        assertEquals(
            "a non-critical error must not take away the Cancel button mid-sync",
            "TaskCancelRequest",
            shadowOf(n.notification.actions.last().actionIntent).savedIntent.extras?.getString("task"),
        )
    }

    /**
     * Review Focus 4 and 5. `errorCountText()`'s `", "` join had no production caller before this
     * phase: `errorCount` only becomes non-zero through `addError()`, whose sole caller is the
     * non-critical arm of `ApiService.onError`. A critical error arriving after a non-critical one
     * must retitle AND report both counts.
     */
    @Test fun `a critical error after a non-critical one reports both counts`() {
        val n = EdziennikNotification(app)
        n.setCurrentTask(7, "syncing")
        n.addError()
        n.setCriticalError()

        assertEquals("${criticalErrors(1)}, ${normalErrors(1)}", textOf(n).toString())
        assertEquals(
            app.getString(R.string.edziennik_notification_api_error_title),
            titleOf(n).toString(),
        )
        assertEquals(
            "a critical error clears the bar, unlike a non-critical one",
            0,
            n.notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX),
        )
    }
}
