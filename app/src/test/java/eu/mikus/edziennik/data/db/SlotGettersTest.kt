/*
 * Copyright (c) Mikolaj Olszewski 2026-11-5.
 */

package eu.mikus.edziennik.data.db

import eu.mikus.edziennik.data.db.entity.Event
import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.entity.knownSubjectId
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.data.db.full.isTimetableBlock
import eu.mikus.edziennik.data.db.full.knownSubjectId
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlotGettersTest {

    private fun lesson(
        type: Int = Lesson.TYPE_NORMAL,
        subject: Long? = null,
        start: Time? = Time(8, 0, 0),
        end: Time? = Time(8, 45, 0),
    ): LessonFull = LessonFull(1, 1L).apply {
        this.type = type
        subjectId = subject
        startTime = start
        endTime = end
    }

    private fun event(subject: Long): Event =
        Event(1, 1L, Date(2026, 6, 1), Time(8, 0, 0), "topic", null, Event.TYPE_DEFAULT, 0L, subject, 0L)

    @Test
    fun `a lesson's known subject is its display subject`() {
        assertEquals(10L, lesson(subject = 10L).knownSubjectId)
    }

    @Test
    fun `a lesson moved away is known by its old subject`() {
        val moved = lesson(type = Lesson.TYPE_SHIFTED_SOURCE, subject = 20L).apply { oldSubjectId = 10L }
        assertEquals(10L, moved.knownSubjectId)
    }

    @Test
    fun `a lesson's subject of null, -1 or 0 is unknown`() {
        assertNull(lesson(subject = null).knownSubjectId)
        assertNull(lesson(subject = -1L).knownSubjectId)
        assertNull(lesson(subject = 0L).knownSubjectId)
    }

    @Test
    fun `an event's subject of -1 or 0 is unknown, a positive one is known`() {
        assertNull(event(-1L).knownSubjectId)
        assertNull(event(0L).knownSubjectId)
        assertEquals(10L, event(10L).knownSubjectId)
    }

    @Test
    fun `a lesson with both display times is a timetable block`() {
        assertTrue(lesson().isTimetableBlock)
    }

    @Test
    fun `a no-lessons row, or a lesson missing either display time, is not a block`() {
        assertFalse(lesson(type = Lesson.TYPE_NO_LESSONS).isTimetableBlock)
        assertFalse(lesson(start = null).isTimetableBlock)
        assertFalse(lesson(end = null).isTimetableBlock)
    }
}
