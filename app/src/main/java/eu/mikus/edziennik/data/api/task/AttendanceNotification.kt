/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */

package eu.mikus.edziennik.data.api.task

import android.content.Context
import eu.mikus.edziennik.R
import eu.mikus.edziennik.data.db.entity.Attendance
import eu.mikus.edziennik.data.db.full.AttendanceFull

/**
 * The short text of a new-attendance notification.
 *
 * Four formats, one per combination of [Attendance.dateUnknown] and whether the attendance has a
 * `subjectLongName`. Arguments each takes, where the date is `Date.formattedString`:
 *  - dated, with a subject (`notification_attendance_format`): [typeName], subject, date
 *  - dated, no subject (`notification_attendance_no_lesson_format`): [typeName], subject, date. The
 *    format reads `%1$s` and `%3$s` and skips `%2$s`, so it is still handed all three.
 *  - undated, with a subject (`notification_attendance_unknown_date_format`): [typeName], subject
 *  - undated, no subject (`notification_attendance_unknown_date_no_lesson_format`): [typeName]
 *
 * The unknown date has formats of its own rather than a label in the date slot: the Polish dated
 * formats put the date after `z dnia`, a case-governed slot, and "data nieznana" is nominative. So
 * the undated formats have no date slot and say the date is unknown after an em dash, as the
 * unknown-date event and notice notification formats do. Their placeholder indices are contiguous,
 * so the two undated branches below must not pass them a date.
 *
 * Like [noticeNotificationText], and unlike [eventNotificationFormat], this returns the resolved
 * text, so a test pins the text the arguments produce and not only the format. An argument a format
 * does not read is never rendered, so passing an extra one would not change the text and no
 * assertion on the rendered text can tell — only a mock `Context` recording the vararg could.
 *
 * Only the short text is built here. `Notifications.attendanceNotifications()` builds the date for
 * `notification_attendance_long_format` inline, as the event path does: that format puts the date
 * after a colon ("Termin: …"), a nominative slot where the unknown label fits.
 */
internal fun attendanceNotificationText(
    typeName: String,
    attendance: AttendanceFull,
    context: Context,
): String = when {
    attendance.dateUnknown && attendance.subjectLongName.isNullOrEmpty() -> context.getString(
        R.string.notification_attendance_unknown_date_no_lesson_format,
        typeName,
    )
    attendance.dateUnknown -> context.getString(
        R.string.notification_attendance_unknown_date_format,
        typeName,
        attendance.subjectLongName,
    )
    attendance.subjectLongName.isNullOrEmpty() -> context.getString(
        R.string.notification_attendance_no_lesson_format,
        typeName,
        attendance.subjectLongName,
        attendance.date.formattedString,
    )
    else -> context.getString(
        R.string.notification_attendance_format,
        typeName,
        attendance.subjectLongName,
        attendance.date.formattedString,
    )
}
