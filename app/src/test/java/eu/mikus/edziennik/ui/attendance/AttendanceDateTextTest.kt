/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */
package eu.mikus.edziennik.ui.attendance

import android.app.Application
import android.content.Context
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Week
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The gate on [attendanceDateText] and [attendanceDayDateParts], the date text of an attendance
 * row. They need a real `Context` for the string lookup, hence Robolectric (JUnit 4, via Vintage).
 *
 * The five places that call them are NOT gated here and must not be described as test-covered:
 * `AttendanceScreen`'s leaf row, Days header and Months header, and `AttendanceDetailsDialog` are
 * all inside a composition (`createComposeRule` cannot be hosted here), and
 * `AttendanceViewHolder.onBind` takes an `App` and reads `app.attendanceManager`, so it cannot be
 * bound under the stock `Application` this class runs with. Reverting any one of them to format
 * `attendance.date` itself leaves this class green; the Days and Months headers do not even call
 * these helpers, they branch on `DayRangeHeader.dateUnknown` / `MonthHeader.dateUnknown`
 * directly. The emulator is the only evidence for those.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AttendanceDateTextTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private val known = Date(2026, 3, 10)

    // What the Librus parser files an undated row under. A date that would read as a real day.
    private val filing = Date(2019, 1, 1)

    private fun attendance(date: Date, dateUnknown: Boolean) = Attendance(
        profileId = 1,
        id = 1L,
        baseType = Attendance.TYPE_ABSENT,
        typeName = "nieobecność",
        typeShort = "nb",
        typeSymbol = "nb",
        typeColor = null,
        date = date,
        startTime = null,
        semester = 1,
        teacherId = 0L,
        subjectId = 0L,
        addedDate = 0L,
    ).also { it.dateUnknown = dateUnknown }

    @Test fun `a known date reads as a formatted date`() {
        assertEquals(
            known.formattedString,
            attendanceDateText(attendance(known, dateUnknown = false), context),
        )
    }

    @Test fun `an unknown date reads as the unknown label`() {
        val text = attendanceDateText(attendance(filing, dateUnknown = true), context)

        assertEquals(context.getString(R.string.attendance_date_unknown), text)
        // The filing date is a real-looking day; the label must not be it.
        assertNotEquals(filing.formattedString, text)
    }

    @Test fun `an unknown date contributes one part, not a weekday and a date`() {
        assertEquals(
            listOf(context.getString(R.string.attendance_date_unknown)),
            attendanceDayDateParts(attendance(filing, dateUnknown = true), context),
        )
    }

    @Test fun `a known date contributes the weekday and the short date`() {
        assertEquals(
            listOf(Week.getFullDayName(known.weekDay), known.formattedStringShort),
            attendanceDayDateParts(attendance(known, dateUnknown = false), context),
        )
    }
}
