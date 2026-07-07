/*
 * Copyright (c) Mikolaj Olszewski 2026-7-6.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Time

/** Pure render model for the Home timetable card, derived from the lesson window + current time.
 *  (NotPublic is a profile flag handled at the composable edge, not a builder state.) */
sealed interface TimetableHomeUiState {
    /** Not synced for the resolved week; [weekStart] = that week's Monday as `Y_m_d` (sync arg). */
    data class NoTimetable(val weekStart: String) : TimetableHomeUiState
    /** Only a NO_LESSONS marker (or all cancelled) in the next 7 days. */
    data object NoLessons : TimetableHomeUiState
    data class Content(
        val mode: Mode,
        val dayInfoArgs: List<Any>,      // [dayName, dateStr] for future modes; empty for TODAY
        val lessonCount: Int,
        val firstStart: String?,         // HH:MM (future mode's static counter)
        val lastEnd: String?,            // HH:MM
        val firstLesson: LessonFull,     // the big "first" lesson
        val counterStart: Time?,         // live-counter window (TODAY mode only)
        val counterEnd: Time?,
        val showAllLessons: Boolean,
        val nextLessons: List<NextLesson>,
    ) : TimetableHomeUiState

    enum class Mode { TODAY, TOMORROW, THIS_WEEK, FUTURE }
    data class NextLesson(val startHM: String?, val lesson: LessonFull)
}
