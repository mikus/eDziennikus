/*
 * Copyright (c) Mikolaj Olszewski 2026-7-2.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.AttendanceFull
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.data.db.full.isTimetableBlock
import eu.mikus.edziennik.data.db.full.knownSubjectId
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
        stillShowUnseen: Set<Long> = emptySet(),
    ): TimetableDayUiState {
        if (lessons.isEmpty()) return TimetableDayUiState.NoTimetable(date.weekStart.stringY_m_d)

        if (lessons.size == 1 && lessons[0].type == Lesson.TYPE_NO_LESSONS) {
            return TimetableDayUiState.NoLessons(
                weekStart = date.weekStart.stringY_m_d,
                isWeekend = date.weekDay in Week.SATURDAY..Week.SUNDAY,
            )
        }

        val actual = lessons.filter { it.isTimetableBlock }
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

        // Known subjects of every block, by display start time: lets a slot's marks be told apart.
        // Each maps to whether any block of that subject in the slot ran.
        val slotOwners: Map<Int, Map<Long, Boolean>> = actual
            .groupBy { it.displayStartTime!!.value }
            .mapValues { (_, slot) ->
                slot.mapNotNull { block -> block.knownSubjectId?.let { it to !block.isCancelled } }
                    .groupBy({ it.first }) { it.second }
                    .mapValues { (_, ran) -> ran.any { it } }
            }

        // Teachers of the blocks a subject's mark may show on (see slotAttendance), by subject, by display
        // start time, for the subjects two or more such blocks share: lets those blocks' marks be told apart.
        val slotTeachers: Map<Int, Map<Long, Set<Long>>> = actual
            .groupBy { it.displayStartTime!!.value }
            .mapValues { (start, slot) ->
                val owners = slotOwners.getValue(start)
                slot.mapNotNull { block ->
                    block.knownSubjectId
                        ?.takeIf { !block.isCancelled || owners[it] != true }
                        ?.let { it to block.displayTeacherId }
                }
                    .groupBy({ it.first }) { it.second }
                    .filterValues { it.size > 1 }
                    .mapValues { (_, teachers) -> teachers.filterNotNull().toSet() }
            }

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
                    events.forLesson(lesson, actual).take(3)
                else emptyList(),
                // The block's time is never null, so an attendance whose time is unknown
                // matches no block. Marks in the block's slot are then told apart by subject.
                attendance = if (config.showAttendance)
                    slotAttendance(
                        marks = attendance.filter { it.startTime?.value == startTime.value },
                        subject = lesson.knownSubjectId,
                        teacher = lesson.displayTeacherId,
                        ran = !lesson.isCancelled,
                        slotOwners = slotOwners.getValue(startTime.value),
                        slotTeachers = slotTeachers.getValue(startTime.value),
                    )
                else null,
                annotation = classify(lesson),
                unseen = lesson.type != Lesson.TYPE_NORMAL &&
                        (!lesson.seen || lesson.id in stillShowUnseen),
            )
        }

        return TimetableDayUiState.Content(startHour, endHour, blocks)
    }

    /**
     * Picks the mark a block shows among [marks], those recorded in its slot. The block's own known
     * subject wins (unless the block did not run and a block of that subject in the slot did; see below);
     * otherwise the first mark whose subject matches no block in the slot; otherwise nothing.
     * An unknown subject (null, -1 or 0; see knownSubjectId) matches nothing: slotOwners holds only known ones.
     * A block that did not run (cancelled or shifted away) gives up its own subject's mark when a block of that
     * subject in the slot ran, and falls back to the orphan; if none of them ran, all of them keep it.
     *
     * When two or more blocks are left holding a subject's marks after that, they are told apart by teacher
     * ([teacher], collected in [slotTeachers]). A mark whose teacher one of them carries is shown only on the blocks
     * carrying it; a mark whose teacher none of them carries is shown on each of them that takes no mark of its own
     * teacher. A block still shows one mark, so when a slot holds more marks of a subject than blocks to show them,
     * some go unshown, as they did before this stage; it changes only which, preferring each block's own teacher's.
     * A lone block of its subject is not narrowed and keeps the first mark, whoever recorded it. No sentinel needs
     * reading back: a block's display teacher is null or an id the API named, never -1 or 0, so a mark of unknown
     * teacher (-1) matches and claims nothing.
     *
     * Teacher, not team: the Attendances endpoint names no team, and the Lessons assignment a mark names was a
     * whole-class one on every group lesson in a device capture, while the teacher matched the block's on all of
     * them (docs/superpowers/evidence/item80-attendance-team-source.md). Only narrowing onto a block that carries
     * the mark's teacher, never hiding on a mismatch, is what keeps a mark recorded by someone teaching neither
     * block on screen. Orphans are not narrowed: a block shows one mark, so one narrowed onto a block that shows
     * its own would be shown nowhere. Same-subject blocks with the same teacher both show the mark.
     */
    private fun slotAttendance(
        marks: List<AttendanceFull>,
        subject: Long?,
        teacher: Long?,
        ran: Boolean,
        slotOwners: Map<Long, Boolean>,
        slotTeachers: Map<Long, Set<Long>>,
    ): AttendanceFull? {
        val own = if (ran || slotOwners[subject] != true) marks.filter { it.subjectId == subject } else emptyList()
        val claimed = slotTeachers[subject].orEmpty()
        return own.firstOrNull { it.teacherId == teacher && teacher in claimed }
            ?: own.firstOrNull { it.teacherId !in claimed }
            ?: marks.firstOrNull { it.subjectId !in slotOwners }
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
