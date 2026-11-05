/*
 * Copyright (c) Mikolaj Olszewski 2026-11-5.
 */

package eu.mikus.edziennik.ui.timetable

import eu.mikus.edziennik.data.db.full.EventFull
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.utils.models.Date
import eu.mikus.edziennik.utils.models.Time
import kotlin.test.Test
import kotlin.test.assertEquals

class LessonEventsTest {

    private val start = Time(8, 0, 0)
    private val end = Time(8, 45, 0)

    private fun ids(events: List<EventFull>, lesson: LessonFull, day: List<LessonFull>) =
        events.forLesson(lesson, day).map { it.id }

    @Test
    fun `an event at another time shows on no lesson`() {
        val a = normal(1, start, end, 10)
        assertEquals(emptyList(), ids(listOf(examEvent(1, Time(9, 0, 0), 10)), a, listOf(a)))
    }

    @Test
    fun `an event with no time shows on no lesson`() {
        val a = normal(1, start, end, 10)
        assertEquals(emptyList(), ids(listOf(examEvent(1, null, 10)), a, listOf(a)))
    }

    @Test
    fun `alone in its slot a lesson shows every event at its time`() {
        val a = normal(1, start, end, 10)
        val events = listOf(examEvent(1, start, 10), examEvent(2, start, 30), examEvent(3, start, -1))
        assertEquals(listOf(1L, 2L, 3L), ids(events, a, listOf(a)))
    }

    @Test
    fun `an event of the other block's subject is not shown`() {
        val a = normal(1, start, end, 10)
        val b = normal(2, start, end, 20)
        val events = listOf(examEvent(10, start, 10), examEvent(20, start, 20))
        assertEquals(listOf(10L), ids(events, a, listOf(a, b)))
        assertEquals(listOf(20L), ids(events, b, listOf(a, b)))
    }

    @Test
    fun `an event whose subject has no block in the slot shows on every block`() {
        val a = normal(1, start, end, 10)
        val b = normal(2, start, end, 20)
        val events = listOf(examEvent(30, start, 30))
        assertEquals(listOf(30L), ids(events, a, listOf(a, b)))
        assertEquals(listOf(30L), ids(events, b, listOf(a, b)))
    }

    @Test
    fun `an event of unknown subject shows on every block`() {
        val a = normal(1, start, end, 10)
        val b = normal(2, start, end, 20)
        val events = listOf(examEvent(1, start, -1), examEvent(2, start, 0))
        assertEquals(listOf(1L, 2L), ids(events, a, listOf(a, b)))
        assertEquals(listOf(1L, 2L), ids(events, b, listOf(a, b)))
    }

    @Test
    fun `a block of unknown subject does not take a known block's event`() {
        val a = normal(1, start, end, 10)
        val b = normal(2, start, end, null)
        val day = listOf(a, b)
        assertEquals(listOf(10L), ids(listOf(examEvent(10, start, 10)), a, day))
        assertEquals(emptyList(), ids(listOf(examEvent(10, start, 10)), b, day))
        assertEquals(listOf(5L), ids(listOf(examEvent(5, start, -1)), a, day))
        assertEquals(listOf(5L), ids(listOf(examEvent(5, start, -1)), b, day))
    }

    @Test
    fun `a block whose subject is -1 or 0 owns no event`() {
        val a = normal(1, start, end, 10)
        val b = normal(2, start, end, -1)
        val c = normal(3, start, end, 0)
        val day = listOf(a, b, c)
        val events = listOf(examEvent(1, start, -1), examEvent(2, start, 0), examEvent(3, start, 10))
        assertEquals(listOf(1L, 2L, 3L), ids(events, a, day))
        assertEquals(listOf(1L, 2L), ids(events, b, day))
        assertEquals(listOf(1L, 2L), ids(events, c, day))
    }

    @Test
    fun `a cancelled block keeps its own subject's event`() {
        val a = cancelled(1, start, end, 10)
        val b = shiftedTarget(2, Time(10, 0, 0), Time(10, 45, 0), start, end, 20)
        val day = listOf(a, b)
        val events = listOf(examEvent(10, start, 10))
        assertEquals(listOf(10L), ids(events, a, day))
        assertEquals(emptyList(), ids(events, b, day))
    }

    @Test
    fun `a shifted-source block is matched on its old subject and slot`() {
        val a = shiftedSource(1, start, end, Time(10, 0, 0), Time(10, 45, 0), 10)
        val b = normal(2, start, end, 20)
        val day = listOf(a, b)
        val events = listOf(examEvent(10, start, 10))
        assertEquals(listOf(10L), ids(events, a, day))
        assertEquals(emptyList(), ids(events, b, day))
    }

    @Test
    fun `two blocks of the same subject both show its event`() {
        val a = normal(1, start, end, 10)
        val b = normal(2, start, end, 10)
        val c = normal(3, start, end, 20)
        val events = listOf(examEvent(10, start, 10))
        assertEquals(listOf(10L), ids(events, a, listOf(a, b, c)))
        assertEquals(listOf(10L), ids(events, b, listOf(a, b, c)))
        assertEquals(emptyList(), ids(events, c, listOf(a, b, c)))
    }

    @Test
    fun `an event's team never changes where it shows`() {
        val a = normal(1, start, end, 10).apply { teamId = 5 }
        val b = normal(2, start, end, 20).apply { teamId = 6 }
        val day = listOf(a, b)
        val unknown = listOf(examEvent(1, start, -1, team = 5))
        assertEquals(listOf(1L), ids(unknown, a, day))
        assertEquals(listOf(1L), ids(unknown, b, day))
        val known = listOf(examEvent(2, start, 10, team = 6))
        assertEquals(listOf(2L), ids(known, a, day))
        assertEquals(emptyList(), ids(known, b, day))
    }

    @Test
    fun `a lesson of another slot does not claim this one`() {
        val a = normal(1, start, end, 10)
        val b = normal(2, Time(9, 0, 0), Time(9, 45, 0), 20)
        assertEquals(listOf(20L), ids(listOf(examEvent(20, start, 20)), a, listOf(a, b)))
    }

    @Test
    fun `a lesson on another day does not claim this one`() {
        val a = normal(1, start, end, 10)
        val b = normal(2, start, end, 20, date = Date(2026, 6, 2))
        assertEquals(listOf(20L), ids(listOf(examEvent(20, start, 20)), a, listOf(a, b)))
    }

    @Test
    fun `a lesson with no end time does not claim the slot`() {
        val a = normal(1, start, end, 10)
        val b = normal(2, start, end, 20).apply { endTime = null }
        assertEquals(listOf(20L), ids(listOf(examEvent(20, start, 20)), a, listOf(a, b)))
    }
}
