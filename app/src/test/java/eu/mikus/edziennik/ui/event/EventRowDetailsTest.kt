/*
 * Copyright (c) Mikolaj Olszewski 2026-10-22.
 */
package eu.mikus.edziennik.ui.event

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Week
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The gate on [eventDetails], the renderer behind the Compose event row. It is not `@Composable`
 * and [androidx.compose.ui.text.AnnotatedString] needs no composition, so it is callable directly
 * — which is why the function is `internal` rather than `private`. It needs a real `Context` for
 * the string lookups, hence Robolectric (JUnit 4, via Vintage).
 *
 * The other three printers of this same line are NOT gated here and must not be described as
 * test-covered: `EventViewHolder.onBind` binds a `ViewBinding`, `EventDetailsDialog`'s header is
 * inside a composition (`createComposeRule` cannot be hosted here — see `ShellPolicyTest.kt:23-26`)
 * and `Notifications.eventNotifications` reads the DAO. All three are covered by the device smoke.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EventRowDetailsTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    /** 30 days out, so `getRelativeString(context, 7)` returns null and the short date renders. */
    private fun farDate() = Date.getToday().stepForward(0, 0, 30)

    private fun event(at: Date, unknown: Boolean): EventFull = mockk(relaxed = true) {
        every { date } returns at
        every { dateUnknown } returns unknown
        every { time } returns null
        every { typeName } returns null
        every { subjectLongName } returns SUBJECT
    }

    private fun render(
        event: EventFull,
        showWeekDay: Boolean = true,
        showDate: Boolean = true,
    ): String = eventDetails(
        event = event,
        query = "",
        highlight = Color.Yellow,
        showWeekDay = showWeekDay,
        showDate = showDate,
        showTime = true,
        showSubject = true,
        showType = false,
        context = context,
    ).text

    @Test
    fun `a known date renders the weekday and the date`() {
        val at = farDate()
        val text = render(event(at, unknown = false))

        assertEquals(
            listOf(
                Week.getFullDayName(at.weekDay),
                at.formattedStringShort,
                context.getString(R.string.event_all_day),
                SUBJECT,
            ).joinToString(BULLET),
            text,
        )
        assertFalse(text.contains(context.getString(R.string.event_date_unknown)))
    }

    @Test
    fun `an unknown date renders neither weekday nor date`() {
        // An unreadable date is filed at today, so the row's date IS today. A "contains no digits"
        // assertion would therefore be vacuous: getRelativeString(context, 7) answers a zero-day
        // diff with R.string.today ("dzisiaj"), never digits, so it would pass even against an
        // implementation that only ADDED the label ("data nieznana • dzisiaj"). Pin the whole line.
        val at = Date.getToday()
        val text = render(event(at, unknown = true))

        assertEquals(
            listOf(
                context.getString(R.string.event_date_unknown),
                context.getString(R.string.event_all_day),
                SUBJECT,
            ).joinToString(BULLET),
            text,
        )
        assertFalse(text.contains(Week.getFullDayName(at.weekDay)))
        assertFalse(text.contains(context.getString(R.string.today)))
    }

    @Test
    fun `showDate false still hides the unknown label`() {
        // showWeekDay is left at true: an unknown date suppresses the weekday on its own, so the
        // line is the time and the subject only.
        val text = render(event(Date.getToday(), unknown = true), showDate = false)

        assertFalse(text.contains(context.getString(R.string.event_date_unknown)))
        assertEquals(
            listOf(context.getString(R.string.event_all_day), SUBJECT).joinToString(BULLET),
            text,
        )
    }

    private companion object {
        const val SUBJECT = "Matematyka"
        const val BULLET = " • "
    }
}
