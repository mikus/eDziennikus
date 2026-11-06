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
    fun `a shifted-source block is matched on its old slot`() {
        val a = shiftedSource(1, start, end, Time(10, 0, 0), Time(10, 45, 0), 10)
        val b = normal(2, start, end, 20)
        val day = listOf(a, b)
        val events = listOf(examEvent(10, start, 10))
        assertEquals(listOf(10L), ids(events, a, day))
        assertEquals(emptyList(), ids(events, b, day))
    }

    @Test
    fun `a lesson moved to another day still claims its old slot, by value`() {
        val a = shiftedSource(1, start, end, newStart = start, newEnd = end, subject = 10)
        a.date = Date(2026, 6, 2)
        val b = normal(2, start, end, 20, date = Date(2026, 6, 1))
        val c = normal(3, start, end, 30, date = Date(2026, 6, 2))
        val day = listOf(a, b, c)
        val events = listOf(examEvent(10, start, 10), examEvent(30, start, 30))
        assertEquals(listOf(30L), ids(events, b, day))
        assertEquals(listOf(10L, 30L), ids(events, a, day))
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
    fun `a cancelled block keeps its subject's event when the same subject ran in the slot`() {
        val a = cancelled(1, start, end, 10)
        val b = shiftedTarget(2, Time(10, 0, 0), Time(10, 45, 0), start, end, 10)
        val day = listOf(a, b)
        val events = listOf(examEvent(10, start, 10))
        assertEquals(listOf(10L), ids(events, a, day))
        assertEquals(listOf(10L), ids(events, b, day))
    }

    // ===================================================================================
    // Team (item 82). A group's lesson and a group's event both name a VirtualClass, so an
    // exact match says which block of a shared slot an event belongs to.
    // ===================================================================================

    /**
     * The defect item 82 is about: one subject split into two groups, one slot, one group's
     * event. The subject rule alone makes both blocks owners of the subject, so both show it.
     */
    @Test
    fun `two blocks of the same subject show the event only on the one whose team matches`() {
        val a = normal(1, start, end, 10, team = 100)
        val b = normal(2, start, end, 10, team = 200)
        val day = listOf(a, b)
        val events = listOf(examEvent(10, start, 10, team = 100))
        assertEquals(listOf(10L), ids(events, a, day))
        assertEquals(emptyList(), ids(events, b, day))
    }

    /**
     * The shape the capture actually holds: both `VirtualClass` events in a captured `HomeWorks`
     * body had no `Subject` key at all, so they are unknown-subject events and the subject rule
     * puts them on every block. The team is the only thing that can place them.
     */
    @Test
    fun `a group event with no subject shows only on its own block`() {
        val a = normal(1, start, end, 10, team = 100)
        val b = normal(2, start, end, 20, team = 200)
        val day = listOf(a, b)
        val events = listOf(examEvent(5, start, -1, team = 200))
        assertEquals(emptyList(), ids(events, a, day))
        assertEquals(listOf(5L), ids(events, b, day))
    }

    /**
     * Nine of the 36 subject-bearing events in a captured `HomeWorks` feed carried the whole class
     * for a subject every one of whose lessons was a group lesson. No block of their slot carries
     * the class id, so nothing may
     * narrow and the event must still show - a rule that hid on any mismatch would lose them.
     */
    @Test
    fun `an event whose team no block in the slot carries shows on every candidate`() {
        val a = normal(1, start, end, 10, team = 100)
        val b = normal(2, start, end, 20, team = 200)
        val day = listOf(a, b)
        val orphan = listOf(examEvent(7, start, -1, team = 999))
        assertEquals(listOf(7L), ids(orphan, a, day))
        assertEquals(listOf(7L), ids(orphan, b, day))
        val owned = listOf(examEvent(8, start, 10, team = 999))
        assertEquals(listOf(8L), ids(owned, a, day))
        assertEquals(emptyList(), ids(owned, b, day))
    }

    /**
     * Pins that -1 and 0 are read as "no team" and never narrow. Note what this canNOT pin: the
     * block with no team carries `displayTeamId` **null**, not -1, so a rule that matched on the
     * event's raw -1 would find nothing to narrow onto and would leave these assertions green.
     * Nothing can pin that direction, because `Lesson.teamId` is never written as a sentinel -
     * `forLesson`'s KDoc says so rather than this test manufacturing a row that cannot exist.
     */
    @Test
    fun `an event with no team is not narrowed`() {
        val a = normal(1, start, end, 10)
        val b = normal(2, start, end, 10, team = 200)
        val day = listOf(a, b)
        val events = listOf(examEvent(1, start, 10, team = -1), examEvent(2, start, 10, team = 0))
        assertEquals(listOf(1L, 2L), ids(events, a, day))
        assertEquals(listOf(1L, 2L), ids(events, b, day))
    }

    @Test
    fun `a block with no team loses the event to the block that matches`() {
        val a = normal(1, start, end, 10)
        val b = normal(2, start, end, 10, team = 200)
        val day = listOf(a, b)
        val events = listOf(examEvent(20, start, 10, team = 200))
        assertEquals(emptyList(), ids(events, a, day))
        assertEquals(listOf(20L), ids(events, b, day))
    }

    /**
     * Only the slot's own blocks may claim a team. [dayLessons] is any superset - the widget hands
     * in a whole week - so a block of another slot or another day carrying the same team must not
     * take an event off the blocks that share its time, which would leave the event nowhere.
     */
    @Test
    fun `a block outside the slot does not claim an event's team`() {
        val a = normal(1, start, end, 10, team = 100)
        val otherSlot = normal(2, Time(9, 0, 0), Time(9, 45, 0), 20, team = 200)
        val otherDay = normal(3, start, end, 20, team = 200, date = Date(2026, 6, 2))
        val events = listOf(examEvent(5, start, -1, team = 200))
        assertEquals(listOf(5L), ids(events, a, listOf(a, otherSlot)))
        assertEquals(listOf(5L), ids(events, a, listOf(a, otherDay)))
    }

    /**
     * The team stage only ever narrows what the subject stage already kept, so a matching team
     * cannot hand a block an event the subject rule gave to another - and the event stays on the
     * block that owns its subject, because the block that does carry the team is not a candidate.
     */
    @Test
    fun `team never widens what the subject rule hid`() {
        val a = normal(1, start, end, 10, team = 100)
        val b = normal(2, start, end, 20, team = 200)
        val day = listOf(a, b)
        val events = listOf(examEvent(2, start, 10, team = 200))
        assertEquals(listOf(2L), ids(events, a, day))
        assertEquals(emptyList(), ids(events, b, day))
    }

    /**
     * A shifted source is drawn in its old slot, and `displayTeamId` answers `oldTeamId` for it -
     * the team it had there, not the one it has where it moved to. Reading `teamId` instead turns
     * exactly one of these red, whichever way the mutation is applied: mutate both reads and `b`
     * wrongly gains the event (nothing carries 100, so nothing narrows); mutate only the
     * `lesson.displayTeamId` on the last line and `a` wrongly loses it. Neither swaps the pair.
     */
    @Test
    fun `a shifted-source block is matched on its old team`() {
        val a = shiftedSource(1, start, end, Time(10, 0, 0), Time(10, 45, 0), 10, team = 100)
            .apply { teamId = 200 }
        val b = normal(2, start, end, 10, team = 200)
        val day = listOf(a, b)
        val events = listOf(examEvent(10, start, 10, team = 100))
        assertEquals(listOf(10L), ids(events, a, day))
        assertEquals(emptyList(), ids(events, b, day))
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
