/*
 * Copyright (c) Mikolaj Olszewski 2026-11-11.
 */
package eu.mikus.edziennik.ui.event

import eu.mikus.edziennik.data.db.entity.Lesson
import eu.mikus.edziennik.data.db.full.LessonFull
import eu.mikus.edziennik.ui.dialogs.base.FormDropdownItem
import eu.mikus.edziennik.utils.models.Time
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class EventTimeItemsTest {

    private val labels = EventTimeItems.Labels(
        allDay = "All day",
        customTime = "Custom…",
        noTimetable = "No timetable",
        noLessons = "No lessons",
    )

    private fun lesson(
        id: Long,
        start: Time? = Time(8, 0, 0),
        subject: String? = "Maths",
        subjectId: Long? = 10L,
        type: Int = Lesson.TYPE_NORMAL,
    ): LessonFull = LessonFull(1, id).apply {
        this.type = type
        startTime = start
        subjectName = subject
        this.subjectId = subjectId
    }

    private fun List<FormDropdownItem>.selected(id: Long) = first { it.id == id }

    // ---- characterisation: behaviour the fix keeps ----

    @Test fun `rows are all-day, custom trigger, then one per lesson with time and subject`() {
        val a = lesson(100L, Time(8, 0, 0), "Maths")
        val b = lesson(200L, Time(8, 55, 0), null)
        val items = EventTimeItems.build(listOf(a, b), labels)

        assertEquals(listOf("All day", "Custom…", "8:00 - Maths", "8:55"), items.map { it.text })
        assertSame(TimeSelection.AllDay, items[0].tag)
        assertEquals(null, items[1].tag)
        assertSame(a, items[2].tag)
        assertSame(b, items[3].tag)
    }

    @Test fun `a cancelled lesson's row shows its old start time and subject`() {
        val cancelled = LessonFull(1, 300L).apply {
            type = Lesson.TYPE_CANCELLED
            oldStartTime = Time(8, 0, 0)
            oldSubjectName = "Physics"
        }
        val items = EventTimeItems.build(listOf(cancelled), labels)

        assertEquals("8:00 - Physics", items.first { it.tag === cancelled }.text)
    }

    @Test fun `picking the same custom time twice keeps one row`() {
        val once = EventTimeItems.select(EventTimeItems.build(listOf(first), labels), Time(14, 30, 0))
        val twice = EventTimeItems.select(once.items, Time(14, 30, 0))

        assertEquals(once.items, twice.items)
        assertEquals(once.selectedId, twice.selectedId)
    }

    @Test fun `a second, different custom time gets its own row and is the one selected`() {
        val earlier = EventTimeItems.select(EventTimeItems.build(emptyList(), labels), Time(14, 30, 0))
        val later = EventTimeItems.select(earlier.items, Time(15, 0, 0))

        assertEquals(TimeSelection.Custom(Time(15, 0, 0)), later.items.selected(later.selectedId).tag)
        assertEquals(earlier.items.size + 1, later.items.size)
    }

    @Test fun `an empty day ends with the no-timetable row`() {
        val items = EventTimeItems.build(emptyList(), labels)
        assertEquals(listOf("All day", "Custom…", "No timetable"), items.map { it.text })
    }

    @Test fun `a no-lessons row is a plain label row`() {
        val items = EventTimeItems.build(listOf(lesson(1L, type = Lesson.TYPE_NO_LESSONS)), labels)
        assertEquals(listOf("All day", "Custom…", "No lessons"), items.map { it.text })
        assertEquals(null, items[2].tag)
    }

    @Test fun `a time with no row appends a custom row and selects it`() {
        val items = EventTimeItems.build(listOf(lesson(100L)), labels)
        val sel = EventTimeItems.select(items, Time(14, 30, 0))

        assertEquals(items.size + 1, sel.items.size)
        val row = sel.items.selected(sel.selectedId)
        assertEquals("14:30", row.text)
        assertEquals(TimeSelection.Custom(Time(14, 30, 0)), row.tag)
    }

    @Test fun `a time equal to a lesson start selects that lesson's row`() {
        val a = lesson(100L, Time(8, 0, 0))
        val items = EventTimeItems.build(listOf(a), labels)
        val sel = EventTimeItems.select(items, Time(8, 0, 0))

        assertEquals(items, sel.items)
        assertSame(a, sel.items.selected(sel.selectedId).tag)
    }

    // ---- item 79: every row has its own id, and a selection round-trips ----

    // Two lessons in one slot (a group split, or a shifted lesson landing on a taken slot).
    private val first = lesson(100L, Time(8, 0, 0), "Maths", subjectId = 10L)
    private val second = lesson(200L, Time(8, 0, 0), "Physics", subjectId = 11L)

    @Test fun `co-slotted lessons get distinct ids`() {
        val items = EventTimeItems.build(listOf(first, second), labels)
        val firstRow = items.first { it.tag === first }
        val secondRow = items.first { it.tag === second }

        // FormDropdown displays items.firstOrNull { it.id == selectedId }: tapping the second row
        // must display the second row.
        assertSame(secondRow, items.selected(secondRow.id))
        assertSame(firstRow, items.selected(firstRow.id))
    }

    @Test fun `every id in a built list is unique`() {
        val items = EventTimeItems.build(
            listOf(
                first,
                second,
                lesson(300L, start = null),
                lesson(400L, Time(0, 0, 0)),
                lesson(1L, type = Lesson.TYPE_NO_LESSONS),
                lesson(2L, type = Lesson.TYPE_NO_LESSONS),
            ),
            labels,
        )
        val withCustom = EventTimeItems.select(items, Time(0, 0, 0)).items +
            EventTimeItems.select(items, Time(12, 34, 0)).items.last()

        assertEquals(withCustom.size, withCustom.map { it.id }.toSet().size, withCustom.toString())
    }

    @Test fun `a lesson with no start time does not take the custom-time trigger's id`() {
        val noStart = lesson(300L, start = null)
        val items = EventTimeItems.build(listOf(noStart), labels)

        assertSame(noStart, items.selected(items.first { it.tag === noStart }.id).tag)
    }

    @Test fun `a custom midnight time is not all-day`() {
        val items = EventTimeItems.build(listOf(first), labels)
        val sel = EventTimeItems.select(items, Time(0, 0, 0))

        assertEquals(TimeSelection.Custom(Time(0, 0, 0)), sel.items.selected(sel.selectedId).tag)
    }

    @Test fun `a known lesson selects its own row, not the first at its start`() {
        val items = EventTimeItems.build(listOf(first, second), labels)
        val sel = EventTimeItems.select(items, Time(8, 0, 0), preferLessonId = second.id)

        assertEquals(items, sel.items)
        assertSame(second, sel.items.selected(sel.selectedId).tag)
    }

    @Test fun `a preferred subject selects that subject's row at a shared start`() {
        // Reopening an event saved against the second lesson: the event keeps its subject, not the lesson.
        val items = EventTimeItems.build(listOf(first, second), labels)
        val sel = EventTimeItems.select(items, Time(8, 0, 0), preferSubjectId = 11L)

        assertEquals(items, sel.items)
        assertSame(second, sel.items.selected(sel.selectedId).tag)
    }

    @Test fun `a time with no preference selects the first lesson row at that start`() {
        val items = EventTimeItems.build(listOf(first, second), labels)
        val sel = EventTimeItems.select(items, Time(8, 0, 0))

        assertSame(first, sel.items.selected(sel.selectedId).tag)
    }

    @Test fun `no subject preference does not pick a lesson that has no subject`() {
        // A new event has no subject to prefer: null must not match a lesson whose subject is null.
        val noSubject = lesson(300L, Time(8, 0, 0), subject = null, subjectId = null)
        val items = EventTimeItems.build(listOf(first, noSubject), labels)
        val sel = EventTimeItems.select(items, Time(8, 0, 0))

        assertSame(first, sel.items.selected(sel.selectedId).tag)
    }

    @Test fun `a subject preference only looks at lessons starting at the requested time`() {
        // Reopening a Physics event saved at 8:00 must not jump to the 10:00 Physics lesson.
        val physicsLater = lesson(200L, Time(10, 0, 0), "Physics", subjectId = 11L)
        val items = EventTimeItems.build(listOf(first, physicsLater), labels)
        val sel = EventTimeItems.select(items, Time(8, 0, 0), preferSubjectId = 11L)

        assertSame(first, sel.items.selected(sel.selectedId).tag)
    }

    @Test fun `a custom time on a day without a timetable gets its own row`() {
        val items = EventTimeItems.build(emptyList(), labels)
        val sel = EventTimeItems.select(items, Time(0, 0, 0))

        assertEquals(TimeSelection.Custom(Time(0, 0, 0)), sel.items.selected(sel.selectedId).tag)
        assertEquals(sel.items.size, sel.items.map { it.id }.toSet().size)
    }

    // ---- a timetable reload (a sync finishing while the dialog is open) ----

    // A sync that changes a lesson's teacher, team, classroom or type gives it a new Lesson.id
    // (Lesson.buildId hashes them), so the held id can vanish from the rebuilt list.

    @Test fun `a held lesson whose id changed in a sync is found again by its start and subject`() {
        val heldRow = EventTimeItems.build(listOf(first, second), labels).first { it.tag === second }
        val physicsResynced = lesson(999L, Time(8, 0, 0), "Physics", subjectId = 11L)
        val reloaded = EventTimeItems.build(listOf(first, physicsResynced), labels)

        val sel = EventTimeItems.reselect(reloaded, heldRow)

        assertEquals(reloaded, sel?.items)
        assertSame(physicsResynced, sel?.items?.selected(sel.selectedId)?.tag)
    }

    @Test fun `a held row still in the rebuilt list keeps its id`() {
        val items = EventTimeItems.build(listOf(first), labels)
        val allDay = items.first { it.id == EventTimeItems.ALL_DAY_ID }

        assertEquals(EventTimeItems.ALL_DAY_ID, EventTimeItems.reselect(items, allDay)?.selectedId)
    }

    @Test fun `a held custom time is kept across a reload`() {
        val picked = EventTimeItems.select(EventTimeItems.build(listOf(first), labels), Time(14, 30, 0))
        val heldRow = picked.items.selected(picked.selectedId)
        val reloaded = EventTimeItems.build(listOf(first), labels)

        val sel = EventTimeItems.reselect(reloaded, heldRow)

        assertEquals(TimeSelection.Custom(Time(14, 30, 0)), sel?.items?.selected(sel.selectedId)?.tag)
    }

    @Test fun `a held lesson with no lesson left at its start time is dropped`() {
        val heldRow = EventTimeItems.build(listOf(first), labels).first { it.tag === first }
        val moved = lesson(999L, Time(9, 0, 0), "Maths", subjectId = 10L)

        assertEquals(null, EventTimeItems.reselect(EventTimeItems.build(listOf(moved), labels), heldRow))
    }

    @Test fun `a held lesson whose id survived the sync keeps its own row`() {
        // The usual sync: nothing about the lesson changed. Hold the second row, not the first, so
        // "the first row" and "the held row" are different answers.
        val items = EventTimeItems.build(listOf(first, second), labels)
        val heldRow = items.first { it.tag === second }
        val reloaded = EventTimeItems.build(listOf(first, second), labels)

        val sel = EventTimeItems.reselect(reloaded, heldRow)

        assertSame(second, sel?.items?.selected(sel.selectedId)?.tag)
    }

    @Test fun `a held cancelled lesson is found again by its old start time and subject`() {
        // A cancelled lesson carries only old* fields (LibrusApiTimetables), so only the display*
        // getters know its start time and subject.
        fun cancelled(id: Long) = LessonFull(1, id).apply {
            type = Lesson.TYPE_CANCELLED
            oldStartTime = Time(8, 0, 0)
            oldSubjectId = 11L
            oldSubjectName = "Physics"
        }
        val heldRow = EventTimeItems.build(listOf(first, cancelled(200L)), labels).first { it.id == 200L }
        val resynced = cancelled(999L)
        val reloaded = EventTimeItems.build(listOf(first, resynced), labels)

        val sel = EventTimeItems.reselect(reloaded, heldRow)

        assertSame(resynced, sel?.items?.selected(sel.selectedId)?.tag)
    }

    @Test fun `a held custom time now taken by a lesson selects the lesson row, as select does`() {
        val picked = EventTimeItems.select(EventTimeItems.build(listOf(first), labels), Time(14, 30, 0))
        val heldRow = picked.items.selected(picked.selectedId)
        val movedIn = lesson(300L, Time(14, 30, 0), "Physics", subjectId = 11L)
        val reloaded = EventTimeItems.build(listOf(first, movedIn), labels)

        val sel = EventTimeItems.reselect(reloaded, heldRow)

        assertEquals(reloaded, sel?.items)
        assertSame(movedIn, sel?.items?.selected(sel.selectedId)?.tag)
    }

    @Test fun `a held lesson of a same-subject group split keeps its own row`() {
        // Two groups of one subject in one slot: start time and subject cannot tell them apart, so
        // only the surviving id can.
        val group1 = lesson(100L, Time(8, 0, 0), "English", subjectId = 12L)
        val group2 = lesson(200L, Time(8, 0, 0), "English", subjectId = 12L)
        val heldRow = EventTimeItems.build(listOf(group1, group2), labels).first { it.tag === group2 }
        val reloaded = EventTimeItems.build(listOf(group1, group2), labels)

        val sel = EventTimeItems.reselect(reloaded, heldRow)

        assertSame(group2, sel?.items?.selected(sel.selectedId)?.tag)
    }

    @Test fun `nothing held reselects nothing`() {
        assertEquals(null, EventTimeItems.reselect(EventTimeItems.build(listOf(first), labels), null))
    }
}
