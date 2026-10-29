/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */

package eu.mikus.edziennik.data.api.task

import android.app.Application
import android.content.Context
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.utils.models.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The gate on [attendanceNotificationText], the short text of a new-attendance notification. It
 * needs a real `Context` for the string lookup, hence Robolectric (JUnit 4, via Vintage).
 *
 * Only the SHORT text is gated. `Notifications.attendanceNotifications()` reads its rows off
 * `app`'s DAO, which this class does not seed, so its call of [attendanceNotificationText] is not
 * covered, and neither is the LONG text: the date for `notification_attendance_long_format` is
 * built inline there, as the event path builds its own, and is not a function this class can call.
 * Reverting the long text to format `attendance.date` itself, or the short text to format it
 * itself, leaves this class green. The emulator is the only evidence for those.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AttendanceNotificationTest {

    private val context: Context
        get() = RuntimeEnvironment.getApplication()

    private val known = Date(2026, 3, 10)

    // What the Librus parser files an undated row under. A date that would read as a real day.
    private val filing = Date(2019, 1, 1)

    private fun attendance(date: Date, dateUnknown: Boolean, subject: String?) = AttendanceFull(
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
    ).also {
        it.dateUnknown = dateUnknown
        it.subjectLongName = subject
    }

    @Test fun `a dated attendance with a subject reads as the dated format`() {
        assertEquals(
            context.getString(
                R.string.notification_attendance_format,
                "Nieobecność",
                "Matematyka",
                known.formattedString,
            ),
            attendanceNotificationText(
                "Nieobecność",
                attendance(known, dateUnknown = false, subject = "Matematyka"),
                context,
            ),
        )
    }

    @Test fun `a dated attendance with no subject reads as the dated no-lesson format`() {
        // The format reads %1$s and %3$s and skips %2$s, so it is still handed three arguments.
        assertEquals(
            context.getString(
                R.string.notification_attendance_no_lesson_format,
                "Nieobecność",
                null,
                known.formattedString,
            ),
            attendanceNotificationText(
                "Nieobecność",
                attendance(known, dateUnknown = false, subject = null),
                context,
            ),
        )
    }

    // An empty subject is a no-subject row, not a row whose subject happens to be "". Without these
    // two, weakening either `isNullOrEmpty()` to `== null` keeps every other test in this class
    // green, and an "" row renders through the with-subject format with a blank where a name goes.
    @Test fun `a dated attendance whose subject is empty reads as the dated no-lesson format`() {
        assertEquals(
            context.getString(
                R.string.notification_attendance_no_lesson_format,
                "Nieobecność",
                "",
                known.formattedString,
            ),
            attendanceNotificationText(
                "Nieobecność",
                attendance(known, dateUnknown = false, subject = ""),
                context,
            ),
        )
    }

    @Test fun `an undated attendance whose subject is empty says the date is unknown`() {
        assertEquals(
            context.getString(
                R.string.notification_attendance_unknown_date_no_lesson_format,
                "Nieobecność",
            ),
            attendanceNotificationText(
                "Nieobecność",
                attendance(filing, dateUnknown = true, subject = ""),
                context,
            ),
        )
    }

    @Test fun `an undated attendance with a subject says the date is unknown`() {
        assertEquals(
            context.getString(
                R.string.notification_attendance_unknown_date_format,
                "Nieobecność",
                "Matematyka",
            ),
            attendanceNotificationText(
                "Nieobecność",
                attendance(filing, dateUnknown = true, subject = "Matematyka"),
                context,
            ),
        )
    }

    @Test fun `an undated attendance with no subject says the date is unknown`() {
        assertEquals(
            context.getString(
                R.string.notification_attendance_unknown_date_no_lesson_format,
                "Nieobecność",
            ),
            attendanceNotificationText(
                "Nieobecność",
                attendance(filing, dateUnknown = true, subject = null),
                context,
            ),
        )
    }

    @Test fun `an undated attendance notification does not carry its filing date`() {
        val filingText = filing.formattedString
        for (subject in listOf("Matematyka", null, "")) {
            val text = attendanceNotificationText(
                "Nieobecność",
                attendance(filing, dateUnknown = true, subject = subject),
                context,
            )
            assertFalse(
                "\"$text\" contains the filing date \"$filingText\"",
                text.contains(filingText),
            )
        }
    }
}
