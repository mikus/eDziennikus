/*
 * Copyright (c) Mikolaj Olszewski 2026-10-28.
 */

package eu.mikus.edziennik.data.api.task

import android.app.Application
import android.content.Context
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Notice
import eu.mikus.edziennik.data.db.full.NoticeFull
import eu.mikus.edziennik.utils.models.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The gate on [noticeNotificationText] and [noticeNotificationTime], the text and the time of a
 * new-notice notification. The text needs a real `Context` for the string lookup, hence Robolectric
 * (JUnit 4, via Vintage).
 *
 * Their caller is NOT gated here and must not be described as test-covered:
 * `Notifications.behaviourNotifications()` reads its notices off `app`'s DAO, which this class does
 * not seed. Reverting it to format `addedDate` itself and to copy `addedDate` as the notification's
 * time leaves this class green.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NoticeNotificationTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private val d = Date(2026, 3, 10)
    private val now = 1_700_000_000_000L

    private fun notice(addedDate: Long) =
        NoticeFull(1, 1L, Notice.TYPE_NEGATIVE, 1, "t", null, null, 7L, addedDate)
            .also { it.teacherName = "Jan Kowalski" }

    @Test fun `a dated notice notification names its date`() {
        assertEquals(
            context.getString(
                R.string.notification_notice_format,
                "Uwaga",
                "Jan Kowalski",
                Date.fromMillis(d.inMillis).formattedString,
            ),
            noticeNotificationText("Uwaga", notice(d.inMillis), context),
        )
    }

    @Test fun `an undated notice notification says the date is unknown`() {
        assertEquals(
            context.getString(
                R.string.notification_notice_unknown_date_format,
                "Uwaga",
                "Jan Kowalski",
            ),
            noticeNotificationText("Uwaga", notice(Notice.ADDED_DATE_UNKNOWN), context),
        )
    }

    @Test fun `an undated notice notification has no 1970 in it`() {
        val epochDate = Date.fromMillis(0L).formattedString
        val text = noticeNotificationText("Uwaga", notice(Notice.ADDED_DATE_UNKNOWN), context)
        assertFalse("\"$text\" contains the epoch date \"$epochDate\"", text.contains(epochDate))
    }

    @Test fun `an undated notice notification is timed now`() {
        assertEquals(now, noticeNotificationTime(notice(Notice.ADDED_DATE_UNKNOWN), now))
    }

    @Test fun `a dated notice notification keeps the notice's time`() {
        assertEquals(d.inMillis, noticeNotificationTime(notice(d.inMillis), now))
    }
}
