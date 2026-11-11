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
}
