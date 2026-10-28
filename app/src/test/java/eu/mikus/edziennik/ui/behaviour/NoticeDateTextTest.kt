/*
 * Copyright (c) Mikolaj Olszewski 2026-10-28.
 */
package eu.mikus.edziennik.ui.behaviour

import android.app.Application
import android.content.Context
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Notice
import eu.mikus.edziennik.utils.models.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The gate on [noticeDateText], the date text of a notice row. It needs a real `Context` for the
 * string lookup, hence Robolectric (JUnit 4, via Vintage).
 *
 * Its two callers are NOT gated here and must not be described as test-covered: BehaviourScreen's
 * notice row is inside a composition (`createComposeRule` cannot be hosted here), and
 * `NoticesAdapter.onBindViewHolder` casts `context.applicationContext` to `App` and reads
 * `app.data.uiConfig`, so it cannot be bound under the stock `Application` this class runs with.
 * Reverting either one to format `addedDate` itself leaves this class green.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NoticeDateTextTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private val d = Date(2026, 3, 10)

    private fun notice(addedDate: Long) =
        Notice(1, 1L, Notice.TYPE_NEUTRAL, 1, "t", null, null, 7L, addedDate)

    @Test fun `an unknown added date reads as unknown`() {
        assertEquals(
            context.getString(R.string.notice_date_unknown),
            noticeDateText(notice(Notice.ADDED_DATE_UNKNOWN), context),
        )
    }

    @Test fun `a known added date reads as the date`() {
        assertEquals(
            Date.fromMillis(d.inMillis).formattedString,
            noticeDateText(notice(d.inMillis), context),
        )
    }

    @Test fun `the unknown label is not the 1970 date`() {
        // A literal 0L, not the constant: this fails whenever a stored 0 renders as
        // Date.fromMillis(0L).formattedString, whatever the constant holds.
        assertNotEquals(Date.fromMillis(0L).formattedString, noticeDateText(notice(0L), context))
    }
}
