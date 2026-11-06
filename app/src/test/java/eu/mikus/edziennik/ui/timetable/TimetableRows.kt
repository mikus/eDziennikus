/*
 * Copyright (c) Mikolaj Olszewski 2026-11-5.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time

internal val FIXTURE_DATE = Date(2026, 6, 1)

// The four builders below are real LessonFull rows, in the shape LibrusApiTimetables.parseLesson
// stores: displayStartTime is computed from type, never stubbed alongside startTime.
internal fun cancelled(id: Long, oldStart: Time, oldEnd: Time, subject: Long? = null, team: Long? = null, date: Date = FIXTURE_DATE): LessonFull =
    LessonFull(1, id).apply {
        type = Lesson.TYPE_CANCELLED
        oldSubjectId = subject
        oldTeamId = team
        oldDate = date
        oldStartTime = oldStart
        oldEndTime = oldEnd
        seen = true
    }

internal fun shiftedSource(
    id: Long, oldStart: Time, oldEnd: Time, newStart: Time, newEnd: Time, subject: Long? = null, team: Long? = null,
    date: Date = FIXTURE_DATE,
): LessonFull =
    LessonFull(1, id).apply {
        type = Lesson.TYPE_SHIFTED_SOURCE
        oldSubjectId = subject
        subjectId = subject
        oldTeamId = team
        teamId = team
        oldDate = date
        oldStartTime = oldStart
        oldEndTime = oldEnd
        this.date = date
        startTime = newStart
        endTime = newEnd
        seen = true
    }

internal fun normal(id: Long, start: Time, end: Time, subject: Long? = null, team: Long? = null, date: Date = FIXTURE_DATE): LessonFull =
    LessonFull(1, id).apply {
        type = Lesson.TYPE_NORMAL
        subjectId = subject
        teamId = team
        this.date = date
        startTime = start
        endTime = end
        seen = true
    }

/**
 * A lesson moved INTO this slot from elsewhere: the shape `LibrusApiTimetables` stores when a
 * substitution's date or lesson number differs from the original's. It is the only non-cancelled
 * type that can share a slot with a cancellation, and unlike [normal] it is not TYPE_NORMAL — so
 * it is what tells `ran` apart from "is a plain lesson".
 */
internal fun shiftedTarget(
    id: Long, oldStart: Time, oldEnd: Time, newStart: Time, newEnd: Time, subject: Long? = null, team: Long? = null,
    date: Date = FIXTURE_DATE,
): LessonFull =
    LessonFull(1, id).apply {
        type = Lesson.TYPE_SHIFTED_TARGET
        subjectId = subject
        oldSubjectId = subject
        teamId = team
        oldTeamId = team
        oldDate = date
        oldStartTime = oldStart
        oldEndTime = oldEnd
        this.date = date
        startTime = newStart
        endTime = newEnd
        seen = true
    }

internal fun examEvent(id: Long, time: Time?, subject: Long, team: Long = -1, date: Date = FIXTURE_DATE): EventFull =
    EventFull(1, id, date, time, "e$id", null, Event.TYPE_EXAM, -1, subject, team)
