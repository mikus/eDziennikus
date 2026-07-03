/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Week

/**
 * Pure, Android-free classifier: turns a day's lessons (+ same-day events/attendance snapshots)
 * into a [TimetableDayUiState]. Ports the legacy TimetableDayFragment.processLessonList +
 * TimetableManager.getAnnotation logic. No Context, no getString, no dp — geometry in minutes.
 */
object TimetableDayBuilder {

    data class Config(
        val trimHourRange: Boolean,
        val showEvents: Boolean,
        val showAttendance: Boolean,
        val defaultStartHour: Int,
        val defaultEndHour: Int,
    )

    fun build(
        date: Date,
        lessons: List<LessonFull>,
        events: List<EventFull>,
        attendance: List<AttendanceFull>,
        config: Config,
    ): TimetableDayUiState {
        if (lessons.isEmpty()) return TimetableDayUiState.NoTimetable(date.weekStart.stringY_m_d)

        if (lessons.size == 1 && lessons[0].type == Lesson.TYPE_NO_LESSONS) {
            return TimetableDayUiState.NoLessons(
                weekStart = date.weekStart.stringY_m_d,
                isWeekend = date.weekDay in Week.SATURDAY..Week.SUNDAY,
            )
        }

        val actual = lessons.filter { it.type != Lesson.TYPE_NO_LESSONS && it.displayStartTime != null && it.displayEndTime != null }
        if (actual.isEmpty()) return TimetableDayUiState.NoTimetable(date.weekStart.stringY_m_d)

        // hour range (mirror legacy: fallback of DEFAULT_END/START keeps null-timed lessons from skewing bounds)
        val minStartHour = actual.minOf { it.displayStartTime?.hour ?: config.defaultEndHour }
        val maxEndHour = actual.maxOf { it.displayEndTime?.hour?.plus(1) ?: config.defaultStartHour }
        val startHour: Int
        val endHour: Int
        if (config.trimHourRange) {
            startHour = minStartHour
            endHour = maxEndHour
        } else {
            startHour = minOf(config.defaultStartHour, minStartHour)
            endHour = maxOf(config.defaultEndHour, maxEndHour)
        }

        val spans = actual.map {
            TimetableArrange.LessonSpan(it.displayStartTime!!.inMinutes, it.displayEndTime!!.inMinutes)
        }
        val positions = TimetableArrange.arrange(spans)

        val blocks = actual.mapIndexed { i, lesson ->
            val pos = positions[i]
            val startTime = lesson.displayStartTime!!
            PositionedLesson(
                lesson = lesson,
                startMinute = startTime.inMinutes,
                endMinute = lesson.displayEndTime!!.inMinutes,
                column = pos.column,
                columnCount = pos.columnCount,
                events = if (config.showEvents)
                    events.filter { it.time?.value == startTime.value }.take(3)
                else emptyList(),
                attendance = if (config.showAttendance)
                    attendance.firstOrNull { it.startTime?.value == lesson.startTime?.value }
                else null,
                annotation = classify(lesson),
                unseen = lesson.type != Lesson.TYPE_NORMAL && !lesson.seen,
            )
        }

        return TimetableDayUiState.Content(startHour, endHour, blocks)
    }

    private fun classify(lesson: LessonFull): LessonAnnotation = when (lesson.type) {
        Lesson.TYPE_CANCELLED -> LessonAnnotation.Cancelled
        Lesson.TYPE_CHANGE -> LessonAnnotation.Changed(
            subject = lesson.subjectId != lesson.oldSubjectId,
            teacher = lesson.teacherId != lesson.oldTeacherId,
            team = lesson.teamId != lesson.oldTeamId,
            classroom = lesson.classroom != lesson.oldClassroom,
        )
        Lesson.TYPE_SHIFTED_SOURCE -> LessonAnnotation.Shifted(
            isSource = true,
            otherDateArg = if (lesson.date != lesson.oldDate && lesson.date != null) lesson.date?.stringY_m_d else null,
            otherTimeArg = when {
                lesson.date != lesson.oldDate && lesson.date != null -> lesson.startTime?.stringHM
                lesson.startTime != lesson.oldStartTime && lesson.startTime != null -> lesson.startTime?.stringHM
                else -> null
            },
        )
        Lesson.TYPE_SHIFTED_TARGET -> LessonAnnotation.Shifted(
            isSource = false,
            otherDateArg = if (lesson.date != lesson.oldDate && lesson.oldDate != null) lesson.oldDate?.stringY_m_d else null,
            otherTimeArg = when {
                lesson.date != lesson.oldDate && lesson.oldDate != null -> lesson.oldStartTime?.stringHM
                lesson.startTime != lesson.oldStartTime && lesson.oldStartTime != null -> lesson.oldStartTime?.stringHM
                else -> null
            },
        )
        else -> LessonAnnotation.None
    }
}
