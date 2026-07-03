/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull

/** State of a single timetable day. Bundle-safe keys (Int) are used at the pager, not here. */
sealed interface TimetableDayUiState {
    data object Loading : TimetableDayUiState
    /** No timetable synced for the week. [weekStart] = the week's Monday as `Y_m_d` (sync arg). */
    data class NoTimetable(val weekStart: String) : TimetableDayUiState
    /** A single TYPE_NO_LESSONS marker. [isWeekend] hides the sync button (Sat/Sun). */
    data class NoLessons(val weekStart: String, val isWeekend: Boolean) : TimetableDayUiState
    data class Content(
        val startHour: Int,
        val endHour: Int,
        val blocks: List<PositionedLesson>,
    ) : TimetableDayUiState
}

/** One placed lesson: geometry (minutes + overlap columns), matched events/attendance, and its
 *  classified annotation. The block composable maps [annotation] to a string/colour at the edge. */
data class PositionedLesson(
    val lesson: LessonFull,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columnCount: Int,
    val events: List<EventFull>,
    val attendance: AttendanceFull?,
    val annotation: LessonAnnotation,
    val unseen: Boolean,
)

/** Pure, Android-free classification of a lesson's change state — port of the legacy
 *  TimetableManager.getAnnotation matrix. Carries only booleans/strings; the composable resolves
 *  string resources, colours, and old→new strikethrough. */
sealed interface LessonAnnotation {
    data object None : LessonAnnotation
    data object Cancelled : LessonAnnotation
    /** TYPE_CHANGE: which id-vs-old-id fields changed (drives the annotation text + strikethrough). */
    data class Changed(
        val subject: Boolean,
        val teacher: Boolean,
        val team: Boolean,
        val classroom: Boolean,
    ) : LessonAnnotation
    /** SHIFTED_SOURCE ([isSource]=true) / SHIFTED_TARGET. Args name the other day/time (nullable). */
    data class Shifted(
        val isSource: Boolean,
        val otherDateArg: String?,
        val otherTimeArg: String?,
    ) : LessonAnnotation
}
