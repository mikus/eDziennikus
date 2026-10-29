/*
 * Copyright (c) Mikolaj Olszewski 2026-10-29.
 */

package eu.mikus.edziennik.data.api.edziennik.librus.data.api

import eu.mikus.edziennik.utils.models.Date

/**
 * The date a Librus attendance is filed under, and whether the school actually gave it.
 *
 * When [unknown] is true, [date] is a filing date chosen by the parser, not the school's date.
 * The parser copies [unknown] onto `Attendance.dateUnknown`.
 */
internal data class LibrusAttendanceDate(val date: Date, val unknown: Boolean)

/**
 * Reads an attendance's date, or files it at 2019-01-01 and says so when the school's date is
 * unreadable.
 *
 * Unreadable means null, empty, truncated or malformed. The parser used to read through
 * `Date.fromY_m_d`, which answers 2019-01-01 for all of those, so an unreadable date was stored
 * as a confident 2019-01-01, a date a school can really send. The two are now told apart by
 * [LibrusAttendanceDate.unknown], never by the value.
 *
 * The filing date is 2019-01-01 and not today, deliberately the opposite of `librusEventDate`.
 * Do not "fix" the literal to today for consistency with it. `AttendanceDao.getAllByDateNow` is
 * an exact date match, and it is what `TimetableViewModel`'s `attendanceFetch` calls for each day
 * the Timetable draws. An attendance filed at today would be fetched for today and drawn on
 * today's timetable as a real absence, re-filed to the new day on every sync, and, with no start
 * time, it would feed the `null == null` start-time matcher in `TimetableDayBuilder` (backlog
 * item 65) and bind to every cancelled lesson of the day. 2019-01-01 matches no school day, so
 * the row stays off every timetable, and `ORDER BY attendanceDate DESC` already lists it last.
 * It is also the value the parser stored before, so no stored row changes meaning.
 *
 * None of `librusEventDate`'s reasons for today apply to attendance. An event's date is a
 * position in time that its `seen` rule and its Current/Past tab compare with today; attendance
 * has no `seen = date < today` rule and no Current/Past tab.
 *
 * Readable is not the same as valid: `Date.fromY_m_dOrNull` reads fixed positions and checks
 * neither the separators nor the ranges, so `"2019-99-99"` is read as a date and is not flagged.
 */
internal fun librusAttendanceDate(raw: String?): LibrusAttendanceDate =
    Date.fromY_m_dOrNull(raw)?.let { LibrusAttendanceDate(it, unknown = false) }
        ?: LibrusAttendanceDate(Date(2019, 1, 1), unknown = true)
