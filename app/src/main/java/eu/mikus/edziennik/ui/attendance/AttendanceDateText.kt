/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */

package eu.mikus.edziennik.ui.attendance

import android.content.Context
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.utils.models.Week

/**
 * The date of an attendance as one string, for `AttendanceDetailsDialog`: the unknown label when
 * [Attendance.dateUnknown], else [Attendance.date] as `Date.formattedString`.
 *
 * Takes the attendance rather than its date so that whether the date is known stays the entity's
 * answer. When it is not, [Attendance.date] is a filing date the parser chose, and formatting it
 * would show an invented day.
 *
 * Pass it a row read from the DAO. The display-only synthetics built in `TypeViewHolder.kt` and
 * `MonthViewHolder.kt` (`date = Date(0, 0, 0)`, never persisted) are not rows, carry no flag, and
 * are not meant to reach it. That contract is prose only, and it need not be: the parameter COULD
 * be narrowed to `AttendanceFull`, because all three production callers already hold one - the
 * dialog's own field, the view holder's `item`, and `AttendanceLeaf.attendance`. Narrowing would
 * exclude both synthetics at compile time, since they are base `Attendance` values. It is left
 * wide only because nothing has required it yet; the only base-typed caller is this file's test.
 *
 * A `false` flag means only that the school gave a date that parsed, not that the date is
 * plausible: `Date.fromY_m_dOrNull` reads fixed character positions, so `"0000-00-00"` is stored
 * as a known `Date(0, 0, 0)`.
 */
internal fun attendanceDateText(attendance: Attendance, context: Context): String = when {
    attendance.dateUnknown -> context.getString(R.string.attendance_date_unknown)
    else -> attendance.date.formattedString
}

/**
 * The leading parts of an attendance's date line, for `AttendanceScreen`'s leaf row and
 * `AttendanceViewHolder`: the unknown label alone when [Attendance.dateUnknown], else the weekday
 * and the short date. The caller appends the start time and lesson number, which are sourced
 * independently of the date and stay real.
 *
 * One part and not two because a weekday computed from the filing date would be as invented as the
 * date. The same caller contract as [attendanceDateText] applies: a row from the DAO, never a
 * display-only synthetic, and a `false` flag does not make the date plausible.
 */
internal fun attendanceDayDateParts(attendance: Attendance, context: Context): List<String> = when {
    attendance.dateUnknown -> listOf(context.getString(R.string.attendance_date_unknown))
    else -> listOfNotNull(
        Week.getFullDayName(attendance.date.weekDay),
        attendance.date.formattedStringShort,
    )
}
