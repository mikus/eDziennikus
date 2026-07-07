/*
 * Copyright (c) Mikolaj Olszewski 2026-7-6.
 */

package eu.mikus.edziennik.ui.home

import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.ext.compareTo
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import eu.mikus.edziennik.utils.models.Week

/**
 * Pure, Android-free port of the legacy HomeTimetableCard.update() day-resolution: from the
 * (already profile-filtered) 7-day lesson window pick the day to show — today's still-relevant
 * lessons, else step forward up to 7 days to the next real-lesson day — and classify.
 * [now] must be the bell-synced current time; the composable re-invokes this as [now] ticks.
 */
object TimetableHomeBuilder {

    fun build(lessons: List<LessonFull>, now: Time, today: Date): TimetableHomeUiState {
        val cursor = today.clone()
        var checkedDays = 0

        // today's still-relevant lessons (drop the day if all cancelled)
        var day = lessons.filter { it.displayDate == today && it.displayEndTime > now }
        if (day.all { it.isCancelled }) day = emptyList()

        while (
            (day.isEmpty() || day.none {
                it.type != Lesson.TYPE_NO_LESSONS &&
                    (it.displayDate != today || (it.displayEndTime != null && it.displayEndTime >= now)) &&
                    !it.isCancelled
            }) && checkedDays < 7
        ) {
            cursor.stepForward(0, 0, 1)
            day = lessons.filter { it.displayDate == cursor }.dropWhile { it.isCancelled }
            if (day.isEmpty()) break
            checkedDays++
        }

        if (day.isEmpty() && checkedDays < 7) return TimetableHomeUiState.NoTimetable(cursor.weekStart.stringY_m_d)
        if (day.none { !it.isCancelled } || (day.size == 1 && day[0].type == Lesson.TYPE_NO_LESSONS))
            return TimetableHomeUiState.NoLessons

        val actual = day.filter { it.type != Lesson.TYPE_NO_LESSONS }
        val firstLesson = actual.first { !it.isCancelled }
        val lastLesson = actual.last { !it.isCancelled }
        val skipFirst = actual.indexOf(firstLesson)
        val skipLast = actual.size - 1 - actual.indexOf(lastLesson)
        val lessonCount = actual.size - skipFirst - skipLast
        val isToday = today == cursor

        val mode: TimetableHomeUiState.Mode
        val dayInfoArgs: List<Any>
        val counterStart: Time?
        val counterEnd: Time?
        val showAllLessons: Boolean
        if (isToday) {
            mode = TimetableHomeUiState.Mode.TODAY
            dayInfoArgs = emptyList()
            counterStart = firstLesson.displayStartTime
            counterEnd = firstLesson.displayEndTime
            val ongoing = counterStart <= now && now <= counterEnd
            showAllLessons = !ongoing
        } else {
            mode = when {
                today.clone().stepForward(0, 0, 1) == cursor -> TimetableHomeUiState.Mode.TOMORROW
                today.weekStart == cursor.weekStart -> TimetableHomeUiState.Mode.THIS_WEEK
                else -> TimetableHomeUiState.Mode.FUTURE
            }
            dayInfoArgs = listOf(Week.getFullDayName(cursor.weekDay), cursor.formattedString)
            counterStart = null
            counterEnd = null
            showAllLessons = true
        }

        val next = (if (showAllLessons) actual.drop(skipFirst) else actual.drop(skipFirst + 1))
            .map { TimetableHomeUiState.NextLesson(it.displayStartTime?.stringHM, it) }

        return TimetableHomeUiState.Content(
            mode = mode,
            dayInfoArgs = dayInfoArgs,
            lessonCount = lessonCount,
            firstStart = firstLesson.displayStartTime?.stringHM,
            lastEnd = lastLesson.displayEndTime?.stringHM,
            firstLesson = firstLesson,
            counterStart = counterStart,
            counterEnd = counterEnd,
            showAllLessons = showAllLessons,
            nextLessons = next,
        )
    }
}
