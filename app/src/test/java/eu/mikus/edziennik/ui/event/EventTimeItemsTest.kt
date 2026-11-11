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

    @Test fun `an empty day ends with the no-timetable row`() {
        val items = EventTimeItems.build(emptyList(), labels)
        assertEquals(listOf("All day", "Custom…", "No timetable"), items.map { it.text })
    }

    @Test fun `a no-lessons row is a plain label row`() {
        val items = EventTimeItems.build(listOf(lesson(1L, type = Lesson.TYPE_NO_LESSONS)), labels)
        assertEquals("No lessons", items[2].text)
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
}
